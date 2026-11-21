package com.example.bricksandbeats;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class BricksAndBeatsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.THROWN_BRICK, ThrownItemRenderer::new);
		EntityRenderers.register(ModEntities.BRICK_SHARD, ThrownItemRenderer::new);
		EntityRenderers.register(ModEntities.AMETHYST_PELLET, ThrownItemRenderer::new);
	}
}
