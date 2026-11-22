package com.example.bricksandbeats;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Break time for zombies / skeletons that dig: soft blocks fall almost at once, hard blocks take disproportionately longer
 * (time ~ hardness^1.4, so stone is ~3x slower than dirt per hardness point and obsidian-class blocks take minutes).
 */
public final class BlockBreaking {
	/** Fraction of the block broken per tick (1.0 = done). speed: 1.0 = bare hands, a diamond pickaxe is 8. */
	public static float perTick(ServerLevel level, BlockPos pos, BlockState st, float speed) {
		float h = st.getDestroySpeed(level, pos);
		if (h < 0.0F) return 0.0F;
		float ticks = (float) (Math.pow(Math.max(h, 0.05F), 1.4) * 30.0 / Math.max(0.25F, speed));
		ticks = Math.max(3.0F, Math.min(2400.0F, ticks));
		return 1.0F / ticks;
	}

	private BlockBreaking() {}
}
