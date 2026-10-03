package net.example.dynamicchests.block.entity;

import net.example.dynamicchests.registry.ModRegistry;
import net.example.dynamicchests.screen.ShadowChestMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

public class ShadowChestBlockEntity extends AbstractVaultChestBlockEntity implements ExtendedMenuProvider<Integer> {

    public static final int COLUMNS     = 9;
    public static final int ROWS_SINGLE = 3;
    public static final int SINGLE_SIZE = COLUMNS * ROWS_SINGLE;

    public ShadowChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SHADOW_CHEST_BLOCK_ENTITY, pos, state,
                SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE);
    }

    @Override
    public int getSingleContainerSize() {
        return SINGLE_SIZE;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.dynamicchests.shadow_chest");
    }

    @Override
    public Integer getScreenOpeningData(ServerPlayer player) {
        return this.getCombinedContainer().getContainerSize() / COLUMNS;
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        Container combined = this.getCombinedContainer();
        int rows = combined.getContainerSize() / COLUMNS;
        return new ShadowChestMenu(syncId, playerInventory, combined, rows);
    }
}
