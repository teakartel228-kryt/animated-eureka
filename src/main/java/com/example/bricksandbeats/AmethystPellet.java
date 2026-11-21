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
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Pellet of the "Amethyst Spray" shotgun: short range, damages and knocks back along its flight direction. */
public class AmethystPellet extends ThrowableItemProjectile {
	public static final float DAMAGE = 3.0F;
	private static final int MAX_FLIGHT_TICKS = 12; // a bit shorter than a normal arrow

	public AmethystPellet(EntityType<? extends ThrowableItemProjectile> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	public AmethystPellet(Level level, double x, double y, double z, ItemStack stack) {
		super(ModEntities.AMETHYST_PELLET, x, y, z, level, stack);
		setNoGravity(true);
	}

	/** Fires a cone of pellets. */
	public static void spray(ServerLevel level, LivingEntity owner, Vec3 pos, Vec3 dir, int count, double spread, double speed) {
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			Vec3 d = dir.normalize().add(r.nextGaussian() * spread, r.nextGaussian() * spread, r.nextGaussian() * spread)
					.normalize().scale(speed);
			AmethystPellet p = new AmethystPellet(level, pos.x, pos.y, pos.z, new ItemStack(Items.AMETHYST_SHARD));
			p.setOwner(owner);
			p.setDeltaMovement(d);
			level.addFreshEntity(p);
		}
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0F, 0.6F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.DECORATED_POT_SHATTER, SoundSource.PLAYERS, 1.0F, 1.6F);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.AMETHYST_SHARD;
	}

	@Override
	public void tick() {
		super.tick();
		if (!isRemoved() && tickCount > MAX_FLIGHT_TICKS) {
			discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (level() instanceof ServerLevel serverLevel) {
			Entity target = hit.getEntity();
			target.hurtServer(serverLevel, damageSources().thrown(this, getOwner()), DAMAGE);
			// Knockback follows the flight direction, including its vertical part (shoot up = launch up, down = slam down).
			Vec3 push = getDeltaMovement().normalize().scale(0.55);
			target.push(push.x, push.y + 0.05, push.z);
			target.hurtMarked = true;
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (level() instanceof ServerLevel serverLevel && !isRemoved()) {
			Vec3 p = hit.getLocation();
			serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()),
					p.x, p.y, p.z, 6, 0.1, 0.1, 0.1, 0.05);
			discard();
		}
	}
}
