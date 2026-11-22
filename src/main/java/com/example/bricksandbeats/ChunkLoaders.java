package com.example.bricksandbeats;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Chunk loader block: keeps the 3x3 chunks around it loaded (forced) all the time.
 * Loaders are remembered in bricks_and_beats_chunk_loaders.txt in the world folder; a loader whose block is gone is released.
 */
public final class ChunkLoaders {
	private record Loader(String dim, BlockPos pos) {}

	private static final class Pending {
		final ServerLevel level;
		final BlockPos pos;
		int left = 3;

		Pending(ServerLevel level, BlockPos pos) {
			this.level = level;
			this.pos = pos;
		}
	}

	private static final List<Loader> LOADERS = new ArrayList<>();
	private static final List<Pending> PENDING = new ArrayList<>();

	private static String dimOf(ServerLevel level) {
		return level.dimension().toString();
	}

	private static ServerLevel levelOf(MinecraftServer server, String dim) {
		for (ServerLevel l : server.getAllLevels()) if (dimOf(l).equals(dim)) return l;
		return null;
	}

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("bricks_and_beats_chunk_loaders.txt");
	}

	private static void save(MinecraftServer server) {
		try {
			List<String> lines = new ArrayList<>();
			for (Loader l : LOADERS) lines.add(l.dim() + "|" + l.pos().getX() + "|" + l.pos().getY() + "|" + l.pos().getZ());
			Files.write(file(server), lines);
		} catch (Exception e) {
			BricksAndBeats.LOGGER.warn("Could not save chunk loaders", e);
		}
	}

	private static void load(MinecraftServer server) {
		LOADERS.clear();
		try {
			Path f = file(server);
			if (!Files.exists(f)) return;
			for (String line : Files.readAllLines(f)) {
				String[] p = line.split("\\|");
				if (p.length != 4) continue;
				Loader l = new Loader(p[0], new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
				LOADERS.add(l);
				ServerLevel level = levelOf(server, l.dim());
				if (level != null) force(level, l.pos(), true);
			}
		} catch (Exception e) {
			BricksAndBeats.LOGGER.warn("Could not read chunk loaders", e);
		}
	}

	private static void force(ServerLevel level, BlockPos pos, boolean on) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setChunkForced((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz, on);
			}
		}
	}

	/** Releases a loader's chunks, except the ones another loader of the same dimension still needs. */
	private static void release(ServerLevel level, Loader gone) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int cx = (gone.pos().getX() >> 4) + dx;
				int cz = (gone.pos().getZ() >> 4) + dz;
				boolean needed = false;
				for (Loader o : LOADERS) {
					if (o.dim().equals(gone.dim()) && Math.abs((o.pos().getX() >> 4) - cx) <= 1 && Math.abs((o.pos().getZ() >> 4) - cz) <= 1) {
						needed = true;
						break;
					}
				}
				if (!needed) level.setChunkForced(cx, cz, false);
			}
		}
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(ChunkLoaders::load);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world instanceof ServerLevel sl && player.getItemInHand(hand).is(ModBlocks.CHUNK_LOADER.asItem())) {
				PENDING.add(new Pending(sl, hit.getBlockPos().relative(hit.getDirection())));
				PENDING.add(new Pending(sl, hit.getBlockPos()));
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(ChunkLoaders::tick);
	}

	private static void tick(MinecraftServer server) {
		if (!PENDING.isEmpty()) {
			Iterator<Pending> it = PENDING.iterator();
			while (it.hasNext()) {
				Pending p = it.next();
				if (--p.left > 0) continue;
				it.remove();
				if (p.level.getBlockState(p.pos).is(ModBlocks.CHUNK_LOADER)) {
					Loader l = new Loader(dimOf(p.level), p.pos.immutable());
					if (!LOADERS.contains(l)) {
						LOADERS.add(l);
						force(p.level, l.pos(), true);
						save(server);
					}
				}
			}
		}
		if (server.getTickCount() % 40 == 0 && !LOADERS.isEmpty()) {
			boolean changed = false;
			for (Loader l : new ArrayList<>(LOADERS)) {
				ServerLevel level = levelOf(server, l.dim());
				if (level == null || !level.hasChunkAt(l.pos())) continue;
				if (!level.getBlockState(l.pos()).is(ModBlocks.CHUNK_LOADER)) {
					LOADERS.remove(l);
					release(level, l);
					changed = true;
				}
			}
			if (changed) save(server);
		}
	}

	private ChunkLoaders() {}
}
