package com.example.bricksandbeats;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Abilities of ALL zombies / skeletons (not only the variants):
 *  - a mob holding a hook shoots it at its target and pulls it in (see Hooks.mobFire);
 *  - a zombie holding a tool digs the blocks that tool is good at when they block its way to the target
 *    (pickaxe: stone/ores, axe: wood, shovel: dirt/sand/gravel, hoe: leaves/hay..., sword: cobwebs...).
 *    Breaking takes the same time as for a player and wears the tool down.
 */
public final class MobAbilities {
	private static final Set<String> ZOMBIES = Set.of("zombie", "husk", "drowned", "zombie_villager");
	private static final Set<String> SKELETONS = Set.of("skeleton", "stray", "bogged", "wither_skeleton");

	private static final Set<Mob> MOBS = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Map<Mob, Dig> DIG = new IdentityHashMap<>();

	private static final class Dig {
		BlockPos pos;
		float progress;
	}

	private static String typeName(Mob m) {
		var id = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType());
		return "minecraft".equals(id.getNamespace()) ? id.getPath() : "";
	}

	private static boolean zombie(Mob m) {
		return ZOMBIES.contains(typeName(m));
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof Mob m) {
				String t = typeName(m);
				if (ZOMBIES.contains(t) || SKELETONS.contains(t)) MOBS.add(m);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e instanceof Mob m) {
				MOBS.remove(m);
				Dig d = DIG.remove(m);
				if (d != null && level instanceof ServerLevel sl) sl.destroyBlockProgress(m.getId(), d.pos, -1);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	private static void tick() {
		for (Mob mob : new ArrayList<>(MOBS)) {
			if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel sl)) {
				MOBS.remove(mob);
				DIG.remove(mob);
				continue;
			}
			LivingEntity target = mob.getTarget();
			if (target == null || !target.isAlive()) {
				stopDig(sl, mob);
				continue;
			}
			ItemStack held = mob.getMainHandItem();
			if (held.isEmpty()) {
				stopDig(sl, mob);
				continue;
			}
			// --- hooks
			if (Hooks.levelOf(held) >= 0) {
				if (mob.tickCount % 5 == 0) {
					double dist = mob.distanceTo(target);
					if (dist > 4.0 && dist < 18.0 && mob.hasLineOfSight(target)) Hooks.mobFire(sl, mob, target);
				}
				continue;
			}
			// --- tools (zombies only; the old breaker variants keep their own logic)
			if (zombie(mob) && !MobVariants.isBlockBreaker(mob) && held.isDamageableItem()) dig(sl, mob, target, held);
		}
	}

	private static void stopDig(ServerLevel sl, Mob mob) {
		Dig d = DIG.remove(mob);
		if (d != null) sl.destroyBlockProgress(mob.getId(), d.pos, -1);
	}

	/** Can this tool break the block efficiently? (speed above 1 means "the right tool") */
	private static boolean usable(ItemStack tool, BlockState s) {
		return tool.getDestroySpeed(s) > 1.0F && (!s.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(s));
	}

	private static boolean diggable(ServerLevel sl, BlockPos p, ItemStack tool) {
		BlockState s = sl.getBlockState(p);
		if (s.isAir() || !s.getFluidState().isEmpty()) return false;
		float h = s.getDestroySpeed(sl, p);
		return h >= 0.0F && usable(tool, s);
	}

	private static void dig(ServerLevel sl, Mob mob, LivingEntity target, ItemStack tool) {
		Dig d = DIG.get(mob);
		if (d == null) {
			if (mob.tickCount % 5 != 0) return;
			double dx = target.getX() - mob.getX();
			double dz = target.getZ() - mob.getZ();
			double hd = dx * dx + dz * dz;
			if (hd > 32 * 32 || mob.distanceTo(target) > 32) return;
			// only dig when the mob is stuck at an obstacle on its way
			boolean stuck = mob.horizontalCollision || mob.getNavigation().isDone();
			if (!stuck) return;
			BlockPos feet = mob.blockPosition();
			Direction dir = Direction.getApproximateNearest(dx, 0, dz);
			java.util.List<BlockPos> cand = new ArrayList<>();
			if (hd > 1.5) {
				cand.add(feet.above().relative(dir));
				cand.add(feet.relative(dir));
			}
			if (target.getY() > mob.getY() + 1.5) cand.add(feet.above(2));      // dig upwards
			if (target.getY() < mob.getY() - 1.5) cand.add(feet.below());       // dig downwards
			for (BlockPos p : cand) {
				if (diggable(sl, p, tool)) {
					d = new Dig();
					d.pos = p;
					DIG.put(mob, d);
					break;
				}
			}
			if (d == null) return;
		}
		BlockState s = sl.getBlockState(d.pos);
		if (!diggable(sl, d.pos, tool) || mob.distanceToSqr(Vec3.atCenterOf(d.pos)) > 16.0) {
			stopDig(sl, mob);
			return;
		}
		mob.getLookControl().setLookAt(d.pos.getX() + 0.5, d.pos.getY() + 0.5, d.pos.getZ() + 0.5);
		float hardness = s.getDestroySpeed(sl, d.pos);
		float speed = tool.getDestroySpeed(s);
		d.progress += hardness <= 0.0F ? 1.0F : speed / hardness / 30.0F;
		if (mob.tickCount % 6 == 0) mob.swing(InteractionHand.MAIN_HAND);
		if (d.progress < 1.0F) {
			sl.destroyBlockProgress(mob.getId(), d.pos, (int) (d.progress * 10.0F));
			return;
		}
		sl.destroyBlockProgress(mob.getId(), d.pos, -1);
		sl.destroyBlock(d.pos, true, mob);
		DIG.remove(mob);
		tool.setDamageValue(tool.getDamageValue() + 1);
		if (tool.getDamageValue() >= tool.getMaxDamage()) mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
	}

	private MobAbilities() {}
}
