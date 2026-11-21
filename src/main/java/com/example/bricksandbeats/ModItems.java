package com.example.bricksandbeats;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	public static final Item BRICK_SHARD = register("brick_shard", new Item.Properties());
	public static final Item NETHER_BRICK_SHARD = register("nether_brick_shard", new Item.Properties());

	public static final Item NECROMANCER_STAFF = register("necromancer_staff", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	public static final Item[] STAVES = {
			register("wooden_staff", new Item.Properties().stacksTo(1)),
			register("stone_staff", new Item.Properties().stacksTo(1)),
			register("iron_staff", new Item.Properties().stacksTo(1)),
			register("golden_staff", new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)),
			register("diamond_staff", new Item.Properties().stacksTo(1).rarity(Rarity.RARE)),
			register("netherite_staff", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
	};

	public static final Item HAND_PISTON = register("hand_piston", new Item.Properties().stacksTo(1).enchantable(15).rarity(Rarity.UNCOMMON), true);

	/** Flying claw of the hook (renders the claw model; not obtainable, not in the creative tab). */
	public static final Item HOOK_HEAD = register("hook_head", new Item.Properties());

	// Songs are defined in data/bricks_and_beats/jukebox_song/*.json
	public static final Item DISC_HAMMER_OF_JUSTICE = disc("music_disc_hammer_of_justice");
	public static final Item DISC_ITS_TV_TIME = disc("music_disc_its_tv_time");
	public static final Item DISC_THE_WORLD_REVOLVING = disc("music_disc_the_world_revolving");
	public static final Item DISC_STEVES_INFERNO = disc("music_disc_steves_inferno");
	public static final Item DISC_BIG_SHOT = disc("music_disc_big_shot");

	private static Item disc(String name) {
		ResourceKey<JukeboxSong> song = ResourceKey.create(Registries.JUKEBOX_SONG, BricksAndBeats.id(name));
		return register(name, new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(song));
	}

	private static Item register(String name, Item.Properties properties) {
		return register(name, properties, false);
	}

	private static Item register(String name, Item.Properties properties, boolean piston) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BricksAndBeats.id(name));
		Item.Properties p = properties.setId(key);
		return Registry.register(BuiltInRegistries.ITEM, key, piston ? new HandPiston.PistonItem(p) : new Item(p));
	}

	public static void init() {
		// Touching the class registers everything above.
	}

	private ModItems() {}
}
