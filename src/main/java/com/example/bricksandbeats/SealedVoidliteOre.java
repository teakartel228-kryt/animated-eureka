package com.example.bricksandbeats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiConsumer;

/**
 * Looks like bedrock and cannot be mined. A strong enough explosion does not destroy it, it only weakens it:
 * the block turns into Weakened Voidlite Ore, which can then be mined (very slowly).
 */
public class SealedVoidliteOre extends Block {
	public SealedVoidliteOre(Properties p) {
		super(p);
	}

	@Override
	protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
		level.setBlock(pos, ModBlocks.WEAKENED_VOIDLITE_ORE.defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.5F, 0.5F);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.1);
	}
}
