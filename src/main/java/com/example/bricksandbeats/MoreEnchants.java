package com.example.bricksandbeats;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Bounce, Vampirism, Frostbite, Venom, Adrenaline, Owl Eyes, Magnetism, Spring. */
public final class MoreEnchants {
	private static int lvl(ItemStack s, String n) {
		return NewEnchants.lvl(s, n);
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MoreEnchants::onDamage);
		// Bounce: fall damage turns into a bounce (sneak to land normally)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof Player p && source.is(DamageTypes.FALL) && !p.isShiftKeyDown()) {
				int b = lvl(p.getItemBySlot(EquipmentSlot.FEET), "bounce");
				if (b > 0) {
					Vec3 v = p.getDeltaMovement();
					p.setDeltaMovement(v.x, Math.min(1.4, 0.35 + 0.06 * amount * b), v.z);
					p.hurtMarked = true;
					p.resetFallDistance();
					return false;
				}
			}
			return true;
		});
		ServerTickEvents.END_SERVER_TICK.register(MoreEnchants::tick);
	}

	private static void onDamage(LivingEntity target, DamageSource source, float base, float taken, boolean blocked) {
		if (taken <= 0.0F || !(target.level() instanceof ServerLevel level)) return;
		if (!(source.getEntity() instanceof LivingEntity attacker)) return;
		ItemStack w = source.getWeaponItem();
		if (w == null || w.isEmpty()) return;
		int vamp = lvl(w, "vampirism");
		if (vamp > 0) {
			attacker.heal(taken * 0.1F * vamp);
			level.sendParticles(ParticleTypes.HEART, attacker.getX(), attacker.getY() + attacker.getBbHeight(), attacker.getZ(), 2, 0.25, 0.2, 0.25, 0.01);
		}
		int frost = lvl(w, "frostbite");
		if (frost > 0) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40 + 30 * frost, frost > 2 ? 2 : 1));
			target.setTicksFrozen(100 + 60 * frost);
			level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 10, 0.3, 0.3, 0.3, 0.03);
		}
		int venom = lvl(w, "venom");
		if (venom > 0) {
			target.addEffect(new MobEffectInstance(MobEffects.POISON, 60 + 40 * venom, venom > 2 ? 1 : 0));
		}
	}

	private static void tick(MinecraftServer server) {
		boolean slow = server.getTickCount() % 10 == 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (var pl : level.players()) {
				ItemStack main = pl.getMainHandItem();
				int mag = lvl(main, "magnetism");
				if (mag > 0) {
					double r = 3.0 + 2.0 * mag;
					Vec3 to = pl.position().add(0, 0.6, 0);
					for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, pl.getBoundingBox().inflate(r), e -> e.isAlive())) {
						Vec3 d = to.subtract(it.position());
						if (d.lengthSqr() < 0.64) continue;
						it.setDeltaMovement(d.normalize().scale(0.28));
						it.hurtMarked = true;
					}
				}
				if (!slow) continue;
				if (lvl(pl.getItemBySlot(EquipmentSlot.HEAD), "owl_eyes") > 0)
					pl.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, true, false));
				int spring = lvl(pl.getItemBySlot(EquipmentSlot.FEET), "spring");
				if (spring > 0) pl.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 30, spring - 1, true, false));
				int adr = lvl(pl.getItemBySlot(EquipmentSlot.CHEST), "adrenaline");
				if (adr > 0 && pl.getHealth() <= 6.0F) {
					pl.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, adr - 1, true, false));
					pl.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, adr - 1, true, false));
				}
			}
		}
	}

	private MoreEnchants() {}
}
