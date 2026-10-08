package net.example.dynamicchests.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.example.dynamicchests.block.ShadowChestBlock;
import net.example.dynamicchests.block.entity.ShadowChestBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the Shadow Chest as the block directly beneath it (in a stack of shadow chests, the block under the whole stack), achieving a camouflage effect.
 * The chest model is never shown — only the mimic block is rendered at the chest's position.
 * If nothing is below (air), nothing is rendered.
 */
public class ShadowChestBlockEntityRenderer
        implements BlockEntityRenderer<ShadowChestBlockEntity, ShadowChestRenderState> {

    public ShadowChestBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public ShadowChestRenderState createRenderState() {
        return new ShadowChestRenderState();
    }

    @Override
    public void extractRenderState(ShadowChestBlockEntity blockEntity, ShadowChestRenderState state,
            float tickProgress, Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, tickProgress, cameraPos, crumblingOverlay);

        Level level = blockEntity.getLevel();
        if (!(level instanceof ClientLevel clientLevel)) {
            state.hasMimic = false;
            return;
        }

        BlockPos pos = blockEntity.getBlockPos();
        BlockPos belowPos = pos.below();
        BlockState below = clientLevel.getBlockState(belowPos);

        if (below.isAir()) {
            state.hasMimic = false;
            return;
        }

        state.hasMimic = true;
        state.mimicRenderState.blockState      = below;
        state.mimicRenderState.blockPos        = pos;
        state.mimicRenderState.randomSeedPos   = belowPos;
        state.mimicRenderState.biome           = clientLevel.getBiome(belowPos);
        state.mimicRenderState.cardinalLighting = clientLevel.cardinalLighting();
        state.mimicRenderState.lightEngine     = clientLevel.getLightEngine();
    }

    @Override
    public void submit(ShadowChestRenderState state, PoseStack poseStack,
            SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        if (!state.hasMimic) return;
        collector.submitMovingBlock(poseStack, state.mimicRenderState, 0);
    }
}
