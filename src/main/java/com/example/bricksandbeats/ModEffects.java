package com.example.bricksandbeats;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.alchemy.Potion;

/** New effects: Rhythm (speed), Brick Skin (tank), Fragility (curse), Chaos (random effects). */
public final class ModEffects {
	private static class Simple extends MobEffect {
		Simple(MobEffectCategory c, int color) { super(c, color); }
	}

	private static class Chaos extends Simple {
		private static final java.util.List<Holder<MobEffect>> POOL = java.util.List.of(
				MobEffects.SPEED, MobEffects.STRENGTH, MobEffects.JUMP_BOOST, MobEffects.REGENERATION, MobEffects.SLOWNESS,
				MobEffects.WEAKNESS, MobEffects.LEVITATION, MobEffects.GLOWING, MobEffects.NAUSEA, MobEffects.NIGHT_VISION);
		Chaos() { super(MobEffectCategory.NEUTRAL, 0xA020F0); }

		@Override
		public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
			return tickCount % 80 == 0;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
			entity.addEffect(new MobEffectInstance(POOL.get(level.getRandom().nextInt(POOL.size())), 100));
			level.sendParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getY() + 1, entity.getZ(), 15, 0.4, 0.6, 0.4, 0.5);
			return true;
		}
	}

	public static final Holder<MobEffect> RHYTHM = effect("rhythm", new Simple(MobEffectCategory.BENEFICIAL, 0xE06BFF)
			.addAttributeModifier(Attributes.MOVEMENT_SPEED, BricksAndBeats.id("effect.rhythm.speed"), 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
			.addAttributeModifier(Attributes.ATTACK_SPEED, BricksAndBeats.id("effect.rhythm.attack"), 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	public static final Holder<MobEffect> BRICK_SKIN = effect("brick_skin", new Simple(MobEffectCategory.BENEFICIAL, 0xB04A3A)
			.addAttributeModifier(Attributes.ARMOR, BricksAndBeats.id("effect.brick_skin.armor"), 6.0, AttributeModifier.Operation.ADD_VALUE)
			.addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, BricksAndBeats.id("effect.brick_skin.kb"), 0.5, AttributeModifier.Operation.ADD_VALUE)
			.addAttributeModifier(Attributes.MOVEMENT_SPEED, BricksAndBeats.id("effect.brick_skin.slow"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	public static final Holder<MobEffect> FRAGILITY = effect("fragility", new Simple(MobEffectCategory.HARMFUL, 0x7A7A7A)
			.addAttributeModifier(Attributes.ARMOR, BricksAndBeats.id("effect.fragility.armor"), -8.0, AttributeModifier.Operation.ADD_VALUE)
			.addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, BricksAndBeats.id("effect.fragility.kb"), -0.5, AttributeModifier.Operation.ADD_VALUE));
	public static final Holder<MobEffect> CHAOS = effect("chaos", new Chaos());

	public static final Holder<Potion> RHYTHM_POTION = potion("rhythm", RHYTHM, 3600);
	public static final Holder<Potion> BRICK_SKIN_POTION = potion("brick_skin", BRICK_SKIN, 2400);
	public static final Holder<Potion> FRAGILITY_POTION = potion("fragility", FRAGILITY, 1800);
	public static final Holder<Potion> CHAOS_POTION = potion("chaos", CHAOS, 2400);

	private static Holder<MobEffect> effect(String name, MobEffect effect) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, BricksAndBeats.id(name), effect);
	}

	private static Holder<Potion> potion(String name, Holder<MobEffect> effect, int ticks) {
		return Registry.registerForHolder(BuiltInRegistries.POTION, BricksAndBeats.id(name),
				new Potion(name, new MobEffectInstance(effect, ticks)));
	}

	/** Touching the class registers the effects and potions; brewing recipes are added by PotionBrewingMixin. */
	public static void init() {
	}

	private ModEffects() {}
}
