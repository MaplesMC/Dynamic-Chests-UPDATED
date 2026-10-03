package net.example.dynamicchests.block;

import com.mojang.serialization.MapCodec;
import net.example.dynamicchests.block.entity.GamblerChestBlockEntity;
import net.example.dynamicchests.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

/**
 * The Gambler's Chest. Always a single block (it never pairs into a double chest) and only one
 * player at a time may use it.
 */
public class GamblerChestBlock extends AbstractVaultChestBlock {

	public GamblerChestBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return simpleCodec(GamblerChestBlock::new);
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
		if (!(level.getBlockEntity(pos) instanceof GamblerChestBlockEntity chest) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		if (chest.isBlockedFromOpening(level, pos)) {
			return InteractionResult.FAIL;
		}
		if (!chest.tryOccupy(serverPlayer)) {
			serverPlayer.sendSystemMessage(Component.translatable("gambler.dynamicchests.in_use"), true);
			return InteractionResult.FAIL;
		}
		if (serverPlayer.openMenu(chest).isEmpty()) {
			chest.release(serverPlayer.getUUID());
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(ModRegistry.RNG_OVERRIDE_CHIP)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof GamblerChestBlockEntity chest && player instanceof ServerPlayer serverPlayer) {
			if (chest.primeGuaranteedJackpot()) {
				if (!player.hasInfiniteMaterials()) {
					stack.shrink(1);
				}
				serverPlayer.sendSystemMessage(Component.translatable("gambler.dynamicchests.chip.primed"), true);
				level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 1.5f);
				((net.minecraft.server.level.ServerLevel) level).sendParticles(ParticleTypes.ELECTRIC_SPARK,
						pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.4, 0.3, 0.4, 0.2);
			} else {
				serverPlayer.sendSystemMessage(Component.translatable("gambler.dynamicchests.chip.already_primed"), true);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GamblerChestBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type != ModRegistry.GAMBLER_CHEST_BLOCK_ENTITY) return null;
		if (level.isClientSide()) {
			return (lvl, pos, st, be) -> ((GamblerChestBlockEntity) be).tickLidAnimation();
		}
		return (lvl, pos, st, be) -> {
			GamblerChestBlockEntity chest = (GamblerChestBlockEntity) be;
			chest.tickLidAnimation();
			if (lvl instanceof net.minecraft.server.level.ServerLevel serverLevel) {
				chest.serverTick(serverLevel);
			}
		};
	}
}
