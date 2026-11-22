package com.example.bricksandbeats;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Function;

public final class ModBlocks {
	// traps root the victim (slowness + no jumping), spikes just hurt; players that sneak walk over them safely
	public static final Block COPPER_TRAP = hazard("copper_trap", 4.0F, 60, 80, false, 2.0, 1.0, SoundType.COPPER);
	public static final Block IRON_TRAP = hazard("iron_trap", 7.0F, 100, 100, false, 2.0, 1.0, SoundType.IRON);
	public static final Block COPPER_SPIKES = hazard("copper_spikes", 2.5F, 0, 10, false, 8.0, 1.0, SoundType.COPPER);
	public static final Block IRON_SPIKES = hazard("iron_spikes", 4.0F, 0, 10, false, 8.0, 1.0, SoundType.IRON);
	public static final Block DIAMOND_SPIKES = hazard("diamond_spikes", 6.0F, 0, 10, false, 8.0, 1.0, SoundType.IRON);
	public static final Block HIDDEN_SPIKES = hazard("hidden_spikes", 4.5F, 0, 10, true, 1.0, 0.0, SoundType.GRAVEL);

	public static final Block CHUNK_LOADER = block("chunk_loader", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 1200.0F).sound(SoundType.METAL).lightLevel(s -> 7));

	// Voidlite: see Voidlite.java. Sealed = unbreakable until a strong explosion (blast resistance 8: TNT next to it) weakens it.
	public static final Block SEALED_VOIDLITE_ORE = block("sealed_voidlite_ore", SealedVoidliteOre::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 8.0F).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops());
	// hardness 1400 = 60 s with a Netherite pickaxe + Efficiency V (1400 * 30 / 35 = 1200 ticks)
	public static final Block WEAKENED_VOIDLITE_ORE = block("weakened_voidlite_ore", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE).strength(1400.0F, 1200.0F).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops().lightLevel(s -> 3));
	// "almost unbreakable": about 30 minutes with the same pickaxe
	public static final Block VOIDLITE_BLOCK = block("voidlite_block", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK).strength(42000.0F, 3600000.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops());

	private static Block hazard(String name, float damage, int root, int cooldown, boolean bleed, double height, double inset, SoundType sound) {
		BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F, 3.0F)
				.sound(sound).noCollision().noOcclusion().pushReaction(PushReaction.DESTROY);
		return block(name, props -> new HazardBlock(props, damage, root, cooldown, bleed, height, inset), p);
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> bk = ResourceKey.create(Registries.BLOCK, BricksAndBeats.id(name));
		Block b = Registry.register(BuiltInRegistries.BLOCK, bk, factory.apply(props.setId(bk)));
		ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, BricksAndBeats.id(name));
		Registry.register(BuiltInRegistries.ITEM, ik, new BlockItem(b, new Item.Properties().setId(ik).useBlockDescriptionPrefix()));
		return b;
	}

	public static void init() {
	}

	private ModBlocks() {}
}
