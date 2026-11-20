package com.example.bricksandbeats;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * TNT in a plain furnace.
 *
 * Put TNT into the furnace's input slot through its normal GUI. A smelting recipe
 * (data/bricks_and_beats/recipe/tnt_smelting.json) makes the furnace "cook" it like any other item with fuel.
 * The moment the cooking finishes (one TNT consumed, output slot grows) the furnace explodes, the output is
 * wiped (it gives nothing) and the furnace ceases to exist, so it cannot trigger again.
 * Furnaces without TNT behave 100% vanilla.
 */
public final class FurnaceDynamite {
	private static final int INPUT_SLOT = 0;
	private static final int OUTPUT_SLOT = 2;
	private static final float POWER = 2.5F;

	private record Snapshot(int tnt, Item outItem, int outCount) {}

	/** Loaded plain furnaces -> last seen state (null = not observed yet). */
	private static final Map<FurnaceBlockEntity, Snapshot> TRACKED = new IdentityHashMap<>();

	private static Snapshot snapshot(FurnaceBlockEntity furnace) {
		ItemStack in = furnace.getItem(INPUT_SLOT);
		ItemStack out = furnace.getItem(OUTPUT_SLOT);
		return new Snapshot(in.is(Items.TNT) ? in.getCount() : 0,
				out.isEmpty() ? null : out.getItem(), out.getCount());
	}

	public static void init() {
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof FurnaceBlockEntity furnace) {
				TRACKED.put(furnace, null);
			}
		});
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof FurnaceBlockEntity furnace) {
				TRACKED.remove(furnace);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	private static void tick() {
		if (TRACKED.isEmpty()) {
			return;
		}
		for (FurnaceBlockEntity furnace : new ArrayList<>(TRACKED.keySet())) {
			if (furnace.isRemoved() || !(furnace.getLevel() instanceof ServerLevel level)) {
				TRACKED.remove(furnace);
				continue;
			}

			Snapshot now = snapshot(furnace);
			Snapshot before = TRACKED.get(furnace);
			TRACKED.put(furnace, now);
			if (before == null) {
				continue; // first observation after load: just remember the state
			}

			boolean tntConsumed = now.tnt() < before.tnt();
			boolean outputGrew = now.outCount() > 0
					&& (before.outCount() == 0
						|| (now.outItem() == before.outItem() && now.outCount() > before.outCount()));
			if (tntConsumed && outputGrew) {
				explode(level, furnace);
			}
		}
	}

	private static void explode(ServerLevel level, FurnaceBlockEntity furnace) {
		BlockPos pos = furnace.getBlockPos();

		// Forget the furnace first and wipe the result: it gives nothing and can't trigger twice.
		TRACKED.remove(furnace);
		furnace.setItem(OUTPUT_SLOT, ItemStack.EMPTY);

		Vec3 c = Vec3.atCenterOf(pos);

		// Entity damage + vanilla blast sound/particles, but no terrain damage.
		level.explode(null, c.x, c.y, c.z, POWER, Level.ExplosionInteraction.NONE);

		// Extra, separate sounds and particles.
		level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 2.0F, 0.6F);
		level.playSound(null, pos, SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 1.0F, 0.5F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 40, 0.4, 0.4, 0.4, 0.15);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, 0.5, 0.5, 0.5, 0.05);

		// The furnace is gone (break particles; remaining contents such as fuel drop out).
		level.destroyBlock(pos, false);
	}

	private FurnaceDynamite() {}
}
