package com.example.bricksandbeats;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand Piston (idea: the "Against Minecraft" animation, episode 1).
 *  - left click: damage + knockback
 *  - right click: shoot the piston head in the looked-at direction (6 directions); hold to charge (1 s)
 *  - charged: 2x knockback, 2x longer head, 1.5x recoil
 *  - the shot pushes every block a vanilla piston could push (PistonStructureResolver) and throws you back
 *    the other way - except while sneaking.
 * Enchantments: Piston Power (+50 % damage / knockback per level), Sticky Piston (pulls targets instead),
 * Jet Recoil (+60 % recoil per level, for rocket-jumping).
 */
public final class HandPiston {
	public static final int CHARGE_TICKS = 20;

	/** Item with a charge-up use animation. */
	public static class PistonItem extends Item {
		public PistonItem(Properties p) {
			super(p);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity entity) {
			return 72000;
		}

		@Override
		public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
			if (level instanceof ServerLevel sl && entity instanceof Player p) {
				int used = getUseDuration(stack, entity) - timeLeft;
				shoot(sl, p, stack, used >= CHARGE_TICKS);
			}
			return true;
		}
	}

	public static boolean isPiston(ItemStack s) {
		return s.is(ModItems.HAND_PISTON);
	}

	public static void init() {
		// left click: hit + knockback (no recoil for the wielder)
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!isPiston(stack) || !(world instanceof ServerLevel sl) || !(entity instanceof LivingEntity target)) return InteractionResult.PASS;
			if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
			float power = 1.0F + 0.5F * Hooks.ench(stack, "piston_power");
			boolean sticky = Hooks.ench(stack, "sticky_piston") > 0;
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
			target.hurtServer(sl, sl.damageSources().playerAttack(player), 4.0F * power);
			double k = 1.1 * power * (sticky ? -1 : 1);
			target.push(flat.x * k, 0.3, flat.z * k);
			target.hurtMarked = true;
			sl.playSound(null, target.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.6F, 1.5F);
			player.getCooldowns().addCooldown(stack, 10);
			player.swing(hand);
			return InteractionResult.SUCCESS;
		});
		// charge feedback
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (!p.isUsingItem() || !isPiston(p.getUseItem()) || !(p.level() instanceof ServerLevel sl)) continue;
				int used = p.getUseItem().getUseDuration(p) - p.getUseItemRemainingTicks();
				if (used == CHARGE_TICKS) {
					sl.playSound(null, p.blockPosition(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.8F, 1.8F);
					sl.sendParticles(ParticleTypes.CRIT, p.getX(), p.getEyeY(), p.getZ(), 8, 0.4, 0.3, 0.4, 0.1);
				} else if (used > 0 && used < CHARGE_TICKS && used % 5 == 0) {
					sl.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getEyeY() - 0.4, p.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
				}
			}
		});
	}

	static void shoot(ServerLevel sl, Player p, ItemStack stack, boolean charged) {
		if (p.getCooldowns().isOnCooldown(stack)) return;
		Vec3 look = p.getLookAngle();
		Direction dir = Direction.getApproximateNearest(look.x, look.y, look.z);
		Vec3 d = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
		int power = Hooks.ench(stack, "piston_power");
		int recoil = Hooks.ench(stack, "piston_recoil");
		boolean sticky = Hooks.ench(stack, "sticky_piston") > 0;
		int len = charged ? 4 : 2;                           // head length in blocks (charged = 2x)
		float mult = (charged ? 2.0F : 1.0F) * (1.0F + 0.5F * power);

		// cell the shot starts from: eye level (feet level when shooting down)
		BlockPos origin = dir == Direction.DOWN ? p.blockPosition() : BlockPos.containing(p.getEyePosition());
		int reach = len;
		// first solid cell on the way: push it like a piston
		for (int i = 1; i <= len; i++) {
			BlockPos cell = origin.relative(dir, i);
			BlockState st = sl.getBlockState(cell);
			if (st.isAir() || !st.getFluidState().isEmpty()) continue;
			reach = i - 1;
			pushBlocks(sl, origin.relative(dir, i - 1), dir);
			break;
		}

		// entities in the line of the head
		Vec3 start = dir == Direction.DOWN ? p.position() : p.getEyePosition();
		AABB zone = new AABB(start, start.add(d.scale(len + 0.6))).inflate(0.7);
		List<Entity> hit = new ArrayList<>(sl.getEntitiesOfClass(LivingEntity.class, zone, e -> e != p && e.isAlive() && !MobVariants.isMinion(e)));
		for (Entity e : hit) {
			if (e instanceof LivingEntity le) le.hurtServer(sl, sl.damageSources().playerAttack(p), (charged ? 6.0F : 3.0F) * (1.0F + 0.5F * power));
			double k = 1.0 * mult * (sticky ? -0.8 : 1.0);
			e.push(d.x * k, d.y * k + (dir.getAxis().isHorizontal() ? 0.25 : 0.0), d.z * k);
			e.hurtMarked = true;
		}

		// recoil: thrown the opposite way unless sneaking
		if (!p.isShiftKeyDown()) {
			double r = (charged ? 1.2 : 0.8) * (1.0 + 0.6 * recoil);
			Vec3 v = p.getDeltaMovement();
			p.setDeltaMovement(v.x - d.x * r, v.y - d.y * r + (dir.getAxis().isHorizontal() ? 0.12 : 0.0), v.z - d.z * r);
			p.hurtMarked = true;
			p.resetFallDistance();
		}

		// visual: telescoping piston heads
		final int shown = Math.max(1, reach);
		List<Entity> heads = new ArrayList<>();
		String facing = dir.getSerializedName();
		for (int i = 1; i <= shown; i++) {
			BlockPos c = origin.relative(dir, i);
			Entity head = Fx.block(sl, "{Name:\"minecraft:piston_head\",Properties:{facing:\"" + facing + "\",short:\"false\",type:\"" + (sticky ? "sticky" : "normal") + "\"}}",
					new Vec3(c.getX(), c.getY(), c.getZ()));
			if (head != null) heads.add(head);
		}
		sl.playSound(null, p.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0F, charged ? 0.7F : 1.0F);
		Fx.later(sl, charged ? 8 : 5, () -> {
			for (Entity h : heads) if (!h.isRemoved()) h.discard();
			sl.playSound(null, p.blockPosition(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.7F, 1.0F);
		});
		p.getCooldowns().addCooldown(stack, charged ? 30 : 14);
		p.swing(InteractionHand.MAIN_HAND);
	}

	/** Moves one block one step along dir exactly like a vanilla piston would (12 blocks max, no block entities, no obsidian...). */
	private static void pushBlocks(ServerLevel sl, BlockPos pistonPos, Direction dir) {
		PistonStructureResolver r = new PistonStructureResolver(sl, pistonPos, dir, true);
		if (!r.resolve()) return;
		for (BlockPos d : r.getToDestroy()) sl.destroyBlock(d, true);
		Map<BlockPos, BlockState> moving = new LinkedHashMap<>();
		for (BlockPos q : r.getToPush()) moving.put(q, sl.getBlockState(q));
		for (BlockPos q : moving.keySet()) sl.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		for (Map.Entry<BlockPos, BlockState> en : moving.entrySet()) sl.setBlock(en.getKey().relative(dir), en.getValue(), 3);
	}

	private HandPiston() {}
}
