package net.example.dynamicchests.screen;

import net.example.dynamicchests.block.entity.FurnaceChestBlockEntity;
import net.example.dynamicchests.registry.ModRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * Menu of the Furnace Chest. Top: six furnaces in a row, each just an input slot with a progress bar
 * and flame under it. Middle: a single 27-slot output chest the furnaces deliver into (you can use it for storage too). Bottom: the
 * player's inventory. On the right: a 3x4 fuel store that all furnaces burn from. All progress numbers
 * come from the server through menu data.
 */
public class FurnaceChestMenu extends AbstractContainerMenu {

	// ---- layout (menu-relative pixels) ----
	public static final int IMAGE_WIDTH = 176;
	public static final int COLUMN_START_X = 19;
	public static final int COLUMN_STEP = 24;
	public static final int INPUT_Y = 20;
	public static final int BAR_Y = 40;
	public static final int BAR_HEIGHT = 24;
	public static final int FLAME_Y = 68;
	/** Top of the output-chest section (a vanilla three-row chest picture). */
	public static final int CHEST_SECTION_Y = 90;
	public static final int CHEST_SECTION_HEIGHT = 17 + 3 * 18;
	/** Top of the vanilla player-inventory picture. */
	public static final int PLAYER_SECTION_Y = CHEST_SECTION_Y + CHEST_SECTION_HEIGHT;
	public static final int PLAYER_SECTION_HEIGHT = 96;
	public static final int IMAGE_HEIGHT = PLAYER_SECTION_Y + PLAYER_SECTION_HEIGHT;

	/** The fuel store sits in its own panel to the right of the main picture: 3 columns by 4 rows. */
	public static final int FUEL_COLUMNS = 3;
	public static final int FUEL_ROWS = 4;
	public static final int FUEL_PANEL_X = IMAGE_WIDTH + 2;
	public static final int FUEL_PANEL_WIDTH = 7 + FUEL_COLUMNS * 18 + 7;
	public static final int FUEL_PANEL_HEIGHT = 17 + FUEL_ROWS * 18 + 7;
	public static final int FUEL_SLOTS_X = FUEL_PANEL_X + 7;
	public static final int FUEL_SLOTS_Y = 18;

	public static int columnX(int furnace) {
		return COLUMN_START_X + furnace * COLUMN_STEP;
	}

	private static final int CHEST_SLOTS = FurnaceChestBlockEntity.CONTAINER_SIZE;

	public static final int BUTTON_TOGGLE_SORT = 0;

	private final Container container;
	private final ContainerData data;

	public FurnaceChestMenu(int syncId, Inventory playerInventory, Container container, ContainerData data) {
		super(ModRegistry.FURNACE_CHEST_MENU, syncId);
		checkContainerSize(container, CHEST_SLOTS);
		checkContainerDataCount(data, FurnaceChestBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		container.startOpen(playerInventory.player);

		// Slot order matches the container: inputs 0-5, fuel store 6-17, output chest 18-44.
		for (int furnace = 0; furnace < FurnaceChestBlockEntity.FURNACES; furnace++) {
			addSlot(new Slot(container, FurnaceChestBlockEntity.INPUT_START + furnace, columnX(furnace), INPUT_Y));
		}
		for (int row = 0; row < FUEL_ROWS; row++) {
			for (int col = 0; col < FUEL_COLUMNS; col++) {
				addSlot(new FuelSlot(container, FurnaceChestBlockEntity.FUEL_START + col + row * FUEL_COLUMNS,
						FUEL_SLOTS_X + col * 18, FUEL_SLOTS_Y + row * 18, playerInventory.player));
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(container, FurnaceChestBlockEntity.CHEST_START + col + row * 9,
						8 + col * 18, CHEST_SECTION_Y + 18 + row * 18));
			}
		}
		int invY = PLAYER_SECTION_Y + 14;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, invY + 58));
		}
		addDataSlots(data);
	}

	public static FurnaceChestMenu fromNetwork(int syncId, Inventory playerInventory, Integer unused) {
		return new FurnaceChestMenu(syncId, playerInventory,
				new SimpleContainer(CHEST_SLOTS), new SimpleContainerData(FurnaceChestBlockEntity.DATA_COUNT));
	}

	// ------------------------------------------------------------------
	// Progress (server-synced)
	// ------------------------------------------------------------------

	private int value(int furnace, int field) {
		return this.data.get(furnace * FurnaceChestBlockEntity.DATA_STRIDE + field);
	}

	/** True while the furnace is cooking a batch. */
	public boolean isBusy(int furnace) {
		return value(furnace, FurnaceChestBlockEntity.DATA_BATCH) > 0;
	}

	/** Number of items in the batch being smelted. */
	public int getBatchSize(int furnace) {
		return value(furnace, FurnaceChestBlockEntity.DATA_BATCH);
	}

	/** Fraction of the current batch that is done, 0 to 1. */
	public float getProgress(int furnace) {
		int cook = value(furnace, FurnaceChestBlockEntity.DATA_COOK);
		int total = value(furnace, FurnaceChestBlockEntity.DATA_COOK_TOTAL);
		return isBusy(furnace) && total != 0 ? Math.clamp((float) cook / total, 0.0f, 1.0f) : 0.0f;
	}

	/** Fraction of the last fuel item still in reserve, for the flame, like the vanilla furnace's lit progress. */
	public float getFlameProgress(int furnace) {
		int total = value(furnace, FurnaceChestBlockEntity.DATA_LIT_TOTAL);
		if (total == 0) {
			total = 200;
		}
		return Math.clamp((float) value(furnace, FurnaceChestBlockEntity.DATA_LIT) / total, 0.0f, 1.0f);
	}

	public boolean isAutoSort() {
		return this.data.get(FurnaceChestBlockEntity.DATA_AUTO_SORT) != 0;
	}

	// ------------------------------------------------------------------
	// Interaction
	// ------------------------------------------------------------------

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == BUTTON_TOGGLE_SORT && this.container instanceof FurnaceChestBlockEntity chest) {
			chest.toggleAutoSort();
			return true;
		}
		return false;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.container.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int fuelEnd = FurnaceChestBlockEntity.FUEL_START + FurnaceChestBlockEntity.FUEL_SLOTS;
		if (index < CHEST_SLOTS) {
			if (!moveItemStackTo(stack, CHEST_SLOTS, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			int inputEnd = FurnaceChestBlockEntity.FUEL_START;
			int chestStart = FurnaceChestBlockEntity.CHEST_START;
			boolean moved;
			if (isSmeltable(player, stack)) {
				moved = moveItemStackTo(stack, FurnaceChestBlockEntity.INPUT_START, inputEnd, false)
						|| moveItemStackTo(stack, chestStart, CHEST_SLOTS, false);
			} else if (this.slots.get(FurnaceChestBlockEntity.FUEL_START).mayPlace(stack)) {
				moved = moveItemStackTo(stack, FurnaceChestBlockEntity.FUEL_START, fuelEnd, false)
						|| moveItemStackTo(stack, chestStart, CHEST_SLOTS, false);
			} else {
				moved = moveItemStackTo(stack, chestStart, CHEST_SLOTS, false);
			}
			if (!moved) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	/** Whether the item has a smelting recipe. Only the server can look recipes up; the client assumes yes and is corrected. */
	private static boolean isSmeltable(Player player, ItemStack stack) {
		if (player.level() instanceof ServerLevel level) {
			return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level).isPresent();
		}
		// Client guess: things that burn are fuel, everything else goes to the inputs.
		return !player.level().fuelValues().isFuel(stack);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.container.stopOpen(player);
	}

	// ------------------------------------------------------------------
	// Slots
	// ------------------------------------------------------------------

	/** A slot of the shared fuel store: only things that burn. */
	private static final class FuelSlot extends Slot {
		private final Player player;

		FuelSlot(Container container, int index, int x, int y, Player player) {
			super(container, index, x, y);
			this.player = player;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return this.player.level().fuelValues().isFuel(stack);
		}
	}
}
