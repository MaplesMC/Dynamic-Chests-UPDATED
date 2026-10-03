package net.example.dynamicchests.gambler.logic;

/** Risk tier of an item, derived from its configured value. */
public enum ValueTier {
	COMMON("common"),
	RARE("rare"),
	VERY_RARE("very_rare");

	public static final ValueTier[] VALUES = values();

	private final String key;

	ValueTier(String key) {
		this.key = key;
	}

	public String key() {
		return this.key;
	}

	public static ValueTier byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
	}
}
