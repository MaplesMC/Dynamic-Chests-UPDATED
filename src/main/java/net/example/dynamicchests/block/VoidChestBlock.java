package net.example.dynamicchests.block;

import com.mojang.serialization.MapCodec;
import net.example.dynamicchests.block.entity.VoidChestBlockEntity;
import net.example.dynamicchests.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class VoidChestBlock extends AbstractVaultChestBlock {

    public VoidChestBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(VoidChestBlock::new);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Void chests never pair into a double chest.
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(CHEST_TYPE, VaultChestType.SINGLE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VoidChestBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModRegistry.VOID_CHEST_BLOCK_ENTITY) return null;
        if (level.isClientSide()) {
            return (lvl, pos, st, be) -> {
                VoidChestBlockEntity vbe = (VoidChestBlockEntity) be;
                vbe.tickLidAnimation();
                vbe.tickParticles();
            };
        } else {
            return (lvl, pos, st, be) -> ((VoidChestBlockEntity) be).tickDestructionTimer();
        }
    }
}
