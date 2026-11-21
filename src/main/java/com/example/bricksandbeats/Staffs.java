package com.example.bricksandbeats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tiered staffs (wooden .. netherite). Each tier has its own attack; the cooldown depends on the attack. */
public final class Staffs {
	/** Cooldown in ticks per tier: thorn 1.5 s, rock 2.5 s, shockwave 4 s, light 5 s, beam 6 s, wrath 20 s. */
	private static final int[] COOLDOWN = {30, 50, 80, 100, 120, 400};

	private static int tier(ItemStack stack) {
		for (int i = 0; i < ModItems.STAVES.length; i++) if (stack.is(ModItems.STAVES[i])) return i;
		return -1;
	}

	public static InteractionResult use(Player player, Level level, ItemStack stack) {
		int tier = tier(stack);
		if (tier < 0) return InteractionResult.PASS;
		if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
		int cd = COOLDOWN[tier];
		if (level instanceof ServerLevel sl) {
			boolean hit = cast(sl, player, tier);
			if (!hit) cd = cd / 2; // a miss recharges twice as fast
		}
		player.getCooldowns().addCooldown(stack, cd);
		return InteractionResult.SUCCESS;
	}

	/** Living entities close to the look ray, nearest first. */
	private static List<LivingEntity> along(ServerLevel level, Player p, double range, double width) {
		Vec3 eye = p.getEyePosition();
		Vec3 dir = p.getLookAngle().normalize();
		AABB box = new AABB(eye, eye.add(dir.scale(range))).inflate(width + 1.0);
		List<double[]> found = new ArrayList<>();
		List<LivingEntity> all = level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e != p && e.isAlive() && !MobVariants.isMinion(e));
		List<LivingEntity> hits = new ArrayList<>();
		List<Double> dist = new ArrayList<>();
		for (LivingEntity e : all) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double t = to.dot(dir);
			if (t < 0 || t > range) continue;
			if (to.subtract(dir.scale(t)).length() <= width + e.getBbWidth() / 2.0) {
				int i = 0;
				while (i < dist.size() && dist.get(i) < t) i++;
				dist.add(i, t);
				hits.add(i, e);
			}
		}
		return hits;
	}

	private static void beam(ServerLevel level, Player p, double range, ParticleOptions particle) {
		Vec3 eye = p.getEyePosition().add(0, -0.2, 0);
		Vec3 dir = p.getLookAngle().normalize();
		for (double d = 1.0; d < range; d += 0.5) {
			Vec3 q = eye.add(dir.scale(d));
			level.sendParticles(particle, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	private static void sound(ServerLevel level, Player p, float pitch) {
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, pitch);
	}

	private static void hurt(ServerLevel level, LivingEntity e, float dmg) {
		e.invulnerableTime = 0;
		e.hurtServer(level, level.damageSources().magic(), dmg);
	}

	/** @return true when the attack hit something. */
	private static boolean cast(ServerLevel level, Player p, int tier) {
		Vec3 dir = p.getLookAngle().normalize();
		switch (tier) {
			case 0 -> { // wooden: thorn bolt - 4 damage + poison
				beam(level, p, 14, ParticleTypes.HAPPY_VILLAGER);
				sound(level, p, 1.6F);
				List<LivingEntity> t = along(level, p, 14, 0.6);
				if (t.isEmpty()) return false;
				hurt(level, t.get(0), 4.0F);
				t.get(0).addEffect(new MobEffectInstance(MobEffects.POISON, 60));
				return true;
			}
			case 1 -> { // stone: thrown rock - 6 damage, heavy knockback
				beam(level, p, 12, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()));
				sound(level, p, 0.5F);
				List<LivingEntity> t = along(level, p, 12, 0.7);
				if (t.isEmpty()) return false;
				LivingEntity e = t.get(0);
				hurt(level, e, 6.0F);
				e.push(dir.x * 1.8, 0.4, dir.z * 1.8);
				e.hurtMarked = true;
				return true;
			}
			case 2 -> { // iron: shockwave around the player - 5 damage + push away
				level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 40, 2.0, 0.1, 2.0, 0.15);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1.0, p.getZ(), 6, 1.5, 0.2, 1.5, 0.0);
				sound(level, p, 0.8F);
				boolean hit = false;
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4.5),
						e -> e != p && e.isAlive() && !MobVariants.isMinion(e))) {
					Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1).normalize();
					hurt(level, e, 5.0F);
					e.push(away.x * 1.2, 0.35, away.z * 1.2);
					e.hurtMarked = true;
					hit = true;
				}
				return hit;
			}
			case 3 -> { // gold: healing light - heals you, burns nearby monsters
				p.heal(8.0F);
				p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100));
				level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.5, p.getZ(), 8, 0.5, 0.4, 0.5, 0.0);
				level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 40, 2.5, 1.0, 2.5, 0.05);
				sound(level, p, 1.2F);
				boolean hit = false;
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(5.0),
						e -> e != p && e.isAlive() && e.getType().getCategory() == MobCategory.MONSTER && !MobVariants.isMinion(e))) {
					hurt(level, e, 4.0F);
					e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200));
					hit = true;
				}
				return true; // healing always counts
			}
			case 4 -> { // diamond: piercing beam - 10 damage to everything on the line
				beam(level, p, 24, ParticleTypes.END_ROD);
				sound(level, p, 2.0F);
				List<LivingEntity> t = along(level, p, 24, 0.8);
				for (LivingEntity e : t) hurt(level, e, 10.0F);
				return !t.isEmpty();
			}
			default -> { // netherite: wrath - lightning on the target + area damage
				List<LivingEntity> t = along(level, p, 30, 1.5);
				Vec3 at = t.isEmpty() ? p.getEyePosition().add(dir.scale(20)) : t.get(0).position();
				beam(level, p, 30, ParticleTypes.ENCHANTED_HIT);
				var server = level.getServer();
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(),
						String.format(Locale.ROOT, "summon minecraft:lightning_bolt %.2f %.2f %.2f", at.x, at.y, at.z));
				boolean hit = false;
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.5),
						e -> e != p && e.isAlive() && !MobVariants.isMinion(e))) {
					hurt(level, e, 8.0F);
					e.igniteForSeconds(5.0F);
					hit = true;
				}
				return hit;
			}
		}
	}

	private Staffs() {}
}
