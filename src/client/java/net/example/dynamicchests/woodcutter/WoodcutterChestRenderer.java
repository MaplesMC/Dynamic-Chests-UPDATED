package net.example.dynamicchests.woodcutter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.example.dynamicchests.block.AbstractVaultChestBlock;
import net.example.dynamicchests.render.VaultChestModel;
import net.example.dynamicchests.render.VaultChestRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Draws the Woodcutter's chest, the same solid chest model the other Dynamic Chests use, with the lid that lifts while the menu is open. */
public class WoodcutterChestRenderer implements BlockEntityRenderer<WoodcutterBlockEntity, VaultChestRenderState> {

	private static final Identifier TEXTURE =
			Identifier.fromNamespaceAndPath("dynamicchests", "textures/entity/chest/wood_cutter.png");

	private final VaultChestModel model = VaultChestModel.createSingle();

	public WoodcutterChestRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public VaultChestRenderState createRenderState() {
		return new VaultChestRenderState();
	}

	@Override
	public void extractRenderState(WoodcutterBlockEntity blockEntity, VaultChestRenderState state, float tickProgress,
			Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, state, tickProgress, cameraPos, crumblingOverlay);
		state.lidAngle = blockEntity.getEffectiveAnimationProgress(tickProgress);
		BlockState blockState = blockEntity.getBlockState();
		Direction facing = blockState.hasProperty(AbstractVaultChestBlock.FACING)
				? blockState.getValue(AbstractVaultChestBlock.FACING) : Direction.SOUTH;
		state.facingYaw = facing.toYRot();
	}

	@Override
	public void submit(VaultChestRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState cameraRenderState) {
		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.facingYaw));
		poseStack.translate(-0.5, -0.5, -0.5);

		// Vanilla cubic ease: fast open, soft close.
		float openAmount = 1.0f - state.lidAngle;
		openAmount = 1.0f - openAmount * openAmount * openAmount;

		collector.submitModel(this.model, openAmount, poseStack, TEXTURE,
				state.lightCoords, OverlayTexture.NO_OVERLAY, 0, state.breakProgress);
		poseStack.popPose();
	}
}
