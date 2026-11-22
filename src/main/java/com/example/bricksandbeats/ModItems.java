package com.example.bricksandbeats;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.equipment.ArmorType;

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

	// daggers: fast, backstab bonus. scythes: slow, hit everything around the target.
	public static final Item[] DAGGERS = {
			weapon("iron_dagger", 3.0, -1.0, 250, 14, Rarity.COMMON),
			weapon("golden_dagger", 2.0, -1.0, 32, 22, Rarity.UNCOMMON),
			weapon("diamond_dagger", 4.0, -1.0, 1561, 10, Rarity.RARE),
			weapon("copper_dagger", 2.5, -1.0, 190, 12, Rarity.COMMON),
	};
	public static final Item[] SCYTHES = {
			weapon("iron_scythe", 6.0, -3.0, 250, 14, Rarity.COMMON),
			weapon("golden_scythe", 4.0, -3.0, 32, 22, Rarity.UNCOMMON),
			weapon("diamond_scythe", 7.0, -3.0, 1561, 10, Rarity.RARE),
			weapon("copper_scythe", 5.0, -3.0, 190, 12, Rarity.COMMON),
	};

	// creative-only, no recipes and no loot. The dropped item entity is invulnerable (fire, lava, explosions, cactus...):
	// the damage types are listed in data/bricks_and_beats/tags/damage_type/gravitite_resistant.json
	private static final DamageResistant GRAVITITE_RESISTANT = new DamageResistant(TagKey.create(Registries.DAMAGE_TYPE, BricksAndBeats.id("gravitite_resistant")));
	public static final Item HEAVY_GRAVITITE = register("heavy_gravitite", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).component(DataComponents.DAMAGE_RESISTANT, GRAVITITE_RESISTANT), p -> new Gravitites.FlipItem(p, true));
	public static final Item LIGHT_GRAVITITE = register("light_gravitite", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).component(DataComponents.DAMAGE_RESISTANT, GRAVITITE_RESISTANT), p -> new Gravitites.FlipItem(p, false));

	// Voidlite (see Voidlite.java): the ingot-like gem, netherite-beating armor, sword and pickaxe. Fire-proof like netherite.
	private static final DamageResistant VOID_RESISTANT = new DamageResistant(DamageTypeTags.IS_FIRE);
	public static final Item VOIDLITE = register("voidlite", new Item.Properties().rarity(Rarity.EPIC).component(DataComponents.DAMAGE_RESISTANT, VOID_RESISTANT));
	public static final Item VOIDLITE_HELMET = voidArmor("voidlite_helmet", ArmorType.HELMET);
	public static final Item VOIDLITE_CHESTPLATE = voidArmor("voidlite_chestplate", ArmorType.CHESTPLATE);
	public static final Item VOIDLITE_LEGGINGS = voidArmor("voidlite_leggings", ArmorType.LEGGINGS);
	public static final Item VOIDLITE_BOOTS = voidArmor("voidlite_boots", ArmorType.BOOTS);
	// netherite sword is 3 + 4 bonus (+1 base) = 8 damage; voidlite is 3 + 6 (+1) = 10
	public static final Item VOIDLITE_SWORD = register("voidlite_sword", new Item.Properties().sword(Voidlite.TOOL, 3.0F, -2.4F)
			.rarity(Rarity.EPIC).component(DataComponents.DAMAGE_RESISTANT, VOID_RESISTANT), Item::new);
	public static final Item VOIDLITE_PICKAXE = register("voidlite_pickaxe", new Item.Properties().pickaxe(Voidlite.TOOL, 1.0F, -2.8F)
			.rarity(Rarity.EPIC).component(DataComponents.DAMAGE_RESISTANT, VOID_RESISTANT), Item::new);

	private static Item voidArmor(String name, ArmorType type) {
		return register(name, new Item.Properties().humanoidArmor(Voidlite.ARMOR, type)
				.durability(type.getDurability(Voidlite.BASE_DURABILITY))
				.rarity(Rarity.EPIC).component(DataComponents.DAMAGE_RESISTANT, VOID_RESISTANT), Item::new);
	}

	public static final Item COPPER_ARROW = register("copper_arrow", new Item.Properties(), CustomArrows.CopperArrow::new);
	public static final Item PHANTOM_ARROW = register("phantom_arrow", new Item.Properties().rarity(Rarity.UNCOMMON), CustomArrows.PhantomArrow::new);

	private static Item weapon(String name, double dmg, double speed, int durability, int enchantability, Rarity rarity) {
		ItemAttributeModifiers mods = ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, dmg, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.build();
		return register(name, new Item.Properties().durability(durability).enchantable(enchantability).rarity(rarity)
				.component(DataComponents.ATTRIBUTE_MODIFIERS, mods), Item::new);
	}

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

	private static Item register(String name, Item.Properties properties, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BricksAndBeats.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
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
