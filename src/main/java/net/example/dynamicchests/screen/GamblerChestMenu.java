package net.example.dynamicchests.screen;

import net.example.dynamicchests.block.entity.GamblerChestBlockEntity;
import net.example.dynamicchests.gambler.data.GamblerPlayerData;
import net.example.dynamicchests.gambler.logic.GambleOutcome;
import net.example.dynamicchests.gambler.logic.ItemRules;
import net.example.dynamicchests.gambler.logic.OutcomeRoller;
import net.example.dynamicchests.gambler.logic.PendingGamble;
import net.example.dynamicchests.gambler.logic.ValueTier;
import net.example.dynamicchests.gambler.network.GamblerNetworking;
import net.example.dynamicchests.registry.ModRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu of the Gambler's Chest. The server decides everything; the client only sees a read-only
 * mirror through {@link ContainerData} (animation phase, odds, streak, history, status) and can
 * ask for a gamble by pressing the button, which the server validates from scratch.
 */
public class GamblerChestMenu extends AbstractContainerMenu {

	// ---- layout (menu-relative pixels) ----
	public static final int IMAGE_WIDTH = 176;
	public static final int IMAGE_HEIGHT = 244;
	public static final int INPUT_X = 8;
	public static final int INPUT_Y = 29;
	public static final int OUTPUT_X = 82;
	public static final int OUTPUT_Y = 29;
	public static final int INV_X = 8;
	public static final int INV_Y = 162;
	public static final int HOTBAR_Y = 220;

	// ---- synced data layout ----
	public static final int D_PHASE = 0;
	public static final int D_ELAPSED = 1;
	public static final int D_TOTAL = 2;
	public static final int D_RESULT = 3;
	public static final int D_STREAK = 4;
	public static final int D_TIER = 5;
	public static final int D_STATUS = 6;
	public static final int D_ODDS = 7;
	public static final int D_HISTORY = D_ODDS + GambleOutcome.VALUES.length;
	public static final int D_COUNT = D_HISTORY + GamblerPlayerData.HISTORY_SIZE;

	public static final int PHASE_IDLE = 0;
	public static final int PHASE_ROLLING = 1;
	public static final int PHASE_RESULT = 2;

	private static final int CHEST_SLOTS = GamblerChestBlockEntity.CONTAINER_SIZE;

	private final Container container;
	private final Player player;
	private final SyncedData data = new SyncedData(D_COUNT);

	public GamblerChestMenu(int syncId, Inventory playerInventory, Container container) {
		super(ModRegistry.GAMBLER_CHEST_MENU, syncId);
		this.container = container;
		this.player = playerInventory.player;
		checkContainerSize(container, CHEST_SLOTS);
		container.startOpen(playerInventory.player);

		addSlot(new InputSlot(container, GamblerChestBlockEntity.INPUT_SLOT, INPUT_X, INPUT_Y));
		for (int i = 0; i < GamblerChestBlockEntity.OUTPUT_SLOTS; i++) {
			addSlot(new OutputSlot(container, GamblerChestBlockEntity.OUTPUT_START + i, OUTPUT_X + i * 18, OUTPUT_Y));
		}

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
		}

		addDataSlots(this.data);
		refreshData();
	}

	public static GamblerChestMenu fromNetwork(int syncId, Inventory playerInventory, Integer unused) {
		return new GamblerChestMenu(syncId, playerInventory, new SimpleContainer(CHEST_SLOTS));
	}

	// ------------------------------------------------------------------
	// Server -> client data
	// ------------------------------------------------------------------

	/** Recomputes everything the GUI shows. Server side only; on the client the values arrive by packet. */
	private void refreshData() {
		if (!(this.container instanceof GamblerChestBlockEntity chest) || this.player.level().isClientSide()) {
			return;
		}
		PendingGamble pending = chest.getPending();
		GambleOutcome last = chest.getLastResult();

		this.data.values[D_PHASE] = pending != null ? PHASE_ROLLING : last != null ? PHASE_RESULT : PHASE_IDLE;
		this.data.values[D_ELAPSED] = pending != null ? pending.elapsedTicks : 0;
		this.data.values[D_TOTAL] = pending != null ? pending.totalTicks : 0;
		// The real outcome is never sent while the animation is still running.
		this.data.values[D_RESULT] = pending == null && last != null ? last.ordinal() : -1;
		this.data.values[D_STATUS] = pending != null
				? GamblerChestBlockEntity.StartResult.BUSY.ordinal()
				: chest.check(this.player).ordinal();

		ItemStack input = chest.getItem(GamblerChestBlockEntity.INPUT_SLOT);
		double unitValue = input.isEmpty() ? 0 : ItemRules.valueOf(input);
		ValueTier tier = pending != null ? pending.tier
				: input.isEmpty() ? ValueTier.COMMON : ItemRules.tierOf(unitValue);
		this.data.values[D_TIER] = input.isEmpty() && pending == null ? -1 : tier.ordinal();

		// Displayed odds include the item's risk tier but not the hidden luck value.
		double[] odds = OutcomeRoller.odds(tier, 0, 0);
		for (int i = 0; i < odds.length; i++) {
			this.data.values[D_ODDS + i] = (int) Math.round(odds[i] * 10.0);
		}

		if (this.player instanceof ServerPlayer serverPlayer) {
			GamblerPlayerData.Stats stats = GamblerPlayerData.get(serverPlayer.level().getServer()).stats(serverPlayer.getUUID());
			this.data.values[D_STREAK] = stats.signedStreak();
			for (int i = 0; i < GamblerPlayerData.HISTORY_SIZE; i++) {
				this.data.values[D_HISTORY + i] = i < stats.history.size() ? stats.history.get(i) : -1;
			}
		}
	}

	@Override
	public void broadcastChanges() {
		refreshData();
		super.broadcastChanges();
	}

	@Override
	public void broadcastFullState() {
		refreshData();
		super.broadcastFullState();
	}

	// ---- read access for the screen / slots ----

	public int data(int index) {
		return this.data.values[index];
	}

	public boolean isRolling() {
		if (this.container instanceof GamblerChestBlockEntity chest) {
			return chest.isGambling();
		}
		return this.data.values[D_PHASE] == PHASE_ROLLING;
	}

	public boolean isBoundTo(GamblerChestBlockEntity chest) {
		return this.container == chest;
	}

	// ------------------------------------------------------------------
	// Button
	// ------------------------------------------------------------------

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if ((id != GamblerNetworking.BUTTON_GAMBLE && id != GamblerNetworking.BUTTON_BET_ALL)
				|| !(player instanceof ServerPlayer serverPlayer)
				|| !(this.container instanceof GamblerChestBlockEntity chest)) {
			return false;
		}
		// A menu belongs to one player; ignore button packets for someone else's container id.
		if (serverPlayer.containerMenu != this) {
			return false;
		}
		if (!GamblerNetworking.acceptRequest(serverPlayer, serverPlayer.level().getGameTime())) {
			return false;
		}
		GamblerChestBlockEntity.StartResult result = chest.startGamble(serverPlayer, id == GamblerNetworking.BUTTON_BET_ALL);
		if (result != GamblerChestBlockEntity.StartResult.OK && result != GamblerChestBlockEntity.StartResult.BUSY) {
			serverPlayer.sendSystemMessage(
					Component.translatable("gambler.dynamicchests.start_failed." + result.name().toLowerCase()), true);
		}
		return result == GamblerChestBlockEntity.StartResult.OK;
	}

	// ------------------------------------------------------------------
	// Inventory handling
	// ------------------------------------------------------------------

	@Override
	public boolean stillValid(Player player) {
		return this.container.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem() || isRolling() && index == GamblerChestBlockEntity.INPUT_SLOT) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if (index < CHEST_SLOTS) {
			// Chest -> player inventory.
			if (!moveItemStackTo(stack, CHEST_SLOTS, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			// Player inventory -> the bet slot only (respects the slot's own rules).
			if (!moveItemStackTo(stack, GamblerChestBlockEntity.INPUT_SLOT, GamblerChestBlockEntity.INPUT_SLOT + 1, false)) {
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

	/** Belt and braces: while rolling, no click may touch the bet slot, whatever the click type. */
	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		if (isRolling() && slotId == GamblerChestBlockEntity.INPUT_SLOT) {
			return;
		}
		super.clicked(slotId, button, input, player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.container.stopOpen(player);
		if (this.container instanceof GamblerChestBlockEntity chest) {
			chest.release(player.getUUID());
		}
	}

	// ------------------------------------------------------------------
	// Slots
	// ------------------------------------------------------------------

	private final class InputSlot extends Slot {
		InputSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return !isRolling() && ItemRules.isAllowed(stack);
		}

		@Override
		public boolean mayPickup(Player player) {
			return !isRolling();
		}
	}

	private static final class OutputSlot extends Slot {
		OutputSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}

	/** Plain int array exposed to the menu sync; the client side is written by incoming packets. */
	private static final class SyncedData implements ContainerData {
		final int[] values;

		SyncedData(int size) {
			this.values = new int[size];
			this.values[D_RESULT] = -1;
			this.values[D_TIER] = -1;
			for (int i = 0; i < GamblerPlayerData.HISTORY_SIZE; i++) {
				this.values[D_HISTORY + i] = -1;
			}
		}

		@Override
		public int get(int index) {
			return this.values[index];
		}

		@Override
		public void set(int index, int value) {
			this.values[index] = value;
		}

		@Override
		public int getCount() {
			return this.values.length;
		}
	}
}
