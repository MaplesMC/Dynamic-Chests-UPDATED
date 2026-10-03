package net.example.dynamicchests.gambler.logic;

/**
 * Every possible result of a gamble. The ordinal is used on the wire (menu data sync) and in
 * saved history, so only ever append new values at the end.
 */
public enum GambleOutcome {
	/** 4x the bet, plus the tier's bonus loot. */
	JACKPOT("jackpot", true),
	/** 3x the bet. */
	TRIPLE("triple", true),
	/** 2x the bet. */
	DOUBLE("double", true),
	UPGRADE("upgrade", true),
	RETURN("return", false),
	LOSS("loss", false);

	public static final GambleOutcome[] VALUES = values();

	private final String key;
	private final boolean win;

	GambleOutcome(String key, boolean win) {
		this.key = key;
		this.win = win;
	}

	/** Config / translation key fragment. */
	public String key() {
		return this.key;
	}

	/** Counts towards the win streak and the "High Roller" advancement. */
	public boolean isWin() {
		return this.win;
	}

	public static GambleOutcome byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
	}
}
