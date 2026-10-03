package net.example.dynamicchests.render;

import net.example.dynamicchests.block.VaultChestType;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * Per-frame data snapshot for rendering a vault chest's animated lid.
 * Filled in {@link VaultChestBlockEntityRenderer#extractRenderState}, read in
 * {@link VaultChestBlockEntityRenderer#submit}.
 */
public class VaultChestRenderState extends BlockEntityRenderState {

    /** 0.0 = fully closed, 1.0 = fully open. */
    public float lidAngle;

    /** Horizontal facing yaw in degrees (from {@link net.minecraft.core.Direction#toYRot()}). */
    public float facingYaw;

    /** Whether this block is a standalone single chest, the left half, or the right half of a double. */
    public VaultChestType chestType = VaultChestType.SINGLE;
}
