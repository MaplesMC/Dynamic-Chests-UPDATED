package net.example.dynamicchests.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class ShadowChestScreen extends AbstractVaultChestScreen<ShadowChestMenu> {

    private static final int        SHADOW_ACCENT = 0xFF1A1A2E;
    private static final Identifier SINGLE_TEX    = Identifier.fromNamespaceAndPath("dynamicchests", "textures/gui/container/shadow_chest_single.png");
    private static final Identifier DOUBLE_TEX    = Identifier.fromNamespaceAndPath("dynamicchests", "textures/gui/container/shadow_chest_double.png");

    public ShadowChestScreen(ShadowChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, SHADOW_ACCENT, SINGLE_TEX, DOUBLE_TEX, 3);
    }
}
