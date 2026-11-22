package com.example.bricksandbeats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawn eggs for every zombie / skeleton variant, in three power tiers (I weak, II strong, III elite).
 * They are vanilla zombie/skeleton spawn eggs carrying entity data with the variant tag (and ba_tier_N);
 * MobVariants then sets up name, gear, health and abilities when the mob loads.
 * Every egg has its own icon (the face of the base mob + the variant's symbol), a tier colour frame,
 * and a tooltip that says what the mob is and what it does.
 */
public final class VariantEggs {
	// name (lang key suffix + texture), vanilla mob, variant tag
	private static final String[][] EGGS = {
			// zombies
			{"brick_thrower", "zombie", "ba_mob_brick"},
			{"dynamiter", "zombie", "ba_mob_dynamite"},
			{"necromancer", "zombie", "ba_mob_necro"},
			{"breaker", "zombie", "ba_mob_breaker"},
			{"miner", "zombie", "ba_mob_miner"},
			{"ram", "zombie", "ba_mob_ram"},
			{"digger", "zombie", "ba_mob_digger"},
			{"hammer", "zombie", "ba_mob_hammer"},
			{"acid", "zombie", "ba_mob_acid"},
			{"bulwark", "zombie", "ba_mob_bulwark"},
			{"runner", "zombie", "ba_mob_runner"},
			{"plague", "zombie", "ba_mob_plague"},
			{"hooker_zombie", "zombie", "ba_mob_hookz"},
			{"big_zombie", "zombie", "ba_mob_big"},
			{"smart_zombie", "zombie", "ba_mob_smart"},
			{"giant_boss", "zombie", "ba_mob_giant"},
			{"zombie_shop", "zombie", "ba_mob_shop"},
			{"kotorybka", "zombie", "ba_mob_fisher"},
			// skeletons
			{"bard", "skeleton", "ba_mob_bard"},
			{"amethyst_shooter", "skeleton", "ba_mob_amethyst"},
			{"ink_skeleton", "skeleton", "ba_mob_ink"},
			{"frost_skeleton", "skeleton", "ba_mob_frost"},
			{"fire_skeleton", "skeleton", "ba_mob_fire"},
			{"venom_skeleton", "skeleton", "ba_mob_venom"},
			{"sniper_skeleton", "skeleton", "ba_mob_sniper"},
			{"storm_skeleton", "skeleton", "ba_mob_storm"},
			{"hooker_skeleton", "skeleton", "ba_mob_hooks"},
	};

	/** Name-colour rarity and frame colour of tier 1 / 2 / 3. */
	private static final String[] RARITY = {"uncommon", "rare", "epic"};
	private static final int[] TIER_COLOR = {0x55FF55, 0xFFAA00, 0xFF5555};

	public static List<ItemStack> stacks() {
		List<ItemStack> list = new ArrayList<>();
		// plain mobs first: ba_rolled is set, so they never roll a variant
		for (String mob : new String[]{"zombie", "skeleton"}) {
			ItemStack plain = create("plain_" + mob, mob, "ba_plain", 1, true);
			if (!plain.isEmpty()) list.add(plain);
		}
		for (int n = 1; n <= Stages.MAX; n++) {
			ItemStack st = createStage(n);
			if (!st.isEmpty()) list.add(st);
		}
		// all zombies then all skeletons, tier I -> III one after another so the stages are easy to find
		for (int tier = 1; tier <= 3; tier++) {
			for (String[] e : EGGS) {
				if (!e[1].equals("zombie")) continue;
				if (tier > 1 && (e[0].equals("giant_boss") || e[0].equals("zombie_shop"))) continue;
				ItemStack stack = create(e[0], e[1], e[2], tier, false);
				if (!stack.isEmpty()) list.add(stack);
			}
		}
		for (int tier = 1; tier <= 3; tier++) {
			for (String[] e : EGGS) {
				if (!e[1].equals("skeleton")) continue;
				ItemStack stack = create(e[0], e[1], e[2], tier, false);
				if (!stack.isEmpty()) list.add(stack);
			}
		}
		return list;
	}

	private static CompoundTag text(String translate, String color) {
		CompoundTag c = new CompoundTag();
		c.putString("translate", translate);
		c.putString("color", color);
		c.putByte("italic", (byte) 0);
		return c;
	}

	private static ItemStack create(String name, String mob, String variantTag, int tier, boolean plain) {
		CompoundTag entity = new CompoundTag();
		entity.putString("id", "minecraft:" + mob);
		ListTag tags = new ListTag();
		tags.add(StringTag.valueOf("ba_rolled"));
		tags.add(StringTag.valueOf(variantTag));
		if (tier > 1) tags.add(StringTag.valueOf("ba_tier_" + tier));
		entity.put("Tags", tags);

		// item name: "Spawn Egg: <mob> (<tier>)"
		CompoundTag itemName = new CompoundTag();
		ListTag with = new ListTag();
		if (plain) {
			itemName.putString("translate", "item.bricks_and_beats." + name + "_spawn_egg");
		} else {
			itemName.putString("translate", "item.bricks_and_beats.spawn_egg");
			with.add(translatable("mob.bricks_and_beats." + name));
			CompoundTag t = new CompoundTag();
			t.putString("text", tier == 3 ? "III" : tier == 2 ? "II" : "I");
			with.add(t);
			itemName.put("with", with);
		}

		// tooltip: what it is, what it does, how strong
		ListTag lore = new ListTag();
		lore.add(text("egg.bricks_and_beats.base." + mob, mob.equals("zombie") ? "green" : "gray"));
		if (!plain) {
			lore.add(text("ability.bricks_and_beats." + name, "gray"));
			lore.add(text("egg.bricks_and_beats.tier." + tier, tier == 3 ? "red" : tier == 2 ? "gold" : "green"));
		}

		CompoundTag components = new CompoundTag();
		components.put("minecraft:entity_data", entity);
		components.put("minecraft:item_name", itemName);
		components.put("minecraft:lore", lore);
		components.putString("minecraft:rarity", plain ? "common" : RARITY[tier - 1]);
		components.putString("minecraft:item_model", "bricks_and_beats:" + name + "_spawn_egg");
		// tier colour of the frame (tint index 0 of the item model)
		CompoundTag cmd = new CompoundTag();
		ListTag colors = new ListTag();
		colors.add(IntTag.valueOf(plain ? 0xAAAAAA : TIER_COLOR[tier - 1]));
		cmd.put("colors", colors);
		components.put("minecraft:custom_model_data", cmd);

		CompoundTag root = new CompoundTag();
		root.putString("id", "minecraft:" + mob + "_spawn_egg");
		root.putInt("count", 1);
		root.put("components", components);

		return ItemStack.CODEC.parse(NbtOps.INSTANCE, root)
				.resultOrPartial(err -> BricksAndBeats.LOGGER.warn("Could not build spawn egg {} (tier {}): {}", name, tier, err))
				.orElse(ItemStack.EMPTY);
	}

	/** Spawn egg of a plain zombie of stage n (1-15). */
	private static ItemStack createStage(int n) {
		int band = (n - 1) / 5;
		CompoundTag entity = new CompoundTag();
		entity.putString("id", "minecraft:zombie");
		ListTag tags = new ListTag();
		tags.add(StringTag.valueOf("ba_rolled"));
		tags.add(StringTag.valueOf("ba_stage_" + n));
		entity.put("Tags", tags);

		CompoundTag itemName = new CompoundTag();
		itemName.putString("translate", "item.bricks_and_beats.stage_egg");
		ListTag with = new ListTag();
		CompoundTag num = new CompoundTag();
		num.putString("text", String.valueOf(n));
		with.add(num);
		itemName.put("with", with);

		ListTag lore = new ListTag();
		lore.add(text("egg.bricks_and_beats.base.zombie", "green"));
		lore.add(text("ability.bricks_and_beats.stage_" + n, "gray"));

		CompoundTag components = new CompoundTag();
		components.put("minecraft:entity_data", entity);
		components.put("minecraft:item_name", itemName);
		components.put("minecraft:lore", lore);
		components.putString("minecraft:rarity", RARITY[band]);
		components.putString("minecraft:item_model", "bricks_and_beats:stage_zombie_" + n + "_spawn_egg");
		CompoundTag cmd = new CompoundTag();
		ListTag colors = new ListTag();
		colors.add(IntTag.valueOf(TIER_COLOR[band]));
		cmd.put("colors", colors);
		components.put("minecraft:custom_model_data", cmd);

		CompoundTag root = new CompoundTag();
		root.putString("id", "minecraft:zombie_spawn_egg");
		root.putInt("count", 1);
		root.put("components", components);
		return ItemStack.CODEC.parse(NbtOps.INSTANCE, root)
				.resultOrPartial(err -> BricksAndBeats.LOGGER.warn("Could not build stage egg {}: {}", n, err))
				.orElse(ItemStack.EMPTY);
	}

	private static CompoundTag translatable(String key) {
		CompoundTag c = new CompoundTag();
		c.putString("translate", key);
		return c;
	}

	private VariantEggs() {}
}
