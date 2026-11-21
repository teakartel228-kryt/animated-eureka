package com.example.bricksandbeats;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BricksAndBeats implements ModInitializer {
	public static final String MOD_ID = "bricks_and_beats";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModItems.init();
		ModEntities.init();
		FurnaceDynamite.init();
		ModEffects.init();
		ArrowEnchants.init();
		MobVariants.init();
		ModTab.init();

		// Right click with a brick / nether brick -> throw it.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.BRICK) && !stack.is(Items.NETHER_BRICK)) {
				return InteractionResult.PASS;
			}

			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS,
					0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

			if (level instanceof ServerLevel serverLevel) {
				Projectile.spawnProjectileFromRotation(ThrownBrick::new, serverLevel, stack, player,
						0.0F, 1.5F, 1.0F);
			}
			stack.consume(1, player);
			return InteractionResult.SUCCESS;
		});
	}
}
