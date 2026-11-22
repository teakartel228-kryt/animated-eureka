package com.example.bricksandbeats;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Applies traps / spikes to every living entity standing in one (checked every 4 ticks near players). */
public final class Hazards {
	private static final Map<UUID, Long> LAST = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 4 != 0) return;
			for (ServerLevel level : server.getAllLevels()) {
				Set<UUID> done = new HashSet<>();
				for (ServerPlayer pl : level.players()) {
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, pl.getBoundingBox().inflate(40.0, 12.0, 40.0))) {
						if (!e.isAlive() || !done.add(e.getUUID())) continue;
						BlockState st = level.getBlockState(e.blockPosition());
						if (st.getBlock() instanceof HazardBlock hb) hit(level, e, hb);
					}
				}
			}
			if (LAST.size() > 2000) {
				long now = server.overworld().getGameTime();
				LAST.values().removeIf(v -> now - v > 400);
			}
		});
	}

	private static void hit(ServerLevel level, LivingEntity e, HazardBlock hb) {
		if (e instanceof Player p && (p.isShiftKeyDown() || p.isCreative() || p.isSpectator())) return;
		long now = level.getGameTime();
		Long last = LAST.get(e.getUUID());
		if (last != null && now - last < hb.cooldown) return;
		LAST.put(e.getUUID(), now);
		e.hurtServer(level, level.damageSources().cactus(), hb.damage);
		if (hb.rootTicks > 0) {
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, hb.rootTicks, 6));
			e.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, hb.rootTicks, 128));
			level.playSound(null, e.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.7F, 1.6F);
		} else {
			level.playSound(null, e.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3F, 2.0F);
		}
		if (hb.bleed) e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
		level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 0.3, e.getZ(), 8, 0.2, 0.1, 0.2, 0.1);
	}

	private Hazards() {}
}
