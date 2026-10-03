package net.example.dynamicchests.block.entity;

import net.example.dynamicchests.block.AbstractVaultChestBlock;
import net.example.dynamicchests.block.VaultChestType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Shared storage + lid-animation logic for the vault chests.
 *
 * Each half of a double chest stores its OWN half of the items (just like vanilla),
 * and {@link #getCombinedContainer()} stitches the two halves into one {@link Container}
 * for the menu to use whenever the block is paired. Subclasses only specify how many
 * slots a single half holds.
 */
public abstract class AbstractVaultChestBlockEntity extends BlockEntity implements Container {

	private NonNullList<ItemStack> items;
	protected int openCount;
	private final SoundEvent openSound;
	private final SoundEvent closeSound;

	protected AbstractVaultChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
			SoundEvent openSound, SoundEvent closeSound) {
		super(type, pos, state);
		this.items = NonNullList.withSize(this.getSingleContainerSize(), ItemStack.EMPTY);
		this.openSound = openSound;
		this.closeSound = closeSound;
	}

	/** Number of slots in ONE half of this chest (single-block inventory size). */
	public abstract int getSingleContainerSize();

	// -----------------------------------------------------------
	// Container implementation (operates on THIS half only — vanilla
	// pattern. The combined view for paired chests is built separately.)
	// -----------------------------------------------------------

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : this.items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack result = ContainerHelper.removeItem(this.items, slot, amount);
		if (!result.isEmpty()) {
			this.setChanged();
		}
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(this.items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		this.items.set(slot, stack);
		stack.limitSize(this.getMaxStackSize(stack));
		this.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		if (this.level == null || this.level.getBlockEntity(this.worldPosition) != this) {
			return false;
		}
		return player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) <= 64.0;
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}

	// -----------------------------------------------------------
	// Pairing / combined container
	// -----------------------------------------------------------

	/**
	 * If this chest is paired with a neighbor, returns a combined {@link Container} of
	 * (this half + other half), ordered so the LEFT half always comes first — matching
	 * vanilla double-chest slot ordering. If unpaired, returns {@code this}.
	 */
	public Container getCombinedContainer() {
		if (this.level == null) {
			return this;
		}
		BlockState state = this.getBlockState();
		if (!(state.getBlock() instanceof AbstractVaultChestBlock) || state.getValue(AbstractVaultChestBlock.CHEST_TYPE) == VaultChestType.SINGLE) {
			return this;
		}

		Direction facing = state.getValue(AbstractVaultChestBlock.FACING);
		VaultChestType type = state.getValue(AbstractVaultChestBlock.CHEST_TYPE);
		Direction towardsNeighbor = type == VaultChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
		BlockPos neighborPos = this.worldPosition.relative(towardsNeighbor);

		VaultChestType expectedNeighborType = (type == VaultChestType.LEFT) ? VaultChestType.RIGHT : VaultChestType.LEFT;

		if (this.level.getBlockEntity(neighborPos) instanceof AbstractVaultChestBlockEntity neighbor) {
			BlockState neighborState = neighbor.getBlockState();
			if (neighborState.getBlock() == state.getBlock()
					&& neighborState.hasProperty(AbstractVaultChestBlock.CHEST_TYPE)
					&& neighborState.getValue(AbstractVaultChestBlock.CHEST_TYPE) == expectedNeighborType
					&& neighborState.hasProperty(AbstractVaultChestBlock.FACING)
					&& neighborState.getValue(AbstractVaultChestBlock.FACING) == facing) {
				return type == VaultChestType.LEFT
						? new CompoundContainer(this, neighbor)
						: new CompoundContainer(neighbor, this);
			}
		}
		return this;
	}

	public boolean isPaired() {
		BlockState state = this.getBlockState();
		return state.hasProperty(AbstractVaultChestBlock.CHEST_TYPE)
				&& state.getValue(AbstractVaultChestBlock.CHEST_TYPE) != VaultChestType.SINGLE;
	}

	/** Total slot count of the inventory this menu should bind to right now. */
	public int getEffectiveContainerSize() {
		return this.getCombinedContainer().getContainerSize();
	}

	/**
	 * Prevents opening if a block is directly above the chest, mirroring vanilla chests.
	 */
	public boolean isBlockedFromOpening(Level level, BlockPos pos) {
		return isBlockedAbove(level, pos);
	}

	private static boolean isBlockedAbove(Level level, BlockPos pos) {
		return level.getBlockState(pos.above()).isRedstoneConductor(level, pos.above());
	}

	// -----------------------------------------------------------
	// Open/close sound + lid-animation bookkeeping.
	//
	// openCount tracks how many players currently have this chest's menu open.
	// It's mirrored to ALL nearby clients (not just the opener) via a block event
	// (level.blockEvent -> Block.triggerEvent -> BlockEntity.triggerEvent), exactly
	// like vanilla chests, so everyone sees the lid animate, not just the opener.
	//
	// animationProgress is a client-side-only 0..1 value smoothly tracking openCount,
	// advanced each tick in tickLidAnimation() and read by the renderer for the lid angle.
	//
	// These @Override the default methods declared on the Container interface itself,
	// so CompoundContainer (used when this chest is paired) correctly forwards into
	// both halves — opening either half of a double chest animates both lids.
	// -----------------------------------------------------------

	private static final float ANIMATION_STEP_PER_TICK = 0.1f; // ~5 ticks (0.25s) to fully open/close

	/** 0.0 = fully closed, 1.0 = fully open. Client-side only; not saved. */
	float animationProgress;
	float previousAnimationProgress;

	@Override
	public void startOpen(ContainerUser user) {
		if (!user.getLivingEntity().isSpectator()) {
			int oldCount = this.openCount;
			if (this.openCount < 0) {
				this.openCount = 0;
			}
			this.openCount++;
			if (oldCount <= 0 && this.openCount > 0) {
				this.playSound(this.openSound);
				if (this.level != null && !this.level.isClientSide()) {
					this.level.gameEvent(null, GameEvent.CONTAINER_OPEN, this.worldPosition);
				}
			}
			this.signalOpenCountChanged();
		}
	}

	@Override
	public void stopOpen(ContainerUser user) {
		if (!user.getLivingEntity().isSpectator()) {
			int oldCount = this.openCount;
			this.openCount--;
			if (this.openCount < 0) {
				this.openCount = 0;
			}
			if (oldCount > 0 && this.openCount <= 0) {
				this.playSound(this.closeSound);
				if (this.level != null && !this.level.isClientSide()) {
					this.level.gameEvent(null, GameEvent.CONTAINER_CLOSE, this.worldPosition);
				}
			}
			this.signalOpenCountChanged();
		}
	}

	/** Broadcasts the new openCount to every nearby client via a block event. */
	private void signalOpenCountChanged() {
		if (this.level != null && !this.level.isClientSide()) {
			this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), 1, this.openCount);
		}
	}

	/**
	 * Called from {@link AbstractVaultChestBlock#triggerEvent}. {@code type} is always 1
	 * for the "lid state changed" event; {@code data} is the new openCount.
	 */
	public boolean receiveOpenCountEvent(int type, int data) {
		if (type == 1) {
			this.openCount = data;
			return true;
		}
		return false;
	}

	@Override
	public boolean triggerEvent(int id, int type) {
		return this.receiveOpenCountEvent(id, type);
	}

	/**
	 * Advances the lid animation by one tick. Safe to call on both sides; only meaningful
	 * client-side, since that's the only place {@link #getAnimationProgress} is read.
	 */
	protected float getAnimationTarget() {
		return this.openCount > 0 ? 1.0f : 0.0f;
	}

	public void tickLidAnimation() {
		this.previousAnimationProgress = this.animationProgress;
		float target = getAnimationTarget();
		if (this.animationProgress < target) {
			this.animationProgress = Math.min(1.0f, this.animationProgress + ANIMATION_STEP_PER_TICK);
		} else if (this.animationProgress > target) {
			this.animationProgress = Math.max(0.0f, this.animationProgress - ANIMATION_STEP_PER_TICK);
		}

	}

	private AbstractVaultChestBlockEntity getNeighborIfPaired() {
		BlockState state = this.getBlockState();
		if (!(state.getBlock() instanceof AbstractVaultChestBlock)) return null;
		if (!state.hasProperty(AbstractVaultChestBlock.CHEST_TYPE)) return null;
		VaultChestType type = state.getValue(AbstractVaultChestBlock.CHEST_TYPE);
		if (type == VaultChestType.SINGLE) return null;
		if (!state.hasProperty(AbstractVaultChestBlock.FACING)) return null;
		Direction facing = state.getValue(AbstractVaultChestBlock.FACING);
		Direction towards = type == VaultChestType.LEFT
				? facing.getClockWise() : facing.getCounterClockWise();
		if (this.level.getBlockEntity(this.worldPosition.relative(towards))
				instanceof AbstractVaultChestBlockEntity neighbor) {
			return neighbor;
		}
		return null;
	}

	/** Interpolated 0..1 lid-open progress for smooth rendering between ticks. */
	public float getAnimationProgress(float partialTick) {
		return this.previousAnimationProgress + (this.animationProgress - this.previousAnimationProgress) * partialTick;
	}

	/**
	 * For double chests, returns the higher of this half's and the partner's animation
	 * progress so both halves always show the same state — including when a chest is
	 * placed next to one that is mid-animation.
	 */
	public float getEffectiveAnimationProgress(float partialTick) {
		float own = this.getAnimationProgress(partialTick);
		AbstractVaultChestBlockEntity neighbor = getNeighborIfPaired();
		return neighbor != null ? Math.max(own, neighbor.getAnimationProgress(partialTick)) : own;
	}

	public void playSound(SoundEvent sound) {
		if (this.level == null || this.level.isClientSide()) {
			return;
		}
		BlockState state = this.getBlockState();
		double x = this.worldPosition.getX() + 0.5;
		double y = this.worldPosition.getY() + 0.5;
		double z = this.worldPosition.getZ() + 0.5;
		this.level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.5f,
				this.level.getRandom().nextFloat() * 0.1f + 0.9f);
	}

	public SoundEvent getOpenSound() {
		return this.openSound;
	}

	public SoundEvent getCloseSound() {
		return this.closeSound;
	}

	public int getOpenCount() {
		return this.openCount;
	}

	// -----------------------------------------------------------
	// Drops on break
	// -----------------------------------------------------------

	public void dropContents(Level level, BlockPos pos) {
		if (!level.isClientSide()) {
			net.minecraft.world.Containers.dropContents(level, pos, this);
		}
	}

	// -----------------------------------------------------------
	// Save / load
	// -----------------------------------------------------------

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(this.getSingleContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
	}
}
