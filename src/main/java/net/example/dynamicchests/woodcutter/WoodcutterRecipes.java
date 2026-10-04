package net.example.dynamicchests.woodcutter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Wood Cutter's recipes: pick one output for one kind of input, Stonecutter style.
 *
 * <p>The list is generated from {@link WoodSet}s and the vanilla item registry, which both the client and the
 * server have, so the screen and the menu always agree without any recipe syncing. Each recipe says how many
 * input items it needs ({@link Entry#inputCount}) and what it gives; the ratios follow the vanilla crafting
 * recipes but without needing sticks, chests or other side ingredients.
 */
public final class WoodcutterRecipes {

	private WoodcutterRecipes() {
	}

	/** One selectable recipe: {@code inputCount} of {@code input} turn into {@code output}. */
	public record Entry(Item input, int inputCount, ItemStack output) {
		/** A fresh copy of the result, safe to hand to a player. */
		public ItemStack result() {
			return this.output.copy();
		}
	}

	private static final List<WoodSet> WOOD_SETS = new CopyOnWriteArrayList<>(List.of(
			WoodSet.logs("oak"),
			WoodSet.logs("spruce"),
			WoodSet.logs("birch"),
			WoodSet.logs("jungle"),
			WoodSet.logs("acacia"),
			WoodSet.logs("dark_oak"),
			WoodSet.logs("mangrove"),
			WoodSet.logs("cherry"),
			WoodSet.logs("pale_oak"),
			WoodSet.stems("crimson"),
			WoodSet.stems("warped"),
			WoodSet.bamboo()
	));

	private static volatile Map<Item, List<Entry>> byInput;

	/** Adds another wood family (from another mod, say). Safe to call before the first recipe lookup or later. */
	public static void addWoodSet(WoodSet set) {
		WOOD_SETS.add(set);
		byInput = null;
	}

	/** All recipes for an input stack, in display order. Empty if the stack is empty or has no recipes. */
	public static List<Entry> forInput(ItemStack stack) {
		if (stack.isEmpty()) {
			return List.of();
		}
		return recipes().getOrDefault(stack.getItem(), List.of());
	}

	public static boolean hasRecipes(Item item) {
		return recipes().containsKey(item);
	}

	private static Map<Item, List<Entry>> recipes() {
		Map<Item, List<Entry>> map = byInput;
		if (map == null) {
			map = build();
			byInput = map;
		}
		return map;
	}

	private static Map<Item, List<Entry>> build() {
		Map<Item, List<Entry>> map = new HashMap<>();
		for (WoodSet set : WOOD_SETS) {
			addWoodSet(map, set);
		}
		return map;
	}

	private static void addWoodSet(Map<Item, List<Entry>> map, WoodSet set) {
		String name = set.name();

		// Logs and wood blocks: strip them, or cut them into planks.
		strip(map, set.log(), set.strippedLog());
		strip(map, set.wood(), set.strippedWood());
		for (String trunk : new String[] {set.log(), set.wood(), set.strippedLog(), set.strippedWood()}) {
			add(map, trunk, 1, set.planks(), set.planksPerLog());
		}
		// Stripped logs are what hanging signs are made from.
		add(map, set.strippedLog(), 1, name + "_hanging_sign", 1);

		// Planks: everything a carpenter makes out of them.
		String planks = set.planks();
		add(map, planks, 1, name + "_slab", 2);
		add(map, planks, 1, name + "_stairs", 1);
		add(map, planks, 1, name + "_fence", 1);
		add(map, planks, 2, name + "_fence_gate", 1);
		add(map, planks, 2, name + "_door", 1);
		add(map, planks, 3, name + "_trapdoor", 1);
		add(map, planks, 1, name + "_button", 1);
		add(map, planks, 2, name + "_pressure_plate", 1);
		add(map, planks, 2, name + "_sign", 1);
		add(map, planks, 5, set.boat(), 1);
		add(map, planks, 8, set.chestBoat(), 1);
		add(map, planks, 4, "chest", 1);
		add(map, planks, 1, "stick", 2);
	}

	private static void strip(Map<Item, List<Entry>> map, String from, String to) {
		if (from != null && to != null) {
			add(map, from, 1, to, 1);
		}
	}

	/** Registers a recipe if both items exist; missing items (a wood from a newer or older version) are skipped. */
	private static void add(Map<Item, List<Entry>> map, String inputName, int inputCount, String outputName, int outputCount) {
		if (inputName == null || outputName == null) {
			return;
		}
		Optional<Item> input = item(inputName);
		Optional<Item> output = item(outputName);
		if (input.isEmpty() || output.isEmpty()) {
			return;
		}
		map.computeIfAbsent(input.get(), key -> new ArrayList<>())
				.add(new Entry(input.get(), inputCount, new ItemStack(output.get(), outputCount)));
	}

	private static Optional<Item> item(String path) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(path));
	}
}
