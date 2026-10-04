package net.example.dynamicchests.sahur;

import com.mojang.serialization.MapCodec;
import net.example.dynamicchests.block.AbstractVaultChestBlock;
import net.example.dynamicchests.block.VaultChestType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A log-shaped storage chest that drums when opened. */
public class SahurChestBlock extends AbstractVaultChestBlock {

	public SahurChestBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return simpleCodec(SahurChestBlock::new);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState()
				.setValue(FACING, context.getHorizontalDirection().getOpposite())
				.setValue(CHEST_TYPE, VaultChestType.SINGLE);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof SahurChestBlockEntity chest && player instanceof ServerPlayer serverPlayer) {
			if (chest.isBlockedFromOpening(level, pos)) {
				return InteractionResult.FAIL;
			}
			chest.startDrumming();
			serverPlayer.openMenu(chest);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SahurChestBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type != SahurChest.BLOCK_ENTITY) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			SahurChestBlockEntity chest = (SahurChestBlockEntity) be;
			chest.tickLidAnimation();
			if (lvl instanceof ServerLevel serverLevel) {
				chest.serverTick(serverLevel);
			}
		};
	}
}
