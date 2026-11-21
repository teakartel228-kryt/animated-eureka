package com.example.bricksandbeats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawn eggs for every zombie / skeleton variant. They are vanilla zombie/skeleton spawn eggs carrying
 * entity data with the variant tag; MobVariants then sets up name, gear and health when the mob loads.
 */
public final class VariantEggs {
	// name, vanilla mob, variant tag
	private static final String[][] EGGS = {
			{"brick_thrower", "zombie", "ba_mob_brick"},
			{"dynamiter", "zombie", "ba_mob_dynamite"},
			{"bard", "skeleton", "ba_mob_bard"},
			{"amethyst_shooter", "skeleton", "ba_mob_amethyst"},
			{"ink_skeleton", "skeleton", "ba_mob_ink"},
			{"necromancer", "zombie", "ba_mob_necro"},
			{"breaker", "zombie", "ba_mob_breaker"},
			{"miner", "zombie", "ba_mob_miner"},
			{"ram", "zombie", "ba_mob_ram"},
			{"digger", "zombie", "ba_mob_digger"},
			{"bulwark", "zombie", "ba_mob_bulwark"},
			{"frost_skeleton", "skeleton", "ba_mob_frost"},
			{"fire_skeleton", "skeleton", "ba_mob_fire"},
			{"hooker_zombie", "zombie", "ba_mob_hookz"},
			{"hooker_skeleton", "skeleton", "ba_mob_hooks"},
			// plain mobs: ba_rolled is set, so they never roll a variant
			{"plain_zombie", "zombie", "ba_plain"},
			{"plain_skeleton", "skeleton", "ba_plain"},
	};

	public static List<ItemStack> stacks() {
		List<ItemStack> list = new ArrayList<>();
		for (String[] e : EGGS) {
			ItemStack stack = create(e[0], e[1], e[2]);
			if (!stack.isEmpty()) list.add(stack);
		}
		return list;
	}

	private static ItemStack create(String name, String mob, String variantTag) {
		CompoundTag entity = new CompoundTag();
		entity.putString("id", "minecraft:" + mob);
		ListTag tags = new ListTag();
		tags.add(StringTag.valueOf("ba_rolled"));
		tags.add(StringTag.valueOf(variantTag));
		entity.put("Tags", tags);

		CompoundTag itemName = new CompoundTag();
		itemName.putString("translate", "item.bricks_and_beats." + name + "_spawn_egg");

		CompoundTag components = new CompoundTag();
		components.put("minecraft:entity_data", entity);
		components.put("minecraft:item_name", itemName);
		components.putString("minecraft:item_model", "bricks_and_beats:" + name + "_spawn_egg");

		CompoundTag root = new CompoundTag();
		root.putString("id", "minecraft:" + mob + "_spawn_egg");
		root.putInt("count", 1);
		root.put("components", components);

		return ItemStack.CODEC.parse(NbtOps.INSTANCE, root)
				.resultOrPartial(err -> BricksAndBeats.LOGGER.warn("Could not build spawn egg {}: {}", name, err))
				.orElse(ItemStack.EMPTY);
	}

	private VariantEggs() {}
}
