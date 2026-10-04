package net.example.dynamicchests.pocket;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Chunk generator of the pocket dimension. All terrain comes from {@link PocketTerrain}; there are
 * no caves, ores, structures, water or vegetation. Chunks are generated lazily like in any other
 * dimension, so the "endless" world costs nothing until somebody walks there.
 */
public class PocketChunkGenerator extends ChunkGenerator {

	public static final MapCodec<PocketChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource)
	).apply(instance, PocketChunkGenerator::new));

	private final BiomeSource biomeSource;

	public PocketChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
		this.biomeSource = biomeSource;
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
			StructureManager structureManager, ChunkAccess chunk) {
		Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				for (int y = PocketTerrain.MIN_Y; y <= PocketTerrain.BASE_Y; y++) {
					BlockState state = PocketTerrain.blockAt(y);
					pos.set(minX + dx, y, minZ + dz);
					chunk.setBlockState(pos, state);
					oceanFloor.update(dx, y, dz, state);
					worldSurface.update(dx, y, dz, state);
				}
			}
		}
		return CompletableFuture.completedFuture(chunk);
	}

	@Override
	public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
			StructureManager structureManager, ChunkAccess chunk) {
	}

	@Override
	public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState,
			ChunkAccess chunk) {
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
	}

	/** Natural spawning is off unless the server config turns it on; spawn eggs and commands always work. */
	@Override
	public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager structureManager,
			MobCategory category, BlockPos pos) {
		if (!PocketConfig.get().allowNaturalSpawning) {
			return WeightedList.of();
		}
		return super.getMobsAt(biome, structureManager, category, pos);
	}

	@Override
	public int getGenDepth() {
		return PocketTerrain.HEIGHT;
	}

	@Override
	public int getSeaLevel() {
		return PocketTerrain.BASE_Y;
	}

	@Override
	public int getMinY() {
		return PocketTerrain.MIN_Y;
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
		return PocketTerrain.BASE_Y + 1;
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
		BlockState[] states = new BlockState[PocketTerrain.BASE_Y - PocketTerrain.MIN_Y + 1];
		for (int i = 0; i < states.length; i++) {
			states[i] = PocketTerrain.blockAt(PocketTerrain.MIN_Y + i);
		}
		return new NoiseColumn(PocketTerrain.MIN_Y, states);
	}

	@Override
	public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
	}
}
