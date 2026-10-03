package net.example.dynamicchests.gambler.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The Gambler's Chest talks to the client through vanilla menu plumbing only: the Gamble button
 * is a menu-button click and everything displayed is synced via menu data. The client therefore
 * never sends an outcome or an item, only "please try to gamble", which the server validates.
 *
 * This class owns the button ids and throttles duplicate or spammed requests.
 */
public final class GamblerNetworking {

	private GamblerNetworking() {
	}

	/** Gamble a single item from the bet stack. */
	public static final int BUTTON_GAMBLE = 0;
	/** Gamble the whole bet stack as one bet. */
	public static final int BUTTON_BET_ALL = 1;

	/** Minimum ticks between two accepted button clicks from one player. */
	private static final int MIN_REQUEST_GAP_TICKS = 4;

	private static final Map<UUID, Long> LAST_REQUEST = new WeakHashMap<>();

	/** Records a click and returns false if it came too soon after the previous one. */
	public static synchronized boolean acceptRequest(ServerPlayer player, long gameTime) {
		UUID id = player.getUUID();
		Long last = LAST_REQUEST.get(id);
		if (last != null && gameTime - last < MIN_REQUEST_GAP_TICKS && gameTime >= last) {
			return false;
		}
		LAST_REQUEST.put(id, gameTime);
		return true;
	}
}
