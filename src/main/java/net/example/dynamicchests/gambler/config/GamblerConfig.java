package net.example.dynamicchests.gambler.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.example.dynamicchests.DynamicChests;
import net.example.dynamicchests.gambler.logic.GambleOutcome;
import net.example.dynamicchests.gambler.logic.ValueTier;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side configuration for the Gambler's Chest, stored as {@code config/dynamicchests-gambler.json}.
 * Missing fields fall back to the defaults declared here, so old config files keep working
 * after an update. The file is (re)written on load so new options always show up in it.
 */
public final class GamblerConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FILE_NAME = "dynamicchests-gambler.json";
	private static final int CURRENT_VERSION = 2;

	private static GamblerConfig instance = new GamblerConfig();

	static {
		instance.sanitize();
	}

	public static GamblerConfig get() {
		return instance;
	}

	// ------------------------------------------------------------------
	// Config sections
	// ------------------------------------------------------------------

	/** Config layout version; older files get their gamble/tier sections reset to the current defaults. */
	public int version = 0;
	public Gamble gamble = new Gamble();
	public Streak streak = new Streak();
	public Map<String, Tier> tiers = defaultTiers();
	public Values values = new Values();
	public Restrictions restrictions = new Restrictions();
	public Map<String, String> sounds = defaultSounds();
	/** Optional explicit upgrade pairs ("minecraft:iron_ingot": "minecraft:gold_ingot"). Overrides the automatic material chain. */
	public Map<String, String> upgrades = new LinkedHashMap<>();

	public static final class Gamble {
		@SerializedName("jackpot_chance") public double jackpotChance = 1;
		@SerializedName("triple_chance") public double tripleChance = 5;
		@SerializedName("double_chance") public double doubleChance = 15;
		@SerializedName("upgrade_chance") public double upgradeChance = 10;
		@SerializedName("return_chance") public double returnChance = 32;
		@SerializedName("loss_chance") public double lossChance = 37;

		/** Individual outcomes can be switched off; the remaining chances are rescaled to 100%. */
		@SerializedName("outcomes_enabled") public Map<String, Boolean> outcomesEnabled = defaultEnabled();


		/** Items to receive per bet item on a jackpot. */
		@SerializedName("jackpot_multiplier") public int jackpotMultiplier = 4;
		/** Items worth more than this each are never multiplied, so the most valuable items cannot be duplicated. */
		@SerializedName("max_reward_value") public double maxRewardValue = 1000;
		/** What an Upgrade becomes when the item cannot be upgraded: double or return. */
		@SerializedName("upgrade_fallback") public String upgradeFallback = "double";
		/** Length of the roll animation. Clamped to 20..200 ticks. */
		@SerializedName("animation_ticks") public int animationTicks = 50;
		/** How long the result stays on the display afterwards. */
		@SerializedName("result_display_ticks") public int resultDisplayTicks = 100;
	}

	public static final class Streak {
		public boolean enabled = true;
		/** Extra percentage points moved from Loss to Double/Triple per consecutive win. */
		@SerializedName("luck_per_win") public double luckPerWin = 0.5;
		@SerializedName("max_luck") public double maxLuck = 3.0;
		/** Consecutive losses before the recovery bonus kicks in. */
		@SerializedName("loss_streak_threshold") public int lossStreakThreshold = 3;
		/** Percentage points moved from Loss to Return per loss beyond the threshold. */
		@SerializedName("recovery_per_loss") public double recoveryPerLoss = 1.0;
		@SerializedName("max_recovery") public double maxRecovery = 6.0;
		/** Items worth less than this neither build nor break streaks, so cheap gambles can't farm luck. */
		@SerializedName("min_item_value") public double minItemValue = 5.0;
	}

	public static final class Tier {
		/** Items worth at least this much belong to the tier (highest matching tier wins). */
		@SerializedName("min_value") public double minValue;
		/** Percentage points moved from Return/Double into Loss (negative values move them back from Loss). */
		@SerializedName("extra_loss_chance") public double extraLossChance;
		/** Extra loot rolled on top of a jackpot. Empty for none. */
		@SerializedName("jackpot_bonus_loot_table") public String jackpotBonusLootTable = "";

		public Tier() {
		}

		Tier(double minValue, double extraLoss, String bonus) {
			this.minValue = minValue;
			this.extraLossChance = extraLoss;
			this.jackpotBonusLootTable = bonus;
		}
	}

	public static final class Values {
		/** Value of an item with no explicit entry, by vanilla rarity. */
		@SerializedName("rarity_values") public Map<String, Double> rarityValues = defaultRarityValues();
		@SerializedName("item_values") public Map<String, Double> itemValues = defaultItemValues();
	}

	public static final class Restrictions {
		/** Hoppers and other automation may insert/extract. Gambling itself always needs a player. */
		@SerializedName("allow_automation") public boolean allowAutomation = false;
		/** Allow items that carry an inventory (shulker boxes, bundles, filled containers). Dangerous. */
		@SerializedName("allow_containers") public boolean allowContainers = false;
		/** Exact item ids that can never be gambled. */
		public List<String> blacklist = defaultBlacklist();
		/** Item tag ids that can never be gambled. */
		@SerializedName("blacklist_tags") public List<String> blacklistTags = new ArrayList<>(List.of("minecraft:shulker_boxes"));
		/** Item ids ending with one of these suffixes are rejected (spawn eggs by default). */
		@SerializedName("blacklist_suffixes") public List<String> blacklistSuffixes = new ArrayList<>(List.of("_spawn_egg"));
	}

	// ------------------------------------------------------------------
	// Derived, validated state (not serialized)
	// ------------------------------------------------------------------

	private transient double[] baseChances = new double[GambleOutcome.VALUES.length];

	/** Base outcome chances after enable-flags and normalisation; always sums to 100 (indexed by outcome ordinal). */
	public double[] baseChances() {
		return this.baseChances.clone();
	}

	public Tier tier(ValueTier tier) {
		return this.tiers.computeIfAbsent(tier.key(), k -> new Tier());
	}

	public boolean isEnabled(GambleOutcome outcome) {
		return this.gamble.outcomesEnabled.getOrDefault(outcome.key(), true);
	}

	public int animationTicks() {
		return Math.max(20, Math.min(200, this.gamble.animationTicks));
	}

	// ------------------------------------------------------------------
	// Loading
	// ------------------------------------------------------------------

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		GamblerConfig loaded = new GamblerConfig();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				GamblerConfig parsed = GSON.fromJson(reader, GamblerConfig.class);
				if (parsed != null) {
					loaded = parsed;
				}
			} catch (Exception e) {
				DynamicChests.LOGGER.error("Could not read {}; using default Gambler's Chest settings", FILE_NAME, e);
				loaded = new GamblerConfig();
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

	/** Fills in anything a hand-edited file left null and validates the probabilities. */
	private void sanitize() {
		if (version < CURRENT_VERSION) {
			if (version > 0) {
				DynamicChests.LOGGER.warn("Gambler's Chest: config was written by an older version; resetting the gamble and tier sections to the new defaults");
			}
			gamble = new Gamble();
			tiers = defaultTiers();
			version = CURRENT_VERSION;
		}
		if (gamble == null) gamble = new Gamble();
		if (streak == null) streak = new Streak();
		if (values == null) values = new Values();
		if (restrictions == null) restrictions = new Restrictions();
		if (tiers == null) tiers = defaultTiers();
		if (sounds == null) sounds = new LinkedHashMap<>();
		if (upgrades == null) upgrades = new LinkedHashMap<>();
		if (gamble.outcomesEnabled == null) gamble.outcomesEnabled = defaultEnabled();
		if (values.itemValues == null) values.itemValues = new LinkedHashMap<>();
		if (values.rarityValues == null) values.rarityValues = defaultRarityValues();
		if (restrictions.blacklist == null) restrictions.blacklist = new ArrayList<>();
		if (restrictions.blacklistTags == null) restrictions.blacklistTags = new ArrayList<>();
		if (restrictions.blacklistSuffixes == null) restrictions.blacklistSuffixes = new ArrayList<>();
		for (ValueTier t : ValueTier.VALUES) {
			tiers.computeIfAbsent(t.key(), k -> defaultTiers().get(k));
		}
		defaultSounds().forEach(sounds::putIfAbsent);

		double[] raw = {
				gamble.jackpotChance, gamble.tripleChance, gamble.doubleChance, gamble.upgradeChance,
				gamble.returnChance, gamble.lossChance
		};
		double sum = 0;
		for (int i = 0; i < raw.length; i++) {
			if (raw[i] < 0 || Double.isNaN(raw[i])) {
				DynamicChests.LOGGER.error("Gambler's Chest: invalid chance for {}, treating it as 0", GambleOutcome.VALUES[i].key());
				raw[i] = 0;
			}
			sum += raw[i];
		}
		if (Math.abs(sum - 100.0) > 0.001) {
			DynamicChests.LOGGER.error("Gambler's Chest: outcome chances add up to {}% instead of 100%; rescaling them", sum);
		}
		double[] effective = new double[raw.length];
		double enabledSum = 0;
		for (int i = 0; i < raw.length; i++) {
			effective[i] = isEnabled(GambleOutcome.VALUES[i]) ? raw[i] : 0;
			enabledSum += effective[i];
		}
		if (enabledSum <= 0) {
			DynamicChests.LOGGER.error("Gambler's Chest: no enabled outcome has a chance above 0; falling back to returning the item");
			effective = new double[raw.length];
			effective[GambleOutcome.RETURN.ordinal()] = 100;
			enabledSum = 100;
		}
		for (int i = 0; i < effective.length; i++) {
			effective[i] = effective[i] * 100.0 / enabledSum;
		}
		this.baseChances = effective;
		gamble.jackpotMultiplier = Math.max(1, gamble.jackpotMultiplier);
		gamble.maxRewardValue = Math.max(1, gamble.maxRewardValue);
	}

	// ------------------------------------------------------------------
	// Defaults
	// ------------------------------------------------------------------

	private static Map<String, Boolean> defaultEnabled() {
		Map<String, Boolean> map = new LinkedHashMap<>();
		for (GambleOutcome o : GambleOutcome.VALUES) {
			map.put(o.key(), true);
		}
		return map;
	}

	private static Map<String, Tier> defaultTiers() {
		Map<String, Tier> map = new LinkedHashMap<>();
		map.put("common", new Tier(0, -5, ""));
		map.put("rare", new Tier(100, 10, "dynamicchests:gambler/jackpot_rare"));
		map.put("very_rare", new Tier(400, 20, "dynamicchests:gambler/jackpot_very_rare"));
		return map;
	}

	private static Map<String, String> defaultSounds() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("open", "dynamicchests:gambler_open");
		map.put("start", "dynamicchests:gambler_start");
		map.put("roll", "dynamicchests:gambler_roll");
		map.put("win", "dynamicchests:gambler_win");
		map.put("jackpot", "dynamicchests:gambler_jackpot");
		map.put("loss", "dynamicchests:gambler_lose");
		return map;
	}

	private static Map<String, Double> defaultRarityValues() {
		Map<String, Double> map = new LinkedHashMap<>();
		map.put("common", 5.0);
		map.put("uncommon", 25.0);
		map.put("rare", 120.0);
		map.put("epic", 450.0);
		return map;
	}

	private static Map<String, Double> defaultItemValues() {
		Map<String, Double> map = new LinkedHashMap<>();
		String[][] entries = {
				{"cobblestone", "1"}, {"dirt", "1"}, {"stick", "1"}, {"coal", "4"}, {"copper_ingot", "4"},
				{"iron_ingot", "10"}, {"gold_ingot", "25"}, {"redstone", "3"}, {"lapis_lazuli", "6"},
				{"emerald", "40"}, {"diamond", "100"}, {"amethyst_shard", "8"}, {"quartz", "6"},
				{"netherite_scrap", "150"}, {"netherite_ingot", "500"}, {"ancient_debris", "300"},
				{"iron_block", "90"}, {"gold_block", "225"}, {"emerald_block", "360"}, {"diamond_block", "900"},
				{"netherite_block", "4500"}, {"golden_apple", "60"}, {"enchanted_golden_apple", "500"},
				{"totem_of_undying", "400"}, {"elytra", "600"}, {"nether_star", "800"}, {"beacon", "700"},
				{"heart_of_the_sea", "400"}, {"trident", "300"}, {"dragon_egg", "5000"}, {"dragon_head", "300"},
				{"iron_sword", "30"}, {"iron_pickaxe", "40"}, {"iron_axe", "40"}, {"iron_shovel", "15"},
				{"iron_hoe", "25"}, {"iron_helmet", "50"}, {"iron_chestplate", "80"}, {"iron_leggings", "70"},
				{"iron_boots", "40"}, {"diamond_sword", "220"}, {"diamond_pickaxe", "320"}, {"diamond_axe", "320"},
				{"diamond_shovel", "120"}, {"diamond_hoe", "220"}, {"diamond_helmet", "520"},
				{"diamond_chestplate", "820"}, {"diamond_leggings", "720"}, {"diamond_boots", "420"},
				{"netherite_sword", "720"}, {"netherite_pickaxe", "820"}, {"netherite_axe", "820"},
				{"netherite_shovel", "620"}, {"netherite_hoe", "720"}, {"netherite_helmet", "1020"},
				{"netherite_chestplate", "1320"}, {"netherite_leggings", "1220"}, {"netherite_boots", "920"}
		};
		for (String[] e : entries) {
			map.put("minecraft:" + e[0], Double.parseDouble(e[1]));
		}
		return map;
	}

	private static List<String> defaultBlacklist() {
		return new ArrayList<>(List.of(
				"dynamicchests:gambler_chest",
				"minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
				"minecraft:command_block_minecart", "minecraft:structure_block", "minecraft:structure_void",
				"minecraft:jigsaw", "minecraft:test_block", "minecraft:test_instance_block",
				"minecraft:barrier", "minecraft:light", "minecraft:debug_stick", "minecraft:knowledge_book",
				"minecraft:bedrock", "minecraft:end_portal_frame", "minecraft:reinforced_deepslate",
				"minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault", "minecraft:petrified_oak_slab",
				"minecraft:player_head", "minecraft:written_book", "minecraft:filled_map"
		));
	}
}
