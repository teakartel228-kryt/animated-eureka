package com.example.bricksandbeats.mixin;

import com.example.bricksandbeats.Gravitites;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * VVVVVV: walking on the ceiling must feel exactly like walking on the floor. Vanilla only sets onGround when you
 * land on something below you, so with flipped gravity you would slide around like on ice. Here a flipped player
 * touching the ceiling counts as "on the ground".
 */
@Mixin(Entity.class)
public abstract class GravityGroundMixin {
	@Inject(method = "onGround", at = @At("RETURN"), cancellable = true)
	private void bricksandbeats$ceilingIsGround(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) return;
		Entity self = (Entity) (Object) this;
		if (Gravitites.isFlipped(self) && Gravitites.touchingCeiling(self)) cir.setReturnValue(true);
	}
}
