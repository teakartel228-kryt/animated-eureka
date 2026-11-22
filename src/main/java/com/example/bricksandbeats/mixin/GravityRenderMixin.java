package com.example.bricksandbeats.mixin;

import com.example.bricksandbeats.Gravitites;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Draws a player with flipped gravity upside down (same mechanism vanilla uses for "Dinnerbone"). Client only. */
@Mixin(LivingEntityRenderer.class)
public abstract class GravityRenderMixin {
	@Inject(method = "isEntityUpsideDown", at = @At("RETURN"), cancellable = true)
	private static void bricksandbeats$flippedUpsideDown(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && Gravitites.isFlipped(entity)) cir.setReturnValue(true);
	}
}
