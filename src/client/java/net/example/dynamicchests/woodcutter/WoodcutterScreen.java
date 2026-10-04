package net.example.dynamicchests.woodcutter;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Wood Cutter's screen: the vanilla Stonecutter screen, line for line, with the same background picture,
 * recipe button and scroller sprites, the same 4x3 recipe grid and the same scrolling behaviour. Only the
 * recipe source differs, and hovering a recipe also shows how many input items it uses.
 */
public class WoodcutterScreen extends AbstractContainerScreen<WoodcutterMenu> {

	private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/scroller");
	private static final Identifier SCROLLER_DISABLED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/scroller_disabled");
	private static final Identifier RECIPE_SELECTED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe_selected");
	private static final Identifier RECIPE_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe_highlighted");
	private static final Identifier RECIPE_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe");
	private static final Identifier BG_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/stonecutter.png");

	private static final int SCROLLER_WIDTH = 12;
	private static final int SCROLLER_HEIGHT = 15;
	private static final int RECIPES_COLUMNS = 4;
	private static final int RECIPES_ROWS = 3;
	private static final int RECIPES_IMAGE_SIZE_WIDTH = 16;
	private static final int RECIPES_IMAGE_SIZE_HEIGHT = 18;
	private static final int SCROLLER_FULL_HEIGHT = 54;
	private static final int RECIPES_X = 52;
	private static final int RECIPES_Y = 14;

	private float scrollOffs;
	private boolean scrolling;
	private int startIndex;
	private boolean displayRecipes;

	public WoodcutterScreen(WoodcutterMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		menu.registerUpdateListener(this::containerChanged);
		this.titleLabelY--;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(gfx, mouseX, mouseY, partialTick);
		int left = this.leftPos;
		int top = this.topPos;
		gfx.blit(RenderPipelines.GUI_TEXTURED, BG_LOCATION, left, top, 0f, 0f, this.imageWidth, this.imageHeight, 256, 256);
		int scrollerY = (int) (41.0f * this.scrollOffs);
		Identifier scroller = isScrollBarActive() ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
		gfx.blitSprite(RenderPipelines.GUI_TEXTURED, scroller, left + 119, top + 15 + scrollerY, SCROLLER_WIDTH, SCROLLER_HEIGHT);
		int recipeX = left + RECIPES_X;
		int recipeY = top + RECIPES_Y;
		int lastVisible = this.startIndex + RECIPES_COLUMNS * RECIPES_ROWS;
		extractButtons(gfx, mouseX, mouseY, recipeX, recipeY, lastVisible);
		extractRecipes(gfx, recipeX, recipeY, lastVisible);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
		super.extractTooltip(gfx, mouseX, mouseY);
		if (!this.displayRecipes) {
			return;
		}
		int recipeX = this.leftPos + RECIPES_X;
		int recipeY = this.topPos + RECIPES_Y;
		int lastVisible = this.startIndex + RECIPES_COLUMNS * RECIPES_ROWS;
		List<WoodcutterRecipes.Entry> recipes = this.menu.getVisibleRecipes();
		for (int i = this.startIndex; i < lastVisible && i < recipes.size(); i++) {
			int visibleIndex = i - this.startIndex;
			int buttonX = recipeX + visibleIndex % RECIPES_COLUMNS * RECIPES_IMAGE_SIZE_WIDTH;
			int buttonY = recipeY + visibleIndex / RECIPES_COLUMNS * RECIPES_IMAGE_SIZE_HEIGHT + 2;
			if (mouseX >= buttonX && mouseX < buttonX + RECIPES_IMAGE_SIZE_WIDTH
					&& mouseY >= buttonY && mouseY < buttonY + RECIPES_IMAGE_SIZE_HEIGHT) {
				WoodcutterRecipes.Entry recipe = recipes.get(i);
				ItemStack result = recipe.output();
				List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(result));
				if (recipe.inputCount() > 1) {
					lines.add(Component.translatable("gui.dynamicchests.wood_cutter.cost",
							recipe.inputCount(), recipe.input().getName(new ItemStack(recipe.input()))));
				}
				gfx.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
			}
		}
	}

	private void extractButtons(GuiGraphicsExtractor gfx, int mouseX, int mouseY, int x, int y, int lastVisible) {
		for (int i = this.startIndex; i < lastVisible && i < this.menu.getNumberOfVisibleRecipes(); i++) {
			int visibleIndex = i - this.startIndex;
			int buttonX = x + visibleIndex % RECIPES_COLUMNS * RECIPES_IMAGE_SIZE_WIDTH;
			int buttonY = y + visibleIndex / RECIPES_COLUMNS * RECIPES_IMAGE_SIZE_HEIGHT + 2;
			Identifier sprite;
			if (i == this.menu.getSelectedRecipeIndex()) {
				sprite = RECIPE_SELECTED_SPRITE;
			} else if (mouseX >= buttonX && mouseY >= buttonY
					&& mouseX < buttonX + RECIPES_IMAGE_SIZE_WIDTH && mouseY < buttonY + RECIPES_IMAGE_SIZE_HEIGHT) {
				sprite = RECIPE_HIGHLIGHTED_SPRITE;
			} else {
				sprite = RECIPE_SPRITE;
			}
			gfx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, buttonX, buttonY - 1,
					RECIPES_IMAGE_SIZE_WIDTH, RECIPES_IMAGE_SIZE_HEIGHT);
		}
	}

	private void extractRecipes(GuiGraphicsExtractor gfx, int x, int y, int lastVisible) {
		List<WoodcutterRecipes.Entry> recipes = this.menu.getVisibleRecipes();
		for (int i = this.startIndex; i < lastVisible && i < recipes.size(); i++) {
			int visibleIndex = i - this.startIndex;
			int itemX = x + visibleIndex % RECIPES_COLUMNS * RECIPES_IMAGE_SIZE_WIDTH;
			int itemY = y + visibleIndex / RECIPES_COLUMNS * RECIPES_IMAGE_SIZE_HEIGHT + 2;
			gfx.item(recipes.get(i).output(), itemX, itemY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		this.scrolling = false;
		if (this.displayRecipes) {
			int recipeX = this.leftPos + RECIPES_X;
			int recipeY = this.topPos + RECIPES_Y;
			int lastVisible = this.startIndex + RECIPES_COLUMNS * RECIPES_ROWS;
			for (int i = this.startIndex; i < lastVisible; i++) {
				int visibleIndex = i - this.startIndex;
				double relX = event.x() - (recipeX + visibleIndex % RECIPES_COLUMNS * RECIPES_IMAGE_SIZE_WIDTH);
				double relY = event.y() - (recipeY + visibleIndex / RECIPES_COLUMNS * RECIPES_IMAGE_SIZE_HEIGHT);
				if (relX >= 0.0 && relY >= 0.0 && relX < RECIPES_IMAGE_SIZE_WIDTH && relY < RECIPES_IMAGE_SIZE_HEIGHT
						&& this.menu.clickMenuButton(this.minecraft.player, i)) {
					this.minecraft.getSoundManager().play(
							SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0f));
					this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, i);
					return true;
				}
			}
			int scrollerX = this.leftPos + 119;
			int scrollerY = this.topPos + 9;
			if (event.x() >= scrollerX && event.x() < scrollerX + SCROLLER_WIDTH
					&& event.y() >= scrollerY && event.y() < scrollerY + SCROLLER_FULL_HEIGHT) {
				this.scrolling = true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.scrolling && isScrollBarActive()) {
			int scrollTop = this.topPos + RECIPES_Y;
			int scrollBottom = scrollTop + SCROLLER_FULL_HEIGHT;
			this.scrollOffs = ((float) event.y() - scrollTop - 7.5f) / (scrollBottom - scrollTop - 15.0f);
			this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0f, 1.0f);
			this.startIndex = (int) (this.scrollOffs * getOffscreenRows() + 0.5) * RECIPES_COLUMNS;
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.scrolling = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (super.mouseScrolled(x, y, scrollX, scrollY)) {
			return true;
		}
		if (isScrollBarActive()) {
			int offscreen = getOffscreenRows();
			float step = (float) scrollY / offscreen;
			this.scrollOffs = Mth.clamp(this.scrollOffs - step, 0.0f, 1.0f);
			this.startIndex = (int) (this.scrollOffs * offscreen + 0.5) * RECIPES_COLUMNS;
		}
		return true;
	}

	private boolean isScrollBarActive() {
		return this.displayRecipes && this.menu.getNumberOfVisibleRecipes() > RECIPES_COLUMNS * RECIPES_ROWS;
	}

	private int getOffscreenRows() {
		return (this.menu.getNumberOfVisibleRecipes() + RECIPES_COLUMNS - 1) / RECIPES_COLUMNS - RECIPES_ROWS;
	}

	private void containerChanged() {
		this.displayRecipes = this.menu.hasInputItem();
		if (!this.displayRecipes) {
			this.scrollOffs = 0.0f;
			this.startIndex = 0;
		} else if (this.startIndex >= this.menu.getNumberOfVisibleRecipes()) {
			this.scrollOffs = 0.0f;
			this.startIndex = 0;
		}
	}
}
