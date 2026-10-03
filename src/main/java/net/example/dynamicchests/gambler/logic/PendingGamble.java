package net.example.dynamicchests.gambler.logic;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A gamble that has started but not yet paid out. It is written to the chest's save data, so a
 * crash, restart or chunk unload mid-animation simply resumes (or completes) the payout - the
 * bet is never just "gone". The outcome is decided before the animation begins.
 */
public final class PendingGamble {

	public final ItemStack bet;
	public final GambleOutcome outcome;
	public final ValueTier tier;
	public final double betValue;
	public final List<ItemStack> payout;
	public final int totalTicks;
	public final UUID player;
	public int elapsedTicks;

	public PendingGamble(ItemStack bet, GambleOutcome outcome, ValueTier tier, double betValue,
			List<ItemStack> payout, int totalTicks, int elapsedTicks, UUID player) {
		this.bet = bet;
		this.outcome = outcome;
		this.tier = tier;
		this.betValue = betValue;
		this.payout = payout;
		this.totalTicks = totalTicks;
		this.elapsedTicks = elapsedTicks;
		this.player = player;
	}

	public void save(ValueOutput output) {
		output.store("bet", ItemStack.CODEC, this.bet);
		output.putInt("outcome", this.outcome.ordinal());
		output.putInt("tier", this.tier.ordinal());
		output.putDouble("bet_value", this.betValue);
		output.putInt("total", this.totalTicks);
		output.putInt("elapsed", this.elapsedTicks);
		output.putString("player", this.player.toString());
		ValueOutput.TypedOutputList<ItemStack> list = output.list("payout", ItemStack.CODEC);
		for (ItemStack stack : this.payout) {
			list.add(stack);
		}
	}

	/** Returns empty only if not even the bet item can be read. */
	public static Optional<PendingGamble> load(ValueInput input) {
		Optional<ItemStack> bet = input.read("bet", ItemStack.CODEC);
		GambleOutcome outcome = GambleOutcome.byOrdinal(input.getIntOr("outcome", -1));
		ValueTier tier = ValueTier.byOrdinal(input.getIntOr("tier", 0));
		Optional<String> player = input.getString("player");
		if (bet.isEmpty()) {
			return Optional.empty();
		}
		UUID uuid = null;
		if (player.isPresent()) {
			try {
				uuid = UUID.fromString(player.get());
			} catch (IllegalArgumentException ignored) {
				// handled below
			}
		}
		if (outcome == null || tier == null || uuid == null) {
			// Damaged record: safest thing is to give the bet back untouched.
			return Optional.of(new PendingGamble(bet.get(), GambleOutcome.RETURN, ValueTier.COMMON, 0,
					List.of(bet.get().copy()), 1, 1, uuid == null ? new UUID(0, 0) : uuid));
		}
		List<ItemStack> payout = new ArrayList<>();
		for (ItemStack stack : input.listOrEmpty("payout", ItemStack.CODEC)) {
			payout.add(stack);
		}
		return Optional.of(new PendingGamble(bet.get(), outcome, tier, input.getDoubleOr("bet_value", 0),
				payout, Math.max(1, input.getIntOr("total", 1)), input.getIntOr("elapsed", 0), uuid));
	}
}
