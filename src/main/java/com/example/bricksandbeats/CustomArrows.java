package com.example.bricksandbeats;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Copper arrow (+1.5 damage) and phantom arrow (no gravity, pierces up to 3 targets). */
public final class CustomArrows {
	public static class CopperArrow extends ArrowItem {
		public CopperArrow(Item.Properties p) {
			super(p);
		}

		@Override
		public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity owner, ItemStack weapon) {
			AbstractArrow a = super.createArrow(level, ammo, owner, weapon);
			a.setBaseDamage(a.getBaseDamage() + 1.5);
			a.addTag("ba_copper_arrow");
			return a;
		}
	}

	public static class PhantomArrow extends ArrowItem {
		public PhantomArrow(Item.Properties p) {
			super(p);
		}

		@Override
		public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity owner, ItemStack weapon) {
			AbstractArrow a = super.createArrow(level, ammo, owner, weapon);
			a.setNoGravity(true);
			a.setPierceLevel((byte) 3);
			a.addTag("ba_phantom_arrow");
			return a;
		}
	}

	private CustomArrows() {}
}
