package com.example.bricksandbeats;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Trap / spikes: entities can walk into it (no collision); Hazards.tick() applies the damage. */
public class HazardBlock extends Block {
	public final float damage;
	public final int rootTicks;
	public final int cooldown;
	public final boolean bleed;
	private final VoxelShape shape;

	public HazardBlock(Properties p, float damage, int rootTicks, int cooldown, boolean bleed, double height, double inset) {
		super(p);
		this.damage = damage;
		this.rootTicks = rootTicks;
		this.cooldown = cooldown;
		this.bleed = bleed;
		this.shape = Block.box(inset, 0, inset, 16 - inset, height, 16 - inset);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return shape;
	}
}
