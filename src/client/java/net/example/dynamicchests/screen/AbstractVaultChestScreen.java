package net.example.dynamicchests.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Shared rendering for vault-chest screens. Blits a pixel-accurate PNG background
 * (with slot grid baked in) at exact GUI size. Separate textures are provided for
 * single and double chest configurations since the height differs.
 */
public abstract class AbstractVaultChestScreen<T extends AbstractVaultChestMenu> extends AbstractContainerScreen<T> {

	protected final int accentColor;
	private final Identifier singleTexture;
	private final Identifier doubleTexture; // null for single-only chests
	private final int singleRows;

	protected AbstractVaultChestScreen(T menu, Inventory playerInventory, Component title,
			int accentColor, Identifier singleTexture, Identifier doubleTexture, int singleRows) {
		super(menu, playerInventory, title,
				AbstractVaultChestMenu.GRID_LEFT_MARGIN * 2 + menu.getColumns() * AbstractVaultChestMenu.SLOT_PX,
				AbstractVaultChestMenu.GRID_TOP_MARGIN
						+ menu.getRows() * AbstractVaultChestMenu.SLOT_PX
						+ AbstractVaultChestMenu.GRID_TO_INVENTORY_GAP
						+ 3 * AbstractVaultChestMenu.SLOT_PX + 4
						+ AbstractVaultChestMenu.SLOT_PX
						+ 6);
		this.accentColor    = accentColor;
		this.singleTexture  = singleTexture;
		this.doubleTexture  = doubleTexture;
		this.singleRows     = singleRows;
	}

	/** Constructor for single-only chests (no double variant). */
	protected AbstractVaultChestScreen(T menu, Inventory playerInventory, Component title,
			int accentColor, Identifier singleTexture) {
		this(menu, playerInventory, title, accentColor, singleTexture, null, Integer.MAX_VALUE);
	}

	@Override
	protected void init() {
		super.init();
		this.inventoryLabelX = this.menu.getInventoryLeft();
		this.inventoryLabelY = AbstractVaultChestMenu.GRID_TOP_MARGIN
				+ this.menu.getRows() * AbstractVaultChestMenu.SLOT_PX
				+ AbstractVaultChestMenu.GRID_TO_INVENTORY_GAP - 10;
		this.titleLabelX = AbstractVaultChestMenu.GRID_LEFT_MARGIN;
		this.titleLabelY = 6;
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		// Pick single or double texture based on current row count.
		Identifier tex = (doubleTexture != null && this.menu.getRows() > singleRows)
				? doubleTexture : singleTexture;

		// Blit the full-size PNG at 1:1 scale — no stretching needed since the
		// texture was generated at exactly imageWidth × imageHeight pixels.
		// The PNG has 8px extra padding on every side; blit from UV (8,8) so the
		// game GUI stays the same size while editors have extra border to work with.
		graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
				x, y, 8f, 8f, this.imageWidth, this.imageHeight,
				this.imageWidth + 16, this.imageHeight + 16);

		super.extractContents(graphics, mouseX, mouseY, partialTick);
	}
}
