package com.example.bricksandbeats;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * TNT in a plain furnace.
 *
 * Sneak + right-click a furnace while holding TNT to arm it. The furnace then works exactly as usual; the moment
 * it finishes smelting an item (its output slot grows) it explodes and ceases to exist. The "armed" flag is
 * cleared before the explosion, so it can never trigger twice. Unarmed furnaces are untouched.
 */
public final class FurnaceDynamite {
	public static final AttachmentType<Boolean> ARMED =
			AttachmentRegistry.createPersistent(BricksAndBeats.id("tnt_armed"), Codec.BOOL);

	private static final int OUTPUT_SLOT = 2;
	private static final float POWER = 2.5F;

	private record Snapshot(Item item, int count) {}

	/** Loaded plain furnaces -> last seen output (null = not observed yet). */
	private static final Map<FurnaceBlockEntity, Snapshot> TRACKED = new IdentityHashMap<>();

	private static Snapshot snapshot(FurnaceBlockEntity furnace) {
		ItemStack out = furnace.getItem(OUTPUT_SLOT);
		return new Snapshot(out.isEmpty() ? null : out.getItem(), out.getCount());
	}

	private static boolean isArmed(FurnaceBlockEntity furnace) {
		return furnace.getAttachedOrElse(ARMED, false);
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

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player.isSpectator() || !player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			BlockPos pos = hit.getBlockPos();
			if (!stack.is(Items.TNT) || !level.getBlockState(pos).is(Blocks.FURNACE)) {
				return InteractionResult.PASS;
			}
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (!(level.getBlockEntity(pos) instanceof FurnaceBlockEntity furnace)) {
				return InteractionResult.PASS;
			}
			if (isArmed(furnace)) {
				player.displayClientMessage(Component.translatable("message.bricks_and_beats.already_armed"), true);
				return InteractionResult.SUCCESS;
			}

			furnace.setAttached(ARMED, true);
			furnace.setChanged();
			TRACKED.put(furnace, snapshot(furnace));
			stack.consume(1, player);
			level.playSound(null, pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
			player.displayClientMessage(Component.translatable("message.bricks_and_beats.armed"), true);
			return InteractionResult.SUCCESS;
		});
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
			if (!isArmed(furnace)) {
				TRACKED.put(furnace, null);
				continue;
			}

			Snapshot now = snapshot(furnace);
			Snapshot before = TRACKED.get(furnace);
			TRACKED.put(furnace, now);
			if (before == null) {
				continue; // first observation after load: only remember the output
			}

			boolean finishedSmelting = now.count() > 0
					&& (before.count() == 0 || (now.item() == before.item() && now.count() > before.count()));
			if (finishedSmelting) {
				explode(level, furnace);
			}
		}
	}

	private static void explode(ServerLevel level, FurnaceBlockEntity furnace) {
		BlockPos pos = furnace.getBlockPos();

		// Disarm first: a second trigger is impossible.
		TRACKED.remove(furnace);
		furnace.removeAttached(ARMED);

		Vec3 c = Vec3.atCenterOf(pos);

		// Entity damage + vanilla blast sound/particles, but no terrain damage.
		level.explode(null, c.x, c.y, c.z, POWER, Level.ExplosionInteraction.NONE);

		// Extra, separate sounds and particles.
		level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 2.0F, 0.6F);
		level.playSound(null, pos, SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 1.0F, 0.5F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 40, 0.4, 0.4, 0.4, 0.15);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, 0.5, 0.5, 0.5, 0.05);

		// The furnace is gone (break particles, contents drop).
		level.destroyBlock(pos, false);
	}

	private FurnaceDynamite() {}
}
