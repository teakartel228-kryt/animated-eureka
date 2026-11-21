package com.example.bricksandbeats;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

/** Creative tab "Bricks & Beats". */
public final class ModTab {
	public static void init() {
		ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, BricksAndBeats.id("main"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, FabricItemGroup.builder()
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
					for (Holder<Potion> p : new Holder[]{ModEffects.RHYTHM_POTION, ModEffects.BRICK_SKIN_POTION,
							ModEffects.FRAGILITY_POTION, ModEffects.CHAOS_POTION}) {
						@SuppressWarnings("unchecked") Holder<Potion> potion = p;
						out.accept(PotionContents.createItemStack(Items.POTION, potion));
					}
				}).build());
	}

	private ModTab() {}
}
