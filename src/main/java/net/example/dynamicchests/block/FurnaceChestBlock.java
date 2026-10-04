package net.example.dynamicchests.block;

import com.mojang.serialization.MapCodec;
import net.example.dynamicchests.block.entity.FurnaceChestBlockEntity;
import net.example.dynamicchests.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** The Furnace Chest: a single-block chest that smelts four stacks at once. */
public class FurnaceChestBlock extends AbstractVaultChestBlock {

	public FurnaceChestBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return simpleCodec(FurnaceChestBlock::new);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState()
				.setValue(FACING, context.getHorizontalDirection().getOpposite())
				.setValue(CHEST_TYPE, VaultChestType.SINGLE);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FurnaceChestBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type != ModRegistry.FURNACE_CHEST_BLOCK_ENTITY) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			FurnaceChestBlockEntity chest = (FurnaceChestBlockEntity) be;
			chest.tickLidAnimation();
			if (lvl instanceof ServerLevel serverLevel) {
				chest.serverTick(serverLevel);
			}
		};
	}
}
