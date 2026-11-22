package com.example.bricksandbeats.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** setPierceLevel is private in AbstractArrow, so expose it through an invoker. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Invoker("setPierceLevel")
	void bricksandbeats$setPierceLevel(byte level);
}
