package net.example.dynamicchests.block.entity;

import net.example.dynamicchests.registry.ModRegistry;
import net.example.dynamicchests.screen.VoidChestMenu;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.block.state.BlockState;

public class VoidChestBlockEntity extends AbstractVaultChestBlockEntity implements ExtendedMenuProvider<Integer> {

    public static final int COLUMNS     = 9;
    public static final int ROWS_SINGLE = 6;
    public static final int SINGLE_SIZE = COLUMNS * ROWS_SINGLE; // 54

    private static final int GRACE_PERIOD_TICKS = 100; // 5 seconds

    /** Server-side countdown to item deletion. 0 = idle. */
    private int graceTicksRemaining = 0;

    /**
     * Client-side countdown that keeps the lid animation open during the grace period.
     * Set via a custom block event (type=2); ticked down in tickLidAnimation().
     */
    private int graceTicksClient = 0;

    /** Client-side tick counter used to advance the particle spiral angle. */
    @Environment(EnvType.CLIENT)
    private int particleTick = 0;

    public VoidChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.VOID_CHEST_BLOCK_ENTITY, pos, state,
                SoundEvents.ENDER_CHEST_OPEN, SoundEvents.ENDER_CHEST_CLOSE);
    }

    @Override
    public int getSingleContainerSize() {
        return SINGLE_SIZE;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.dynamicchests.void_chest");
    }

    @Override
    public Integer getScreenOpeningData(ServerPlayer player) {
        return ROWS_SINGLE;
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new VoidChestMenu(syncId, playerInventory, this, ROWS_SINGLE);
    }

    // -------------------------------------------------------------------------
    // Open / close tracking
    // -------------------------------------------------------------------------

    @Override
    public void startOpen(ContainerUser user) {
        super.startOpen(user);
        if (!user.getLivingEntity().isSpectator()) {
            // Cancel pending deletion and tell clients to stop the grace countdown.
            this.graceTicksRemaining = 0;
            sendGraceEvent(0);
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        super.stopOpen(user); // decrements openCount, sends type=1 signal (openCount=0) to clients
        if (this.openCount <= 0 && !user.getLivingEntity().isSpectator() && hasItems()) {
            // Only hold the lid open and start the grace period if there are items to destroy.
            this.graceTicksRemaining = GRACE_PERIOD_TICKS;
            sendGraceEvent(GRACE_PERIOD_TICKS);
        }
    }

    private boolean hasItems() {
        for (int i = 0; i < this.getContainerSize(); i++) {
            if (!this.getItem(i).isEmpty()) return true;
        }
        return false;
    }

    /** Sends a custom block event (type=2) to set the client-side grace countdown. */
    private void sendGraceEvent(int ticks) {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), 2, ticks);
        }
    }

    // -------------------------------------------------------------------------
    // Custom block event — type=2 carries the client grace countdown value
    // -------------------------------------------------------------------------

    @Override
    public boolean triggerEvent(int id, int data) {
        if (id == 2) {
            // Only meaningful on the client; server doesn't need graceTicksClient.
            this.graceTicksClient = data;
            return true;
        }
        return super.triggerEvent(id, data);
    }

    // -------------------------------------------------------------------------
    // Lid animation — stay open while graceTicksClient > 0
    // -------------------------------------------------------------------------

    @Override
    protected float getAnimationTarget() {
        return (this.openCount > 0 || this.graceTicksClient > 0) ? 1.0f : 0.0f;
    }

    @Override
    public void tickLidAnimation() {
        if (this.graceTicksClient > 0) {
            this.graceTicksClient--;
        }
        super.tickLidAnimation();
    }

    // -------------------------------------------------------------------------
    // Client-side particles
    // -------------------------------------------------------------------------

    @Environment(EnvType.CLIENT)
    public void tickParticles() {
        if (this.level == null) return;
        net.minecraft.util.RandomSource rng = this.level.getRandom();
        particleTick++;

        double cx = this.worldPosition.getX() + 0.5;
        double cy = this.worldPosition.getY();
        double cz = this.worldPosition.getZ() + 0.5;
        // Lid opening centre — top face of the chest block.
        double lidY = cy + 1.0;

        if (this.animationProgress > 0.05f) {
            spawnOpenParticles(rng, cx, cy, cz, lidY);
        } else {
            spawnClosedParticles(rng, cx, cy, cz);
        }
    }

    /**
     * Vortex effect while open: two counter-rotating PORTAL/REVERSE_PORTAL disk layers
     * tightening into the lid, ENCHANT motes drifting in from outside, and soul wisps
     * with strong suction during the grace period.
     */
    @Environment(EnvType.CLIENT)
    private void spawnOpenParticles(net.minecraft.util.RandomSource rng,
                                    double cx, double cy, double cz, double lidY) {
        // --- Inner ring: 3 REVERSE_PORTAL arms, counter-clockwise, tighter and faster ---
        for (int arm = 0; arm < 3; arm++) {
            double angle = -(particleTick * 0.25) + arm * (2.0 * Math.PI / 3.0);
            double radius = 0.35 + rng.nextFloat() * 0.12;
            double height = lidY + 0.04 + rng.nextFloat() * 0.18;
            double px = cx + radius * Math.cos(angle);
            double pz = cz + radius * Math.sin(angle);
            double tangX =  Math.sin(angle) * 0.085;
            double tangZ = -Math.cos(angle) * 0.085;
            double inX = (cx - px) * 0.20;
            double inZ = (cz - pz) * 0.20;
            this.level.addParticle(ParticleTypes.REVERSE_PORTAL,
                    px, height, pz,
                    tangX + inX, -0.10 - rng.nextFloat() * 0.04, tangZ + inZ);
        }

        // --- Soul wisps: grace period only, arc down from above into the lid ---
        if (graceTicksClient > 0 && particleTick % 3 == 0) {
            double angle = rng.nextFloat() * 2.0 * Math.PI;
            double radius = 0.5 + rng.nextFloat() * 0.35;
            double px = cx + radius * Math.cos(angle);
            double pz = cz + radius * Math.sin(angle);
            double py = lidY + 0.6 + rng.nextFloat() * 0.5;
            this.level.addParticle(ParticleTypes.SCULK_SOUL,
                    px, py, pz,
                    (cx - px) * 0.08, -0.04, (cz - pz) * 0.08);
        }
    }

    /**
     * Contained-power effect while closed: a slow orbiting PORTAL halo, REVERSE_PORTAL
     * wisps rising from the base, and periodic END_ROD crackle bursts.
     */
    @Environment(EnvType.CLIENT)
    private void spawnClosedParticles(net.minecraft.util.RandomSource rng,
                                      double cx, double cy, double cz) {
        double midY = cy + 0.5;

        // --- Ambient halo: 3 PORTAL particles orbiting slowly at all times ---
        for (int arm = 0; arm < 3; arm++) {
            double angle = particleTick * 0.055 + arm * (2.0 * Math.PI / 3.0);
            double radius = 0.58 + rng.nextFloat() * 0.07;
            double px = cx + radius * Math.cos(angle);
            double pz = cz + radius * Math.sin(angle);
            double tangX = -Math.sin(angle) * 0.016;
            double tangZ =  Math.cos(angle) * 0.016;
            this.level.addParticle(ParticleTypes.PORTAL,
                    px, midY + (rng.nextFloat() - 0.5) * 0.12, pz,
                    tangX, 0.003, tangZ);
        }

        // --- REVERSE_PORTAL wisps: drift upward from the base, every 6 ticks ---
        if (particleTick % 6 == 0) {
            double px = cx + (rng.nextFloat() - 0.5) * 0.55;
            double pz = cz + (rng.nextFloat() - 0.5) * 0.55;
            this.level.addParticle(ParticleTypes.REVERSE_PORTAL,
                    px, cy + 0.05 + rng.nextFloat() * 0.20, pz,
                    (rng.nextFloat() - 0.5) * 0.015,
                    0.025 + rng.nextFloat() * 0.025,
                    (rng.nextFloat() - 0.5) * 0.015);
        }

        // --- ASH base glow: lazy motes near the ground, every 10 ticks ---
        if (particleTick % 10 == 0) {
            double px = cx + (rng.nextFloat() - 0.5) * 0.75;
            double pz = cz + (rng.nextFloat() - 0.5) * 0.75;
            this.level.addParticle(ParticleTypes.ASH,
                    px, cy + 0.04, pz,
                    (rng.nextFloat() - 0.5) * 0.008, 0.004,
                    (rng.nextFloat() - 0.5) * 0.008);
        }
    }

    // -------------------------------------------------------------------------
    // Server-side destruction timer
    // -------------------------------------------------------------------------

    /** Called every server tick by the block ticker. */
    public void tickDestructionTimer() {
        if (this.level == null || this.level.isClientSide()) return;
        if (this.graceTicksRemaining > 0) {
            this.graceTicksRemaining--;
            if (this.graceTicksRemaining == 0 && this.openCount == 0) {
                destroyContents();
                // openCount is already 0; the normal type=1 event was sent in stopOpen.
                // graceTicksClient on clients will naturally tick to 0 and close the lid.
            }
        }
    }

    private void destroyContents() {
        boolean hadItems = false;
        for (int i = 0; i < this.getContainerSize(); i++) {
            if (!this.getItem(i).isEmpty()) {
                hadItems = true;
                this.setItem(i, ItemStack.EMPTY);
            }
        }

        if (hadItems && this.level instanceof ServerLevel serverLevel) {
            double x = this.worldPosition.getX() + 0.5;
            double y = this.worldPosition.getY() + 0.5;
            double z = this.worldPosition.getZ() + 0.5;
            // Bright reverse-portal burst — fast outward spread, very visible
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.5, z, 250, 0.6, 0.6, 0.6, 0.7);
            // Classic portal swirl around the chest
            serverLevel.sendParticles(ParticleTypes.PORTAL,          x, y + 0.5, z,  80, 0.5, 0.5, 0.5, 0.3);
            // Purple witch-sparkle cloud
            serverLevel.sendParticles(ParticleTypes.WITCH,            x, y + 0.5, z,  80, 0.4, 0.4, 0.4, 0.08);
            // Dark smoke column rising above
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,     x, y + 1.0, z,  25, 0.3, 0.3, 0.3, 0.03);
            // Ash drifting down as aftermath
            serverLevel.sendParticles(ParticleTypes.ASH,             x, y + 2.0, z,  60, 0.6, 0.5, 0.6, 0.04);
            serverLevel.playSound(null, this.worldPosition,
                    SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 0.6f, 0.4f);
        }
    }
}
