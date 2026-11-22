package com.example.bricksandbeats;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zombie stages 1-15 (tag ba_stage_N). Natural zombies get the world stage (one stage per 3 days, up to 2 stages weaker).
 * Every stage makes zombies tougher, smarter (longer sight, they find you through walls, dig tunnels from stage 5)
 * and unlocks new abilities and sub-types (see MobVariants.rollStageVariant). Time to break a block depends on its hardness.
 */
public final class Stages {
	public static final int MAX = 15;

	private static final Map<Mob, Integer> STAGE = new IdentityHashMap<>();
	private static final Map<Mob, Dig> DIG = new IdentityHashMap<>();

	private static final class Dig {
		BlockPos pos;
		float progress;
	}

	private static final Item[] HEAD = {Items.LEATHER_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET};
	private static final Item[] CHEST = {Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE};
	private static final Item[] LEGS = {Items.LEATHER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS};
	private static final Item[] FEET = {Items.LEATHER_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS};

	public static int stageOf(Mob m) {
		return STAGE.getOrDefault(m, 0);
	}

	public static boolean isStageMob(Mob m) {
		return STAGE.containsKey(m);
	}

	/** Stage of a natural zombie: 1 + one per 3 world days, rolled down by 0-2. */
	public static int naturalStage(ServerLevel level) {
		long days = level.getGameTime() / 24000L;
		int base = (int) Math.min(MAX, 1 + days / 3);
		return Math.max(1, base - level.getRandom().nextInt(3));
	}

	/** Reads the ba_stage_N tag (0 when the mob has none). */
	public static int read(Mob mob) {
		for (int i = MAX; i >= 1; i--) {
			if (TagUtil.has(mob, "ba_stage_" + i)) return i;
		}
		return 0;
	}

	public static void register(Mob mob, int stage) {
		STAGE.put(mob, stage);
	}

	private static void set(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double v) {
		var a = mob.getAttribute(attr);
		if (a != null) a.setBaseValue(v);
	}

	private static int gearTier(int s) {
		return s < 2 ? -1 : s < 4 ? 0 : s < 6 ? 1 : s < 9 ? 2 : s < 13 ? 3 : 4;
	}

	/** Stats, gear and name of a plain stage zombie. */
	public static void setup(Mob mob, int s) {
		mob.setCustomNameVisible(true);
		mob.setCustomName(Component.translatable("mob.bricks_and_beats.stage_zombie", s));
		set(mob, Attributes.MAX_HEALTH, 20.0 * (1.0 + 0.35 * (s - 1)));
		set(mob, Attributes.ATTACK_DAMAGE, 3.0 * (1.0 + 0.22 * (s - 1)));
		set(mob, Attributes.MOVEMENT_SPEED, Math.min(0.34, 0.23 + 0.0075 * (s - 1)));
		set(mob, Attributes.FOLLOW_RANGE, 35.0 + 3.0 * s);
		if (s >= 8) set(mob, Attributes.KNOCKBACK_RESISTANCE, Math.min(0.8, 0.1 * (s - 7)));
		int g = gearTier(s);
		if (g >= 0) {
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(HEAD[g]));
			if (s >= 3) mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(CHEST[g]));
			if (s >= 4) mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(LEGS[g]));
			if (s >= 5) mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(FEET[g]));
		}
		if (s >= 13) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		else if (s >= 10) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		else if (s >= 6) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		if (s >= 5) mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1));
		mob.setHealth((float) mob.getAttributeValue(Attributes.MAX_HEALTH));
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e instanceof Mob m) {
				STAGE.remove(m);
				Dig d = DIG.remove(m);
				if (d != null && level instanceof ServerLevel sl) sl.destroyBlockProgress(m.getId(), d.pos, -1);
			}
		});
		// stage 12+: burning hits, stage 13+: wither on hit
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (!(source.getEntity() instanceof Mob m) || !(target instanceof Player)) return;
			int s = stageOf(m);
			if (s >= 12) target.igniteForSeconds(3.0F);
			if (s >= 13) target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
		});
	}

	private static void stop(ServerLevel level, Mob mob) {
		Dig d = DIG.remove(mob);
		if (d != null) level.destroyBlockProgress(mob.getId(), d.pos, -1);
	}

	private static boolean breakable(ServerLevel level, BlockPos p, float cap) {
		BlockState st = level.getBlockState(p);
		if (st.isAir() || !st.getFluidState().isEmpty()) return false;
		float h = st.getDestroySpeed(level, p);
		return h >= 0.0F && h <= cap;
	}

	private static void tick() {
		for (Mob mob : new ArrayList<>(STAGE.keySet())) {
			if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
				STAGE.remove(mob);
				DIG.remove(mob);
				continue;
			}
			if (mob.noPhysics) continue; // still climbing out of the ground
			int s = STAGE.get(mob);
			int t = mob.tickCount;
			if (s >= 4 && t % 40 == 0 && mob.getHealth() < mob.getMaxHealth()) mob.heal(0.5F + 0.1F * s);

			LivingEntity target = mob.getTarget();
			// smarter: from stage 3 they find the nearest player by themselves, through walls
			if ((target == null || !target.isAlive()) && s >= 3 && t % 20 == 0) {
				double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
				Player p = level.getNearestPlayer(mob, range);
				if (p != null && !p.isCreative() && !p.isSpectator()) {
					mob.setTarget(p);
					target = p;
				}
			}
			if (target == null || !target.isAlive()) {
				stop(level, mob);
				continue;
			}
			double dist = mob.distanceTo(target);
			double dx = target.getX() - mob.getX();
			double dz = target.getZ() - mob.getZ();

			if (s >= 5 && !MobVariants.isBlockBreaker(mob)) dig(level, mob, target, s);

			// stage 6+: leap
			if (s >= 6 && t % 100 == 0 && dist > 4.0 && dist < 12.0 && mob.onGround()) {
				Vec3 f = new Vec3(dx, 0, dz).normalize().scale(0.5 + 0.02 * s);
				mob.setDeltaMovement(f.x, 0.45, f.z);
				mob.hurtMarked = true;
			}
			// stage 7+: stench aura (hunger / weakness)
			if (s >= 7 && t % 40 == 0 && dist < 8.0) {
				for (Player pl : level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(4.0))) {
					pl.addEffect(new MobEffectInstance(MobEffects.HUNGER, 80, 0));
					if (s >= 11) pl.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
				}
			}
			// stage 9+: ground slam
			if (s >= 9 && t % 120 == 0 && dist < 5.0) MobVariants.slam(level, mob, MobVariants.tierOf(mob));
			// stage 10+: calls reinforcements out of the ground
			if (s >= 10 && t % 400 == 200 && dist < 20.0 && mob.hasLineOfSight(target)) {
				MobVariants.summonTriangle(level, mob.position(), dx, dz, false);
			}
			// stage 14+: blinding scream
			if (s >= 14 && t % 160 == 0 && dist < 10.0) {
				target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
			}
		}
	}

	/** Tunnelling: when stuck, breaks blocks towards the target (forward, up as stairs, down). Break time depends on hardness. */
	private static void dig(ServerLevel level, Mob mob, LivingEntity target, int s) {
		float cap = s >= 15 ? 55.0F : 2.0F * (s - 3);
		Dig d = DIG.get(mob);
		if (d == null) {
			if (mob.tickCount % 5 != 0) return;
			double dx = target.getX() - mob.getX();
			double dz = target.getZ() - mob.getZ();
			double hd = dx * dx + dz * dz;
			if (mob.distanceTo(target) > mob.getAttributeValue(Attributes.FOLLOW_RANGE)) return;
			boolean stuck = mob.horizontalCollision || mob.getNavigation().isDone();
			if (!stuck) return;
			BlockPos feet = mob.blockPosition();
			Direction dir = Direction.getApproximateNearest(dx, 0, dz);
			List<BlockPos> cand = new ArrayList<>();
			if (hd > 1.5) {
				cand.add(feet.above().relative(dir));
				cand.add(feet.relative(dir));
			}
			if (target.getY() > mob.getY() + 1.5) {
				cand.add(feet.above(2));
				cand.add(feet.above(2).relative(dir));
			}
			if (target.getY() < mob.getY() - 1.5) {
				cand.add(feet.below());
				cand.add(feet.below().relative(dir));
			}
			for (BlockPos p : cand) {
				if (breakable(level, p, cap)) {
					d = new Dig();
					d.pos = p;
					DIG.put(mob, d);
					break;
				}
			}
			if (d == null) return;
		}
		BlockState st = level.getBlockState(d.pos);
		if (!breakable(level, d.pos, cap) || mob.distanceToSqr(Vec3.atCenterOf(d.pos)) > 16.0) {
			stop(level, mob);
			return;
		}
		mob.getLookControl().setLookAt(d.pos.getX() + 0.5, d.pos.getY() + 0.5, d.pos.getZ() + 0.5);
		float h = Math.max(0.2F, st.getDestroySpeed(level, d.pos));
		d.progress += BlockBreaking.perTick(level, d.pos, st, 0.5F + 0.25F * s);
		if (mob.tickCount % 6 == 0) mob.swing(InteractionHand.MAIN_HAND);
		if (d.progress < 1.0F) {
			level.destroyBlockProgress(mob.getId(), d.pos, (int) (d.progress * 10.0F));
			return;
		}
		level.destroyBlockProgress(mob.getId(), d.pos, -1);
		level.destroyBlock(d.pos, true, mob);
		DIG.remove(mob);
	}

	private Stages() {}
}
