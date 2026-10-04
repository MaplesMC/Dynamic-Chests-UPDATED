package net.example.dynamicchests.woodcutter;

import net.example.dynamicchests.registry.ModRegistry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Menu of the Wood Cutter. It follows the vanilla Stonecutter's menu step by step: one input slot, one result
 * slot, the player's inventory, and a selected-recipe index kept in a data slot. The differences are the
 * recipe source ({@link WoodcutterRecipes}) and that a recipe can need more than one input item.
 *
 * <p>The server is the only authority: the client sends "select recipe n" and "take the result", and the
 * server validates both against its own copy of the input slot. Slots and the selected index sync back.
 */
public class WoodcutterMenu extends AbstractContainerMenu {

	public static final int INPUT_SLOT = 0;
	public static final int RESULT_SLOT = 1;
	private static final int INV_SLOT_START = 2;
	private static final int INV_SLOT_END = 29;
	private static final int USE_ROW_SLOT_START = 29;
	private static final int USE_ROW_SLOT_END = 38;

	private final ContainerLevelAccess access;
	private final DataSlot selectedRecipeIndex = DataSlot.standalone();
	private final Slot inputSlot;
	private final Slot resultSlot;
	private final Container container = new SimpleContainer(1) {
		@Override
		public void setChanged() {
			super.setChanged();
			WoodcutterMenu.this.slotsChanged(this);
			WoodcutterMenu.this.slotUpdateListener.run();
		}
	};
	private final ResultContainer resultContainer = new ResultContainer();

	private List<WoodcutterRecipes.Entry> recipesForInput = List.of();
	private ItemStack input = ItemStack.EMPTY;
	private long lastSoundTime;
	private Runnable slotUpdateListener = () -> {
	};

	/** Client-side constructor (the server tells the client what the slots hold). */
	public static WoodcutterMenu fromNetwork(int syncId, Inventory inventory, Integer unused) {
		return new WoodcutterMenu(syncId, inventory, ContainerLevelAccess.NULL);
	}

	public WoodcutterMenu(int syncId, Inventory playerInventory, ContainerLevelAccess access) {
		super(ModRegistry.WOODCUTTER_MENU, syncId);
		this.access = access;

		this.inputSlot = addSlot(new Slot(this.container, 0, 20, 33));
		this.resultSlot = addSlot(new Slot(this.resultContainer, 1, 143, 33) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack stack) {
				stack.onCraftedBy(player, stack.getCount());
				int selected = WoodcutterMenu.this.selectedRecipeIndex.get();
				if (WoodcutterMenu.this.isValidRecipeIndex(selected)) {
					int cost = WoodcutterMenu.this.recipesForInput.get(selected).inputCount();
					if (!WoodcutterMenu.this.inputSlot.remove(cost).isEmpty()) {
						WoodcutterMenu.this.setupResultSlot(selected);
					}
				}
				WoodcutterMenu.this.access.execute((level, pos) -> {
					long time = level.getGameTime();
					if (WoodcutterMenu.this.lastSoundTime != time) {
						level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0f, 1.0f);
						WoodcutterMenu.this.lastSoundTime = time;
					}
				});
				super.onTake(player, stack);
			}
		});

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
		}
		addDataSlot(this.selectedRecipeIndex);

		// The chest lid lifts while somebody has the menu open.
		access.execute((level, pos) -> {
			if (level.getBlockEntity(pos) instanceof WoodcutterBlockEntity chest) {
				chest.startOpen(playerInventory.player);
			}
		});

	}

	// ------------------------------------------------------------------
	// What the screen needs
	// ------------------------------------------------------------------

	public int getSelectedRecipeIndex() {
		return this.selectedRecipeIndex.get();
	}

	public List<WoodcutterRecipes.Entry> getVisibleRecipes() {
		return this.recipesForInput;
	}

	public int getNumberOfVisibleRecipes() {
		return this.recipesForInput.size();
	}

	public boolean hasInputItem() {
		return this.inputSlot.hasItem() && !this.recipesForInput.isEmpty();
	}

	public void registerUpdateListener(Runnable listener) {
		this.slotUpdateListener = listener;
	}

	// ------------------------------------------------------------------
	// Selecting and producing
	// ------------------------------------------------------------------

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, ModRegistry.WOODCUTTER_BLOCK);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (isValidRecipeIndex(id)) {
			this.selectedRecipeIndex.set(id);
			setupResultSlot(id);
			return true;
		}
		return false;
	}

	private boolean isValidRecipeIndex(int index) {
		return index >= 0 && index < this.recipesForInput.size();
	}

	@Override
	public void slotsChanged(Container changed) {
		ItemStack stack = this.inputSlot.getItem();
		if (!stack.is(this.input.getItem())) {
			this.input = stack.copy();
			setupRecipeList(stack);
		} else if (stack.getCount() != this.input.getCount()) {
			// Same item, different amount: the selected recipe may have become affordable or not.
			this.input = stack.copy();
			setupResultSlot(this.selectedRecipeIndex.get());
		}
	}

	private void setupRecipeList(ItemStack stack) {
		this.selectedRecipeIndex.set(-1);
		this.resultSlot.set(ItemStack.EMPTY);
		this.recipesForInput = WoodcutterRecipes.forInput(stack);
	}

	/** Puts the selected recipe's result in the result slot, if the input slot holds enough for it. */
	private void setupResultSlot(int index) {
		if (isValidRecipeIndex(index)) {
			WoodcutterRecipes.Entry recipe = this.recipesForInput.get(index);
			boolean affordable = this.inputSlot.getItem().getCount() >= recipe.inputCount();
			this.resultContainer.setItem(0, affordable ? recipe.result() : ItemStack.EMPTY);
		} else {
			this.resultContainer.setItem(0, ItemStack.EMPTY);
		}
		broadcastChanges();
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot slot) {
		return slot.container != this.resultContainer && super.canTakeItemForPickAll(carried, slot);
	}

	// ------------------------------------------------------------------
	// Shift-click and closing
	// ------------------------------------------------------------------

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack original = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot.hasItem()) {
			ItemStack stack = slot.getItem();
			Item item = stack.getItem();
			original = stack.copy();
			if (index == RESULT_SLOT) {
				stack.onCraftedBy(player, stack.getCount());
				if (!moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, true)) {
					return ItemStack.EMPTY;
				}
				slot.onQuickCraft(stack, original);
			} else if (index == INPUT_SLOT) {
				if (!moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false)) {
					return ItemStack.EMPTY;
				}
			} else if (WoodcutterRecipes.hasRecipes(item)) {
				if (!moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (index >= INV_SLOT_START && index < INV_SLOT_END) {
				if (!moveItemStackTo(stack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false)) {
					return ItemStack.EMPTY;
				}
			} else if (index >= USE_ROW_SLOT_START && index < USE_ROW_SLOT_END
					&& !moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, false)) {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			}
			slot.setChanged();
			if (stack.getCount() == original.getCount()) {
				return ItemStack.EMPTY;
			}
			slot.onTake(player, stack);
			broadcastChanges();
		}
		return original;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.resultContainer.removeItemNoUpdate(1);
		this.access.execute((level, pos) -> {
			if (level.getBlockEntity(pos) instanceof WoodcutterBlockEntity chest) {
				chest.stopOpen(player);
			}
			clearContainer(player, this.container);
		});
	}
}
