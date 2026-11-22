package com.example.bricksandbeats;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Hook items (index = upgrade level 0..6: hook, hook_1 ... hook_6 (6 = voidlite)) and the hook entity. */
public final class HookRegistry {
	public static final Item[] HOOKS = new Item[7];
	public static EntityType<HookEntity> HOOK;

	public static void init() {
		for (int i = 0; i < HOOKS.length; i++) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BricksAndBeats.id(i == 0 ? "hook" : "hook_" + i));
			// durability 5 doubles as the redstone-hook charge counter (damage value = charges)
			Item.Properties props = new Item.Properties().durability(5).enchantable(15)
					.rarity(i >= 4 ? Rarity.EPIC : i >= 2 ? Rarity.RARE : Rarity.UNCOMMON);
			HOOKS[i] = Registry.register(BuiltInRegistries.ITEM, key, new Item(props.setId(key)));
		}
		ResourceKey<EntityType<?>> ek = ResourceKey.create(Registries.ENTITY_TYPE, BricksAndBeats.id("hook"));
		HOOK = Registry.register(BuiltInRegistries.ENTITY_TYPE, ek,
				EntityType.Builder.<HookEntity>of(HookEntity::new, MobCategory.MISC)
						.sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1).build(ek));
		Hooks.init();
	}

	private HookRegistry() {}
}
