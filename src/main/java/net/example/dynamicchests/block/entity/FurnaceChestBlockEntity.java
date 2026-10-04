package net.example.dynamicchests.block.entity;

import net.example.dynamicchests.registry.ModRegistry;
import net.example.dynamicchests.screen.FurnaceChestMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Block entity of the Furnace Chest: six bulk furnaces that burn from a shared fuel store and deliver
 * straight into a single output chest.
 *
 * <p>Slots of the single container (45): six inputs (0-5), the 12-slot fuel store (6-17) and the 27-slot
 * output chest (18-44). Finished items go directly into the output chest; if it has no room the batch
 * simply waits, nothing is dropped or lost.
 *
 * <p>Each furnace smelts one item at a time ({@link #BATCH_LIMIT}), {@link #COOK_SPEED} times faster than a
 * vanilla furnace. Fuel comes from the shared fuel store and is paid at the vanilla rate per item (a coal
 * still smelts 8 items). Recipes and fuel values are the vanilla ones, and the server owns everything.
 *
 * <p>The optional auto-sort keeps the items in the input slots spread evenly, so six furnaces share one
 * big stack.
 */
public class FurnaceChestBlockEntity extends AbstractVaultChestBlockEntity
		implements ExtendedMenuProvider<Integer>, WorldlyContainer {

	public static final int FURNACES = 6;
	public static final int INPUT_START = 0;
	public static final int FUEL_START = FURNACES;
	public static final int FUEL_SLOTS = 12;
	public static final int CHEST_START = FUEL_START + FUEL_SLOTS;
	public static final int CHEST_SLOTS = 27;
	public static final int CONTAINER_SIZE = CHEST_START + CHEST_SLOTS;

	/** Items a furnace smelts at once. 1 = one item at a time (raise it to smelt several as one batch). */
	public static final int BATCH_LIMIT = 1;

	/** How many times faster than a vanilla furnace each furnace cooks. Fuel is still paid at the vanilla rate per item. */
	public static final int COOK_SPEED = 2;

	/** Menu data layout: five ints per furnace, then the auto-sort flag and the number of busy furnaces. */
	public static final int DATA_STRIDE = 5;
	public static final int DATA_LIT = 0;
	public static final int DATA_LIT_TOTAL = 1;
	public static final int DATA_COOK = 2;
	public static final int DATA_COOK_TOTAL = 3;
	public static final int DATA_BATCH = 4;
	public static final int DATA_AUTO_SORT = FURNACES * DATA_STRIDE;
	public static final int DATA_ACTIVE = DATA_AUTO_SORT + 1;
	public static final int DATA_COUNT = DATA_ACTIVE + 1;

	/** Bumped when the slot layout changes. Version 3 had per-furnace output and fuel slots. */
	private static final int LAYOUT_VERSION = 4;

	/** Burn time left over from fuel already paid in (in ticks of smelting). */
	private final int[] fuelReserve = new int[FURNACES];
	/** Burn duration of the last fuel item, only for the flame display. */
	private final int[] fuelDuration = new int[FURNACES];
	private final int[] cookTime = new int[FURNACES];
	private final int[] cookTotal = new int[FURNACES];
	/** Items in the batch currently cooking (0 = idle). */
	private final int[] batch = new int[FURNACES];
	private boolean autoSort = true;
	/** Items from an older layout of this chest, returned to the world on the first tick. */
	private final List<ItemStack> legacyItems = new ArrayList<>();

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			if (index < DATA_AUTO_SORT) {
				int furnace = index / DATA_STRIDE;
				return switch (index % DATA_STRIDE) {
					case DATA_LIT -> fuelReserve[furnace];
					case DATA_LIT_TOTAL -> fuelDuration[furnace];
					case DATA_COOK -> cookTime[furnace];
					case DATA_COOK_TOTAL -> cookTotal[furnace];
					default -> batch[furnace];
				};
			}
			if (index == DATA_AUTO_SORT) {
				return autoSort ? 1 : 0;
			}
			int active = 0;
			for (int size : batch) {
				if (size > 0) {
					active++;
				}
			}
			return active;
		}

		@Override
		public void set(int index, int value) {
			// Server-owned: the menu never writes back.
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public FurnaceChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModRegistry.FURNACE_CHEST_BLOCK_ENTITY, pos, state, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE);
	}

	@Override
	public int getSingleContainerSize() {
		return CONTAINER_SIZE;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.dynamicchests.furnace_chest");
	}

	@Override
	public Integer getScreenOpeningData(ServerPlayer player) {
		return 0;
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
		return new FurnaceChestMenu(syncId, playerInventory, this, this.data);
	}

	public boolean isAutoSort() {
		return this.autoSort;
	}

	public void toggleAutoSort() {
		this.autoSort = !this.autoSort;
		setChanged();
	}

	// ------------------------------------------------------------------
	// Ticking
	// ------------------------------------------------------------------

	public void serverTick(ServerLevel level) {
		if (!this.legacyItems.isEmpty()) {
			for (ItemStack stack : this.legacyItems) {
				Block.popResource(level, this.worldPosition.above(), stack);
			}
			this.legacyItems.clear();
			setChanged();
		}
		boolean changed = false;
		// Sort first, every tick, so a stack is already shared out before any furnace claims it as a batch.
		if (this.autoSort) {
			changed |= spreadEvenly();
		}
		for (int furnace = 0; furnace < FURNACES; furnace++) {
			changed |= tickFurnace(level, furnace);
		}
		if (changed) {
			setChanged();
		}
	}

	/** One tick of one furnace: start a batch when idle, otherwise count it down and deliver it. */
	private boolean tickFurnace(ServerLevel level, int furnace) {
		int inputSlot = INPUT_START + furnace;

		if (this.batch[furnace] > 0) {
			// Items may be taken out of the slot mid-batch: the batch shrinks with them and their fuel is refunded.
			int present = getItem(inputSlot).getCount();
			if (present < this.batch[furnace]) {
				this.fuelReserve[furnace] += (this.batch[furnace] - present) * this.cookTotal[furnace] * COOK_SPEED;
				this.batch[furnace] = present;
				if (present == 0) {
					this.cookTime[furnace] = 0;
					return true;
				}
			}
			if (this.cookTime[furnace] < this.cookTotal[furnace]) {
				this.cookTime[furnace]++;
			}
			if (this.cookTime[furnace] >= this.cookTotal[furnace]) {
				deliverBatch(level, furnace); // waits (and retries next tick) if the output chest is full
			}
			return true;
		}

		ItemStack input = getItem(inputSlot);
		if (input.isEmpty()) {
			if (this.cookTime[furnace] != 0) {
				this.cookTime[furnace] = 0;
				return true;
			}
			return false;
		}
		SingleRecipeInput recipeInput = new SingleRecipeInput(input);
		RecipeHolder<SmeltingRecipe> recipe = level.recipeAccess()
				.getRecipeFor(RecipeType.SMELTING, recipeInput, level).orElse(null);
		if (recipe == null) {
			return false;
		}
		ItemStack result = recipe.value().assemble(recipeInput);
		if (result.isEmpty()) {
			return false;
		}

		// How many items can be smelted? Limited by the stack, the batch limit and the room in the output chest.
		int items = Math.min(Math.min(input.getCount(), BATCH_LIMIT), chestRoomFor(result) / result.getCount());
		if (items <= 0) {
			return false;
		}

		// Pay for the whole batch: burn fuel from the shared store until the reserve covers every item.
		boolean changed = false;
		int cookingTime = recipe.value().cookingTime();
		long needed = (long) items * cookingTime;
		while (this.fuelReserve[furnace] < needed) {
			int burn = burnOneFuel(level);
			if (burn <= 0) {
				break;
			}
			this.fuelReserve[furnace] += burn;
			this.fuelDuration[furnace] = burn;
			changed = true;
		}
		if (this.fuelReserve[furnace] < needed) {
			items = (int) (this.fuelReserve[furnace] / cookingTime);
		}
		if (items <= 0) {
			return changed;
		}

		this.fuelReserve[furnace] -= items * cookingTime;
		this.batch[furnace] = items;
		this.cookTime[furnace] = 0;
		this.cookTotal[furnace] = Math.max(1, cookingTime / COOK_SPEED);
		return true;
	}

	/** Uses up one fuel item from the shared store and returns its burn time, or 0 if there is none. */
	private int burnOneFuel(ServerLevel level) {
		for (int slot = FUEL_START; slot < FUEL_START + FUEL_SLOTS; slot++) {
			ItemStack fuel = getItem(slot);
			if (fuel.isEmpty()) {
				continue;
			}
			int burn = level.fuelValues().burnDuration(fuel);
			if (burn <= 0) {
				continue;
			}
			ItemStackTemplate remainderTemplate = fuel.getCraftingRemainder();
			ItemStack remainder = remainderTemplate == null ? ItemStack.EMPTY : remainderTemplate.create();
			fuel.shrink(1);
			if (fuel.isEmpty()) {
				setItem(slot, remainder);
			}
			return burn;
		}
		return 0;
	}

	/**
	 * Delivers the finished batch into the output chest and removes the smelted items from the input
	 * slot. If the chest cannot take all of it yet, nothing happens and the batch stays finished.
	 */
	private void deliverBatch(ServerLevel level, int furnace) {
		int inputSlot = INPUT_START + furnace;
		ItemStack input = getItem(inputSlot);
		int items = Math.min(this.batch[furnace], input.getCount());
		if (items > 0) {
			SingleRecipeInput recipeInput = new SingleRecipeInput(input);
			Optional<RecipeHolder<SmeltingRecipe>> recipe = level.recipeAccess()
					.getRecipeFor(RecipeType.SMELTING, recipeInput, level);
			if (recipe.isPresent()) {
				ItemStack result = recipe.get().value().assemble(recipeInput);
				int total = result.getCount() * items;
				if (chestRoomFor(result) < total) {
					return; // output chest full: wait for room
				}
				insertIntoChest(result.copyWithCount(total));
				input.shrink(items);
			}
		}
		this.batch[furnace] = 0;
		this.cookTime[furnace] = 0;
	}

	// ------------------------------------------------------------------
	// Output chest
	// ------------------------------------------------------------------

	/** How many items like {@code stack} the output chest can still take. */
	private int chestRoomFor(ItemStack stack) {
		int room = 0;
		for (int slot = CHEST_START; slot < CHEST_START + CHEST_SLOTS; slot++) {
			ItemStack existing = getItem(slot);
			if (existing.isEmpty()) {
				room += stack.getMaxStackSize();
			} else if (ItemStack.isSameItemSameComponents(existing, stack)) {
				room += existing.getMaxStackSize() - existing.getCount();
			}
		}
		return room;
	}

	/** Adds the whole stack to the output chest (callers check {@link #chestRoomFor} first). */
	private void insertIntoChest(ItemStack stack) {
		for (int slot = CHEST_START; slot < CHEST_START + CHEST_SLOTS && !stack.isEmpty(); slot++) {
			ItemStack existing = getItem(slot);
			if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
				int moved = Math.min(existing.getMaxStackSize() - existing.getCount(), stack.getCount());
				if (moved > 0) {
					existing.grow(moved);
					stack.shrink(moved);
				}
			}
		}
		for (int slot = CHEST_START; slot < CHEST_START + CHEST_SLOTS && !stack.isEmpty(); slot++) {
			if (getItem(slot).isEmpty()) {
				setItem(slot, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
			}
		}
	}

	// ------------------------------------------------------------------
	// Auto-sort: spread items evenly over the six furnaces
	// ------------------------------------------------------------------

	/** Total items in the input slots after the last sort; tells "items were added" from "items were taken". */
	private int lastInputTotal;

	/**
	 * Evenly spreads the items of the six input slots.
	 *
	 * <p>Every kind of item is given a share of the free slots in proportion to how many of it there are
	 * (at least one slot each, as long as there are enough slots), then split evenly across them.
	 * Empty slots are only <em>filled</em> when items were just added; when items were taken out (by you
	 * or by a finished batch) they are left alone, so you can always empty the inputs. Slots of furnaces
	 * in the middle of a batch and stacks that can't stack (tools) are never touched.
	 *
	 * @return true if any slot changed
	 */
	private boolean spreadEvenly() {
		int total = 0;
		for (int i = 0; i < FURNACES; i++) {
			total += getItem(INPUT_START + i).getCount();
		}
		boolean useEmpties = total > this.lastInputTotal;

		// Slots we may rearrange: not in the middle of a batch, and not holding something unstackable.
		boolean[] free = new boolean[FURNACES];
		int freeCount = 0;
		for (int i = 0; i < FURNACES; i++) {
			ItemStack stack = getItem(INPUT_START + i);
			free[i] = stack.isEmpty() || stack.getMaxStackSize() > 1;
			if (free[i]) {
				freeCount++;
			}
		}

		// Group the stacks in the free slots by kind.
		List<ItemStack> kinds = new ArrayList<>();
		List<Integer> totals = new ArrayList<>();
		List<List<Integer>> homes = new ArrayList<>();
		for (int i = 0; i < FURNACES; i++) {
			ItemStack stack = getItem(INPUT_START + i);
			if (!free[i] || stack.isEmpty()) {
				continue;
			}
			int kind = -1;
			for (int k = 0; k < kinds.size(); k++) {
				if (ItemStack.isSameItemSameComponents(kinds.get(k), stack)) {
					kind = k;
					break;
				}
			}
			if (kind < 0) {
				kinds.add(stack);
				totals.add(0);
				homes.add(new ArrayList<>());
				kind = kinds.size() - 1;
			}
			totals.set(kind, totals.get(kind) + stack.getCount());
			homes.get(kind).add(i);
		}
		if (kinds.isEmpty() || kinds.size() > freeCount) {
			this.lastInputTotal = total;
			return false;
		}

		// How many slots each kind gets: the ones it already uses, plus (when allowed) empty ones, handed
		// one by one to whichever kind currently has the most items per slot.
		int[] slotsFor = new int[kinds.size()];
		int used = 0;
		for (int k = 0; k < kinds.size(); k++) {
			slotsFor[k] = homes.get(k).size();
			used += slotsFor[k];
		}
		int emptyLeft = freeCount - used;
		while (useEmpties && emptyLeft > 0) {
			int best = -1;
			double bestLoad = 1.0; // only split a kind that has at least two items per slot
			for (int k = 0; k < kinds.size(); k++) {
				double load = (double) totals.get(k) / slotsFor[k];
				if (slotsFor[k] < totals.get(k) && load > bestLoad) {
					bestLoad = load;
					best = k;
				}
			}
			if (best < 0) {
				break;
			}
			slotsFor[best]++;
			emptyLeft--;
		}

		// Pick the actual slots (current homes first, then empty free slots in order) and split evenly.
		boolean[] taken = new boolean[FURNACES];
		int[] wanted = new int[FURNACES];
		ItemStack[] kindAt = new ItemStack[FURNACES];
		for (int k = 0; k < kinds.size(); k++) {
			List<Integer> chosen = new ArrayList<>(homes.get(k));
			for (int j = 0; j < FURNACES && chosen.size() < slotsFor[k]; j++) {
				if (free[j] && getItem(INPUT_START + j).isEmpty() && !taken[j]) {
					chosen.add(j);
					taken[j] = true;
				}
			}
			chosen.sort(Integer::compare);
			int per = totals.get(k) / chosen.size();
			int extra = totals.get(k) % chosen.size();
			int max = kinds.get(k).getMaxStackSize();
			for (int n = 0; n < chosen.size(); n++) {
				wanted[chosen.get(n)] = Math.min(max, per + (n < extra ? 1 : 0));
				kindAt[chosen.get(n)] = kinds.get(k);
			}
		}

		boolean changed = false;
		for (int j = 0; j < FURNACES; j++) {
			if (!free[j]) {
				continue;
			}
			ItemStack current = getItem(INPUT_START + j);
			int have = current.isEmpty() ? 0 : current.getCount();
			if (have == wanted[j]) {
				continue;
			}
			changed = true;
			setItem(INPUT_START + j, wanted[j] == 0 || kindAt[j] == null
					? ItemStack.EMPTY : kindAt[j].copyWithCount(wanted[j]));
		}
		this.lastInputTotal = total;
		return changed;
	}

	// ------------------------------------------------------------------
	// Hoppers and other automation: top feeds the inputs, sides the fuel store, the bottom empties the chest
	// ------------------------------------------------------------------

	@Override
	public int[] getSlotsForFace(Direction side) {
		int start = side == Direction.DOWN ? CHEST_START : side == Direction.UP ? INPUT_START : FUEL_START;
		int count = side == Direction.DOWN ? CHEST_SLOTS : side == Direction.UP ? FURNACES : FUEL_SLOTS;
		int[] slots = new int[count];
		for (int i = 0; i < count; i++) {
			slots[i] = start + i;
		}
		return slots;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
		return canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return slot >= CHEST_START;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot < FUEL_START) {
			return true;
		}
		if (slot < CHEST_START) {
			return this.level != null && this.level.fuelValues().isFuel(stack);
		}
		return true; // the output chest takes anything
	}

	@Override
	public boolean canTakeItem(Container target, int slot, ItemStack stack) {
		return slot >= CHEST_START;
	}

	// ------------------------------------------------------------------
	// Removal and saving
	// ------------------------------------------------------------------

	@Override
	public void dropContents(Level level, BlockPos pos) {
		if (!level.isClientSide()) {
			super.dropContents(level, pos);
			clearContent();
			for (ItemStack stack : this.legacyItems) {
				Block.popResource(level, pos, stack);
			}
			this.legacyItems.clear();
			setChanged();
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level != null) {
			dropContents(this.level, pos);
		}
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("layout", LAYOUT_VERSION);
		output.putIntArray("fuel_reserve", this.fuelReserve);
		output.putIntArray("fuel_duration", this.fuelDuration);
		output.putIntArray("cook_time", this.cookTime);
		output.putIntArray("cook_total", this.cookTotal);
		output.putIntArray("batch", this.batch);
		output.putBoolean("auto_sort", this.autoSort);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		copyInto(input.getIntArray("fuel_reserve"), this.fuelReserve);
		copyInto(input.getIntArray("fuel_duration"), this.fuelDuration);
		copyInto(input.getIntArray("cook_time"), this.cookTime);
		copyInto(input.getIntArray("cook_total"), this.cookTotal);
		copyInto(input.getIntArray("batch"), this.batch);
		this.autoSort = input.getBooleanOr("auto_sort", true);

		int layout = input.getIntOr("layout", 1);
		if (layout < 2) {
			// The very first 120-slot version: nothing in it fits, so every item goes back to the world.
			this.legacyItems.clear();
			for (ItemStackWithSlot entry : input.listOrEmpty("Items", ItemStackWithSlot.CODEC)) {
				if (!entry.stack().isEmpty()) {
					this.legacyItems.add(entry.stack());
				}
			}
			clearContent();
			Arrays.fill(this.fuelReserve, 0);
			Arrays.fill(this.fuelDuration, 0);
			Arrays.fill(this.cookTime, 0);
			Arrays.fill(this.cookTotal, 0);
			Arrays.fill(this.batch, 0);
		} else if (layout == 3) {
			// Layout 3 had a furnace output slot in 6-11, which are fuel-store slots now: hand those items back.
			for (int slot = FUEL_START; slot < FUEL_START + FURNACES; slot++) {
				ItemStack stack = getItem(slot);
				if (!stack.isEmpty()) {
					this.legacyItems.add(stack.copy());
					setItem(slot, ItemStack.EMPTY);
				}
			}
		}
	}

	private static void copyInto(Optional<int[]> source, int[] target) {
		Arrays.fill(target, 0);
		source.ifPresent(values -> System.arraycopy(values, 0, target, 0, Math.min(values.length, target.length)));
	}
}
