package net.example.dynamicchests.woodcutter;

/**
 * One family of wood the Wood Cutter knows how to process. Adding a new wood type to the Wood Cutter is one
 * line: create a {@code WoodSet} and hand it to {@link WoodcutterRecipes#addWoodSet}. Item ids are derived
 * from the {@code name} with vanilla's naming scheme; anything that does not exist is simply skipped.
 *
 * @param name       prefix of the wood's item ids, such as "oak" or "dark_oak"
 * @param trunk      how the trunk blocks are named (logs, stems or bamboo)
 * @param planksPerLog planks produced from one log or wood block
 */
public record WoodSet(String name, Trunk trunk, int planksPerLog) {

	/** Naming scheme of the trunk blocks. */
	public enum Trunk {
		/** oak_log / oak_wood, stripped_oak_log / stripped_oak_wood; has boats. */
		LOG,
		/** crimson_stem / crimson_hyphae; the nether woods, no boats. */
		STEM,
		/** bamboo_block only; has rafts instead of boats. */
		BAMBOO
	}

	/** A normal overworld wood: logs, wood blocks, boats. */
	public static WoodSet logs(String name) {
		return new WoodSet(name, Trunk.LOG, 4);
	}

	/** A nether wood: stems and hyphae, no boats. */
	public static WoodSet stems(String name) {
		return new WoodSet(name, Trunk.STEM, 4);
	}

	/** Bamboo: blocks of bamboo (two planks each) and rafts. */
	public static WoodSet bamboo() {
		return new WoodSet("bamboo", Trunk.BAMBOO, 2);
	}

	public String log() {
		return switch (this.trunk) {
			case LOG -> this.name + "_log";
			case STEM -> this.name + "_stem";
			case BAMBOO -> "bamboo_block";
		};
	}

	/** The bark-all-around block (oak_wood, crimson_hyphae), or null if this wood has none. */
	public String wood() {
		return switch (this.trunk) {
			case LOG -> this.name + "_wood";
			case STEM -> this.name + "_hyphae";
			case BAMBOO -> null;
		};
	}

	public String strippedLog() {
		return "stripped_" + log();
	}

	public String strippedWood() {
		return wood() == null ? null : "stripped_" + wood();
	}

	public String planks() {
		return this.name + "_planks";
	}

	/** Id of the boat (or raft) item, or null for woods without one. */
	public String boat() {
		return switch (this.trunk) {
			case LOG -> this.name + "_boat";
			case BAMBOO -> this.name + "_raft";
			case STEM -> null;
		};
	}

	public String chestBoat() {
		return switch (this.trunk) {
			case LOG -> this.name + "_chest_boat";
			case BAMBOO -> this.name + "_chest_raft";
			case STEM -> null;
		};
	}
}
