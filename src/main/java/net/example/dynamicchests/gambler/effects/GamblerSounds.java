package net.example.dynamicchests.gambler.effects;

import net.example.dynamicchests.DynamicChests;
import net.example.dynamicchests.gambler.config.GamblerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom sound events of the Gambler's Chest. The events are defined in
 * {@code assets/dynamicchests/sounds.json} (resource packs can replace them), and the server config
 * maps each logical sound ("open", "jackpot", ...) to whichever sound event id the owner wants.
 */
public final class GamblerSounds {

	private GamblerSounds() {
	}

	private static final Map<String, SoundEvent> DEFAULTS = new LinkedHashMap<>();

	public static final SoundEvent OPEN = register("open", "gambler_open");
	public static final SoundEvent START = register("start", "gambler_start");
	public static final SoundEvent ROLL = register("roll", "gambler_roll");
	public static final SoundEvent WIN = register("win", "gambler_win");
	public static final SoundEvent JACKPOT = register("jackpot", "gambler_jackpot");
	public static final SoundEvent LOSS = register("loss", "gambler_lose");

	private static SoundEvent register(String key, String path) {
		Identifier id = Identifier.fromNamespaceAndPath(DynamicChests.MOD_ID, path);
		SoundEvent event = Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
		DEFAULTS.put(key, event);
		return event;
	}

	public static void init() {
		// Static initializer trigger.
	}

	/** Resolves the configured sound for a logical key, falling back to the built-in event. */
	public static SoundEvent resolve(String key) {
		SoundEvent fallback = DEFAULTS.get(key);
		String configured = GamblerConfig.get().sounds.get(key);
		if (configured == null || configured.isBlank()) {
			return fallback;
		}
		Identifier id = Identifier.tryParse(configured);
		if (id == null) {
			return fallback;
		}
		return BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(fallback);
	}

	public static void play(ServerLevel level, BlockPos pos, String key, float volume, float pitch) {
		level.playSound(null, pos, resolve(key), SoundSource.BLOCKS, volume, pitch);
	}
}
