package net.example.dynamicchests.render;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** Render state for the Shadow Chest — carries the mimic block to render in place of the chest. */
public class ShadowChestRenderState extends BlockEntityRenderState {
    public final MovingBlockRenderState mimicRenderState = new MovingBlockRenderState();
    public boolean hasMimic = false;
}
