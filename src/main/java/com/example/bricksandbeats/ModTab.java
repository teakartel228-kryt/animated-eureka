package com.example.bricksandbeats;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;

/** Creative tab "Bricks & Beats". */
public final class ModTab {
	/** Fabric API renamed its tab-builder class between versions; try the known names. */
	private static final String[] BUILDER_CLASSES = {
			"net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab",
			"net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup"
	};

	private static CreativeModeTab.Builder newBuilder() {
		for (String name : BUILDER_CLASSES) {
			try {
				return (CreativeModeTab.Builder) Class.forName(name).getMethod("builder").invoke(null);
			} catch (ReflectiveOperationException | LinkageError ignored) {
				// try the next name
			}
		}
		return null;
	}

	public static void init() {
		CreativeModeTab.Builder builder = newBuilder();
		if (builder == null) {
			BricksAndBeats.LOGGER.warn("Fabric API creative tab builder not found; skipping the Bricks & Beats tab");
			return;
		}
		ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, BricksAndBeats.id("main"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, builder
				.title(Component.translatable("itemGroup.bricks_and_beats"))
				.icon(() -> new ItemStack(Items.BRICK))
				.displayItems((params, out) -> {
					out.accept(Items.BRICK);
					out.accept(Items.NETHER_BRICK);
					out.accept(ModItems.BRICK_SHARD);
					out.accept(ModItems.NETHER_BRICK_SHARD);
					out.accept(ModItems.DISC_HAMMER_OF_JUSTICE);
					out.accept(ModItems.DISC_ITS_TV_TIME);
					out.accept(ModItems.DISC_THE_WORLD_REVOLVING);
					out.accept(ModItems.DISC_STEVES_INFERNO);
					out.accept(ModItems.DISC_BIG_SHOT);
					out.accept(Items.TNT);
					out.accept(ModItems.NECROMANCER_STAFF);
					for (Item st : ModItems.STAVES) out.accept(st);
					for (ItemStack egg : VariantEggs.stacks()) out.accept(egg);
					for (Item h : HookRegistry.HOOKS) out.accept(h);
					out.accept(ModItems.HAND_PISTON);
					for (Item d : ModItems.DAGGERS) out.accept(d);
					for (Item s : ModItems.SCYTHES) out.accept(s);
					out.accept(ModItems.HEAVY_GRAVITITE);
					out.accept(ModItems.LIGHT_GRAVITITE);
					out.accept(ModItems.COPPER_ARROW);
					out.accept(ModItems.PHANTOM_ARROW);
					out.accept(ModBlocks.COPPER_TRAP);
					out.accept(ModBlocks.IRON_TRAP);
					out.accept(ModBlocks.COPPER_SPIKES);
					out.accept(ModBlocks.IRON_SPIKES);
					out.accept(ModBlocks.DIAMOND_SPIKES);
					out.accept(ModBlocks.HIDDEN_SPIKES);
					out.accept(ModBlocks.CHUNK_LOADER);
					out.accept(ModBlocks.SEALED_VOIDLITE_ORE);
					out.accept(ModBlocks.WEAKENED_VOIDLITE_ORE);
					out.accept(ModBlocks.VOIDLITE_BLOCK);
					out.accept(ModItems.VOIDLITE);
					out.accept(ModItems.VOIDLITE_HELMET);
					out.accept(ModItems.VOIDLITE_CHESTPLATE);
					out.accept(ModItems.VOIDLITE_LEGGINGS);
					out.accept(ModItems.VOIDLITE_BOOTS);
					out.accept(ModItems.VOIDLITE_SWORD);
					out.accept(ModItems.VOIDLITE_PICKAXE);
					List<Holder<Potion>> potions = List.of(ModEffects.RHYTHM_POTION, ModEffects.BRICK_SKIN_POTION,
							ModEffects.FRAGILITY_POTION, ModEffects.CHAOS_POTION);
					for (Holder<Potion> potion : potions) {
						out.accept(PotionContents.createItemStack(Items.POTION, potion));
					}
				}).build());
	}

	private ModTab() {}
}
