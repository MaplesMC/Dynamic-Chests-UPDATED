package net.example.dynamicchests.block;

import com.mojang.serialization.MapCodec;
import net.example.dynamicchests.block.entity.PocketChestBlockEntity;
import net.example.dynamicchests.pocket.PocketData;
import net.example.dynamicchests.pocket.PocketManager;
import net.example.dynamicchests.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Pocket-Dimension Chest. Right-click to step into the pocket of the player who placed it
 * (crouch + right-click your own chest with empty hands to make your pocket private or public)
 * (your own, or a friend's who has trusted you). Always a single block.
 */
public class PocketChestBlock extends AbstractVaultChestBlock {

	public PocketChestBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return simpleCodec(PocketChestBlock::new);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState()
				.setValue(FACING, context.getHorizontalDirection().getOpposite())
				.setValue(CHEST_TYPE, VaultChestType.SINGLE);
	}

	/** Whoever places the chest owns it: it leads to that player's pocket. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player
				&& level.getBlockEntity(pos) instanceof PocketChestBlockEntity chest) {
			chest.setOwner(player.getUUID(), player.getName().getString());
			PocketData data = PocketData.get(serverLevel.getServer());
			PocketManager.remember(data.plotFor(player.getUUID()), level, pos);
			data.markChanged();
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof PocketChestBlockEntity chest) {
			// Crouch + right-click (empty hands) on your own chest switches your pocket between private and public.
			if (serverPlayer.isShiftKeyDown() && serverPlayer.getUUID().equals(chest.getOwnerId())) {
				boolean nowPrivate = PocketData.get(serverPlayer.level().getServer()).togglePrivacy(serverPlayer.getUUID());
				serverPlayer.sendSystemMessage(Component.translatable(nowPrivate
						? "pocket.dynamicchests.privacy.private" : "pocket.dynamicchests.privacy.public"), true);
				return InteractionResult.SUCCESS_SERVER;
			}
			PocketManager.enter(serverPlayer, chest);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PocketChestBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return null;
	}
}
