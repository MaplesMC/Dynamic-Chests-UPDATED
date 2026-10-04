package net.example.dynamicchests.gambler.effects;

import net.example.dynamicchests.DynamicChests;
import net.example.dynamicchests.gambler.data.GamblerPlayerData;
import net.example.dynamicchests.gambler.logic.GambleOutcome;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Awards the Gambler's Chest advancements. The advancement JSONs use an impossible criterion
 * named {@value #CRITERION}; progress is tracked in {@link GamblerPlayerData} and granted from here.
 */
public final class GamblerAdvancements {

	private GamblerAdvancements() {
	}

	private static final String CRITERION = "gambled";

	public static final int HIGH_ROLLER_WINS = 10;
	public static final int DEGENERATE_LOSSES = 100;

	public static void onGambleFinished(ServerPlayer player, GambleOutcome outcome, GamblerPlayerData.Stats stats, ItemStack bet) {
		award(player, "gambler/feeling_lucky");
		if (stats.wins >= HIGH_ROLLER_WINS) {
			award(player, "gambler/high_roller");
		}
		if (outcome == GambleOutcome.JACKPOT) {
			award(player, "gambler/jackpot");
		}
		if (outcome == GambleOutcome.JACKPOT && bet.is(Items.JACK_O_LANTERN)) {
			award(player, "gambler/jack_o_jackpot");
		}
		if (stats.losses >= DEGENERATE_LOSSES) {
			award(player, "gambler/degenerate");
		}
	}

	private static void award(ServerPlayer player, String path) {
		AdvancementHolder holder = player.level().getServer().getAdvancements()
				.get(Identifier.fromNamespaceAndPath(DynamicChests.MOD_ID, path));
		if (holder != null) {
			player.getAdvancements().award(holder, CRITERION);
		}
	}
}
