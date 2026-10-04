package net.example.dynamicchests.pocket;

import net.example.dynamicchests.DynamicChests;
import net.example.dynamicchests.block.AbstractVaultChestBlock;
import net.example.dynamicchests.block.entity.PocketChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Everything that happens around the pocket dimension at runtime: entering from a chest (your own or
 * a friend's), leaving through the return gate, and the optional growth-speed and clock settings.
 *
 * Every player owns one pocket: a plot of the shared pocket dimension at least 20,000 blocks from
 * any other. A chest leads to the pocket of the player who placed it.
 */
public final class PocketManager {

	private PocketManager() {
	}

	public static final ResourceKey<Level> LEVEL_KEY =
			ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(DynamicChests.MOD_ID, "pocket"));
	private static final ResourceKey<WorldClock> CLOCK_KEY =
			ResourceKey.create(Registries.WORLD_CLOCK, Identifier.fromNamespaceAndPath(DynamicChests.MOD_ID, "pocket"));

	/** Noon on the clock: the sun at its highest. */
	private static final long NOON_TICKS = 6000;

	public static ServerLevel pocketLevel(MinecraftServer server) {
		return server.getLevel(LEVEL_KEY);
	}

	public static boolean isPocket(Level level) {
		return level.dimension().equals(LEVEL_KEY);
	}

	// ------------------------------------------------------------------
	// Entering
	// ------------------------------------------------------------------

	public static void enter(ServerPlayer player, PocketChestBlockEntity chest) {
		// No pockets inside pockets: a chest placed in the pocket dimension cannot be used to enter another one.
		if (isPocket(player.level())) {
			player.sendSystemMessage(Component.translatable("pocket.dynamicchests.no_nesting"), true);
			return;
		}
		MinecraftServer server = player.level().getServer();
		ServerLevel pocket = pocketLevel(server);
		UUID owner = chest.getOwnerId();
		if (pocket == null || owner == null) {
			player.sendSystemMessage(Component.translatable("pocket.dynamicchests.unavailable"), true);
			return;
		}
		PocketData data = PocketData.get(server);
		if (!data.mayEnter(owner, player.getUUID())) {
			player.sendSystemMessage(Component.translatable("pocket.dynamicchests.not_trusted", chest.getOwnerName()), true);
			return;
		}
		PocketData.Plot plot = data.plotFor(owner);
		boolean own = owner.equals(player.getUUID());
		if (own) {
			remember(plot, chest.getLevel(), chest.getBlockPos());
		}
		// Every visitor returns to the chest they actually walked in through.
		data.setReturn(player.getUUID(), new PocketData.ReturnPoint(
				chest.getLevel().dimension().identifier().toString(), chest.getBlockPos().asLong(),
				player.getYRot(), player.getXRot()));

		BlockPos origin = plot.origin();
		if (!plot.gateBuilt) {
			PocketStructures.buildReturnGate(pocket, origin.getX(), origin.getZ());
			plot.gateBuilt = true;
		}
		data.markChanged();

		burst((ServerLevel) player.level(), player.position());
		// Appear on the open floor, facing away from the return gate.
		player.teleportTo(pocket, origin.getX() + 0.5, PocketTerrain.BASE_Y + 1, origin.getZ() + 0.5, Set.of(), 0.0f, 0.0f, true);
		burst(pocket, player.position());
		if (!own) {
			player.sendSystemMessage(Component.translatable("pocket.dynamicchests.visiting", chest.getOwnerName()), true);
		}
	}

	public static void remember(PocketData.Plot plot, Level level, BlockPos pos) {
		plot.chestDimension = level.dimension().identifier().toString();
		plot.chestPos = pos.asLong();
	}

	// ------------------------------------------------------------------
	// Leaving
	// ------------------------------------------------------------------

	/** A pocket chest found in the world, with the side it faces. */
	private record ChestSpot(ServerLevel level, BlockPos pos, Direction facing) {
	}

	/** Sends a player from the pocket back to the chest they entered through. */
	public static void leave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		PocketData data = PocketData.get(server);
		PocketData.ReturnPoint ret = data.returnOf(player.getUUID());
		UUID plotOwner = data.ownerAt(player.getBlockX(), player.getBlockZ());

		ServerLevel destLevel = null;
		Vec3 target = null;
		float yaw = player.getYRot();
		float pitch = player.getXRot();

		if (ret != null) {
			yaw = ret.yaw();
			pitch = ret.pitch();
			// 1) the chest they came through, if it is still there
			ChestSpot spot = findChest(server, ret.dimension(), ret.pos());
			// 2) otherwise the pocket owner's current chest (it may simply have been moved)
			if (spot == null && plotOwner != null) {
				PocketData.Plot plot = data.existing(plotOwner);
				if (plot != null) {
					spot = findChest(server, plot.chestDimension, plot.chestPos);
				}
			}
			if (spot != null) {
				destLevel = spot.level();
				target = safeSpotNear(destLevel, spot.pos(), spot.facing());
				destLevel.sendParticles(ParticleTypes.PORTAL, spot.pos().getX() + 0.5, spot.pos().getY() + 1.0, spot.pos().getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.1);
			} else {
				// 3) the chest is gone: use the spot where it last stood
				destLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(ret.dimension())));
				if (destLevel != null) {
					BlockPos last = BlockPos.of(ret.pos());
					destLevel.getChunk(last.getX() >> 4, last.getZ() >> 4);
					target = safeSpotNear(destLevel, last, Direction.SOUTH);
				}
			}
		}
		if (target == null) {
			// Last resort: the overworld spawn.
			ServerLevel overworld = server.overworld();
			LevelData.RespawnData respawn = overworld.getRespawnData();
			destLevel = overworld;
			BlockPos spawn = respawn.pos();
			overworld.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
			target = safeSpotNear(overworld, spawn, Direction.SOUTH);
		}

		burst((ServerLevel) player.level(), player.position());
		player.teleportTo(destLevel, target.x, target.y, target.z, Set.of(), yaw, pitch, true);
		burst(destLevel, player.position());
	}

	private static ChestSpot findChest(MinecraftServer server, String dimension, long packedPos) {
		Identifier id = Identifier.tryParse(dimension);
		ServerLevel level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
		if (level == null) {
			return null;
		}
		BlockPos pos = BlockPos.of(packedPos);
		level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
		if (!(level.getBlockEntity(pos) instanceof PocketChestBlockEntity)) {
			return null;
		}
		BlockState state = level.getBlockState(pos);
		Direction facing = state.hasProperty(AbstractVaultChestBlock.FACING)
				? state.getValue(AbstractVaultChestBlock.FACING) : Direction.SOUTH;
		return new ChestSpot(level, pos, facing);
	}

	/** First free two-block-high spot next to {@code pos}, preferring the given side; the top of the block as last resort. */
	private static Vec3 safeSpotNear(ServerLevel level, BlockPos pos, Direction preferred) {
		Direction[] order = {preferred, preferred.getClockWise(), preferred.getCounterClockWise(), preferred.getOpposite()};
		for (Direction direction : order) {
			BlockPos feet = pos.relative(direction);
			if (isFree(level, feet) && isFree(level, feet.above())) {
				return Vec3.atBottomCenterOf(feet);
			}
		}
		BlockPos above = pos.above();
		if (isFree(level, above) && isFree(level, above.above())) {
			return Vec3.atBottomCenterOf(above);
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		return new Vec3(pos.getX() + 0.5, top, pos.getZ() + 0.5);
	}

	private static boolean isFree(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private static void burst(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 18, 0.35, 0.5, 0.35, 0.03);
		level.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6f, 0.8f);
	}

	// ------------------------------------------------------------------
	// Per-tick work
	// ------------------------------------------------------------------

	public static void tick(MinecraftServer server) {
		ServerLevel pocket = pocketLevel(server);
		if (pocket == null || pocket.players().isEmpty()) {
			return;
		}
		double scale = PocketConfig.get().dimensionTimeScale;
		if (scale > 1.0) {
			accelerateGrowth(pocket, scale);
		}
	}

	/** Extra random ticks near players, so crops and saplings grow faster than the normal rate. */
	private static void accelerateGrowth(ServerLevel pocket, double scale) {
		RandomSource random = pocket.getRandom();
		double extraPerSection = (scale - 1.0) * 3.0;
		int whole = (int) extraPerSection;
		double fraction = extraPerSection - whole;
		for (ServerPlayer player : pocket.players()) {
			int centerX = player.getBlockX() >> 4;
			int centerZ = player.getBlockZ() >> 4;
			for (int cx = centerX - 3; cx <= centerX + 3; cx++) {
				for (int cz = centerZ - 3; cz <= centerZ + 3; cz++) {
					LevelChunk chunk = pocket.getChunkSource().getChunkNow(cx, cz);
					if (chunk == null) {
						continue;
					}
					LevelChunkSection[] sections = chunk.getSections();
					for (int index = 0; index < sections.length; index++) {
						LevelChunkSection section = sections[index];
						if (section == null || section.hasOnlyAir() || !section.isRandomlyTickingBlocks()) {
							continue;
						}
						int tries = whole + (random.nextDouble() < fraction ? 1 : 0);
						int baseY = chunk.getMinY() + index * 16;
						for (int i = 0; i < tries; i++) {
							int lx = random.nextInt(16);
							int ly = random.nextInt(16);
							int lz = random.nextInt(16);
							BlockState state = section.getBlockState(lx, ly, lz);
							if (state.isRandomlyTicking()) {
								state.randomTick(pocket, new BlockPos((cx << 4) + lx, baseY + ly, (cz << 4) + lz), random);
							}
						}
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// Clock
	// ------------------------------------------------------------------

	/** Applies the day/night settings to the pocket dimension's own world clock. */
	public static void applyClockSettings(MinecraftServer server) {
		Optional<Holder.Reference<WorldClock>> clock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).get(CLOCK_KEY);
		if (clock.isEmpty()) {
			DynamicChests.LOGGER.warn("Pocket dimension clock is not registered; skipping clock setup");
			return;
		}
		PocketConfig config = PocketConfig.get();
		if (config.permanentDaytime) {
			server.clockManager().setTotalTicks(clock.get(), NOON_TICKS);
			server.clockManager().setPaused(clock.get(), true);
		} else {
			server.clockManager().setPaused(clock.get(), false);
			server.clockManager().setRate(clock.get(), (float) config.timeSpeed);
		}
	}
}
