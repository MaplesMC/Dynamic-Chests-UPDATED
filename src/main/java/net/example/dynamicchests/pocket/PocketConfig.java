package net.example.dynamicchests.pocket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.example.dynamicchests.DynamicChests;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server config of the Pocket Dimension, stored as {@code config/dynamicchests-pocket.json}.
 * Missing values fall back to the defaults below and the file is rewritten on load.
 */
public final class PocketConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FILE_NAME = "dynamicchests-pocket.json";

	private static PocketConfig instance = new PocketConfig();

	static {
		instance.sanitize();
	}

	public static PocketConfig get() {
		return instance;
	}

	/** Keep the dimension in permanent bright daytime. Set false to enable a day/night cycle. */
	@SerializedName("permanent_daytime") public boolean permanentDaytime = true;
	/** Speed of the dimension's clock when the day/night cycle is on (1.0 = normal). */
	@SerializedName("time_speed") public double timeSpeed = 1.0;
	/** Allow hostile/passive mobs to spawn on their own. Mobs can always be spawned by hand. */
	@SerializedName("allow_natural_spawning") public boolean allowNaturalSpawning = false;
	/**
	 * Time scale for growth inside the dimension: 1.0 is normal, 2.0 makes random-tick processes
	 * (crops, saplings, ...) run twice as fast. Values below 1.0 are treated as 1.0.
	 */
	@SerializedName("dimension_time_scale") public double dimensionTimeScale = 1.0;

	private void sanitize() {
		timeSpeed = Math.max(0.0, Math.min(20.0, timeSpeed));
		dimensionTimeScale = Math.max(1.0, Math.min(8.0, dimensionTimeScale));
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		PocketConfig loaded = new PocketConfig();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				PocketConfig parsed = GSON.fromJson(reader, PocketConfig.class);
				if (parsed != null) {
					loaded = parsed;
				}
			} catch (Exception e) {
				DynamicChests.LOGGER.error("Could not read {}; using default Pocket Dimension settings", FILE_NAME, e);
			}
		}
		loaded.sanitize();
		instance = loaded;
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(loaded, writer);
		} catch (IOException e) {
			DynamicChests.LOGGER.warn("Could not write {}", FILE_NAME, e);
		}
	}
}
