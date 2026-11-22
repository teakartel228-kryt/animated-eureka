package com.example.bricksandbeats.mixin;

import com.example.bricksandbeats.Gravitites;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** VVVVVV has no jump: while gravity is flipped the jump key does nothing. */
@Mixin(LivingEntity.class)
public abstract class GravityJumpMixin {
	@Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
	private void bricksandbeats$noJumpWhileFlipped(CallbackInfo ci) {
		if (Gravitites.isFlipped((Entity) (Object) this)) ci.cancel();
	}
}
