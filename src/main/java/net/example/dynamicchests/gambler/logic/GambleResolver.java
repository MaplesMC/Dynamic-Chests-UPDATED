package net.example.dynamicchests.gambler.logic;

import net.example.dynamicchests.gambler.config.GamblerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Converts a rolled outcome into the concrete items paid out. The whole bet (one item or a full
 * stack) shares one result. The result is fully decided here, at the moment the gamble starts, and
 * is then persisted by the chest; the animation that follows is cosmetic only.
 */
public final class GambleResolver {

	private GambleResolver() {
	}

	/** Number of output slots. A payout larger than this spills out of the chest as dropped items. */
	public static final int MAX_PAYOUT_STACKS = 5;

	/**
	 * @param outcome the outcome that actually applies (an Upgrade may have become a Double)
	 * @param payout  items to hand over once the animation ends (empty on a loss)
	 */
	public record Resolution(GambleOutcome outcome, List<ItemStack> payout) {
	}

	/** @param bet everything being gambled; its count is the number of items at stake */
	public static Resolution resolve(ServerLevel level, BlockPos pos, ItemStack bet, GambleOutcome rolled,
			ValueTier tier, RandomSource random) {
		GamblerConfig config = GamblerConfig.get();
		GambleOutcome outcome = rolled;
		int count = bet.getCount();
		ItemStack unit = bet.copyWithCount(1);

		if (outcome == GambleOutcome.UPGRADE) {
			Optional<ItemStack> upgraded = UpgradeTable.upgrade(unit);
			if (upgraded.isPresent()) {
				List<ItemStack> payout = new ArrayList<>();
				addCopies(payout, upgraded.get(), count);
				return new Resolution(outcome, payout);
			}
			outcome = upgradeFallback(config);
		}

		List<ItemStack> payout = new ArrayList<>();
		switch (outcome) {
			case LOSS -> {
			}
			case RETURN -> addCopies(payout, unit, count);
			case DOUBLE -> addMultiplied(payout, unit, count, 2);
			case TRIPLE -> addMultiplied(payout, unit, count, 3);
			case JACKPOT -> {
				addMultiplied(payout, unit, count, config.gamble.jackpotMultiplier);
				addLoot(level, pos, config.tier(tier).jackpotBonusLootTable, payout, random);
			}
			default -> addCopies(payout, unit, count);
		}
		return new Resolution(outcome, payout);
	}

	private static GambleOutcome upgradeFallback(GamblerConfig config) {
		GambleOutcome fallback = "return".equalsIgnoreCase(config.gamble.upgradeFallback)
				? GambleOutcome.RETURN : GambleOutcome.DOUBLE;
		return config.isEnabled(fallback) ? fallback : GambleOutcome.RETURN;
	}

	/**
	 * Adds {@code count * multiplier} items. Items worth more than {@code max_reward_value} each are
	 * never multiplied (they come back 1:1), which stops the most valuable items from being
	 * duplicated while every ordinary stack multiplies in full.
	 */
	private static void addMultiplied(List<ItemStack> payout, ItemStack unit, int count, int multiplier) {
		boolean tooValuable = ItemRules.valueOf(unit) > GamblerConfig.get().gamble.maxRewardValue;
		long total = tooValuable ? count : (long) count * multiplier;
		addCopies(payout, unit, (int) Math.min(total, 64L * 64L));
	}

	private static void addCopies(List<ItemStack> payout, ItemStack unit, int count) {
		int perStack = Math.max(1, unit.getMaxStackSize());
		int remaining = count;
		while (remaining > 0) {
			int now = Math.min(perStack, remaining);
			payout.add(unit.copyWithCount(now));
			remaining -= now;
		}
	}

	private static void addLoot(ServerLevel level, BlockPos pos, String tableId, List<ItemStack> payout, RandomSource random) {
		Identifier id = tableId == null || tableId.isBlank() ? null : Identifier.tryParse(tableId);
		if (id == null) {
			return;
		}
		LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
				.create(LootContextParamSets.CHEST);
		double cap = GamblerConfig.get().gamble.maxRewardValue;
		for (ItemStack stack : table.getRandomItems(params, random)) {
			if (stack.isEmpty() || !ItemRules.isAllowed(stack) || ItemRules.valueOf(stack) > cap) {
				continue;
			}
			addCopies(payout, stack.copyWithCount(1), stack.getCount());
		}
	}
}
