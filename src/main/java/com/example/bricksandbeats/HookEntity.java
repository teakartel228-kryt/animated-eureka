package com.example.bricksandbeats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** The flying hook head. States: FLYING -> GRAPPLE (stuck in a block, pulls the owner) or PULL (drags a target). */
public class HookEntity extends ThrowableItemProjectile {
	public enum State { FLYING, GRAPPLE, PULL }

	public State state = State.FLYING;
	public int lvl;      // upgrade level 0..5
	public int power;    // Power enchantment: pull speed
	public double range = 20;
	public Vec3 anchor;  // GRAPPLE point
	public Entity pulled; // PULL target
	private Vec3 origin;
	private int age;

	public HookEntity(EntityType<? extends ThrowableItemProjectile> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	public HookEntity(Level level, LivingEntity owner, ItemStack stack) {
		super(HookRegistry.HOOK, owner, level, stack);
		setNoGravity(true);
	}

	@Override
	protected Item getDefaultItem() {
		return HookRegistry.HOOKS[0];
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		return !(e instanceof HookEntity) && super.canHitEntity(e);
	}

	@Override
	public void tick() {
		if (!(level() instanceof ServerLevel sl)) {
			super.tick();
			return;
		}
		if (!(getOwner() instanceof LivingEntity owner) || !owner.isAlive() || owner.level() != level()) {
			discard();
			return;
		}
		age++;
		if (origin == null) origin = position();
		if (age % 2 == 0) Hooks.chain(sl, owner.getEyePosition().add(0, -0.4, 0), position());

		switch (state) {
			case FLYING -> {
				if (position().distanceTo(origin) > range) {
					discard();
					return;
				}
				// Catch foreign projectiles: send them back (or along your aim while sneaking).
				for (Projectile p : sl.getEntitiesOfClass(Projectile.class, getBoundingBox().inflate(1.2),
						e -> e != this && !(e instanceof HookEntity) && e.getOwner() != owner)) {
					Vec3 v = p.getDeltaMovement();
					double sp = Math.max(v.length(), 1.0);
					p.setDeltaMovement(owner.isShiftKeyDown() ? owner.getLookAngle().scale(sp) : v.scale(-1.0));
					p.setOwner(owner);
					p.hurtMarked = true;
					discard();
					return;
				}
				super.tick();
			}
			case GRAPPLE -> {
				setDeltaMovement(Vec3.ZERO);
				if (anchor != null) setPos(anchor);
			}
			case PULL -> {
				Entity t = pulled;
				if (t == null || t.isRemoved() || age > 100) {
					discard();
					return;
				}
				Vec3 d = owner.position().add(0, owner.getBbHeight() / 2, 0).subtract(t.position());
				if (d.length() < 2.5) {
					discard();
					return;
				}
				t.setDeltaMovement(d.normalize().scale(0.6 + 0.15 * power).add(0, 0.05, 0));
				t.hurtMarked = true;
				setPos(t.getX(), t.getY(0.5), t.getZ());
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (level() instanceof ServerLevel sl && state == State.FLYING) Hooks.hitEntity(sl, this, hit.getEntity());
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		if (level() instanceof ServerLevel sl && state == State.FLYING) Hooks.hitBlock(sl, this, hit);
	}
}
