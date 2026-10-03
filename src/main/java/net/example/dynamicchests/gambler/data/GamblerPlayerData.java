package net.example.dynamicchests.gambler.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.example.dynamicchests.gambler.config.GamblerConfig;
import net.example.dynamicchests.gambler.logic.GambleOutcome;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent per-player gambling statistics: totals for the advancements, the win/loss streak,
 * the hidden luck value and the recent-results history shown in the GUI.
 *
 * All streak maths lives here so the chest itself never has to know how luck is earned.
 */
public class GamblerPlayerData extends SavedData {

	public static final int HISTORY_SIZE = 5;

	/** Mutable statistics of one player. */
	public static final class Stats {
		public int gambles;
		public int wins;
		public int losses;
		public int jackpots;
		public int winStreak;
		public int lossStreak;
		/** Hidden luck in percentage points, built by winning streaks. */
		public double luck;
		/** Sum / count of the item values that built the current streak (anti-exploit scaling). */
		public double streakValueSum;
		public int streakCount;
		/** Newest first, outcome ordinals. */
		public List<Integer> history = new ArrayList<>();

		public Stats() {
		}

		Stats(int gambles, int wins, int losses, int jackpots, int winStreak, int lossStreak,
				double luck, double streakValueSum, int streakCount, List<Integer> history) {
			this.gambles = gambles;
			this.wins = wins;
			this.losses = losses;
			this.jackpots = jackpots;
			this.winStreak = winStreak;
			this.lossStreak = lossStreak;
			this.luck = luck;
			this.streakValueSum = streakValueSum;
			this.streakCount = streakCount;
			this.history = new ArrayList<>(history);
		}

		/** Positive = consecutive wins, negative = consecutive losses, 0 = none. */
		public int signedStreak() {
			return this.winStreak > 0 ? this.winStreak : -this.lossStreak;
		}

		private void resetStreak() {
			this.winStreak = 0;
			this.lossStreak = 0;
			this.luck = 0;
			this.streakValueSum = 0;
			this.streakCount = 0;
		}

		/**
		 * Streak bonuses only apply fully to items no more valuable than the ones that earned
		 * them: luck built on cobblestone is nearly worthless on a diamond.
		 */
		private double valueScale(double itemValue) {
			if (this.streakCount <= 0 || itemValue <= 0) {
				return 1.0;
			}
			double average = this.streakValueSum / this.streakCount;
			return Math.max(0.0, Math.min(1.0, average / itemValue));
		}

		public double effectiveLuck(double itemValue) {
			if (!GamblerConfig.get().streak.enabled) {
				return 0;
			}
			return this.luck * valueScale(itemValue);
		}

		public double effectiveRecovery(double itemValue) {
			GamblerConfig.Streak cfg = GamblerConfig.get().streak;
			if (!cfg.enabled || this.lossStreak < cfg.lossStreakThreshold) {
				return 0;
			}
			double points = (this.lossStreak - cfg.lossStreakThreshold + 1) * cfg.recoveryPerLoss;
			return Math.min(cfg.maxRecovery, points) * valueScale(itemValue);
		}
	}

	private static final Codec<Stats> STATS_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("gambles", 0).forGetter(s -> s.gambles),
			Codec.INT.optionalFieldOf("wins", 0).forGetter(s -> s.wins),
			Codec.INT.optionalFieldOf("losses", 0).forGetter(s -> s.losses),
			Codec.INT.optionalFieldOf("jackpots", 0).forGetter(s -> s.jackpots),
			Codec.INT.optionalFieldOf("win_streak", 0).forGetter(s -> s.winStreak),
			Codec.INT.optionalFieldOf("loss_streak", 0).forGetter(s -> s.lossStreak),
			Codec.DOUBLE.optionalFieldOf("luck", 0.0).forGetter(s -> s.luck),
			Codec.DOUBLE.optionalFieldOf("streak_value_sum", 0.0).forGetter(s -> s.streakValueSum),
			Codec.INT.optionalFieldOf("streak_count", 0).forGetter(s -> s.streakCount),
			Codec.INT.listOf().optionalFieldOf("history", List.of()).forGetter(s -> s.history)
	).apply(i, Stats::new));

	public static final Codec<GamblerPlayerData> CODEC = Codec.unboundedMap(Codec.STRING, STATS_CODEC)
			.xmap(GamblerPlayerData::new, data -> data.players);

	public static final SavedDataType<GamblerPlayerData> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath("dynamicchests", "gambler_players"),
			GamblerPlayerData::new,
			CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);

	private final Map<String, Stats> players;

	public GamblerPlayerData() {
		this.players = new HashMap<>();
	}

	GamblerPlayerData(Map<String, Stats> loaded) {
		this.players = new HashMap<>(loaded);
	}

	public static GamblerPlayerData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public Stats stats(UUID player) {
		return this.players.computeIfAbsent(player.toString(), k -> new Stats());
	}

	/**
	 * Applies a finished gamble to the player's record and returns the updated stats.
	 *
	 * @param itemValue configured value of the gambled item
	 */
	public Stats record(UUID player, GambleOutcome outcome, double itemValue) {
		Stats stats = stats(player);
		GamblerConfig.Streak cfg = GamblerConfig.get().streak;

		stats.gambles++;
		if (outcome.isWin()) stats.wins++;
		if (outcome == GambleOutcome.LOSS) stats.losses++;
		if (outcome == GambleOutcome.JACKPOT) stats.jackpots++;

		stats.history.add(0, outcome.ordinal());
		while (stats.history.size() > HISTORY_SIZE) {
			stats.history.remove(stats.history.size() - 1);
		}

		if (outcome == GambleOutcome.JACKPOT) {
			// A jackpot always resets, regardless of how cheap the item was.
			stats.resetStreak();
		} else if (itemValue >= cfg.minItemValue) {
			if (outcome == GambleOutcome.LOSS) {
				if (stats.winStreak > 0) stats.resetStreak();
				stats.winStreak = 0;
				stats.luck = 0;
				stats.lossStreak++;
				stats.streakValueSum += itemValue;
				stats.streakCount++;
			} else if (outcome.isWin()) {
				if (stats.lossStreak > 0) stats.resetStreak();
				stats.lossStreak = 0;
				stats.winStreak++;
				stats.luck = Math.min(cfg.maxLuck, stats.winStreak * cfg.luckPerWin);
				stats.streakValueSum += itemValue;
				stats.streakCount++;
			}
			// RETURN and MYSTERY leave the streak untouched.
		}
		setDirty();
		return stats;
	}
}
