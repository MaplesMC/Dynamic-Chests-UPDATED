package net.example.dynamicchests.block;

import net.example.dynamicchests.pocket.PocketManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The shimmering doorway in the entrance room. Shaped like a nether portal block: a pane 4/16 of a
 * block thick that can run along either horizontal axis. Walking into it returns you to your
 * Pocket-Dimension Chest.
 */
public class PocketReturnGateBlock extends Block {

	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

	private static final VoxelShape SHAPE_X = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
	private static final VoxelShape SHAPE_Z = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

	public PocketReturnGateBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AXIS) == Direction.Axis.Z ? SHAPE_Z : SHAPE_X;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
			InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (!level.isClientSide() && entity instanceof ServerPlayer player && PocketManager.isPocket(level)) {
			PocketManager.leave(player);
		}
	}
}
