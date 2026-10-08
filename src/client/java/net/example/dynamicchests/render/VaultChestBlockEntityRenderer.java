package net.example.dynamicchests.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.example.dynamicchests.block.AbstractVaultChestBlock;
import net.example.dynamicchests.block.VaultChestType;
import net.example.dynamicchests.block.entity.AbstractVaultChestBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the full vault chest (body + animated lid) using a single 64×64 entity texture sheet
 * per variant, matching vanilla's UV layout. Three texture paths are required per instance:
 * single, double-left, and double-right.
 *
 * The block's baked model has no elements (see the block model JSON files), so nothing is
 * baked into the static chunk mesh — this renderer owns all geometry.
 */
public class VaultChestBlockEntityRenderer<T extends AbstractVaultChestBlockEntity>
		implements BlockEntityRenderer<T, VaultChestRenderState> {

	private final VaultChestModel singleModel;
	private final VaultChestModel leftModel;
	private final VaultChestModel rightModel;

	private final Identifier singleTexture;
	private final Identifier leftTexture;
	private final Identifier rightTexture;

	/** Optional replacement for the single texture while the block state is {@code lit=true}. */
	@Nullable
	private BlendedFrameTexture litSingleTexture;

	/**
	 * Sets the textures used for a single chest whose block state is lit. With several frames they cross-fade in a loop,
	 * each shown for {@code frameTicks} ticks (a vanilla blast furnace's lit front uses 10).
	 */
	public VaultChestBlockEntityRenderer<T> withLitTextures(Identifier animatedId, int frameTicks, Identifier... frames) {
		this.litSingleTexture = new BlendedFrameTexture(animatedId, frameTicks, frames);
		return this;
	}

	public VaultChestBlockEntityRenderer(BlockEntityRendererProvider.Context context,
			Identifier singleTexture, Identifier leftTexture, Identifier rightTexture) {
		this(context, singleTexture, leftTexture, rightTexture, true);
	}

	public VaultChestBlockEntityRenderer(BlockEntityRendererProvider.Context context,
			Identifier singleTexture, Identifier leftTexture, Identifier rightTexture, boolean includeLock) {
		this.singleModel = VaultChestModel.createSingle(includeLock);
		this.leftModel   = VaultChestModel.createLeft(includeLock);
		this.rightModel  = VaultChestModel.createRight(includeLock);
		this.singleTexture = singleTexture;
		this.leftTexture   = leftTexture;
		this.rightTexture  = rightTexture;
	}

	@Override
	public VaultChestRenderState createRenderState() {
		return new VaultChestRenderState();
	}

	@Override
	public void extractRenderState(T blockEntity, VaultChestRenderState state, float tickProgress,
			Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, state, tickProgress, cameraPos, crumblingOverlay);
		state.lidAngle = blockEntity.getEffectiveAnimationProgress(tickProgress);

		BlockState blockState = blockEntity.getBlockState();

		Direction facing = blockState.hasProperty(AbstractVaultChestBlock.FACING)
				? blockState.getValue(AbstractVaultChestBlock.FACING)
				: Direction.SOUTH;
		state.facingYaw = facing.toYRot();

		state.gameTime = blockEntity.getLevel() == null ? 0 : blockEntity.getLevel().getGameTime();
		state.lit = blockState.hasProperty(BlockStateProperties.LIT) && blockState.getValue(BlockStateProperties.LIT);

		state.chestType = blockState.hasProperty(AbstractVaultChestBlock.CHEST_TYPE)
				? blockState.getValue(AbstractVaultChestBlock.CHEST_TYPE)
				: VaultChestType.SINGLE;
	}

	@Override
	public void submit(VaultChestRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
		VaultChestModel model;
		Identifier texture;
		switch (state.chestType) {
			case LEFT  -> { model = this.leftModel;   texture = this.leftTexture; }
			case RIGHT -> { model = this.rightModel;  texture = this.rightTexture; }
			default    -> { model = this.singleModel; texture = this.singleTexture; }
		}
		if (state.lit && state.chestType == VaultChestType.SINGLE && this.litSingleTexture != null) {
			texture = this.litSingleTexture.at(state.gameTime);
		}

		poseStack.pushPose();

		// Rotate around the block's centre so the chest faces its FACING direction.
		// Vanilla convention: SOUTH = no rotation (model's south face is the front).
		poseStack.translate(0.5, 0.5, 0.5);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.facingYaw));
		poseStack.translate(-0.5, -0.5, -0.5);

		// Vanilla cubic ease: fast open, soft close.
		float openAmount = state.lidAngle;
		openAmount = 1.0f - openAmount;
		openAmount = 1.0f - openAmount * openAmount * openAmount;

		// Pass openAmount as a per-submission parameter so the render system calls
		// model.setupAnim(openAmount) at actual draw time — not at submit time.
		// This prevents the shared-model xRot bug where all chests of the same type
		// animate together because submitModelPart defers vertex generation.
		collector.submitModel(model, openAmount, poseStack, texture,
				state.lightCoords, OverlayTexture.NO_OVERLAY, 0, state.breakProgress);

		poseStack.popPose();
	}
}
