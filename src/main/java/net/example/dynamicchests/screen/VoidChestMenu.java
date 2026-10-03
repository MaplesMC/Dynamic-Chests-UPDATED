package net.example.dynamicchests.screen;

import net.example.dynamicchests.block.entity.VoidChestBlockEntity;
import net.example.dynamicchests.registry.ModRegistry;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;

public class VoidChestMenu extends AbstractVaultChestMenu {

    public VoidChestMenu(int syncId, Inventory playerInventory, Container container, int rows) {
        super(ModRegistry.VOID_CHEST_MENU, syncId, playerInventory, container, VoidChestBlockEntity.COLUMNS, rows);
    }

    public static VoidChestMenu fromNetwork(int syncId, Inventory playerInventory, Integer rows) {
        Container placeholder = createPlaceholderContainer(VoidChestBlockEntity.COLUMNS * rows);
        return new VoidChestMenu(syncId, playerInventory, placeholder, rows);
    }
}
