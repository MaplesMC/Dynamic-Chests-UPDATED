package net.example.dynamicchests.gambler.logic;

import net.example.dynamicchests.gambler.config.GamblerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

/**
 * Finds the "next tier" of an item. Explicit config pairs win; otherwise tool/armor material
 * prefixes (wooden to stone to iron to diamond) and a short raw-material chain are tried.
 * Enchantments, names and damage carry over to the upgraded item.
 */
public final class UpgradeTable {

	private UpgradeTable() {
	}

	private static final Map<String, String> MATERIAL_PREFIXES = Map.of(
			"wooden_", "stone_",
			"stone_", "iron_",
			"leather_", "chainmail_",
			"chainmail_", "iron_",
			"golden_", "iron_",
			"iron_", "diamond_"
	);

	private static final Map<String, String> MATERIALS = Map.of(
			"coal", "copper_ingot",
			"copper_ingot", "iron_ingot",
			"iron_ingot", "gold_ingot",
			"gold_ingot", "emerald",
			"emerald", "diamond"
	);

	/** The upgraded copy (count 1), or empty if there is no valid upgrade. */
	public static Optional<ItemStack> upgrade(ItemStack input) {
		Identifier id = BuiltInRegistries.ITEM.getKey(input.getItem());

		String explicit = GamblerConfig.get().upgrades.get(id.toString());
		if (explicit != null) {
			return build(input, Identifier.tryParse(explicit));
		}

		String path = id.getPath();
		for (Map.Entry<String, String> prefix : MATERIAL_PREFIXES.entrySet()) {
			if (path.startsWith(prefix.getKey())) {
				String target = prefix.getValue() + path.substring(prefix.getKey().length());
				Optional<ItemStack> result = build(input, Identifier.fromNamespaceAndPath(id.getNamespace(), target));
				if (result.isPresent()) {
					return result;
				}
			}
		}
		String material = MATERIALS.get(path);
		if (material != null) {
			return build(input, Identifier.fromNamespaceAndPath(id.getNamespace(), material));
		}
		return Optional.empty();
	}

	private static Optional<ItemStack> build(ItemStack input, Identifier target) {
		if (target == null) {
			return Optional.empty();
		}
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(target);
		if (item.isEmpty() || item.get() == input.getItem()) {
			return Optional.empty();
		}
		ItemStack upgraded = input.transmuteCopy(item.get(), 1);
		if (upgraded.isEmpty() || !ItemRules.isAllowed(upgraded)) {
			return Optional.empty();
		}
		// Never let an upgrade smuggle an item past the value cap.
		if (ItemRules.valueOf(upgraded) > GamblerConfig.get().gamble.maxRewardValue) {
			return Optional.empty();
		}
		return Optional.of(upgraded);
	}
}
