package net.example.dynamicchests.woodcutter;

import com.mojang.serialization.MapCodec;
import net.example.dynamicchests.block.AbstractVaultChestBlock;
import net.example.dynamicchests.block.VaultChestType;
import net.example.dynamicchests.registry.ModRegistry;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Woodcutter: a chest (same shape, lid and renderer family as the other Dynamic Chests) with a saw hidden
 * inside. Right-click to open its Stonecutter-style menu; the lid lifts and reveals the saw. Always a single block.
 */
public class WoodcutterBlock extends AbstractVaultChestBlock {

	public WoodcutterBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return simpleCodec(WoodcutterBlock::new);
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
		if (player instanceof ServerPlayer serverPlayer) {
			if (level.getBlockEntity(pos) instanceof WoodcutterBlockEntity chest && chest.isBlockedFromOpening(level, pos)) {
				return InteractionResult.FAIL;
			}
			serverPlayer.openMenu(new ExtendedMenuProvider<Integer>() {
				@Override
				public Integer getScreenOpeningData(ServerPlayer opener) {
					return 0;
				}

				@Override
				public Component getDisplayName() {
					return Component.translatable("container.dynamicchests.wood_cutter");
				}

				@Override
				public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player menuPlayer) {
					return new WoodcutterMenu(syncId, inventory, ContainerLevelAccess.create(level, pos));
				}
			});
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WoodcutterBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type != ModRegistry.WOODCUTTER_BLOCK_ENTITY) {
			return null;
		}
		return (lvl, pos, st, be) -> ((WoodcutterBlockEntity) be).tickLidAnimation();
	}
}
