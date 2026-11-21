package com.example.bricksandbeats;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Special zombies / skeletons (about 12% of natural ones). Tags: ba_mob_brick, ba_mob_dynamite (zombies),
 * ba_mob_bard, ba_mob_amethyst, ba_mob_ink (skeletons).
 */
public final class MobVariants {
	private static final Set<Mob> MOBS = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Map<Mob, Integer> FUSES = new IdentityHashMap<>();
	private static final String[] ZOMBIE = {"ba_mob_brick", "ba_mob_dynamite"};
	private static final String[] SKELETON = {"ba_mob_bard", "ba_mob_amethyst", "ba_mob_ink"};

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (!(e instanceof Mob mob)) return;
			String type = typeName(mob);
			boolean zombie = type.equals("zombie");
			boolean skeleton = type.equals("skeleton");
			if (!zombie && !skeleton) return;
			if (!TagUtil.has(mob, "ba_rolled")) {
				mob.addTag("ba_rolled");
				if (level.getRandom().nextFloat() < 0.12F) {
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
				MOBS.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e instanceof Mob m) {
				MOBS.remove(m);
				FUSES.remove(m);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	/** Plain registry path of the mob's type ("zombie", "skeleton"...), independent of EntityType constants. */
	private static String typeName(Mob mob) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		return "minecraft".equals(id.getNamespace()) ? id.getPath() : "";
	}

	private static String variantTag(Mob mob) {
		for (String t : ZOMBIE) if (TagUtil.has(mob, t)) return t;
		for (String t : SKELETON) if (TagUtil.has(mob, t)) return t;
		return null;
	}

	private static void setup(Mob mob, String tag) {
		mob.addTag(tag);
		mob.setCustomNameVisible(true);
		var hp = mob.getAttribute(Attributes.MAX_HEALTH);
		switch (tag) {
			case "ba_mob_brick" -> {
				mob.setCustomName(Component.translatable("mob.bricks_and_beats.brick_thrower"));
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BRICK));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			}
			case "ba_mob_dynamite" -> {
				mob.setCustomName(Component.translatable("mob.bricks_and_beats.dynamiter"));
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TNT));
				mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
			}
			case "ba_mob_bard" -> {
				mob.setCustomName(Component.translatable("mob.bricks_and_beats.bard"));
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NOTE_BLOCK));
				hp.setBaseValue(30);
			}
			case "ba_mob_amethyst" -> {
				mob.setCustomName(Component.translatable("mob.bricks_and_beats.amethyst_shooter"));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.AMETHYST_BLOCK));
				hp.setBaseValue(26);
			}
			default -> {
				mob.setCustomName(Component.translatable("mob.bricks_and_beats.ink_skeleton"));
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.INK_SAC));
				hp.setBaseValue(26);
			}
		}
		mob.setHealth((float) mob.getAttributeValue(Attributes.MAX_HEALTH));
	}

	private static void tick() {
		for (Mob mob : new ArrayList<>(MOBS)) {
			if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
				MOBS.remove(mob);
				FUSES.remove(mob);
				continue;
			}
			LivingEntity target = mob.getTarget();
			if (target == null || !target.isAlive()) {
				FUSES.remove(mob);
				continue;
			}
			double dist = mob.distanceTo(target);
			int t = mob.tickCount;

			if (TagUtil.has(mob, "ba_mob_brick") && t % 40 == 0 && dist > 3 && dist < 14) {
				ThrownBrick b = new ThrownBrick(level, mob, new ItemStack(Items.BRICK));
				Vec3 d = target.getEyePosition().subtract(b.position());
				b.shoot(d.x, d.y + dist * 0.12, d.z, 1.4F, 3.0F);
				level.addFreshEntity(b);
				level.playSound(null, mob.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 1.0F, 0.6F);
			} else if (TagUtil.has(mob, "ba_mob_amethyst") && t % 70 == 0 && dist < 12 && mob.hasLineOfSight(target)) {
				Vec3 from = mob.getEyePosition();
				AmethystPellet.spray(level, mob, from, target.getEyePosition().subtract(from), 6, 0.1, 1.6);
			} else if (TagUtil.has(mob, "ba_mob_ink") && t % 80 == 0 && dist < 12 && mob.hasLineOfSight(target)) {
				target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60));
				level.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getEyeY(), target.getZ(), 25, 0.3, 0.3, 0.3, 0.05);
			} else if (TagUtil.has(mob, "ba_mob_bard") && t % 60 == 0 && dist < 8) {
				level.sendParticles(ParticleTypes.NOTE, mob.getX(), mob.getY() + 2, mob.getZ(), 20, 3, 0.5, 3, 1.0);
				level.playSound(null, mob.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0F, 0.7F);
				for (LivingEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, mob.getBoundingBox().inflate(7))) {
					e.hurtServer(level, level.damageSources().sonicBoom(mob), 3.0F);
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
				}
			} else if (TagUtil.has(mob, "ba_mob_dynamite")) {
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
		}
	}

	private MobVariants() {}
}
