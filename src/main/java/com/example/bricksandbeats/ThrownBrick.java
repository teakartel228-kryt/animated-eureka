package com.example.bricksandbeats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A thrown brick / nether brick: hurts what it hits, then bursts into flying shards. */
public class ThrownBrick extends ThrowableItemProjectile {
	public static final float BRICK_DAMAGE = 3.0F;
	public static final float NETHER_BRICK_DAMAGE = 5.0F;

	public ThrownBrick(EntityType<? extends ThrowableItemProjectile> type, Level level) {
		super(type, level);
	}

	public ThrownBrick(Level level, LivingEntity owner, ItemStack stack) {
		super(ModEntities.THROWN_BRICK, owner, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.BRICK;
	}

	private boolean isNether() {
		return getItem().is(Items.NETHER_BRICK);
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (level() instanceof ServerLevel serverLevel) {
			Entity target = hit.getEntity();
			float damage = isNether() ? NETHER_BRICK_DAMAGE : BRICK_DAMAGE;
			target.hurtServer(serverLevel, damageSources().thrown(this, getOwner()), damage);
			if (isNether()) {
				target.igniteForSeconds(3.0F); // nether bricks are hot
			}
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (level() instanceof ServerLevel serverLevel && !isRemoved()) {
			shatter(serverLevel, hit);
			discard();
		}
	}

	private void shatter(ServerLevel level, HitResult hit) {
		boolean nether = isNether();
		Item shardItem = nether ? ModItems.NETHER_BRICK_SHARD : ModItems.BRICK_SHARD;
		RandomSource random = level.getRandom();

		Vec3 origin = hit.getLocation();
		Vec3 normal = Vec3.ZERO;
		if (hit instanceof BlockHitResult blockHit) {
			normal = new Vec3(blockHit.getDirection().getStepX(),
					blockHit.getDirection().getStepY(), blockHit.getDirection().getStepZ());
			origin = origin.add(normal.scale(0.15)); // keep shards out of the wall
		}

		// 4 shards craft back into 1 brick. Regular: 2-4 shards (lossy). Nether: 3-4 (hardier).
		int count = nether ? 3 + random.nextInt(2) : 2 + random.nextInt(3);
		for (int i = 0; i < count; i++) {
			BrickShard shard = new BrickShard(level, origin.x, origin.y, origin.z, new ItemStack(shardItem));
			shard.setOwner(getOwner());
			Vec3 dir = new Vec3(random.nextGaussian(), random.nextDouble() * 0.7 + 0.2, random.nextGaussian())
					.normalize().scale(0.25 + random.nextDouble() * 0.3)
					.add(normal.scale(0.2));
			shard.setDeltaMovement(dir);
			level.addFreshEntity(shard);
		}

		var block = nether ? Blocks.NETHER_BRICKS : Blocks.BRICKS;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState()),
				origin.x, origin.y, origin.z, 14, 0.15, 0.15, 0.15, 0.1);
		level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.DECORATED_POT_SHATTER,
				SoundSource.NEUTRAL, 1.0F, nether ? 0.8F : 1.1F);
	}
}
