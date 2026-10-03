package net.example.dynamicchests.screen;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Shared screen-handler logic for the vault chests. The grid is laid out dynamically
 * from {@code columns} x {@code rows}, so the SAME menu class serves both the single-chest
 * and double-chest cases — only the row count differs at construction time.
 *
 * Slot pixel spacing matches vanilla (18px per slot); {@link AbstractVaultChestScreen}
 * reads these same constants to draw a correctly-sized background for any grid size.
 */
public abstract class AbstractVaultChestMenu extends AbstractContainerMenu {

	public static final int SLOT_PX = 18;
	/** Pixels from the top of the texture down to the first row of chest slots. */
	public static final int GRID_TOP_MARGIN = 17;
	/** Pixels from the left of the texture to the first column of chest slots. */
	public static final int GRID_LEFT_MARGIN = 8;
	/** Gap (in pixels) reserved between the bottom of the chest grid and the player inventory. */
	public static final int GRID_TO_INVENTORY_GAP = 14;

	protected final Container container;
	protected final int columns;
	protected final int rows;

	protected AbstractVaultChestMenu(MenuType<?> type, int syncId, Inventory playerInventory,
			Container container, int columns, int rows) {
		super(type, syncId);
		this.container = container;
		this.columns = columns;
		this.rows = rows;
		container.startOpen(playerInventory.player);

		// Chest grid slots
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < columns; col++) {
				int index = col + row * columns;
				this.addSlot(new Slot(container, index,
						GRID_LEFT_MARGIN + col * SLOT_PX,
						GRID_TOP_MARGIN + row * SLOT_PX));
			}
		}

		int invTop = GRID_TOP_MARGIN + rows * SLOT_PX + GRID_TO_INVENTORY_GAP;
		int invLeft = GRID_LEFT_MARGIN + (columns - 9) * SLOT_PX / 2;

		// Player inventory (3x9) — centered under the chest grid
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
						invLeft + col * SLOT_PX,
						invTop + row * SLOT_PX));
			}
		}

		// Player hotbar
		int hotbarTop = invTop + 3 * SLOT_PX + 4;
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col,
					invLeft + col * SLOT_PX,
					hotbarTop));
		}
	}

	/** Convenience constructor used when no real Container/BlockEntity is available yet (e.g. on the client before sync). */
	protected static Container createPlaceholderContainer(int size) {
		return new SimpleContainer(size);
	}

	public int getColumns() {
		return this.columns;
	}

	public int getRows() {
		return this.rows;
	}

	public int getGridSlotCount() {
		return this.columns * this.rows;
	}

	/** Left pixel offset of the player inventory, centered under the chest grid. */
	public int getInventoryLeft() {
		return GRID_LEFT_MARGIN + (this.columns - 9) * SLOT_PX / 2;
	}

	// ServerboundContainerClickPacket hard-limits changedSlots to 128 entries.
	// Creative middle-drag (QUICK_CRAFT with clone type) fills all dragged slots in one
	// packet, easily exceeding 128 on large inventories and crashing the encoder.
	// The button byte encodes phase as (button & 3) and type as ((button >> 2) & 3):
	//   phase 0 = start drag, 1 = add slot, 2 = finish drag
	//   type  2 = clone (creative middle-drag)
	// We count "add slot" calls per drag and drop any beyond 120, keeping the final
	// changedSlots map safely under the 128 limit.
	private static final int MAX_CLONE_FILL = 120;
	private int cloneDragCount = 0;

	@Override
	public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
		if (clickType == ContainerInput.QUICK_CRAFT) {
			int phase = button & 3;
			int type  = (button >> 2) & 3;
			if (type == 2) { // creative clone drag
				if (phase == 0) {
					cloneDragCount = 0;
				} else if (phase == 1) {
					if (cloneDragCount >= MAX_CLONE_FILL) return;
					cloneDragCount++;
				}
			}
		}
		super.clicked(slotId, button, clickType, player);
	}

	@Override
	public boolean stillValid(Player player) {
		return this.container.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.container.stopOpen(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack newStack = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		int gridSlots = this.getGridSlotCount();

		if (slot.hasItem()) {
			ItemStack original = slot.getItem();
			newStack = original.copy();

			if (index < gridSlots) {
				// Moving FROM the chest grid TO the player inventory.
				if (!this.moveItemStackTo(original, gridSlots, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				// Moving FROM the player inventory TO the chest grid.
				if (!this.moveItemStackTo(original, 0, gridSlots, false)) {
					return ItemStack.EMPTY;
				}
			}

			if (original.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}

			if (original.getCount() == newStack.getCount()) {
				return ItemStack.EMPTY;
			}
			slot.onTake(player, original);
		}

		return newStack;
	}
}
