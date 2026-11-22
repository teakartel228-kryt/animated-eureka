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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand Piston (idea: the "Against Minecraft" animation, episode 1).
 *  - left click: quick jab, damage + knockback
 *  - right click: punch along the exact look direction; hold to charge (3 stages: tap / half / full = reach 2 / 4 / 6 blocks,
 *    damage and knockback grow with the stage)
 *  - the punch pushes the first block it meets like a vanilla piston (PistonStructureResolver)
 *  - recoil throws you opposite to the aim (shoot down = launch up, rocket jump), except while sneaking
 * Enchantments: Piston Power (+50 % damage / knockback per level), Sticky Piston (pulls targets instead),
 * Jet Recoil (+60 % recoil per level).
 */
public final class HandPiston {
	public static final int MID_TICKS = 10;   // half charge
	public static final int CHARGE_TICKS = 25; // full charge

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
				shoot(sl, p, stack, used >= CHARGE_TICKS ? 2 : used >= MID_TICKS ? 1 : 0);
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
				} else if (used == MID_TICKS) {
					sl.playSound(null, p.blockPosition(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.5F, 1.4F);
				} else if (used > 0 && used < CHARGE_TICKS && used % 5 == 0) {
					sl.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getEyeY() - 0.4, p.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
				}
			}
		});
	}

	static void shoot(ServerLevel sl, Player p, ItemStack stack, int stage) {
		if (p.getCooldowns().isOnCooldown(stack)) return;
		boolean charged = stage >= 1;
		Vec3 look = p.getLookAngle().normalize();
		// the blocks and the visual head use the nearest axis, the hit and the recoil use the exact look vector
		Direction dir = Direction.getApproximateNearest(look.x, look.y, look.z);
		int power = Hooks.ench(stack, "piston_power");
		int recoil = Hooks.ench(stack, "piston_recoil");
		boolean sticky = Hooks.ench(stack, "sticky_piston") > 0;
		int len = stage == 2 ? 6 : stage == 1 ? 4 : 2;          // reach in blocks: tap / half charge / full charge
		float mult = (1.0F + 0.8F * stage) * (1.0F + 0.5F * power);

		// cell the shot starts from: eye level (feet level when shooting down)
		BlockPos origin = dir == Direction.DOWN ? p.blockPosition() : BlockPos.containing(p.getEyePosition());
		int reach = len;
		// the block you are looking at (exact ray, any angle, not only the eye-level row)
		boolean sneak = p.isShiftKeyDown();
		int hit = -1;
		Vec3 eye = p.getEyePosition();
		BlockHitResult br = sl.clip(new ClipContext(eye, eye.add(look.scale(len)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (br.getType() == HitResult.Type.BLOCK) {
			hit = 1;
			reach = Math.max(0, Math.min(len, (int) Math.floor(eye.distanceTo(br.getLocation()))));
		}
		// normal shot: the block slides away from you, 1 / 2 / 3 cells by charge, one cell per tick, until something blocks it
		// sticky piston: the block slides towards you instead. Sneak = anchor pull, no block is moved.
		if (hit > 0 && !sneak) {
			final BlockPos start0 = br.getBlockPos();
			final Direction pd = sticky ? br.getDirection() : br.getDirection().getOpposite();
			final int steps = 1 + stage;
			final boolean[] stop = {false};
			for (int i = 1; i <= steps; i++) {
				final BlockPos pist = start0.relative(pd, i - 2);
				Runnable step = () -> {
					if (!stop[0] && !pushBlocks(sl, pist, pd)) stop[0] = true;
				};
				int delay = reach + i - 1;
				if (delay == 0) step.run(); else Fx.later(sl, delay, step);
			}
		}

		// entities: everything close to the aim line (works in any direction, not only along the axes)
		Vec3 start = p.getEyePosition();
		double range = Math.min(len, reach + 1) + 0.6;
		AABB zone = new AABB(start, start.add(look.scale(range))).inflate(1.2);
		for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, zone, x -> x != p && x.isAlive() && !MobVariants.isMinion(x))) {
			Vec3 c = e.getBoundingBox().getCenter();
			double t = Math.max(0.0, Math.min(range, c.subtract(start).dot(look)));
			double r = 0.7 + e.getBbWidth() * 0.5;
			if (start.add(look.scale(t)).distanceToSqr(c) > r * r) continue;
			e.hurtServer(sl, sl.damageSources().playerAttack(p), (3.0F + 2.5F * stage) * (1.0F + 0.5F * power));
			double k = 0.9 * mult * (sticky ? -0.7 : 1.0);
			e.push(look.x * k, look.y * k + 0.25, look.z * k);
			e.hurtMarked = true;
		}

		if (sneak && hit > 0) {
			// anchor pull
			double pull = 0.55 + 0.3 * stage;
			p.setDeltaMovement(look.x * pull, look.y * pull + 0.15, look.z * pull);
			p.hurtMarked = true;
			p.resetFallDistance();
			sl.playSound(null, p.blockPosition(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.9F, 0.8F);
		} else if (!sneak) {
			// recoil: thrown opposite to the exact aim (shoot down = launch up)
			double r = (0.6 + 0.4 * stage) * (1.0 + 0.6 * recoil);
			Vec3 v = p.getDeltaMovement();
			double hop = p.onGround() && look.y > -0.5 ? 0.1 : 0.0;
			p.setDeltaMovement(v.x - look.x * r, v.y - look.y * r + hop, v.z - look.z * r);
			p.hurtMarked = true;
			p.resetFallDistance();
		}

		// visual: the shaft extends one segment per tick, holds, then retracts the same way
		final int shown = Math.max(1, reach);
		final int hold = charged ? 3 : 1;
		final Entity[] heads = new Entity[shown + 1];
		String facing = dir.getSerializedName();
		for (int i = 1; i <= shown; i++) {
			final int k = i;
			BlockPos c = origin.relative(dir, i);
			final Vec3 at = new Vec3(c.getX(), c.getY(), c.getZ());
			String state = "{Name:\"minecraft:piston_head\",Properties:{facing:\"" + facing + "\",short:\"false\",type:\"" + (sticky ? "sticky" : "normal") + "\"}}";
			Runnable spawn = () -> {
				heads[k] = Fx.block(sl, state, at);
				sl.playSound(null, at.x, at.y, at.z, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.35F, 1.2F + 0.1F * k);
				sl.sendParticles(ParticleTypes.POOF, at.x + 0.5, at.y + 0.5, at.z + 0.5, 1, 0.1, 0.1, 0.1, 0.01);
			};
			if (i == 1) spawn.run(); else Fx.later(sl, i - 1, spawn);
			Fx.later(sl, shown + hold + (shown - i), () -> {
				if (heads[k] != null && !heads[k].isRemoved()) heads[k].discard();
				sl.playSound(null, at.x, at.y, at.z, SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.3F, 1.5F);
			});
		}
		sl.playSound(null, p.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0F, charged ? 0.7F : 1.0F);
		p.getCooldowns().addCooldown(stack, stage == 2 ? 30 : stage == 1 ? 20 : 12);
		p.swing(InteractionHand.MAIN_HAND);
	}

	/** Moves one block one step along dir exactly like a vanilla piston would (12 blocks max, no block entities, no obsidian...). */
	private static boolean pushBlocks(ServerLevel sl, BlockPos pistonPos, Direction dir) {
		PistonStructureResolver r = new PistonStructureResolver(sl, pistonPos, dir, true);
		if (!r.resolve()) return false;
		for (BlockPos d : r.getToDestroy()) sl.destroyBlock(d, true);
		Map<BlockPos, BlockState> moving = new LinkedHashMap<>();
		for (BlockPos q : r.getToPush()) moving.put(q, sl.getBlockState(q));
		for (BlockPos q : moving.keySet()) sl.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		for (Map.Entry<BlockPos, BlockState> en : moving.entrySet()) sl.setBlock(en.getKey().relative(dir), en.getValue(), 3);
		return true;
	}

	private HandPiston() {}
}
