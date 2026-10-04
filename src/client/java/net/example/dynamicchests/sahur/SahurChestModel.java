package net.example.dynamicchests.sahur;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Chest body and lid in the usual 64x64 chest layout, plus a baseball bat sitting on the lid that
 * swings on every drum hit. The bat uses the free area of the texture below the chest (rows 44+).
 */
public final class SahurChestModel extends Model<SahurChestModel.Pose> {

	/** @param open lid opening 0..1 (already eased), @param swing bat swing 0..1 */
	public record Pose(float open, float swing) {
	}

	private final ModelPart lid;
	private final ModelPart bat;

	private SahurChestModel(ModelPart root) {
		super(root, RenderTypes::entityCutoutCull);
		this.lid = root.getChild("lid");
		this.bat = this.lid.getChild("bat");
	}

	@Override
	public void setupAnim(Pose pose) {
		this.lid.xRot = -(pose.open() * (float) (Math.PI / 2.0));
		// Rests leaning back; a hit snaps it forward and it falls back.
		this.bat.xRot = -0.35f + pose.swing() * 1.35f;
	}

	public static SahurChestModel create() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		root.addOrReplaceChild("bottom",
				CubeListBuilder.create().texOffs(0, 19).addBox(1.0f, 0.0f, 1.0f, 14.0f, 10.0f, 14.0f),
				PartPose.ZERO);

		PartDefinition lid = root.addOrReplaceChild("lid",
				CubeListBuilder.create().texOffs(0, 0).addBox(1.0f, 0.0f, 0.0f, 14.0f, 5.0f, 14.0f),
				PartPose.offset(0.0f, 9.0f, 1.0f));

		// Bat: thin handle plus a thicker barrel, standing on the right-hand corner of the lid.
		PartDefinition bat = lid.addOrReplaceChild("bat",
				CubeListBuilder.create().texOffs(0, 44).addBox(-0.5f, 0.0f, -0.5f, 1.0f, 7.0f, 1.0f),
				PartPose.offsetAndRotation(12.5f, 5.0f, 6.5f, -0.35f, 0.0f, 0.12f));
		bat.addOrReplaceChild("barrel",
				CubeListBuilder.create().texOffs(8, 44).addBox(-1.0f, 7.0f, -1.0f, 2.0f, 8.0f, 2.0f),
				PartPose.ZERO);

		return new SahurChestModel(LayerDefinition.create(mesh, 64, 64).bakeRoot());
	}
}
