package com.example.bricksandbeats;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Visual helpers built on vanilla display entities (item_display / block_display). They give us real 3D models
 * (hook claw, chain links, thrown bricks, piston head) without a custom entity renderer.
 * Everything is created and moved through vanilla commands, so it does not depend on renderer internals.
 * Display entities carry the tag "ba_fx" and are removed when they load (e.g. after a crash).
 */
public final class Fx {
	private static final Map<Entity, List<Entity>> FOLLOW = new IdentityHashMap<>();
	private record Later(ServerLevel level, long at, Runnable run) {}
	private static final List<Later> LATER = new ArrayList<>();

	/** Display entities created in this session; tagged displays that are not in this set are leftovers of a crash and get removed. */
	private static final java.util.Set<UUID> LIVE = new java.util.HashSet<>();

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (!LIVE.contains(e.getUUID()) && TagUtil.has(e, "ba_fx")) e.discard();
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> LIVE.remove(e.getUUID()));
	}

	private static void run(ServerLevel sl, String cmd) {
		MinecraftServer server = sl.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(sl).withSuppressedOutput(), cmd);
	}

	private static String uuidNbt(UUID u) {
		long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
		return "[I;" + (int) (m >> 32) + "," + (int) m + "," + (int) (l >> 32) + "," + (int) l + "]";
	}

	public static float yawOf(Vec3 d) {
		return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
	}

	public static float pitchOf(Vec3 d) {
		double len = d.length();
		return len < 1.0E-6 ? 0.0F : (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d.y / len))));
	}

	/** A 3D item model floating in the world. The model's centre (8,8,8) sits on pos, its +z axis points along (yaw, pitch). */
	public static Entity item(ServerLevel sl, String model, Vec3 pos, float yaw, float pitch) {
		UUID id = UUID.randomUUID();
		LIVE.add(id);
		String cmd = String.format(Locale.ROOT,
				"summon minecraft:item_display %.3f %.3f %.3f {UUID:%s,Rotation:[%.2ff,%.2ff],Tags:[\"ba_fx\"],item_display:\"none\","
						+ "teleport_duration:1,item:{id:\"minecraft:stick\",count:1,components:{\"minecraft:item_model\":\"bricks_and_beats:%s\"}}}",
				pos.x, pos.y, pos.z, uuidNbt(id), yaw, pitch, model);
		run(sl, cmd);
		return sl.getEntity(id);
	}

	/** A vanilla block (e.g. a piston head) shown as a display entity; pos is the block's corner. */
	public static Entity block(ServerLevel sl, String stateSnbt, Vec3 pos) {
		UUID id = UUID.randomUUID();
		LIVE.add(id);
		String cmd = String.format(Locale.ROOT,
				"summon minecraft:block_display %.3f %.3f %.3f {UUID:%s,Tags:[\"ba_fx\"],block_state:%s}",
				pos.x, pos.y, pos.z, uuidNbt(id), stateSnbt);
		run(sl, cmd);
		return sl.getEntity(id);
	}

	public static void move(ServerLevel sl, Entity e, Vec3 pos, float yaw, float pitch) {
		if (e == null || e.isRemoved()) return;
		run(sl, String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f %.2f %.2f", e.getUUID(), pos.x, pos.y, pos.z, yaw, pitch));
	}

	/** Roll of an item display around its local x axis (degrees, must stay under 180 between calls for smooth interpolation). */
	public static void rollX(ServerLevel sl, Entity e, float degrees) {
		if (e == null || e.isRemoved()) return;
		double h = Math.toRadians(degrees) / 2.0;
		run(sl, String.format(Locale.ROOT,
				"data merge entity %s {start_interpolation:0,interpolation_duration:1,transformation:{left_rotation:[%.4ff,0f,0f,%.4ff],"
						+ "right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1f,1f,1f]}}",
				e.getUUID(), Math.sin(h), Math.cos(h)));
	}

	/** The display entity lives exactly as long as its owner. */
	public static void follow(Entity owner, Entity display) {
		if (display != null) FOLLOW.computeIfAbsent(owner, k -> new ArrayList<>()).add(display);
	}

	public static void later(ServerLevel sl, int ticks, Runnable r) {
		LATER.add(new Later(sl, sl.getGameTime() + ticks, r));
	}

	public static void tick() {
		for (Iterator<Map.Entry<Entity, List<Entity>>> it = FOLLOW.entrySet().iterator(); it.hasNext(); ) {
			var en = it.next();
			if (en.getKey().isRemoved()) {
				for (Entity d : en.getValue()) if (!d.isRemoved()) d.discard();
				it.remove();
			}
		}
		for (Later l : new ArrayList<>(LATER)) {
			if (l.level().getGameTime() >= l.at()) {
				LATER.remove(l);
				l.run().run();
			}
		}
	}

	private Fx() {}
}
