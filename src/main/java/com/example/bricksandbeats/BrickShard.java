package com.example.bricksandbeats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A flying shard: damages the first entity it hits, then lands as a pick-up-able item. */
public class BrickShard extends ThrowableItemProjectile {
	public static final float SHARD_DAMAGE = 1.0F;
	public static final float NETHER_SHARD_DAMAGE = 2.0F;
	private static final int MAX_FLIGHT_TICKS = 60;

	public BrickShard(EntityType<? extends ThrowableItemProjectile> type, Level level) {
		super(type, level);
	}

	public BrickShard(Level level, double x, double y, double z, ItemStack stack) {
		super(ModEntities.BRICK_SHARD, x, y, z, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.BRICK_SHARD;
	}

	@Override
	public void tick() {
		super.tick();
		if (!isRemoved() && level() instanceof ServerLevel serverLevel && tickCount > MAX_FLIGHT_TICKS) {
			dropAsItem(serverLevel, position());
			discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (level() instanceof ServerLevel serverLevel) {
			Entity target = hit.getEntity();
			float damage = getItem().is(ModItems.NETHER_BRICK_SHARD) ? NETHER_SHARD_DAMAGE : SHARD_DAMAGE;
			target.hurtServer(serverLevel, damageSources().thrown(this, getOwner()), damage);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (level() instanceof ServerLevel serverLevel && !isRemoved()) {
			Vec3 pos = hit.getLocation();
			if (hit instanceof BlockHitResult blockHit) {
				pos = pos.add(blockHit.getDirection().getStepX() * 0.15,
						blockHit.getDirection().getStepY() * 0.15,
						blockHit.getDirection().getStepZ() * 0.15);
			}
			dropAsItem(serverLevel, pos);
			discard();
		}
	}

	private void dropAsItem(ServerLevel level, Vec3 pos) {
		ItemEntity drop = new ItemEntity(level, pos.x, pos.y, pos.z, getItem().copyWithCount(1));
		drop.setDefaultPickUpDelay();
		level.addFreshEntity(drop);
	}
}
