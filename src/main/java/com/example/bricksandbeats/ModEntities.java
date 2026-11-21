package com.example.bricksandbeats;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<ThrownBrick> THROWN_BRICK = register("thrown_brick",
			EntityType.Builder.<ThrownBrick>of(ThrownBrick::new, MobCategory.MISC)
					.sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));

	public static final EntityType<BrickShard> BRICK_SHARD = register("brick_shard",
			EntityType.Builder.<BrickShard>of(BrickShard::new, MobCategory.MISC)
					.sized(0.2F, 0.2F).clientTrackingRange(4).updateInterval(5));

	public static final EntityType<AmethystPellet> AMETHYST_PELLET = register("amethyst_pellet",
			EntityType.Builder.<AmethystPellet>of(AmethystPellet::new, MobCategory.MISC)
					.sized(0.2F, 0.2F).clientTrackingRange(4).updateInterval(2));

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, BricksAndBeats.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
	}

	private ModEntities() {}
}
