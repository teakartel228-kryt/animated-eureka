package com.example.bricksandbeats;

import net.minecraft.world.entity.Entity;

/**
 * Entity-tag helpers. Entity#getTags() is not available under that name in newer Minecraft versions,
 * so presence is checked through addTag/removeTag (addTag returns false when the tag is already there).
 */
public final class TagUtil {
	public static boolean has(Entity e, String tag) {
		if (e.addTag(tag)) {
			e.removeTag(tag);
			return false;
		}
		return true;
	}

	private TagUtil() {}
}
