package com.example.bricksandbeats;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Special zombies / skeletons (about 12% of natural ones, plus 1% of zombies are necromancers).
 * Zombies: brick, dynamite, necro, breaker, miner, ram, digger, bulwark. Skeletons: bard, amethyst, ink, frost, fire.
 * Helper tags: ba_rise (rising from the ground), ba_life (timed life), ba_minion (friendly to players).
 */
public final class MobVariants {
	private static final String NECRO = "ba_mob_necro";
	private static final String[] ZOMBIE = {"ba_mob_brick", "ba_mob_dynamite", "ba_mob_breaker", "ba_mob_miner",
			"ba_mob_ram", "ba_mob_digger", "ba_mob_bulwark", "ba_mob_hookz"};
	private static final String[] SKELETON = {"ba_mob_bard", "ba_mob_amethyst", "ba_mob_ink", "ba_mob_frost", "ba_mob_fire", "ba_mob_hooks"};

	private static final Map<Mob, String> VARIANT = new IdentityHashMap<>();
	private static final Map<Mob, Integer> FUSES = new IdentityHashMap<>();
	private static final Map<Mob, Integer> RISE = new IdentityHashMap<>();
	private static final Map<Mob, Integer> LIFE = new IdentityHashMap<>();
	private static final Set<Mob> MINIONS = Collections.newSetFromMap(new IdentityHashMap<>());

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (!(e instanceof Mob mob)) return;
			String type = typeName(mob);
			boolean zombie = type.equals("zombie");
			boolean skeleton = type.equals("skeleton");
			if (!zombie && !skeleton) return;
			if (!TagUtil.has(mob, "ba_rolled")) {
				mob.addTag("ba_rolled");
				if (zombie && level.getRandom().nextFloat() < 0.01F) { // 1 necromancer per 100 zombies
					mob.addTag(NECRO);
				} else if (level.getRandom().nextFloat() < 0.12F) {
					String[] pool = zombie ? ZOMBIE : SKELETON;
					mob.addTag(pool[level.getRandom().nextInt(pool.length)]);
				}
			}
			// Variant tag comes from the random roll above or from a spawn egg (see VariantEggs).
			String variant = variantTag(mob);
			if (variant != null) {
				if (!TagUtil.has(mob, "ba_setup")) {
					mob.addTag("ba_setup");
					setup(mob, variant);
				}
				VARIANT.put(mob, variant);
			}
			if (zombie) {
				if (TagUtil.has(mob, "ba_rise") && !TagUtil.has(mob, "ba_rose")) {
					mob.noPhysics = true;
					RISE.put(mob, 0);
				}
				if (TagUtil.has(mob, "ba_minion")) MINIONS.add(mob);
				if (TagUtil.has(mob, "ba_life")) LIFE.put(mob, MINIONS.contains(mob) ? 2400 : 1200);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e instanceof Mob m) {
				VARIANT.remove(m);
				FUSES.remove(m);
				RISE.remove(m);
				LIFE.remove(m);
				MINIONS.remove(m);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());

		// The necromancer's staff drops with a 33.3% chance (instead of vanilla hand-item drop chance).
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof Mob mob && NECRO.equals(VARIANT.get(mob)) && mob.level() instanceof ServerLevel level) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				if (level.getRandom().nextFloat() < 1.0F / 3.0F) {
					ItemEntity drop = new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), new ItemStack(ModItems.NECROMANCER_STAFF));
					level.addFreshEntity(drop);
				}
			}
			return true;
		});
		// Staff-summoned zombies never hurt players.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof Player && source.getEntity() instanceof Mob m && MINIONS.contains(m)));
	}

	/** True for the zombies that already have their own block-breaking logic (breaker / miner / ram / digger). */
	public static boolean isBlockBreaker(Mob m) {
		String v = VARIANT.get(m);
		return v != null && (v.equals("ba_mob_breaker") || v.equals("ba_mob_miner") || v.equals("ba_mob_ram") || v.equals("ba_mob_digger"));
	}

	public static boolean isMinion(net.minecraft.world.entity.Entity e) {
		return e instanceof Mob m && MINIONS.contains(m);
	}

	/** Plain registry path of the mob's type ("zombie", "skeleton"...), independent of EntityType constants. */
	private static String typeName(Mob mob) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		return "minecraft".equals(id.getNamespace()) ? id.getPath() : "";
	}

	private static String variantTag(Mob mob) {
		if (TagUtil.has(mob, NECRO)) return NECRO;
		for (String t : ZOMBIE) if (TagUtil.has(mob, t)) return t;
		for (String t : SKELETON) if (TagUtil.has(mob, t)) return t;
		return null;
	}

	private static void name(Mob mob, String key) {
		mob.setCustomName(Component.translatable("mob.bricks_and_beats." + key));
	}

	private static void setup(Mob mob, String tag) {
		mob.setCustomNameVisible(true);
		var hp = mob.getAttribute(Attributes.MAX_HEALTH);
		switch (tag) {
			case "ba_mob_brick" -> {
				name(mob, "brick_thrower");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BRICK));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			}
			case "ba_mob_dynamite" -> {
				name(mob, "dynamiter");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TNT));
				mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
			}
			case "ba_mob_bard" -> {
				name(mob, "bard");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NOTE_BLOCK));
				hp.setBaseValue(30);
			}
			case "ba_mob_amethyst" -> {
				name(mob, "amethyst_shooter");
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.AMETHYST_BLOCK));
				hp.setBaseValue(26);
			}
			case "ba_mob_ink" -> {
				name(mob, "ink_skeleton");
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.INK_SAC));
				hp.setBaseValue(26);
			}
			case "ba_mob_frost" -> {
				name(mob, "frost_skeleton");
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.BLUE_ICE));
				hp.setBaseValue(26);
			}
			case "ba_mob_fire" -> {
				name(mob, "fire_skeleton");
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.MAGMA_BLOCK));
				mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1));
				hp.setBaseValue(26);
			}
			case NECRO -> {
				name(mob, "necromancer");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.NECROMANCER_STAFF));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.WITHER_SKELETON_SKULL));
				hp.setBaseValue(40);
			}
			case "ba_mob_breaker" -> {
				name(mob, "breaker");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
				hp.setBaseValue(26);
			}
			case "ba_mob_miner" -> {
				name(mob, "miner");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				hp.setBaseValue(30);
			}
			case "ba_mob_ram" -> {
				name(mob, "ram");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.ANVIL));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2);
				mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
				hp.setBaseValue(50);
			}
			case "ba_mob_digger" -> {
				name(mob, "digger");
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28);
				hp.setBaseValue(24);
			}
			default -> { // bulwark
				name(mob, "bulwark");
				mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
				mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6);
				hp.setBaseValue(40);
			}
		}
		mob.setHealth((float) mob.getAttributeValue(Attributes.MAX_HEALTH));
	}

	// ------------------------------------------------------------------ necromancer / staff

	private static boolean solid(ServerLevel level, BlockPos p) {
		return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}

	private static double groundY(ServerLevel level, double x, double y, double z) {
		BlockPos p = BlockPos.containing(x, y, z);
		for (int dy = 2; dy >= -4; dy--) {
			BlockPos feet = p.offset(0, dy, 0);
			if (solid(level, feet.below()) && !solid(level, feet) && !solid(level, feet.above())) return feet.getY();
		}
		return Double.NaN;
	}

	/** Three zombies climb out of the ground in a triangle, 3.5 blocks ahead of (dx, dz). friendly = they fight for the player. */
	public static void summonTriangle(ServerLevel level, Vec3 origin, double dx, double dz, boolean friendly) {
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-3) {
			dx = 1;
			dz = 0;
			len = 1;
		}
		dx /= len;
		dz /= len;
		double cx = origin.x + dx * 3.5;
		double cz = origin.z + dz * 3.5;
		double base = Math.atan2(dz, dx);
		String tags = "\"ba_rolled\",\"ba_rise\",\"ba_life\"" + (friendly ? ",\"ba_minion\"" : "");
		int spawned = 0;
		for (int i = 0; i < 3; i++) {
			double ang = base + i * (2.0 * Math.PI / 3.0);
			double x = cx + Math.cos(ang) * 2.0;
			double z = cz + Math.sin(ang) * 2.0;
			double gy = groundY(level, x, origin.y, z);
			if (Double.isNaN(gy)) continue;
			String cmd = String.format(Locale.ROOT,
					"summon minecraft:zombie %.2f %.2f %.2f {Tags:[%s],NoAI:1b,NoGravity:1b,Invulnerable:1b}", x, gy - 2.0, z, tags);
			var server = level.getServer();
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
					x, gy + 0.2, z, 14, 0.3, 0.1, 0.3, 0.05);
			spawned++;
		}
		if (spawned > 0) {
			level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5F, 0.7F);
		}
	}

	// ------------------------------------------------------------------ block breakers

	private static void breakAhead(ServerLevel level, Mob mob, LivingEntity target, String v, int t) {
		float max;
		int interval;
		boolean tall = false;
		boolean down = false;
		switch (v) {
			case "ba_mob_breaker" -> {
				max = 2.5F;
				interval = 20;
			}
			case "ba_mob_miner" -> {
				max = 4.5F;
				interval = 12;
			}
			case "ba_mob_ram" -> {
				max = 6.0F;
				interval = 30;
				tall = true;
			}
			default -> { // digger
				max = 1.6F;
				interval = 8;
				down = true;
			}
		}
		if (t % interval != 0) return;
		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		BlockPos feet = mob.blockPosition();
		List<BlockPos> cand = new ArrayList<>();
		if (down && target.getY() < mob.getY() - 2.0) cand.add(feet.below()); // dig down towards the target
		if (dx * dx + dz * dz > 1.5) {
			Direction dir = Direction.getApproximateNearest(dx, 0, dz);
			cand.add(feet.relative(dir));
			cand.add(feet.above().relative(dir));
			if (tall) cand.add(feet.above(2).relative(dir));
		}
		for (BlockPos p : cand) {
			if (smash(level, mob, p, max) && !tall) break;
		}
	}

	private static boolean smash(ServerLevel level, Mob mob, BlockPos p, float maxHardness) {
		BlockState s = level.getBlockState(p);
		if (s.isAir() || !s.getFluidState().isEmpty()) return false;
		float h = s.getDestroySpeed(level, p);
		if (h < 0.0F || h > maxHardness) return false;
		mob.swing(InteractionHand.MAIN_HAND);
		level.destroyBlock(p, true, mob);
		return true;
	}

	// ------------------------------------------------------------------ tick

	private static void tick() {
		// Zombies climbing out of the ground (2 blocks over 1 second).
		for (Mob m : new ArrayList<>(RISE.keySet())) {
			if (m.isRemoved() || !(m.level() instanceof ServerLevel level)) {
				RISE.remove(m);
				continue;
			}
			int t = RISE.get(m) + 1;
			m.setPos(m.getX(), m.getY() + 0.1, m.getZ());
			m.setDeltaMovement(Vec3.ZERO);
			m.fallDistance = 0;
			if (t % 2 == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
						m.getX(), m.getY() + (20 - t) * 0.1 + 0.2, m.getZ(), 5, 0.3, 0.1, 0.3, 0.05);
			}
			if (t >= 20) {
				RISE.remove(m);
				m.noPhysics = false;
				m.setNoAi(false);
				m.setNoGravity(false);
				m.setInvulnerable(false);
				m.removeTag("ba_rise");
				m.addTag("ba_rose");
			} else {
				RISE.put(m, t);
			}
		}

		// Timed zombies (summoned) crumble after a while.
		for (Mob m : new ArrayList<>(LIFE.keySet())) {
			if (m.isRemoved() || !(m.level() instanceof ServerLevel level)) {
				LIFE.remove(m);
				continue;
			}
			int left = LIFE.get(m) - 1;
			if (left <= 0) {
				level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 1.0, m.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
				LIFE.remove(m);
				m.discard();
			} else {
				LIFE.put(m, left);
			}
		}

		// Staff minions: never target players, hunt hostile mobs, ignore the sun.
		for (Mob m : new ArrayList<>(MINIONS)) {
			if (m.isRemoved() || !m.isAlive() || !(m.level() instanceof ServerLevel level)) continue;
			m.clearFire();
			if (m.getTarget() instanceof Player) m.setTarget(null);
			if (m.getTarget() == null && m.tickCount % 10 == 0 && !RISE.containsKey(m)) {
				Mob best = null;
				double bestD = Double.MAX_VALUE;
				for (Mob other : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(16),
						o -> o != m && o.isAlive() && o.getType().getCategory() == MobCategory.MONSTER && !MINIONS.contains(o))) {
					double d = m.distanceToSqr(other);
					if (d < bestD) {
						bestD = d;
						best = other;
					}
				}
				if (best != null) m.setTarget(best);
			}
		}

		for (Mob mob : new ArrayList<>(VARIANT.keySet())) {
			if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
				VARIANT.remove(mob);
				FUSES.remove(mob);
				continue;
			}
			String v = VARIANT.get(mob);
			LivingEntity target = mob.getTarget();
			if (target == null || !target.isAlive() || RISE.containsKey(mob)) {
				FUSES.remove(mob);
				continue;
			}
			double dist = mob.distanceTo(target);
			int t = mob.tickCount;

			switch (v) {
				case "ba_mob_brick" -> {
					if (t % 40 == 0 && dist > 3 && dist < 14) {
						ThrownBrick b = new ThrownBrick(level, mob, new ItemStack(Items.BRICK));
						Vec3 d = target.getEyePosition().subtract(b.position());
						b.shoot(d.x, d.y + dist * 0.12, d.z, 1.4F, 3.0F);
						level.addFreshEntity(b);
						level.playSound(null, mob.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 1.0F, 0.6F);
					}
				}
				case "ba_mob_amethyst" -> {
					if (t % 70 == 0 && dist < 12 && mob.hasLineOfSight(target)) {
						Vec3 from = mob.getEyePosition();
						AmethystPellet.spray(level, mob, from, target.getEyePosition().subtract(from), 6, 0.1, 1.6);
					}
				}
				case "ba_mob_ink" -> {
					if (t % 80 == 0 && dist < 12 && mob.hasLineOfSight(target)) {
						target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60));
						level.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getEyeY(), target.getZ(), 25, 0.3, 0.3, 0.3, 0.05);
					}
				}
				case "ba_mob_frost" -> {
					if (t % 60 == 0 && dist < 12 && mob.hasLineOfSight(target)) {
						target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
						target.setTicksFrozen(200);
						level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getEyeY(), target.getZ(), 25, 0.4, 0.4, 0.4, 0.05);
						level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.0F, 1.8F);
					}
				}
				case "ba_mob_fire" -> {
					if (t % 80 == 0 && dist < 14 && mob.hasLineOfSight(target)) {
						target.igniteForSeconds(4.0F);
						level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.3, 0.5, 0.3, 0.03);
						level.playSound(null, mob.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0F, 1.0F);
					}
				}
				case "ba_mob_bard" -> {
					if (t % 60 == 0 && dist < 8) {
						level.sendParticles(ParticleTypes.NOTE, mob.getX(), mob.getY() + 2, mob.getZ(), 20, 3, 0.5, 3, 1.0);
						level.playSound(null, mob.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0F, 0.7F);
						for (Player e : level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(7))) {
							e.hurtServer(level, level.damageSources().sonicBoom(mob), 3.0F);
							e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
						}
					}
				}
				case "ba_mob_dynamite" -> {
					Integer fuse = FUSES.get(mob);
					if (fuse == null && dist < 3) fuse = 0;
					if (fuse != null) {
						fuse++;
						FUSES.put(mob, fuse);
						level.sendParticles(ParticleTypes.FLAME, mob.getX(), mob.getY() + 2.1, mob.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
						if (fuse == 1) level.playSound(null, mob.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.5F, 0.8F);
						if (fuse >= 30) {
							level.explode(mob, mob.getX(), mob.getY(0.5), mob.getZ(), 2.5F, Level.ExplosionInteraction.NONE);
							FUSES.remove(mob);
							mob.discard();
						}
					}
				}
				case NECRO -> {
					if (t % 240 == 120 && dist < 18 && mob.hasLineOfSight(target)) {
						summonTriangle(level, mob.position(), target.getX() - mob.getX(), target.getZ() - mob.getZ(), false);
					}
				}
				case "ba_mob_breaker", "ba_mob_miner", "ba_mob_ram", "ba_mob_digger" -> {
					if (dist < 32) breakAhead(level, mob, target, v, t);
				}
				default -> { // bulwark: plain tank, nothing to tick
				}
			}
		}
	}

	private MobVariants() {}
}
