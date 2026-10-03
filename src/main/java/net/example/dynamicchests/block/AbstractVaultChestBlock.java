package net.example.dynamicchests.block;

import net.example.dynamicchests.block.entity.AbstractVaultChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Shared behaviour for the custom vault chests: placement-facing, left/right pairing
 * with an adjacent chest of the SAME type, and the open/close interaction.
 *
 * The connection model intentionally mirrors vanilla {@code ChestBlock}: a placed chest
 * looks for a same-type, same-facing, currently-SINGLE neighbor immediately to its left
 * or right (relative to facing) and pairs with it, becoming LEFT or RIGHT. Breaking either
 * half reverts the other back to SINGLE.
 */
public abstract class AbstractVaultChestBlock extends BaseEntityBlock {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<VaultChestType> CHEST_TYPE = EnumProperty.create("type", VaultChestType.class);

	private static final VoxelShape SHAPE_SINGLE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);
	// Each directional shape extends flush to the block edge on the side that joins the other half.
	private static final VoxelShape SHAPE_NORTH = Block.box(1.0, 0.0, 0.0, 15.0, 14.0, 15.0);
	private static final VoxelShape SHAPE_SOUTH = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 16.0);
	private static final VoxelShape SHAPE_WEST  = Block.box(0.0, 0.0, 1.0, 15.0, 14.0, 15.0);
	private static final VoxelShape SHAPE_EAST  = Block.box(1.0, 0.0, 1.0, 16.0, 14.0, 15.0);

	protected AbstractVaultChestBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any()
				.setValue(FACING, Direction.SOUTH)
				.setValue(CHEST_TYPE, VaultChestType.SINGLE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, CHEST_TYPE);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VaultChestType type = state.getValue(CHEST_TYPE);
		if (type == VaultChestType.SINGLE) return SHAPE_SINGLE;
		// LEFT half connects to the clockwise neighbor; RIGHT to the counter-clockwise.
		Direction joining = type == VaultChestType.LEFT
				? state.getValue(FACING).getClockWise()
				: state.getValue(FACING).getCounterClockWise();
		return switch (joining) {
			case NORTH -> SHAPE_NORTH;
			case SOUTH -> SHAPE_SOUTH;
			case WEST  -> SHAPE_WEST;
			case EAST  -> SHAPE_EAST;
			default    -> SHAPE_SINGLE;
		};
	}

	// -----------------------------------------------------------
	// Placement: pick facing from the player, and pair with an
	// adjacent same-type chest if one is right beside the click.
	// -----------------------------------------------------------

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection().getOpposite();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		VaultChestType type = VaultChestType.SINGLE;

		Direction clockwise = facing.getClockWise();
		Direction counterClockwise = facing.getCounterClockWise();

		BlockState clockwiseNeighbor = level.getBlockState(pos.relative(clockwise));
		BlockState counterClockwiseNeighbor = level.getBlockState(pos.relative(counterClockwise));

		if (clockwiseNeighbor.is(this)
				&& clockwiseNeighbor.getValue(CHEST_TYPE) == VaultChestType.SINGLE
				&& clockwiseNeighbor.getValue(FACING) == facing) {
			type = VaultChestType.LEFT;
		} else if (counterClockwiseNeighbor.is(this)
				&& counterClockwiseNeighbor.getValue(CHEST_TYPE) == VaultChestType.SINGLE
				&& counterClockwiseNeighbor.getValue(FACING) == facing) {
			type = VaultChestType.RIGHT;
		}

		return this.defaultBlockState().setValue(FACING, facing).setValue(CHEST_TYPE, type);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		if (state.getValue(CHEST_TYPE) != VaultChestType.SINGLE) {
			Direction facing = state.getValue(FACING);
			Direction towardsNeighbor = state.getValue(CHEST_TYPE) == VaultChestType.LEFT
					? facing.getClockWise()
					: facing.getCounterClockWise();
			BlockPos neighborPos = pos.relative(towardsNeighbor);
			BlockState neighborState = level.getBlockState(neighborPos);
			if (neighborState.is(this) && neighborState.getValue(CHEST_TYPE) == VaultChestType.SINGLE) {
				VaultChestType neighborType = state.getValue(CHEST_TYPE) == VaultChestType.LEFT
						? VaultChestType.RIGHT
						: VaultChestType.LEFT;
				level.setBlock(neighborPos, neighborState.setValue(CHEST_TYPE, neighborType).setValue(FACING, facing), Block.UPDATE_ALL);
			}
		}
	}

	// -----------------------------------------------------------
	// Break: drop contents, then un-pair the partner chest (if any).
	// -----------------------------------------------------------

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level.getBlockEntity(pos) instanceof AbstractVaultChestBlockEntity blockEntity) {
			blockEntity.dropContents(level, pos);
		}
		if (state.getValue(CHEST_TYPE) != VaultChestType.SINGLE) {
			Direction facing = state.getValue(FACING);
			Direction towardsNeighbor = state.getValue(CHEST_TYPE) == VaultChestType.LEFT
					? facing.getClockWise()
					: facing.getCounterClockWise();
			BlockPos neighborPos = pos.relative(towardsNeighbor);
			BlockState neighborState = level.getBlockState(neighborPos);
			if (neighborState.is(this) && neighborState.getValue(CHEST_TYPE) != VaultChestType.SINGLE) {
				level.setBlock(neighborPos, neighborState.setValue(CHEST_TYPE, VaultChestType.SINGLE), Block.UPDATE_ALL);
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	// -----------------------------------------------------------
	// Interaction
	// -----------------------------------------------------------

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof AbstractVaultChestBlockEntity vaultEntity) {
			if (!level.isClientSide() && vaultEntity.isBlockedFromOpening(level, pos)) {
				return InteractionResult.FAIL;
			}
			if (be instanceof MenuProvider menuProvider) {
				player.openMenu(menuProvider);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Nullable
	@Override
	public abstract <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type);

	@Nullable
	@Override
	public abstract BlockEntity newBlockEntity(BlockPos pos, BlockState state);
}
