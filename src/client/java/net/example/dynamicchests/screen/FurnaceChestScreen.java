package net.example.dynamicchests.screen;

import net.example.dynamicchests.block.entity.FurnaceChestBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Furnace Chest screen. Top: a row of six furnaces (input slot, progress bar, flame). Middle: a single
 * three-row output chest. Bottom: the player inventory. Right: the 3x4 fuel store in its own panel.
 * The panels, slot frames and chest come from the vanilla chest texture; the empty flame and the
 * burning-flame sprite are the vanilla furnace's. The progress bar is drawn in the vanilla
 * experience-bar colours.
 */
public class FurnaceChestScreen extends AbstractContainerScreen<FurnaceChestMenu> {

	private static final Identifier CHEST_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final Identifier FURNACE_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
	private static final Identifier LIT_PROGRESS = Identifier.withDefaultNamespace("container/furnace/lit_progress");

	private static final int TEXT_COLOR = 0xFF404040;
	private static final int PANEL_GRAY = 0xFFC6C6C6;
	private static final int BAR_BORDER = 0xFF000000;
	private static final int BAR_TRACK = 0xFF373737;
	private static final int BAR_FILL = 0xFFE03030;
	private static final int BAR_WIDTH = 8;
	private static final int SORT_BUTTON_SIZE = 14;
	private static final int SORT_ON_COLOR = 0xFFFFD84A;

	private Button sortButton;

	public FurnaceChestScreen(FurnaceChestMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, FurnaceChestMenu.IMAGE_WIDTH, FurnaceChestMenu.IMAGE_HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = FurnaceChestMenu.PLAYER_SECTION_Y + 3;

		// The sort toggle: a small "S" button in the top-right corner of the panel.
		this.sortButton = addRenderableWidget(Button.builder(sortMessage(), button ->
				this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, FurnaceChestMenu.BUTTON_TOGGLE_SORT)
		).bounds(this.leftPos + FurnaceChestMenu.IMAGE_WIDTH - SORT_BUTTON_SIZE - 3, this.topPos + 2, SORT_BUTTON_SIZE, SORT_BUTTON_SIZE)
				.tooltip(Tooltip.create(Component.translatable("gui.dynamicchests.furnace_chest.sort_tooltip")))
				.build());
	}

	/** Just an "S": gold while auto-sort is on, plain grey when it is off. */
	private Component sortMessage() {
		return Component.literal("S").withStyle(Style.EMPTY.withColor(this.menu.isAutoSort() ? SORT_ON_COLOR : 0xFFA0A0A0).withBold(this.menu.isAutoSort()));
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.sortButton.setMessage(sortMessage());
	}

	/** The fuel panel hangs outside the main picture; clicks on it must not count as "outside the GUI". */
	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
		int panelX = left + FurnaceChestMenu.FUEL_PANEL_X;
		boolean inFuelPanel = mouseX >= panelX && mouseX < panelX + FurnaceChestMenu.FUEL_PANEL_WIDTH
				&& mouseY >= top && mouseY < top + FurnaceChestMenu.FUEL_PANEL_HEIGHT;
		return !inFuelPanel && super.hasClickedOutside(mouseX, mouseY, left, top);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		// Furnace panel: the chest's title strip, a grey body with the chest's own side edges.
		gfx.blit(RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE, x, y, 0f, 0f, FurnaceChestMenu.IMAGE_WIDTH, 17, 256, 256);
		int bodyHeight = FurnaceChestMenu.CHEST_SECTION_Y - 17;
		gfx.fill(x + 3, y + 17, x + FurnaceChestMenu.IMAGE_WIDTH - 3, y + FurnaceChestMenu.CHEST_SECTION_Y, PANEL_GRAY);
		gfx.blit(RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE, x, y + 17, 0f, 17f, 3, bodyHeight, 256, 256);
		gfx.blit(RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE, x + FurnaceChestMenu.IMAGE_WIDTH - 3, y + 17, 173f, 17f, 3, bodyHeight, 256, 256);

		// Output chest: the vanilla three-row chest picture; then the player's inventory.
		gfx.blit(RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE, x, y + FurnaceChestMenu.CHEST_SECTION_Y, 0f, 0f,
				FurnaceChestMenu.IMAGE_WIDTH, FurnaceChestMenu.CHEST_SECTION_HEIGHT, 256, 256);
		gfx.blit(RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE, x, y + FurnaceChestMenu.PLAYER_SECTION_Y, 0f, 126f,
				FurnaceChestMenu.IMAGE_WIDTH, FurnaceChestMenu.PLAYER_SECTION_HEIGHT, 256, 256);

		for (int furnace = 0; furnace < FurnaceChestBlockEntity.FURNACES; furnace++) {
			int columnX = x + FurnaceChestMenu.columnX(furnace);

			// Vanilla slot frame behind the input slot.
			gfx.blit(RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE, columnX - 1, y + FurnaceChestMenu.INPUT_Y - 1, 7f, 17f, 18, 18, 256, 256);

			// Progress bar: fills downwards from the input as the batch cooks.
			int barX = columnX + (16 - BAR_WIDTH) / 2;
			int barY = y + FurnaceChestMenu.BAR_Y;
			int barHeight = FurnaceChestMenu.BAR_HEIGHT;
			gfx.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + barHeight + 1, BAR_BORDER);
			gfx.fill(barX, barY, barX + BAR_WIDTH, barY + barHeight, BAR_TRACK);
			int filled = Mth.floor(barHeight * this.menu.getProgress(furnace));
			if (filled > 0) {
				gfx.fill(barX, barY, barX + BAR_WIDTH, barY + filled, BAR_FILL);
			}

			// The vanilla flame under the bar, burning while a batch cooks.
			int flameX = columnX + 1;
			int flameY = y + FurnaceChestMenu.FLAME_Y;
			gfx.blit(RenderPipelines.GUI_TEXTURED, FURNACE_TEXTURE, flameX, flameY, 56f, 36f, 14, 14, 256, 256);
			if (this.menu.isBusy(furnace)) {
				int flame = Mth.ceil(this.menu.getFlameProgress(furnace) * 13.0f) + 1;
				gfx.blitSprite(RenderPipelines.GUI_TEXTURED, LIT_PROGRESS, 14, 14, 0, 14 - flame,
						flameX, flameY + 14 - flame, 14, flame);
			}
		}

		// Fuel store panel on the right: a vanilla-grey box with a 3x4 grid of slot frames.
		int fuelX = x + FurnaceChestMenu.FUEL_PANEL_X;
		panel(gfx, fuelX, y, FurnaceChestMenu.FUEL_PANEL_WIDTH, FurnaceChestMenu.FUEL_PANEL_HEIGHT);
		for (int row = 0; row < FurnaceChestMenu.FUEL_ROWS; row++) {
			for (int col = 0; col < FurnaceChestMenu.FUEL_COLUMNS; col++) {
				gfx.blit(RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE,
						x + FurnaceChestMenu.FUEL_SLOTS_X + col * 18 - 1, y + FurnaceChestMenu.FUEL_SLOTS_Y + row * 18 - 1,
						7f, 17f, 18, 18, 256, 256);
			}
		}

		super.extractContents(gfx, mouseX, mouseY, partialTick);

		gfx.text(this.font, Component.translatable("gui.dynamicchests.furnace_chest.output"),
				x + 8, y + FurnaceChestMenu.CHEST_SECTION_Y + 6, TEXT_COLOR, false);
		gfx.text(this.font, Component.translatable("gui.dynamicchests.furnace_chest.fuel"),
				fuelX + 7, y + 6, TEXT_COLOR, false);
	}

	/** A vanilla-styled raised grey box, drawn with the usual white, grey and dark-grey bevel. */
	private static void panel(GuiGraphicsExtractor gfx, int x, int y, int width, int height) {
		gfx.fill(x, y, x + width, y + height, 0xFF000000);
		gfx.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF555555);
		gfx.fill(x + 1, y + 1, x + width - 2, y + height - 2, 0xFFFFFFFF);
		gfx.fill(x + 3, y + 3, x + width - 2, y + height - 2, 0xFF555555);
		gfx.fill(x + 3, y + 3, x + width - 3, y + height - 3, PANEL_GRAY);
	}
}
