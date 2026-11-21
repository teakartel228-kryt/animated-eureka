package com.example.bricksandbeats.mixin;

import com.example.bricksandbeats.ModEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the Bricks & Beats potion recipes (on top of an Awkward Potion) to the vanilla brewing recipes. */
@Mixin(PotionBrewing.class)
public abstract class PotionBrewingMixin {
	@Inject(method = "addVanillaMixes", at = @At("TAIL"))
	private static void bricksandbeats$addMixes(PotionBrewing.Builder builder, CallbackInfo ci) {
		builder.addMix(Potions.AWKWARD, Items.NOTE_BLOCK, ModEffects.RHYTHM_POTION);
		builder.addMix(Potions.AWKWARD, Items.BRICK, ModEffects.BRICK_SKIN_POTION);
		builder.addMix(Potions.AWKWARD, Items.NETHER_BRICK, ModEffects.FRAGILITY_POTION);
		builder.addMix(Potions.AWKWARD, Items.AMETHYST_SHARD, ModEffects.CHAOS_POTION);
	}
}
