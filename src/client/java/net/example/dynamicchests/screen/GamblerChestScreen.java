package net.example.dynamicchests.screen;

import net.example.dynamicchests.block.entity.GamblerChestBlockEntity;
import net.example.dynamicchests.gambler.data.GamblerPlayerData;
import net.example.dynamicchests.gambler.logic.AnimationSchedule;
import net.example.dynamicchests.gambler.logic.GambleOutcome;
import net.example.dynamicchests.gambler.logic.ValueTier;
import net.example.dynamicchests.gambler.network.GamblerNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.Random;

/**
 * Custom-drawn Gambler's Chest interface: dark wood panels with gold trim and red accents.
 * Everything here is display only - the odds, streak, history and the final result are values
 * the server sends; the "rolling" faces are cosmetic and cannot influence the outcome.
 */
public class GamblerChestScreen extends AbstractContainerScreen<GamblerChestMenu> {

	private static final int COL_BG = 0xFF1B1010;
	private static final int COL_PANEL = 0xFF2B1715;
	private static final int COL_PANEL_DARK = 0xFF140B0B;
	private static final int COL_GOLD = 0xFFE0A526;
	private static final int COL_GOLD_DARK = 0xFF8A6414;
	private static final int COL_RED = 0xFF9B1C1C;
	private static final int COL_TEXT = 0xFFEAD9B0;
	private static final int COL_DIM = 0xFF9A8666;

	private static final int[] OUTCOME_COLORS = {
			0xFFFFD84A, // jackpot
			0xFFC77DFF, // triple
			0xFF55E86B, // double
			0xFF55D8FF, // upgrade
			0xFFB0B0B0, // return
			0xFFFF5555  // loss
	};

	private Button gambleButton;
	private Button betAllButton;
	private int ticks;

	public GamblerChestScreen(GamblerChestMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, GamblerChestMenu.IMAGE_WIDTH, GamblerChestMenu.IMAGE_HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = GamblerChestMenu.INV_X;
		this.titleLabelY = 6;
		this.inventoryLabelX = GamblerChestMenu.INV_X;
		this.inventoryLabelY = GamblerChestMenu.INV_Y - 11;

		this.gambleButton = addRenderableWidget(Button.builder(
				Component.translatable("gui.dynamicchests.gambler.gamble"),
				button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, GamblerNetworking.BUTTON_GAMBLE)
		).bounds(this.leftPos + 30, this.topPos + 21, 46, 13).build());
		this.betAllButton = addRenderableWidget(Button.builder(
				Component.translatable("gui.dynamicchests.gambler.bet_all"),
				button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, GamblerNetworking.BUTTON_BET_ALL)
		).bounds(this.leftPos + 30, this.topPos + 35, 46, 13).build());
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.ticks++;
		boolean rolling = this.menu.data(GamblerChestMenu.D_PHASE) == GamblerChestMenu.PHASE_ROLLING;
		boolean ready = this.menu.data(GamblerChestMenu.D_STATUS) == GamblerChestBlockEntity.StartResult.OK.ordinal();
		this.gambleButton.active = ready && !rolling;
		this.betAllButton.active = ready && !rolling;
		this.betAllButton.setMessage(Component.translatable(rolling
				? "gui.dynamicchests.gambler.rolling" : "gui.dynamicchests.gambler.bet_all"));
		this.gambleButton.setMessage(Component.translatable(rolling
				? "gui.dynamicchests.gambler.rolling" : "gui.dynamicchests.gambler.gamble"));
	}

	// ------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------

	@Override
	public void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		boolean rolling = this.menu.data(GamblerChestMenu.D_PHASE) == GamblerChestMenu.PHASE_ROLLING;
		int result = this.menu.data(GamblerChestMenu.D_RESULT);
		boolean jackpot = result == GambleOutcome.JACKPOT.ordinal();
		boolean pulse = (this.ticks / 5) % 2 == 0;
		int trim = rolling ? (pulse ? COL_GOLD : COL_RED) : jackpot ? (pulse ? COL_GOLD : 0xFFFFF3B0) : COL_GOLD;

		int x = this.leftPos;
		int y = this.topPos;
		int w = GamblerChestMenu.IMAGE_WIDTH;
		int h = GamblerChestMenu.IMAGE_HEIGHT;

		// Main frame: gold outer trim, red inner line, dark wood body.
		gfx.fill(x, y, x + w, y + h, trim);
		gfx.fill(x + 1, y + 1, x + w - 1, y + h - 1, COL_GOLD_DARK);
		gfx.fill(x + 2, y + 2, x + w - 2, y + h - 2, COL_RED);
		gfx.fill(x + 3, y + 3, x + w - 3, y + h - 3, COL_BG);
		// Title bar.
		gfx.fill(x + 4, y + 4, x + w - 4, y + 16, COL_PANEL);
		gfx.fill(x + 4, y + 16, x + w - 4, y + 17, COL_GOLD_DARK);
		// Gold studs in the corners.
		for (int sx : new int[] {x + 6, x + w - 8}) {
			for (int sy : new int[] {y + 6, y + h - 8}) {
				gfx.fill(sx, sy, sx + 2, sy + 2, COL_GOLD);
			}
		}

		// Bet / payout labels and slot frames.
		gfx.text(this.font, Component.translatable("gui.dynamicchests.gambler.bet"), x + GamblerChestMenu.INPUT_X, y + 18, COL_DIM, false);
		gfx.text(this.font, Component.translatable("gui.dynamicchests.gambler.payout"), x + GamblerChestMenu.OUTPUT_X, y + 18, COL_DIM, false);
		for (Slot slot : this.menu.slots) {
			slotFrame(gfx, x + slot.x, y + slot.y, slot.index == 0 ? COL_GOLD : COL_GOLD_DARK);
		}

		// Result display, odds panel, history strip.
		panel(gfx, x + 8, y + 52, 160, 34, trim);
		panel(gfx, x + 8, y + 90, 160, 40, COL_GOLD_DARK);
		panel(gfx, x + 8, y + 134, 160, 14, COL_GOLD_DARK);

		super.extractContents(gfx, mouseX, mouseY, partialTick);

		// Everything below is text/overlay drawn on top of the panels.
		drawResultDisplay(gfx, x + 8, y + 52, partialTick);
		drawOdds(gfx, x + 8, y + 90);
		drawHistory(gfx, x + 8, y + 134);
	}

	private static void panel(GuiGraphicsExtractor gfx, int x, int y, int w, int h, int border) {
		gfx.fill(x, y, x + w, y + h, border);
		gfx.fill(x + 1, y + 1, x + w - 1, y + h - 1, COL_PANEL_DARK);
	}

	private static void slotFrame(GuiGraphicsExtractor gfx, int slotX, int slotY, int border) {
		gfx.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, border);
		gfx.fill(slotX, slotY, slotX + 16, slotY + 16, COL_PANEL_DARK);
	}

	private void drawResultDisplay(GuiGraphicsExtractor gfx, int x, int y, float partialTick) {
		int phase = this.menu.data(GamblerChestMenu.D_PHASE);
		int cx = x + 80;

		if (phase == GamblerChestMenu.PHASE_ROLLING) {
			int elapsed = this.menu.data(GamblerChestMenu.D_ELAPSED);
			int total = Math.max(1, this.menu.data(GamblerChestMenu.D_TOTAL));
			int step = AnimationSchedule.stepIndex(elapsed, total);
			int fake = fakeOutcome(step);
			// Reel of the six possible outcomes with the current fake one lit up.
			drawReel(gfx, x, y, fake, false);
			bigText(gfx, outcomeName(fake), cx, y + 5, OUTCOME_COLORS[fake], 1.5f, 0);
			// Progress bar.
			int barW = 150;
			int filled = Math.min(barW, barW * elapsed / total);
			gfx.fill(x + 5, y + 31, x + 5 + barW, y + 32, COL_GOLD_DARK);
			gfx.fill(x + 5, y + 31, x + 5 + filled, y + 32, COL_GOLD);
			return;
		}

		if (phase == GamblerChestMenu.PHASE_RESULT) {
			GambleOutcome outcome = GambleOutcome.byOrdinal(this.menu.data(GamblerChestMenu.D_RESULT));
			if (outcome != null) {
				drawReel(gfx, x, y, outcome.ordinal(), true);
				if (outcome == GambleOutcome.JACKPOT) {
					drawJackpot(gfx, cx, y + 11);
				} else {
					bigText(gfx, outcomeName(outcome.ordinal()), cx, y + 5, OUTCOME_COLORS[outcome.ordinal()], 1.5f, 0);
				}
				return;
			}
		}

		drawReel(gfx, x, y, -1, false);
		int status = this.menu.data(GamblerChestMenu.D_STATUS);
		GamblerChestBlockEntity.StartResult[] results = GamblerChestBlockEntity.StartResult.values();
		String key = status >= 0 && status < results.length ? results[status].name().toLowerCase() : "ok";
		Component text = Component.translatable("gui.dynamicchests.gambler.status." + key);
		int color = status == GamblerChestBlockEntity.StartResult.OK.ordinal() ? COL_GOLD : COL_DIM;
		gfx.centeredText(this.font, text, cx, y + 8, color);
	}

	private void drawReel(GuiGraphicsExtractor gfx, int x, int y, int lit, boolean solid) {
		boolean flash = (this.ticks / 3) % 2 == 0;
		for (int i = 0; i < GambleOutcome.VALUES.length; i++) {
			int cellX = x + 3 + i * 26;
			int cellY = y + 20;
			boolean on = i == lit;
			int color = OUTCOME_COLORS[i];
			gfx.fill(cellX, cellY, cellX + 24, cellY + 10, on ? (solid || flash ? color : dim(color, 0.6f)) : COL_PANEL);
			String label = Component.translatable("gui.dynamicchests.gambler.short." + GambleOutcome.VALUES[i].key()).getString();
			gfx.centeredText(this.font, label, cellX + 12, cellY + 1, on ? 0xFF140B0B : COL_DIM);
		}
	}

	/** Large flashing banner with gold sparks radiating from it. */
	private void drawJackpot(GuiGraphicsExtractor gfx, int cx, int cy) {
		boolean flash = (this.ticks / 2) % 2 == 0;
		int color = flash ? 0xFFFFD84A : 0xFFFFFFFF;
		float scale = 2.0f + (float) Math.sin(this.ticks * 0.5) * 0.15f;
		bigText(gfx, Component.translatable("gambler.dynamicchests.outcome.jackpot.banner").getString(), cx, cy - 6, color, scale, 0);
		for (int i = 0; i < 18; i++) {
			double angle = i * (Math.PI * 2 / 18) + this.ticks * 0.05;
			double radius = 18 + ((this.ticks * 2 + i * 7) % 40);
			int px = cx + (int) (Math.cos(angle) * radius * 1.8);
			int py = cy + (int) (Math.sin(angle) * radius * 0.45);
			if (py > cy - 12 && py < cy + 14) {
				gfx.fill(px, py, px + 2, py + 2, flash ? 0xFFFFD84A : 0xFFFF9B2E);
			}
		}
	}

	private void drawOdds(GuiGraphicsExtractor gfx, int x, int y) {
		int tierOrdinal = this.menu.data(GamblerChestMenu.D_TIER);
		ValueTier tier = ValueTier.byOrdinal(tierOrdinal);
		gfx.text(this.font, Component.translatable("gui.dynamicchests.gambler.odds"), x + 5, y + 3, COL_GOLD, false);
		if (tier != null) {
			Component tierText = Component.translatable("gui.dynamicchests.gambler.tier." + tier.key());
			gfx.text(this.font, tierText, x + 155 - this.font.width(tierText), y + 3, tierColor(tier), false);
		}
		for (int i = 0; i < GambleOutcome.VALUES.length; i++) {
			int col = i / 3;
			int row = i % 3;
			int tx = x + 5 + col * 78;
			int ty = y + 14 + row * 8;
			int value = this.menu.data(GamblerChestMenu.D_ODDS + i);
			gfx.fill(tx, ty + 1, tx + 3, ty + 6, OUTCOME_COLORS[i]);
			gfx.text(this.font, outcomeName(i), tx + 6, ty, COL_TEXT, false);
			String percent = value <= 0 ? "-" : (value / 10) + "." + (value % 10) + "%";
			gfx.text(this.font, percent, tx + 74 - this.font.width(percent), ty, value <= 0 ? COL_DIM : OUTCOME_COLORS[i], false);
		}
	}

	private void drawHistory(GuiGraphicsExtractor gfx, int x, int y) {
		gfx.text(this.font, Component.translatable("gui.dynamicchests.gambler.history"), x + 5, y + 3, COL_DIM, false);
		for (int i = 0; i < GamblerPlayerData.HISTORY_SIZE; i++) {
			GambleOutcome outcome = GambleOutcome.byOrdinal(this.menu.data(GamblerChestMenu.D_HISTORY + i));
			int bx = x + 30 + i * 12;
			if (outcome == null) {
				gfx.fill(bx, y + 2, bx + 10, y + 12, COL_PANEL);
			} else {
				gfx.fill(bx, y + 2, bx + 10, y + 12, OUTCOME_COLORS[outcome.ordinal()]);
				String letter = outcomeName(outcome.ordinal()).getString().substring(0, 1);
				gfx.centeredText(this.font, letter, bx + 5, y + 3, 0xFF140B0B);
			}
		}
		int streak = this.menu.data(GamblerChestMenu.D_STREAK);
		Component streakText = streak > 0
				? Component.translatable("gui.dynamicchests.gambler.streak.win", streak)
				: streak < 0 ? Component.translatable("gui.dynamicchests.gambler.streak.loss", -streak)
				: Component.translatable("gui.dynamicchests.gambler.streak.none");
		int streakColor = streak > 0 ? OUTCOME_COLORS[GambleOutcome.DOUBLE.ordinal()] : streak < 0 ? OUTCOME_COLORS[GambleOutcome.LOSS.ordinal()] : COL_DIM;
		gfx.text(this.font, streakText, x + 155 - this.font.width(streakText), y + 3, streakColor, false);
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private static int tierColor(ValueTier tier) {
		return switch (tier) {
			case COMMON -> 0xFFB0B0B0;
			case RARE -> 0xFF55AAFF;
			case VERY_RARE -> 0xFFFFD84A;
		};
	}

	private static Component outcomeName(int ordinal) {
		return Component.translatable("gambler.dynamicchests.outcome." + GambleOutcome.VALUES[ordinal].key());
	}

	private void bigText(GuiGraphicsExtractor gfx, Component text, int centerX, int y, int color, float scale, int unused) {
		bigText(gfx, text.getString(), centerX, y, color, scale, unused);
	}

	private void bigText(GuiGraphicsExtractor gfx, String text, int centerX, int y, int color, float scale, int unused) {
		gfx.pose().pushMatrix();
		gfx.pose().translate(centerX, y);
		gfx.pose().scale(scale, scale);
		gfx.text(this.font, text, -this.font.width(text) / 2, 0, color, true);
		gfx.pose().popMatrix();
	}

	/** Cosmetic only: a repeatable pseudo-random face for the given animation step. */
	private int fakeOutcome(int step) {
		int count = GambleOutcome.VALUES.length;
		int current = new Random(step * 31L + this.menu.containerId).nextInt(count);
		int previous = new Random((step - 1) * 31L + this.menu.containerId).nextInt(count);
		return current == previous ? (current + 1) % count : current;
	}

	private static int dim(int argb, float factor) {
		int r = (int) (((argb >> 16) & 0xFF) * factor);
		int g = (int) (((argb >> 8) & 0xFF) * factor);
		int b = (int) ((argb & 0xFF) * factor);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
