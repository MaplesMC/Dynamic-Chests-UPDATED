package net.example.dynamicchests.block.entity;

import net.example.dynamicchests.DynamicChests;
import net.example.dynamicchests.gambler.config.GamblerConfig;
import net.example.dynamicchests.gambler.data.GamblerPlayerData;
import net.example.dynamicchests.gambler.effects.GamblerAdvancements;
import net.example.dynamicchests.gambler.effects.GamblerParticles;
import net.example.dynamicchests.gambler.effects.GamblerSounds;
import net.example.dynamicchests.gambler.logic.AnimationSchedule;
import net.example.dynamicchests.gambler.logic.GambleOutcome;
import net.example.dynamicchests.gambler.logic.GambleResolver;
import net.example.dynamicchests.gambler.logic.ItemRules;
import net.example.dynamicchests.gambler.logic.OutcomeRoller;
import net.example.dynamicchests.gambler.logic.PendingGamble;
import net.example.dynamicchests.gambler.logic.ValueTier;
import net.example.dynamicchests.registry.ModRegistry;
import net.example.dynamicchests.screen.GamblerChestMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Block entity of the Gambler's Chest. It owns the gamble state machine:
 * <ul>
 *   <li>one slot for the bet, {@value #OUTPUT_SLOTS} read-only slots for the payout;</li>
 *   <li>exactly one player may have it open at a time ({@link #tryOccupy});</li>
 *   <li>the outcome is rolled on the server when the gamble starts and stored in a
 *       {@link PendingGamble} that is saved with the chest, so nothing is lost on a crash;</li>
 *   <li>the animation is a plain tick countdown - the result is only handed over at the end.</li>
 * </ul>
 */
public class GamblerChestBlockEntity extends AbstractVaultChestBlockEntity implements ExtendedMenuProvider<Integer> {

	public static final int INPUT_SLOT = 0;
	public static final int OUTPUT_START = 1;
	public static final int OUTPUT_SLOTS = GambleResolver.MAX_PAYOUT_STACKS;
	public static final int CONTAINER_SIZE = OUTPUT_START + OUTPUT_SLOTS;

	/** Visual states, synced to clients through a block event. */
	public static final int VISUAL_IDLE = 0;
	public static final int VISUAL_GAMBLING = 1;
	public static final int VISUAL_JACKPOT = 2;

	private static final int BLOCK_EVENT_VISUAL = 2;

	/** Result of asking the chest to start a gamble. Ordinals are synced to the GUI, only append. */
	public enum StartResult {
		OK,
		NO_ITEM,
		BLACKLISTED,
		CONTAINER,
		OUTPUT_FULL,
		BUSY,
		NOT_OCCUPANT
	}

	@Nullable
	private PendingGamble pending;
	@Nullable
	private GambleOutcome lastResult;
	private int resultShowTicks;
	/** True from a jackpot until the next gamble starts; keeps the jackpot texture on the chest. */
	private boolean jackpotActive;
	/** Set by the RNG Override Chip: the next gamble is a guaranteed jackpot. */
	private boolean guaranteedJackpot;

	/** Player that currently has the GUI open. Not saved. */
	@Nullable
	private UUID occupant;

	private int visualState = VISUAL_IDLE;
	private int syncedVisualState = -1;

	public GamblerChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModRegistry.GAMBLER_CHEST_BLOCK_ENTITY, pos, state, GamblerSounds.OPEN, SoundEvents.CHEST_CLOSE);
	}

	@Override
	public int getSingleContainerSize() {
		return CONTAINER_SIZE;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.dynamicchests.gambler_chest");
	}

	@Override
	public Integer getScreenOpeningData(ServerPlayer player) {
		return 0;
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
		return new GamblerChestMenu(syncId, playerInventory, this);
	}

	// ------------------------------------------------------------------
	// State accessors (used by the menu and renderer)
	// ------------------------------------------------------------------

	public boolean isGambling() {
		return this.pending != null;
	}

	@Nullable
	public PendingGamble getPending() {
		return this.pending;
	}

	@Nullable
	public GambleOutcome getLastResult() {
		return this.lastResult;
	}


	/** Primes the chest. Returns false if it was already primed. */
	public boolean primeGuaranteedJackpot() {
		if (this.guaranteedJackpot) {
			return false;
		}
		this.guaranteedJackpot = true;
		setChanged();
		return true;
	}

	public int getVisualState() {
		return this.visualState;
	}


	// ------------------------------------------------------------------
	// Exclusive use
	// ------------------------------------------------------------------

	/** Claims the chest for a player. Fails if another player still has it open. */
	public boolean tryOccupy(ServerPlayer player) {
		if (this.occupant != null && !this.occupant.equals(player.getUUID()) && isOccupantStillUsing()) {
			return false;
		}
		this.occupant = player.getUUID();
		return true;
	}

	public void release(UUID player) {
		if (player.equals(this.occupant)) {
			this.occupant = null;
		}
	}

	public boolean isOccupiedBy(Player player) {
		return this.occupant != null && this.occupant.equals(player.getUUID());
	}

	/** Guards against an occupant that vanished without closing (disconnect, crash of the menu, teleport). */
	private boolean isOccupantStillUsing() {
		if (!(this.level instanceof ServerLevel serverLevel) || this.occupant == null) {
			return false;
		}
		ServerPlayer holder = serverLevel.getServer().getPlayerList().getPlayer(this.occupant);
		return holder != null
				&& !holder.isRemoved()
				&& holder.containerMenu instanceof GamblerChestMenu menu
				&& menu.isBoundTo(this);
	}

	@Override
	public boolean stillValid(Player player) {
		return super.stillValid(player) && isOccupiedBy(player);
	}

	// ------------------------------------------------------------------
	// Starting a gamble
	// ------------------------------------------------------------------

	public StartResult check(Player player) {
		if (!(this.level instanceof ServerLevel)) {
			return StartResult.BUSY;
		}
		if (this.pending != null) {
			return StartResult.BUSY;
		}
		if (!isOccupiedBy(player)) {
			return StartResult.NOT_OCCUPANT;
		}
		ItemStack input = getItem(INPUT_SLOT);
		if (input.isEmpty()) {
			return StartResult.NO_ITEM;
		}
		switch (ItemRules.check(input)) {
			case BLACKLISTED -> {
				return StartResult.BLACKLISTED;
			}
			case CONTAINER -> {
				return StartResult.CONTAINER;
			}
			default -> {
			}
		}
		for (int slot = OUTPUT_START; slot < CONTAINER_SIZE; slot++) {
			if (!getItem(slot).isEmpty()) {
				return StartResult.OUTPUT_FULL;
			}
		}
		return StartResult.OK;
	}

	/**
	 * Server-authoritative gamble start. The bet is removed from the input slot and the outcome is
	 * rolled and stored in the same tick, so there is no moment where the item exists nowhere.
	 */
	public StartResult startGamble(ServerPlayer player, boolean betAll) {
		StartResult result = check(player);
		if (result != StartResult.OK || !(this.level instanceof ServerLevel serverLevel)) {
			return result;
		}

		ItemStack bet = removeItem(INPUT_SLOT, betAll ? getItem(INPUT_SLOT).getCount() : 1);
		if (bet.isEmpty()) {
			return StartResult.NO_ITEM;
		}

		// Risk tier follows the item itself; luck scaling and stats use the value of the whole stake.
		double unitValue = ItemRules.valueOf(bet);
		double value = unitValue * bet.getCount();
		ValueTier tier = ItemRules.tierOf(unitValue);
		GamblerPlayerData.Stats stats = GamblerPlayerData.get(serverLevel.getServer()).stats(player.getUUID());
		double[] odds = OutcomeRoller.odds(tier, stats.effectiveLuck(value), stats.effectiveRecovery(value));
		GambleOutcome rolled = this.guaranteedJackpot ? GambleOutcome.JACKPOT : OutcomeRoller.roll(odds, serverLevel.getRandom());
		this.guaranteedJackpot = false;
		GambleResolver.Resolution resolution = GambleResolver.resolve(serverLevel, this.worldPosition, bet, rolled, tier, serverLevel.getRandom());

		this.pending = new PendingGamble(bet, resolution.outcome(), tier, value, resolution.payout(),
				GamblerConfig.get().animationTicks(), 0, player.getUUID());
		this.lastResult = null;
		this.resultShowTicks = 0;
		this.jackpotActive = false;
		setChanged();

		GamblerSounds.play(serverLevel, this.worldPosition, "start", 1.0f, 1.0f);
		return StartResult.OK;
	}

	// ------------------------------------------------------------------
	// Ticking
	// ------------------------------------------------------------------

	public void serverTick(ServerLevel serverLevel) {
		PendingGamble gamble = this.pending;
		if (gamble != null) {
			gamble.elapsedTicks++;
			if (AnimationSchedule.isStepTick(gamble.elapsedTicks, gamble.totalTicks)) {
				float progress = gamble.elapsedTicks / (float) gamble.totalTicks;
				GamblerSounds.play(serverLevel, this.worldPosition, "roll", 0.7f, 0.8f + progress * 0.6f);
			}
			if (gamble.elapsedTicks % 4 == 0) {
				GamblerParticles.rolling(serverLevel, this.worldPosition);
			}
			if (gamble.elapsedTicks >= gamble.totalTicks) {
				finishGamble(serverLevel);
			}
			// Elapsed time is not worth a disk write every tick; it only matters that the pending
			// record itself was saved, and a resumed animation may restart from its saved tick.
		} else if (this.resultShowTicks > 0 && --this.resultShowTicks == 0) {
			this.lastResult = null;
		}

		int desired = this.pending != null ? VISUAL_GAMBLING : this.jackpotActive ? VISUAL_JACKPOT : VISUAL_IDLE;
		this.visualState = desired;
		if (desired != this.syncedVisualState) {
			this.syncedVisualState = desired;
			serverLevel.blockEvent(this.worldPosition, getBlockState().getBlock(), BLOCK_EVENT_VISUAL, desired);
		}
	}

	/** Pays out the pending gamble right now (animation or not) and records the result. */
	private void finishGamble(ServerLevel serverLevel) {
		PendingGamble gamble = this.pending;
		if (gamble == null) {
			return;
		}
		this.pending = null;

		for (ItemStack stack : gamble.payout) {
			deliver(serverLevel, stack.copy());
		}

		this.lastResult = gamble.outcome;
		this.resultShowTicks = GamblerConfig.get().gamble.resultDisplayTicks;
		if (gamble.outcome == GambleOutcome.JACKPOT) {
			this.jackpotActive = true;
		}

		String soundKey = switch (gamble.outcome) {
			case JACKPOT -> "jackpot";
			case TRIPLE, DOUBLE, UPGRADE -> "win";
			case LOSS -> "loss";
			case RETURN -> "win";
		};
		GamblerSounds.play(serverLevel, this.worldPosition, soundKey, gamble.outcome == GambleOutcome.JACKPOT ? 1.4f : 1.0f,
				gamble.outcome == GambleOutcome.RETURN ? 0.8f : 1.0f);
		GamblerParticles.outcome(serverLevel, this.worldPosition, gamble.outcome);

		if (!new UUID(0, 0).equals(gamble.player)) {
			GamblerPlayerData.Stats stats = GamblerPlayerData.get(serverLevel.getServer())
					.record(gamble.player, gamble.outcome, gamble.betValue);
			ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(gamble.player);
			if (player != null) {
				GamblerAdvancements.onGambleFinished(player, gamble.outcome, stats);
			}
		}
		setChanged();
	}

	/** Puts a stack into the result slots; anything that does not fit is dropped next to the chest. */
	private void deliver(ServerLevel serverLevel, ItemStack stack) {
		for (int slot = OUTPUT_START; slot < CONTAINER_SIZE && !stack.isEmpty(); slot++) {
			ItemStack existing = getItem(slot);
			if (existing.isEmpty()) {
				setItem(slot, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
			} else if (ItemStack.isSameItemSameComponents(existing, stack)) {
				int room = existing.getMaxStackSize() - existing.getCount();
				if (room > 0) {
					int moved = Math.min(room, stack.getCount());
					existing.grow(moved);
					stack.shrink(moved);
					setChanged();
				}
			}
		}
		if (!stack.isEmpty()) {
			Block.popResource(serverLevel, this.worldPosition.above(), stack);
		}
	}

	// ------------------------------------------------------------------
	// Automation / hopper rules
	// ------------------------------------------------------------------

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return GamblerConfig.get().restrictions.allowAutomation
				&& slot == INPUT_SLOT
				&& this.pending == null
				&& ItemRules.isAllowed(stack);
	}

	@Override
	public boolean canTakeItem(Container target, int slot, ItemStack stack) {
		return GamblerConfig.get().restrictions.allowAutomation
				&& slot >= OUTPUT_START
				&& this.pending == null;
	}

	// ------------------------------------------------------------------
	// Removal: never swallow a pending bet or leave contents behind
	// ------------------------------------------------------------------

	@Override
	public void dropContents(Level level, BlockPos pos) {
		if (!level.isClientSide()) {
			if (this.pending != null && level instanceof ServerLevel serverLevel) {
				finishGamble(serverLevel);
			}
			super.dropContents(level, pos);
			// Clearing makes this safe to call from both the break handler and preRemoveSideEffects.
			clearContent();
			setChanged();
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level != null) {
			dropContents(this.level, pos);
		}
		super.preRemoveSideEffects(pos, state);
	}

	// ------------------------------------------------------------------
	// Block events (client visual state)
	// ------------------------------------------------------------------

	@Override
	public boolean triggerEvent(int id, int type) {
		if (id == BLOCK_EVENT_VISUAL) {
			this.visualState = type;
			return true;
		}
		return super.triggerEvent(id, type);
	}

	// ------------------------------------------------------------------
	// Save / load
	// ------------------------------------------------------------------

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (this.pending != null) {
			this.pending.save(output.child("pending"));
		}
		output.putInt("last_result", this.lastResult == null ? -1 : this.lastResult.ordinal());
		output.putInt("result_show_ticks", this.resultShowTicks);
		output.putBoolean("jackpot_active", this.jackpotActive);
		output.putBoolean("guaranteed_jackpot", this.guaranteedJackpot);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		Optional<ValueInput> saved = input.child("pending");
		this.pending = saved.flatMap(PendingGamble::load).orElse(null);
		if (saved.isPresent() && this.pending == null) {
			DynamicChests.LOGGER.error("Gambler's Chest at {} had an unreadable pending gamble", this.worldPosition);
		}
		this.lastResult = GambleOutcome.byOrdinal(input.getIntOr("last_result", -1));
		this.resultShowTicks = input.getIntOr("result_show_ticks", 0);
		this.jackpotActive = input.getBooleanOr("jackpot_active", false);
		this.guaranteedJackpot = input.getBooleanOr("guaranteed_jackpot", false);
		this.occupant = null;
		this.syncedVisualState = -1;
	}
}
