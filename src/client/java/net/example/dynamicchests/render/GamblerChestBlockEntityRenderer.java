package net.example.dynamicchests.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.example.dynamicchests.block.AbstractVaultChestBlock;
import net.example.dynamicchests.block.entity.GamblerChestBlockEntity;
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

/**
 * Renders the Gambler's Chest with three swappable textures: idle, active (while rolling) and
 * jackpot. While active the chest is drawn fully lit and flips between the idle and glowing
 * texture so the golden symbol pulses.
 *
 * Textures live in {@code assets/dynamicchests/textures/entity/chest/} as
 * {@code gambler_chest.png}, {@code gambler_chest_active.png} and {@code gambler_chest_jackpot.png}
 * (same 64x64 layout as a vanilla chest) and can be replaced freely.
 */
public class GamblerChestBlockEntityRenderer implements BlockEntityRenderer<GamblerChestBlockEntity, GamblerChestRenderState> {

	private static final int FULL_BRIGHT = 0xF000F0;

	private final VaultChestModel model = VaultChestModel.createSingle();
	private final Identifier idleTexture;
	private final Identifier activeTexture;
	private final Identifier jackpotTexture;

	public GamblerChestBlockEntityRenderer(BlockEntityRendererProvider.Context context,
			Identifier idleTexture, Identifier activeTexture, Identifier jackpotTexture) {
		this.idleTexture = idleTexture;
		this.activeTexture = activeTexture;
		this.jackpotTexture = jackpotTexture;
	}

	@Override
	public GamblerChestRenderState createRenderState() {
		return new GamblerChestRenderState();
	}

	@Override
	public void extractRenderState(GamblerChestBlockEntity blockEntity, GamblerChestRenderState state, float tickProgress,
			Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, state, tickProgress, cameraPos, crumblingOverlay);
		state.lidAngle = blockEntity.getEffectiveAnimationProgress(tickProgress);
		state.visualState = blockEntity.getVisualState();
		long gameTime = blockEntity.getLevel() == null ? 0 : blockEntity.getLevel().getGameTime();
		state.animationTime = gameTime + tickProgress;

		BlockState blockState = blockEntity.getBlockState();
		Direction facing = blockState.hasProperty(AbstractVaultChestBlock.FACING)
				? blockState.getValue(AbstractVaultChestBlock.FACING)
				: Direction.SOUTH;
		state.facingYaw = facing.toYRot();
	}

	@Override
	public void submit(GamblerChestRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
		Identifier texture = this.idleTexture;
		int light = state.lightCoords;
		boolean flicker = ((int) (state.animationTime / 4.0f)) % 2 == 0;

		if (state.visualState == GamblerChestBlockEntity.VISUAL_GAMBLING) {
			texture = flicker ? this.activeTexture : this.idleTexture;
			light = FULL_BRIGHT;
		} else if (state.visualState == GamblerChestBlockEntity.VISUAL_JACKPOT) {
			texture = this.jackpotTexture; // stays until the next gamble starts
			light = FULL_BRIGHT;
		}

		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.facingYaw));
		poseStack.translate(-0.5, -0.5, -0.5);

		float openAmount = state.lidAngle;
		openAmount = 1.0f - openAmount;
		openAmount = 1.0f - openAmount * openAmount * openAmount;

		collector.submitModel(this.model, openAmount, poseStack, texture,
				light, OverlayTexture.NO_OVERLAY, 0, state.breakProgress);

		poseStack.popPose();
	}
}
