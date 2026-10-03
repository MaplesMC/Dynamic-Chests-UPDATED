package net.example.dynamicchests.block.entity;

import net.example.dynamicchests.registry.ModRegistry;
import net.example.dynamicchests.screen.SoulChestMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class SoulChestBlockEntity extends AbstractVaultChestBlockEntity implements ExtendedMenuProvider<Integer> {

    // 36 main/hotbar + 4 armor + 1 offhand = 41 total, mirroring the player's full inventory.
    public static final int CONTAINER_SIZE = 41;

    private boolean hasSoulItems = false;

    public SoulChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SOUL_CHEST_BLOCK_ENTITY, pos, state,
                SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE);
    }

    @Override
    public int getSingleContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.dynamicchests.soul_chest");
    }

    @Override
    public Integer getScreenOpeningData(ServerPlayer player) {
        return 0; // fixed layout — row count not needed
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new SoulChestMenu(syncId, playerInventory, this);
    }

    /**
     * Called from the ALLOW_DEATH event before the player's items drop.
     * Maps player inventory slots 1-to-1 into the chest:
     *   chest 0-35  ← player main + hotbar (Inventory.INVENTORY_SIZE = 36)
     *   chest 36-39 ← player armor (INVENTORY_SIZE to SLOT_OFFHAND-1)
     *   chest 40    ← player offhand (SLOT_OFFHAND = 40)
     */
    public void capturePlayerInventory(Player player) {
        Inventory inv = player.getInventory();

        // Main inventory + hotbar (slots 0-35)
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                setItem(i, stack.copy());
                inv.setItem(i, ItemStack.EMPTY);
                hasSoulItems = true;
            }
        }

        // Armor slots (inv 36-39 → chest 36-39)
        for (int i = Inventory.INVENTORY_SIZE; i < Inventory.SLOT_OFFHAND; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                setItem(i, stack.copy());
                inv.setItem(i, ItemStack.EMPTY);
                hasSoulItems = true;
            }
        }

        // Offhand (inv 40 → chest 40)
        ItemStack offhand = inv.getItem(Inventory.SLOT_OFFHAND);
        if (!offhand.isEmpty()) {
            setItem(Inventory.SLOT_OFFHAND, offhand.copy());
            inv.setItem(Inventory.SLOT_OFFHAND, ItemStack.EMPTY);
            hasSoulItems = true;
        }

        setChanged();
    }

    @Override
    public void stopOpen(ContainerUser user) {
        super.stopOpen(user);
        if (hasSoulItems && openCount == 0 && level instanceof ServerLevel) {
            shatter();
        }
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private void shatter() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 0.5;
        double z = worldPosition.getZ() + 0.5;

        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty()) Block.popResource(level, worldPosition, stack);
        }
        clearContent();

        serverLevel.sendParticles(ParticleTypes.SOUL,            x, y + 0.5, z, 50, 0.6, 0.6, 0.6, 0.08);
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y,       z, 25, 0.4, 0.3, 0.4, 0.06);
        serverLevel.sendParticles(ParticleTypes.ENCHANT,         x, y + 0.5, z, 70, 0.5, 0.5, 0.5, 0.45);
        serverLevel.sendParticles(ParticleTypes.END_ROD,         x, y + 1.0, z, 20, 0.4, 0.4, 0.4, 0.18);
        serverLevel.sendParticles(ParticleTypes.SMOKE,           x, y + 0.5, z, 35, 0.4, 0.3, 0.4, 0.04);

        serverLevel.playSound(null, worldPosition, SoundEvents.GLASS_BREAK,   SoundSource.BLOCKS, 1.0f, 0.7f);
        serverLevel.playSound(null, worldPosition, SoundEvents.WITHER_AMBIENT, SoundSource.BLOCKS, 0.6f, 1.4f);

        serverLevel.removeBlock(worldPosition, false);
    }

    // ── NBT ──────────────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        hasSoulItems = input.getBooleanOr("hasSoulItems", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("hasSoulItems", hasSoulItems);
    }
}
