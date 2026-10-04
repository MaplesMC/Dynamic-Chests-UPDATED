package net.example.dynamicchests.pocket;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Terrain of the pocket dimension: a perfectly flat, endless floor of smooth quartz. There are no
 * hills, caves, ores or water. Everything is a pure function of the coordinates, so chunks can be
 * generated in any order and on any thread.
 *
 * Each chest owns a plot; plots sit {@link #PLOT_SPACING} blocks apart. Coordinates inside a plot
 * are plot-local: the middle of the entrance room is (0, 0).
 */
public final class PocketTerrain {

	private PocketTerrain() {
	}

	/** Distance between two players' pockets. At least 20,000 blocks, so nobody ever stumbles into another pocket. */
	public static final int PLOT_SPACING = 25000;
	/** Y of the highest floor block; the walkable surface is at BASE_Y + 1. */
	public static final int BASE_Y = 63;
	public static final int MIN_Y = 0;
	public static final int HEIGHT = 384;


	private static final BlockState FLOOR = Blocks.SMOOTH_QUARTZ.defaultBlockState();
	private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

	public static int plotCenter(int value) {
		return Math.floorDiv(value + PLOT_SPACING / 2, PLOT_SPACING) * PLOT_SPACING;
	}

	/** Grid position of the n-th plot on a square spiral around (0, 0). */
	public static int[] spiral(int n) {
		int x = 0;
		int z = 0;
		int dx = 1;
		int dz = 0;
		int segment = 1;
		int passed = 0;
		for (int i = 0; i < n; i++) {
			x += dx;
			z += dz;
			passed++;
			if (passed == segment) {
				passed = 0;
				int t = dx;
				dx = -dz;
				dz = t;
				if (dz == 0) {
					segment++;
				}
			}
		}
		return new int[] {x, z};
	}

	/** The block of the flat world at a height: bedrock at the bottom, smooth quartz up to the floor, air above. */
	public static BlockState blockAt(int y) {
		if (y <= MIN_Y) {
			return BEDROCK;
		}
		return y <= BASE_Y ? FLOOR : Blocks.AIR.defaultBlockState();
	}
}
