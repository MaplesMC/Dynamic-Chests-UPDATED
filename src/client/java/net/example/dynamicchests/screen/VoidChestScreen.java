package net.example.dynamicchests.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class VoidChestScreen extends AbstractVaultChestScreen<VoidChestMenu> {

    private static final int        VOID_ACCENT = 0xFF2D0050;
    private static final Identifier SINGLE_TEX  = Identifier.fromNamespaceAndPath("dynamicchests", "textures/gui/container/void_chest.png");

    public VoidChestScreen(VoidChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, VOID_ACCENT, SINGLE_TEX);
    }
}
