package com.example.bricksandbeats;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.Map;

/**
 * Voidlite: an extremely rare ore hidden in the bedrock layer.
 * <ol>
 * <li>Sealed Voidlite Ore looks like bedrock with purple specks and cannot be broken (hardness -1). It has a
 * moderate blast resistance, so a strong explosion (TNT right next to it) turns it into Weakened Voidlite Ore.</li>
 * <li>Weakened Voidlite Ore (hardness 1400) takes about 60 s with a Netherite pickaxe + Efficiency V.</li>
 * <li>Block of Voidlite: hardness 42000, about 30 min with the same pickaxe, blast resistance of bedrock.</li>
 * <li>Armor beats Netherite (defense 24, toughness 6, knockback resistance 0.2 per piece, durability x50).
 * Sword: 10 damage. Pickaxe: faster than Netherite.</li>
 * </ol>
 */
public final class Voidlite {
	public static final TagKey<Item> REPAIRS = TagKey.create(Registries.ITEM, BricksAndBeats.id("repairs_voidlite"));
	public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, BricksAndBeats.id("voidlite"));
	/** Netherite is 37. */
	public static final int BASE_DURABILITY = 50;

	public static final ArmorMaterial ARMOR = new ArmorMaterial(
			BASE_DURABILITY,
			Map.of(ArmorType.HELMET, 4, ArmorType.CHESTPLATE, 9, ArmorType.LEGGINGS, 7, ArmorType.BOOTS, 4),
			25,
			SoundEvents.ARMOR_EQUIP_NETHERITE,
			6.0F,
			0.2F,
			REPAIRS,
			ASSET);

	/** Netherite: 2031 durability, speed 9, bonus damage 4. */
	public static final ToolMaterial TOOL = new ToolMaterial(
			BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
			4000,
			12.0F,
			6.0F,
			25,
			REPAIRS);

	/** Generates Sealed Voidlite Ore inside the bedrock floor of every overworld biome (see data/.../worldgen). */
	public static void init() {
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Decoration.UNDERGROUND_ORES,
				ResourceKey.create(Registries.PLACED_FEATURE, BricksAndBeats.id("ore_voidlite")));
	}

	private Voidlite() {}
}
