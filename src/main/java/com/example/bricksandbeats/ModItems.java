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
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BricksAndBeats.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}

	public static void init() {
		// Touching the class registers everything above.
	}

	private ModItems() {}
}
