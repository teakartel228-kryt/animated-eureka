package com.example.bricksandbeats;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * VVVVVV-style gravity flip (creative-only items).
 * <ul>
 * <li>You can flip ONLY while standing on a surface: on the floor or on the ceiling. In mid-air the item does nothing.</li>
 * <li>Right click on the floor: gravity reverses and you fall UP to the ceiling. Right click while standing on the
 * ceiling: you fall back down. The flip stays until you flip back, even if you put the item away.</li>
 * <li>While flipped, the ceiling counts as "ground" (normal walking control, no ice-like sliding), jumping is
 * disabled (like in VVVVVV) and the player model is drawn upside down. No fall damage in either direction.</li>
 * <li>Heavy Gravitite = the reversed pull is twice as strong, Light Gravitite = half as strong (floaty).</li>
 * </ul>
 * The flipped state is stored as a transient GRAVITY attribute modifier (synced to clients), so the client mixins
 * (GravityGroundMixin, GravityJumpMixin, GravityRenderMixin) can detect it through {@link #isFlipped(Entity)}.
 */
public final class Gravitites {
	private static final String TAG_HEAVY = "ba_gflip_h";
	private static final String TAG_LIGHT = "ba_gflip_l";
	private static final Identifier FLIP = BricksAndBeats.id("gravity_flip");

	/** True when the player currently has reversed gravity. Safe to call on both client and server. */
	public static boolean isFlipped(Entity e) {
		if (!(e instanceof Player p)) return false;
		AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
		return g != null && g.getModifier(FLIP) != null;
	}

	/** True when something solid is right above the entity's head (standing on the ceiling). */
	public static boolean touchingCeiling(Entity e) {
		return !e.level().noCollision(e, e.getBoundingBox().move(0.0, 0.02, 0.0));
	}

	/** True when something solid is right below the entity's feet (standing on the floor). */
	public static boolean touchingFloor(Entity e) {
		return !e.level().noCollision(e, e.getBoundingBox().move(0.0, -0.02, 0.0));
	}

	public static class FlipItem extends Item {
		private final boolean heavy;

		public FlipItem(Properties p, boolean heavy) {
			super(p);
			this.heavy = heavy;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
			ItemStack stack = player.getItemInHand(hand);
			if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
			boolean flipped = player.getTags().contains(TAG_HEAVY) || player.getTags().contains(TAG_LIGHT);
			// VVVVVV rule: you can only flip while standing on a surface (floor, or ceiling when flipped)
			boolean grounded = flipped ? touchingCeiling(player) : touchingFloor(player);
			if (!grounded || player.isInWater() || player.isInLava()) return InteractionResult.FAIL;
			player.removeTag(TAG_HEAVY);
			player.removeTag(TAG_LIGHT);
			if (!flipped) player.addTag(heavy ? TAG_HEAVY : TAG_LIGHT);
			player.resetFallDistance();
			sl.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, flipped ? 0.8F : 1.6F);
			sl.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.3, 0.8, 0.3, 0.2);
			if (player instanceof ServerPlayer sp) sp.sendSystemMessage(Component.translatable(flipped ? "message.bricks_and_beats.gravity_normal" : "message.bricks_and_beats.gravity_flipped"), true);
			player.getCooldowns().addCooldown(stack, 8);
			return InteractionResult.SUCCESS;
		}
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (Player p : server.getPlayerList().getPlayers()) {
				AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
				if (g == null) continue;
				boolean heavy = p.getTags().contains(TAG_HEAVY);
				boolean light = p.getTags().contains(TAG_LIGHT);
				AttributeModifier cur = g.getModifier(FLIP);
				// base gravity 0.08: x(1 + m) -> -0.16 (heavy) / -0.04 (light)
				double want = heavy ? -3.0 : light ? -1.5 : 0.0;
				if ((heavy || light) && (cur == null || cur.amount() != want)) {
					g.removeModifier(FLIP);
					g.addTransientModifier(new AttributeModifier(FLIP, want, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				} else if (!heavy && !light && cur != null) {
					g.removeModifier(FLIP);
				}
				// no fall damage in either direction while flipped
				if (heavy || light) p.resetFallDistance();
			}
		});
	}

	private Gravitites() {}
}
