package net.example.dynamicchests.data;

import com.mojang.serialization.Codec;
import net.example.dynamicchests.block.entity.SoulChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * Persistent registry of every placed Soul Chest keyed by dimension + BlockPos.
 * Uses the MC 26.2 SavedData / SavedDataType / Codec-based save system.
 * Survives server restarts and chunk unloads.
 */
public class SoulChestSavedData extends SavedData {

    // ── Codec ─────────────────────────────────────────────────────────────────

    private static final Codec<Set<Long>> LONG_SET_CODEC =
            Codec.LONG.listOf().xmap(HashSet::new, ArrayList::new);

    private static final Codec<Map<String, Set<Long>>> REGISTRY_CODEC =
            Codec.unboundedMap(Codec.STRING, LONG_SET_CODEC);

    public static final Codec<SoulChestSavedData> CODEC = REGISTRY_CODEC.xmap(
            SoulChestSavedData::new,
            data -> data.registry
    );

    // ── SavedDataType (the registry key + factory for the storage system) ─────

    public static final SavedDataType<SoulChestSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("dynamicchests", "soul_chests"),
            SoulChestSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    // ── State ─────────────────────────────────────────────────────────────────

    // dimension location string → set of encoded BlockPos longs
    private final Map<String, Set<Long>> registry;

    /** No-arg constructor for the SavedDataType supplier (creates empty data). */
    public SoulChestSavedData() {
        this.registry = new HashMap<>();
    }

    /** Codec constructor — called during deserialization. */
    SoulChestSavedData(Map<String, Set<Long>> loaded) {
        this.registry = new HashMap<>(loaded);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static SoulChestSavedData getOrCreate(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void register(ResourceKey<Level> dim, BlockPos pos) {
        registry.computeIfAbsent(dim.identifier().toString(), k -> new HashSet<>())
                .add(pos.asLong());
        setDirty();
    }

    public void unregister(ResourceKey<Level> dim, BlockPos pos) {
        Set<Long> set = registry.get(dim.identifier().toString());
        if (set != null) {
            set.remove(pos.asLong());
            if (set.isEmpty()) registry.remove(dim.identifier().toString());
            setDirty();
        }
    }

    /**
     * Finds the nearest registered Soul Chest that is still free (has not captured a death yet) across ALL dimensions.
     * Same-dimension chests are strongly preferred (10⁶× penalty for cross-dim).
     * Force-loads the chunk synchronously so unloaded chests are reachable.
     * Automatically cleans up stale registry entries on the way.
     */
    public SoulChestBlockEntity findNearest(MinecraftServer server,
                                            ResourceKey<Level> playerDim,
                                            BlockPos playerPos) {
        SoulChestBlockEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Map.Entry<String, Set<Long>> entry : new HashMap<>(registry).entrySet()) {
            ResourceKey<Level> dim = ResourceKey.create(
                    Registries.DIMENSION, Identifier.parse(entry.getKey()));
            ServerLevel dimLevel = server.getLevel(dim);
            if (dimLevel == null) continue;

            double crossDimPenalty = dim.equals(playerDim) ? 1.0 : 1_000_000.0;

            for (Long encoded : new HashSet<>(entry.getValue())) {
                BlockPos chestPos = BlockPos.of(encoded);

                // Force-load the chunk so the block entity is accessible.
                dimLevel.getChunkSource().getChunk(
                        chestPos.getX() >> 4, chestPos.getZ() >> 4, ChunkStatus.FULL, true);
                BlockEntity be = dimLevel.getBlockEntity(chestPos);

                if (!(be instanceof SoulChestBlockEntity soul)) {
                    // Block was removed without going through onPlace unregister — clean up.
                    entry.getValue().remove(encoded);
                    if (entry.getValue().isEmpty()) registry.remove(entry.getKey());
                    setDirty();
                    continue;
                }

                // A chest that already holds a death is used up: later deaths go to another free chest, or drop normally.
                if (soul.hasSoulItems()) {
                    continue;
                }

                double score = chestPos.distSqr(playerPos) * crossDimPenalty;
                if (score < bestScore) {
                    bestScore = score;
                    best = soul;
                }
            }
        }

        return best;
    }
}
