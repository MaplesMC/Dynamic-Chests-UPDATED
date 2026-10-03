package net.example.dynamicchests.block;

import net.minecraft.util.StringRepresentable;

/**
 * Mirrors vanilla's {@code ChestType} (single / left / right) so our custom chests can
 * use the same blockstate-driven connection and rendering approach as vanilla double chests.
 */
public enum VaultChestType implements StringRepresentable {
	SINGLE("single"),
	LEFT("left"),
	RIGHT("right");

	private final String name;

	VaultChestType(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	@Override
	public String toString() {
		return this.name;
	}
}
