package net.example.dynamicchests.sahur;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.example.dynamicchests.block.AbstractVaultChestBlock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Draws the log-shaped chest with its swinging bat. */
public class SahurChestRenderer implements BlockEntityRenderer<SahurChestBlockEntity, SahurChestRenderer.State> {

	public static class State extends BlockEntityRenderState {
		public float lidAngle;
		public float facingYaw;
		public float hitAge = 1000.0f;
	}

	private static final Identifier TEXTURE =
			Identifier.fromNamespaceAndPath("dynamicchests", "textures/entity/chest/sahur_chest.png");
	/** How long one bat swing lasts, in ticks. */
	private static final float SWING_TICKS = 7.0f;

	private final SahurChestModel model = SahurChestModel.create();

	public SahurChestRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SahurChestBlockEntity blockEntity, State state, float tickProgress,
			Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, state, tickProgress, cameraPos, crumblingOverlay);
		state.lidAngle = blockEntity.getEffectiveAnimationProgress(tickProgress);
		state.hitAge = blockEntity.getHitAge(tickProgress);
		BlockState blockState = blockEntity.getBlockState();
		Direction facing = blockState.hasProperty(AbstractVaultChestBlock.FACING)
				? blockState.getValue(AbstractVaultChestBlock.FACING) : Direction.SOUTH;
		state.facingYaw = facing.toYRot();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.facingYaw));
		poseStack.translate(-0.5, -0.5, -0.5);

		float open = 1.0f - state.lidAngle;
		open = 1.0f - open * open * open;
		float swing = state.hitAge < SWING_TICKS
				? (float) Math.sin(state.hitAge / SWING_TICKS * Math.PI)
				: 0.0f;

		collector.submitModel(this.model, new SahurChestModel.Pose(open, swing), poseStack, TEXTURE,
				state.lightCoords, OverlayTexture.NO_OVERLAY, 0, state.breakProgress);
		poseStack.popPose();
	}
}
