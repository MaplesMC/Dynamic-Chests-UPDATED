package net.example.dynamicchests.gambler.logic;

import net.example.dynamicchests.gambler.config.GamblerConfig;
import net.example.dynamicchests.registry.ModRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Decides whether an item may be gambled at all (blacklist, containers) and how valuable it
 * is (config value table, falling back to the item's vanilla rarity).
 */
public final class ItemRules {

	private ItemRules() {
	}

	/** Why an input item was refused. Ordinals are sent to the client, so only append. */
	public enum Rejection {
		NONE,
		BLACKLISTED,
		CONTAINER
	}

	public static String idOf(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	public static Rejection check(ItemStack stack) {
		if (stack.isEmpty()) {
			return Rejection.BLACKLISTED;
		}
		if (stack.is(ModRegistry.RNG_OVERRIDE_CHIP)) {
			return Rejection.BLACKLISTED;
		}
		GamblerConfig.Restrictions rules = GamblerConfig.get().restrictions;
		String id = idOf(stack.getItem());
		if (rules.blacklist.contains(id)) {
			return Rejection.BLACKLISTED;
		}
		for (String suffix : rules.blacklistSuffixes) {
			if (id.endsWith(suffix)) {
				return Rejection.BLACKLISTED;
			}
		}
		for (String tag : rules.blacklistTags) {
			Identifier tagId = Identifier.tryParse(tag);
			if (tagId != null && stack.is(TagKey.create(Registries.ITEM, tagId))) {
				return Rejection.BLACKLISTED;
			}
		}
		if (!rules.allowContainers && carriesInventory(stack)) {
			return Rejection.CONTAINER;
		}
		return Rejection.NONE;
	}

	public static boolean isAllowed(ItemStack stack) {
		return check(stack) == Rejection.NONE;
	}

	/** True for anything that can hide other items: shulker/bundle contents, chests with data, unopened loot. */
	private static boolean carriesInventory(ItemStack stack) {
		return stack.has(DataComponents.CONTAINER)
				|| stack.has(DataComponents.BUNDLE_CONTENTS)
				|| stack.has(DataComponents.CONTAINER_LOOT)
				|| stack.has(DataComponents.BLOCK_ENTITY_DATA);
	}

	public static double valueOf(ItemStack stack) {
		GamblerConfig.Values values = GamblerConfig.get().values;
		Double explicit = values.itemValues.get(idOf(stack.getItem()));
		if (explicit != null) {
			return Math.max(0, explicit);
		}
		String rarity = stack.getRarity().name().toLowerCase(Locale.ROOT);
		return Math.max(0, values.rarityValues.getOrDefault(rarity, 5.0));
	}

	public static ValueTier tierOf(double value) {
		GamblerConfig config = GamblerConfig.get();
		if (value >= config.tier(ValueTier.VERY_RARE).minValue) {
			return ValueTier.VERY_RARE;
		}
		if (value >= config.tier(ValueTier.RARE).minValue) {
			return ValueTier.RARE;
		}
		return ValueTier.COMMON;
	}
}
