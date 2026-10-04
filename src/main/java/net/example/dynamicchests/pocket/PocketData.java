package net.example.dynamicchests.pocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent record of the pocket dimension: one plot per player (owner), the players each owner
 * trusts, and the place every player will return to when they walk out of a pocket.
 */
public class PocketData extends SavedData {

	/** The pocket of one player. */
	public static final class Plot {
		public int index;
		public boolean gateBuilt;
		/** Private pockets admit only the owner and trusted players; public ones admit everyone. */
		public boolean isPrivate = true;
		/** Dimension id and block position of the owner's most recently placed chest (fallback return spot). */
		public String chestDimension = "minecraft:overworld";
		public long chestPos;
		/** Trusted visitors: uuid to last known name. */
		public Map<String, String> trusted = new HashMap<>();

		public Plot() {
		}

		Plot(int index, boolean gateBuilt, boolean isPrivate, String chestDimension, long chestPos, Map<String, String> trusted) {
			this.index = index;
			this.gateBuilt = gateBuilt;
			this.isPrivate = isPrivate;
			this.chestDimension = chestDimension;
			this.chestPos = chestPos;
			this.trusted = new HashMap<>(trusted);
		}

		/** Where players arrive, in pocket-dimension coordinates. */
		public BlockPos origin() {
			int[] grid = PocketTerrain.spiral(this.index);
			return new BlockPos(grid[0] * PocketTerrain.PLOT_SPACING, PocketTerrain.BASE_Y, grid[1] * PocketTerrain.PLOT_SPACING);
		}
	}

	/** Where a player goes when they leave the pocket: the chest they entered through. */
	public record ReturnPoint(String dimension, long pos, float yaw, float pitch) {
		static final Codec<ReturnPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("dimension").forGetter(ReturnPoint::dimension),
				Codec.LONG.fieldOf("pos").forGetter(ReturnPoint::pos),
				Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(ReturnPoint::yaw),
				Codec.FLOAT.optionalFieldOf("pitch", 0f).forGetter(ReturnPoint::pitch)
		).apply(i, ReturnPoint::new));
	}

	private static final Codec<Plot> PLOT_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("index").forGetter(p -> p.index),
			Codec.BOOL.optionalFieldOf("gate_built", false).forGetter(p -> p.gateBuilt),
			Codec.BOOL.optionalFieldOf("private", true).forGetter(p -> p.isPrivate),
			Codec.STRING.optionalFieldOf("chest_dimension", "minecraft:overworld").forGetter(p -> p.chestDimension),
			Codec.LONG.optionalFieldOf("chest_pos", 0L).forGetter(p -> p.chestPos),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("trusted", Map.of()).forGetter(p -> p.trusted)
	).apply(i, Plot::new));

	private record Snapshot(int nextIndex, Map<String, Plot> plots, Map<String, ReturnPoint> returns) {
	}

	private static final Codec<PocketData> CODEC = RecordCodecBuilder.<Snapshot>create(i -> i.group(
			Codec.INT.optionalFieldOf("next_index", 0).forGetter(Snapshot::nextIndex),
			Codec.unboundedMap(Codec.STRING, PLOT_CODEC).optionalFieldOf("plots", Map.of()).forGetter(Snapshot::plots),
			Codec.unboundedMap(Codec.STRING, ReturnPoint.CODEC).optionalFieldOf("returns", Map.of()).forGetter(Snapshot::returns)
	).apply(i, Snapshot::new)).xmap(
			snapshot -> new PocketData(snapshot.nextIndex(), snapshot.plots(), snapshot.returns()),
			data -> new Snapshot(data.nextIndex, data.plots, data.returns)
	);

	public static final SavedDataType<PocketData> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("dynamicchests", "pocket_owners"),
			PocketData::new,
			CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);

	private int nextIndex;
	private final Map<String, Plot> plots;
	private final Map<String, ReturnPoint> returns;

	public PocketData() {
		this.nextIndex = 0;
		this.plots = new HashMap<>();
		this.returns = new HashMap<>();
	}

	PocketData(int nextIndex, Map<String, Plot> plots, Map<String, ReturnPoint> returns) {
		this.nextIndex = nextIndex;
		this.plots = new HashMap<>(plots);
		this.returns = new HashMap<>(returns);
	}

	public static PocketData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** The pocket of a player, allocating their plot the first time it is needed. */
	public Plot plotFor(UUID owner) {
		return this.plots.computeIfAbsent(owner.toString(), k -> {
			Plot plot = new Plot();
			plot.index = this.nextIndex++;
			setDirty();
			return plot;
		});
	}

	public Plot existing(UUID owner) {
		return this.plots.get(owner.toString());
	}

	/** Finds the owner of the plot that contains a pocket-dimension position, or null. */
	public UUID ownerAt(int x, int z) {
		int gridX = Math.floorDiv(PocketTerrain.plotCenter(x), PocketTerrain.PLOT_SPACING);
		int gridZ = Math.floorDiv(PocketTerrain.plotCenter(z), PocketTerrain.PLOT_SPACING);
		for (Map.Entry<String, Plot> entry : this.plots.entrySet()) {
			int[] grid = PocketTerrain.spiral(entry.getValue().index);
			if (grid[0] == gridX && grid[1] == gridZ) {
				return UUID.fromString(entry.getKey());
			}
		}
		return null;
	}

	public boolean mayEnter(UUID owner, UUID visitor) {
		if (owner.equals(visitor)) {
			return true;
		}
		Plot plot = existing(owner);
		if (plot == null) {
			return false;
		}
		return !plot.isPrivate || plot.trusted.containsKey(visitor.toString());
	}

	/** Flips a pocket between private and public and returns true if it is now private. */
	public boolean togglePrivacy(UUID owner) {
		Plot plot = plotFor(owner);
		plot.isPrivate = !plot.isPrivate;
		setDirty();
		return plot.isPrivate;
	}

	public void trust(UUID owner, UUID friend, String friendName) {
		plotFor(owner).trusted.put(friend.toString(), friendName);
		setDirty();
	}

	/** Returns true if the player had been trusted. */
	public boolean untrust(UUID owner, UUID friend) {
		Plot plot = existing(owner);
		boolean removed = plot != null && plot.trusted.remove(friend.toString()) != null;
		if (removed) {
			setDirty();
		}
		return removed;
	}

	public ReturnPoint returnOf(UUID player) {
		return this.returns.get(player.toString());
	}

	public void setReturn(UUID player, ReturnPoint point) {
		this.returns.put(player.toString(), point);
		setDirty();
	}

	public void markChanged() {
		setDirty();
	}
}
