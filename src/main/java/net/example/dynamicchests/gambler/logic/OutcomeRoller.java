package net.example.dynamicchests.gambler.logic;

import net.example.dynamicchests.gambler.config.GamblerConfig;
import net.minecraft.util.RandomSource;

/**
 * Turns the configured base chances into the odds for one specific gamble (item tier, streak
 * luck, loss-streak recovery) and rolls an outcome from them. Always runs on the server.
 */
public final class OutcomeRoller {

	private OutcomeRoller() {
	}

	/**
	 * @param tier     risk tier of the gambled item
	 * @param luck     percentage points of luck already scaled for this item (0 if none)
	 * @param recovery percentage points of loss-streak recovery already scaled for this item
	 * @return chances in percent per outcome ordinal, summing to 100
	 */
	public static double[] odds(ValueTier tier, double luck, double recovery) {
		GamblerConfig config = GamblerConfig.get();
		double[] odds = config.baseChances();

		int triple = GambleOutcome.TRIPLE.ordinal();
		int dbl = GambleOutcome.DOUBLE.ordinal();
		int ret = GambleOutcome.RETURN.ordinal();
		int loss = GambleOutcome.LOSS.ordinal();

		// Tier risk: riskier items lose more often, cheap ones are forgiven a little.
		double extraLoss = config.tier(tier).extraLossChance;
		if (config.isEnabled(GambleOutcome.LOSS)) {
			if (extraLoss > 0) {
				// Take the extra loss proportionally from Return and Double.
				double pool = odds[ret] + odds[dbl];
				double moved = Math.min(extraLoss, pool);
				if (pool > 0) {
					double fromReturn = moved * odds[ret] / pool;
					odds[dbl] -= moved - fromReturn;
					odds[ret] -= fromReturn;
				}
				odds[loss] += moved;
			} else if (extraLoss < 0) {
				double moved = Math.min(-extraLoss, odds[loss]);
				odds[loss] -= moved;
				odds[ret] += moved;
			}
		}

		if (luck > 0 && config.isEnabled(GambleOutcome.LOSS)) {
			double moved = Math.min(luck, odds[loss]);
			odds[loss] -= moved;
			odds[dbl] += moved * 0.7;
			odds[triple] += moved * 0.3;
		}
		if (recovery > 0 && config.isEnabled(GambleOutcome.LOSS)) {
			double moved = Math.min(recovery, odds[loss]);
			odds[loss] -= moved;
			odds[ret] += moved;
		}

		for (GambleOutcome outcome : GambleOutcome.VALUES) {
			if (!config.isEnabled(outcome)) {
				odds[outcome.ordinal()] = 0;
			}
		}
		return normalise(odds);
	}

	public static GambleOutcome roll(double[] odds, RandomSource random) {
		double roll = random.nextDouble() * 100.0;
		double running = 0;
		GambleOutcome last = GambleOutcome.RETURN;
		for (GambleOutcome outcome : GambleOutcome.VALUES) {
			double chance = odds[outcome.ordinal()];
			if (chance <= 0) {
				continue;
			}
			last = outcome;
			running += chance;
			if (roll < running) {
				return outcome;
			}
		}
		return last;
	}

	private static double[] normalise(double[] odds) {
		double sum = 0;
		for (int i = 0; i < odds.length; i++) {
			odds[i] = Math.max(0, odds[i]);
			sum += odds[i];
		}
		if (sum <= 0) {
			odds[GambleOutcome.RETURN.ordinal()] = 100;
			return odds;
		}
		for (int i = 0; i < odds.length; i++) {
			odds[i] = odds[i] * 100.0 / sum;
		}
		return odds;
	}
}
