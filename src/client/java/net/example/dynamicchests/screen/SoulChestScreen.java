package net.example.dynamicchests.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class SoulChestScreen extends AbstractContainerScreen<SoulChestMenu> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("dynamicchests", "textures/gui/container/soul_chest.png");

    public SoulChestScreen(SoulChestMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title,
                SoulChestMenu.IMAGE_WIDTH, SoulChestMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = SoulChestMenu.INV_X;
        this.titleLabelY = 7;
        this.inventoryLabelX = SoulChestMenu.INV_X;
        this.inventoryLabelY = SoulChestMenu.INV_Y - 11;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float pt) {
        gfx.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                this.leftPos, this.topPos, 8f, 8f,
                SoulChestMenu.IMAGE_WIDTH, SoulChestMenu.IMAGE_HEIGHT,
                211, 228); // actual soul_chest.png pixel dimensions
        super.extractContents(gfx, mouseX, mouseY, pt);
    }
}
