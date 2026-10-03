package net.example.dynamicchests.screen;

import net.example.dynamicchests.block.entity.ShadowChestBlockEntity;
import net.example.dynamicchests.registry.ModRegistry;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;

public class ShadowChestMenu extends AbstractVaultChestMenu {

    public ShadowChestMenu(int syncId, Inventory playerInventory, Container container, int rows) {
        super(ModRegistry.SHADOW_CHEST_MENU, syncId, playerInventory, container, ShadowChestBlockEntity.COLUMNS, rows);
    }

    public static ShadowChestMenu fromNetwork(int syncId, Inventory playerInventory, Integer rows) {
        Container placeholder = createPlaceholderContainer(ShadowChestBlockEntity.COLUMNS * rows);
        return new ShadowChestMenu(syncId, playerInventory, placeholder, rows);
    }
}
