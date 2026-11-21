package com.example.bricksandbeats;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Enchantment logic. The enchantment JSONs (data/.../enchantment) only define cost/items/exclusivity;
 * arrow enchantments tag the arrow through a function (projectile_spawned), melee/hit effects are read here.
 * Tags: ba_eternal, ba_amethyst, ba_ultra.
 */
public final class ArrowEnchants {
	private static final Set<AbstractArrow> ARROWS = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Eternal arrows -> launch speed. */
	private static final Map<AbstractArrow, Double> ETERNAL = new IdentityHashMap<>();
	private static final Set<AbstractArrow> DISCARD = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Map<AbstractArrow, Integer> ULTRA_BREAKS = new IdentityHashMap<>();
	private static final int ULTRA_MAX_BREAKS = 12;
	private static final float ULTRA_MAX_HARDNESS = 3.0F; // stone 1.5, wood 2, ores 3 ... obsidian stays
	private static boolean busy; // recursion guard for damage we deal ourselves

	public static boolean has(ItemStack stack, String name) {
		if (stack == null || stack.isEmpty()) return false;
		for (Holder<Enchantment> h : stack.getEnchantments().keySet()) {
			if (h.is(BricksAndBeats.id(name))) return true;
		}
		return false;
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof AbstractArrow a) {
				ARROWS.add(a);
				onArrowLoad(level, a);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e instanceof AbstractArrow a) {
				ARROWS.remove(a);
				ETERNAL.remove(a);
				ULTRA_BREAKS.remove(a);
			}
		});
		// Ultra Power has to clear blocks BEFORE the arrow moves (after the move it is already stuck in the wall).
		ServerTickEvents.START_SERVER_TICK.register(server -> tickUltra());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (AbstractArrow a : new ArrayList<>(DISCARD)) a.discard();
			DISCARD.clear();
			tickArrows();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ArrowEnchants::onDamage);
	}

	// ---------------------------------------------------------------- arrows

	private static void tickArrows() {
		for (AbstractArrow a : new ArrayList<>(ARROWS)) {
			if (a.isRemoved() || !(a.level() instanceof ServerLevel level)) {
				ARROWS.remove(a);
				ETERNAL.remove(a);
				continue;
			}
			if (DISCARD.contains(a)) continue;
			boolean amethyst = TagUtil.has(a, "ba_amethyst");
			boolean ultra = TagUtil.has(a, "ba_ultra");
			boolean eternal = TagUtil.has(a, "ba_eternal");
			if (!amethyst && !ultra && !eternal) continue;

			// Cooldown for the heavy enchantments (once per arrow).
			if ((amethyst || ultra) && !TagUtil.has(a, "ba_cd")) {
				a.addTag("ba_cd");
				ItemStack weapon = a.getWeaponItem();
				if (a.getOwner() instanceof Player p && weapon != null && !weapon.isEmpty()) {
					p.getCooldowns().addCooldown(weapon, amethyst ? 80 : 40);
				}
			}

			if (amethyst) {
				amethyst(level, a);
			}
			if (eternal) {
				eternal(a);
			}
		}
	}

	/** Amethyst Spray: the arrow turns into a shotgun blast. */
	private static void amethyst(ServerLevel level, AbstractArrow a) {
		Vec3 v = a.getDeltaMovement();
		if (v.lengthSqr() > 1.0E-4 && a.getOwner() instanceof LivingEntity owner) {
			AmethystPellet.spray(level, owner, a.position(), v, 8, 0.09, 1.9, a.getWeaponItem());
		}
		ETERNAL.remove(a);
		a.discard();
	}

	/** Amethyst Spray: the pellets fly the very moment the arrow is created (no 1-tick delay, the arrow never flies). */
	private static void onArrowLoad(ServerLevel level, AbstractArrow a) {
		ItemStack weapon = a.getWeaponItem();
		if (weapon == null || weapon.isEmpty() || !has(weapon, "amethyst_spray")) return;
		if (TagUtil.has(a, "ba_sprayed")) return;
		Vec3 v = a.getDeltaMovement();
		if (v.lengthSqr() < 0.25) return; // not a fresh shot
		if (!(a.getOwner() instanceof LivingEntity)) return;
		LivingEntity owner = (LivingEntity) a.getOwner();
		a.addTag("ba_sprayed");
		if (owner instanceof Player p) p.getCooldowns().addCooldown(weapon, 80);
		AmethystPellet.spray(level, owner, a.position(), v, 8, 0.09, 1.9, weapon);
		a.setDeltaMovement(Vec3.ZERO);
		a.setInvisible(true);
		a.noPhysics = true;
		DISCARD.add(a);
	}

	private static void tickUltra() {
		for (AbstractArrow a : new ArrayList<>(ARROWS)) {
			if (a.isRemoved() || !(a.level() instanceof ServerLevel level)) continue;
			if (!TagUtil.has(a, "ba_ultra")) continue;
			smash(level, a);
		}
	}

	/** Ultra Power: looks ahead along the flight path and smashes every block it can (hardness <= 3), up to 12 blocks per arrow. */
	private static void smash(ServerLevel level, AbstractArrow a) {
		Vec3 v = a.getDeltaMovement();
		double len = v.length();
		if (len < 0.5) return; // stuck in a block / spent
		int used = ULTRA_BREAKS.getOrDefault(a, 0);
		if (used >= ULTRA_MAX_BREAKS) return;
		int steps = (int) Math.ceil(len / 0.5);
		BlockPos last = null;
		for (int i = 0; i <= steps && used < ULTRA_MAX_BREAKS; i++) {
			BlockPos pos = BlockPos.containing(a.position().add(v.scale((double) i / steps)));
			if (pos.equals(last)) continue;
			last = pos;
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty()) continue;
			float hardness = state.getDestroySpeed(level, pos);
			if (hardness < 0.0F || hardness > ULTRA_MAX_HARDNESS) continue;
			level.destroyBlock(pos, true, a);
			used++;
			v = v.scale(0.93);
		}
		ULTRA_BREAKS.put(a, used);
		a.setDeltaMovement(v);
		a.hurtMarked = true;
	}

	/**
	 * Eternal Arrow: "No Gravity" is switched on from the first tick and never switched off. During the first
	 * 8 seconds, whenever the arrow slows down (drag, water) its speed is boosted back to the launch speed.
	 */
	private static void eternal(AbstractArrow a) {
		a.setNoGravity(true);
		Vec3 v = a.getDeltaMovement();
		double speed = v.length();
		Double launch = ETERNAL.get(a);
		if (launch == null) {
			if (speed < 0.1) return;
			ETERNAL.put(a, Math.max(speed, 1.5));
			return;
		}
		if (a.tickCount > 160 || speed < 0.1) return; // boost phase over, or landed
		if (speed < launch * 0.85 || v.y < -0.02) {
			a.setDeltaMovement(v.normalize().scale(launch));
			a.hurtMarked = true;
		}
	}

	// ---------------------------------------------------------------- hits

	private static void onDamage(LivingEntity target, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (busy || !(target.level() instanceof ServerLevel level)) return;
		ItemStack weapon = source.getWeaponItem();
		if (weapon == null || weapon.isEmpty()) return;
		LivingEntity attacker = source.getEntity() instanceof LivingEntity l ? l : null;
		applyHit(level, target, attacker, weapon, blocked);
	}

	/** Called by amethyst pellets so the weapon's hit enchantments (ink, chaos, music...) apply to them too. */
	public static void pelletHit(ServerLevel level, LivingEntity target, net.minecraft.world.entity.Entity owner, ItemStack weapon) {
		if (weapon == null || weapon.isEmpty()) return;
		applyHit(level, target, owner instanceof LivingEntity l ? l : null, weapon, false);
	}

	private static void applyHit(ServerLevel level, LivingEntity target, LivingEntity attacker, ItemStack weapon, boolean blocked) {
		if (busy) return;
		busy = true;
		try {
			// Shield Breaker (15%): disables the shield.
			if (blocked && target instanceof Player p && has(weapon, "shield_breaker")
					&& level.getRandom().nextFloat() < 0.15F) {
				ItemStack use = p.getUseItem();
				if (use.is(Items.SHIELD)) {
					p.getCooldowns().addCooldown(use, 100);
					p.stopUsingItem();
					level.broadcastEntityEvent(p, (byte) 30);
				}
			}
			if (blocked) return;

			// Ink Shot: blinds with squid ink.
			if (has(weapon, "ink_shot")) {
				target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60));
				level.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getEyeY(), target.getZ(), 20, 0.3, 0.3, 0.3, 0.05);
			}

			// Music Weapon (25%): area damage.
			if (has(weapon, "music_weapon") && level.getRandom().nextFloat() < 0.25F) {
				Vec3 c = target.position();
				level.sendParticles(ParticleTypes.NOTE, c.x, c.y + 1.2, c.z, 16, 1.2, 0.5, 1.2, 1.0);
				level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
				level.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
						net.minecraft.sounds.SoundSource.PLAYERS, 2.0F, 0.8F);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(target.blockPosition()).inflate(4.0))) {
					if (e != attacker) e.hurtServer(level, level.damageSources().magic(), 4.0F);
				}
			}

			// Chaos Strike / Far Chaos (20%): a random effect on the target.
			if ((has(weapon, "chaos_strike") || has(weapon, "far_chaos")) && level.getRandom().nextFloat() < 0.20F) {
				chaos(level, target, attacker);
			}
		} finally {
			busy = false;
		}
	}

	private static void chaos(ServerLevel level, LivingEntity t, LivingEntity attacker) {
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, t.getX(), t.getEyeY(), t.getZ(), 25, 0.4, 0.4, 0.4, 0.3);
		switch (level.getRandom().nextInt(10)) {
			case 0 -> t.igniteForSeconds(5.0F);
			case 1 -> t.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
			case 2 -> t.addEffect(new MobEffectInstance(MobEffects.WITHER, 80));
			case 3 -> t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
			case 4 -> t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120));
			case 5 -> t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40));
			case 6 -> t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200));
			case 7 -> t.hurtServer(level, level.damageSources().magic(), 4.0F); // "sharpness"
			case 8 -> t.setTicksFrozen(260); // freeze
			default -> { // knockback
				Vec3 d = attacker != null ? attacker.getLookAngle() : new Vec3(0, 0, 0);
				t.push(d.x * 1.4, 0.5, d.z * 1.4);
				t.hurtMarked = true;
			}
		}
	}

	private ArrowEnchants() {}
}
