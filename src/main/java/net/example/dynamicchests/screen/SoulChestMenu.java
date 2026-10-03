package net.example.dynamicchests.screen;

import net.example.dynamicchests.block.entity.SoulChestBlockEntity;
import net.example.dynamicchests.registry.ModRegistry;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SoulChestMenu extends AbstractContainerMenu {

    // ── Screen dimensions ─────────────────────────────────────────────────────
    public static final int IMAGE_WIDTH  = 203; // 211px PNG minus 8px left UV offset
    public static final int IMAGE_HEIGHT = 212;

    // ── Chest slot positions ──────────────────────────────────────────────────
    // Armor column (left side, x=8): aligns with each row of the main grid.
    public static final int ARMOR_X       = 8;
    public static final int ARMOR_START_Y = 20; // pushed down to clear title text
    public static final int OFFHAND_Y     = 96; // 4px gap below boots row

    // Main 9×4 grid (captured main inventory, slots 0-35).
    public static final int GRID_X    = 30;
    public static final int GRID_Y    = 20;    // matches ARMOR_START_Y
    public static final int GRID_COLS = 9;
    public static final int GRID_ROWS = 4;

    // ── Player inventory ──────────────────────────────────────────────────────
    public static final int INV_X     = 8;
    public static final int INV_Y     = 130;
    public static final int HOTBAR_Y  = 188;

    // Total chest slots: 36 main + 4 armor + 1 offhand.
    private static final int CHEST_SLOTS = SoulChestBlockEntity.CONTAINER_SIZE;

    private final Container container;

    public SoulChestMenu(int syncId, Inventory playerInventory, Container container) {
        super(ModRegistry.SOUL_CHEST_MENU, syncId);
        this.container = container;
        container.startOpen(playerInventory.player);

        // Main inventory content: 9×4 grid, chest slots 0-35.
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                addSlot(new ReadOnlySlot(container, col + row * GRID_COLS,
                        GRID_X + col * 18, GRID_Y + row * 18));
            }
        }

        // Armor slots 36-39 (helmet → boots, top to bottom).
        for (int i = 0; i < 4; i++) {
            addSlot(new ReadOnlySlot(container, 36 + i, ARMOR_X, ARMOR_START_Y + i * 18));
        }

        // Offhand slot 40.
        addSlot(new ReadOnlySlot(container, 40, ARMOR_X, OFFHAND_Y));

        // Player inventory (3×9) and hotbar (9).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    public static SoulChestMenu fromNetwork(int syncId, Inventory playerInventory, Integer unused) {
        return new SoulChestMenu(syncId, playerInventory,
                new SimpleContainer(SoulChestBlockEntity.CONTAINER_SIZE));
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    /** Chest slots are read-only — shift-click moves items from chest to player only. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= CHEST_SLOTS) return ItemStack.EMPTY; // player inv → chest: blocked

        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!this.moveItemStackTo(stack, CHEST_SLOTS, this.slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    /** Prevent clicking items INTO the chest. */
    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= 0 && slotId < CHEST_SLOTS && !getCarried().isEmpty()) return;
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    // ── Slot types ────────────────────────────────────────────────────────────

    /** Slot that allows taking items out but never placing them in. */
    private static class ReadOnlySlot extends Slot {
        ReadOnlySlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
