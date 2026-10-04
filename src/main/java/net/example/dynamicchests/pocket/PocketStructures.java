package net.example.dynamicchests.pocket;

import net.example.dynamicchests.block.PocketReturnGateBlock;
import net.example.dynamicchests.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The pocket dimension has no structures. The only thing placed in it is the return gate: a bare
 * 3x2 pane of portal blocks standing on the flat floor where a player arrives, so there is always a
 * way back out.
 */
public final class PocketStructures {

	private PocketStructures() {
	}

	/** Makes sure the chunks around a spot exist (generating them if needed) so blocks can be placed. */
	public static void loadArea(ServerLevel level, int centerX, int centerZ, int radius) {
		for (int cx = (centerX - radius) >> 4; cx <= (centerX + radius) >> 4; cx++) {
			for (int cz = (centerZ - radius) >> 4; cz <= (centerZ + radius) >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
	}

	/** Places the return gate three blocks north of the arrival point, facing it. */
	public static void buildReturnGate(ServerLevel level, int ox, int oz) {
		loadArea(level, ox, oz, 8);
		BlockState gate = ModRegistry.POCKET_RETURN_GATE_BLOCK.defaultBlockState()
				.setValue(PocketReturnGateBlock.AXIS, Direction.Axis.X);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = 1; dy <= 2; dy++) {
				level.setBlock(new BlockPos(ox + dx, PocketTerrain.BASE_Y + dy, oz - 3), gate, Block.UPDATE_CLIENTS);
			}
		}
	}
}
