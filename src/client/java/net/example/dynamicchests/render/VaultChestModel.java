package net.example.dynamicchests.render;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;

import java.util.Set;

/**
 * Full chest model (body + lid) matching vanilla's geometry and 64×64 UV layout.
 * The texture sheet maps:
 *   rows  0–18  → lid top + lid sides (texOffs 0,0)
 *   rows 19–42  → body top + body sides (texOffs 0,19)
 *
 * Three variants exist for single, double-left, and double-right halves, mirroring
 * vanilla's ChestModel.createSingleBodyLayer / createDoubleBodyLeftLayer / createDoubleBodyRightLayer.
 *
 * Extends {@link Model}{@code <Float>} so that {@link #setupAnim(Float)} is called
 * by the render system per-submission with a stored open-amount parameter.  This
 * is the same pattern vanilla's ChestModel uses and prevents the shared-model
 * xRot mutation bug (where all chests of the same type animate in sync because
 * {@code submitModelPart} defers vertex generation until after all chests have
 * had a chance to overwrite {@code lid.xRot}).
 */
public final class VaultChestModel extends Model<Float> {

    /** Direct reference to the lid child part so setupAnim can rotate it. */
    private final ModelPart lid;

    private VaultChestModel(ModelPart root) {
        super(root, RenderTypes::entityCutoutCull);
        this.lid = root.getChild("lid");
    }

    /**
     * Called by the render system at draw time with the per-submission open-amount
     * (0 = closed, 1 = fully open, cubic-eased before being passed in).
     */
    @Override
    public void setupAnim(Float openAmount) {
        this.lid.xRot = -(openAmount * (float) (Math.PI / 2.0));
    }

    /** Single-chest geometry: 14 pixels wide, all six faces on every part. */
    public static VaultChestModel createSingle() { return createSingle(true); }
    public static VaultChestModel createSingle(boolean includeLock) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("bottom",
                CubeListBuilder.create()
                        .texOffs(0, 19)
                        .addBox(1.0f, 0.0f, 1.0f, 14.0f, 10.0f, 14.0f),
                PartPose.ZERO);

        PartDefinition lid = root.addOrReplaceChild("lid",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(1.0f, 0.0f, 0.0f, 14.0f, 5.0f, 14.0f),
                PartPose.offset(0.0f, 9.0f, 1.0f));

        if (includeLock) {
            lid.addOrReplaceChild("lock",
                    CubeListBuilder.create()
                            .texOffs(0, 0)
                            .addBox(7.0f, -1.0f, 15.0f, 2.0f, 4.0f, 1.0f),
                    PartPose.offset(0.0f, -1.0f, -1.0f));
        }

        return bake(mesh);
    }

    /**
     * Left half of a double chest: 15 pixels wide (x 0..15), west face omitted
     * (the seam faces the right half and must not be rendered).
     */
    public static VaultChestModel createLeft() { return createLeft(true); }
    public static VaultChestModel createLeft(boolean includeLock) {
        Set<Direction> noWest = Util.allOfEnumExcept(Direction.WEST);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("bottom",
                CubeListBuilder.create()
                        .texOffs(0, 19)
                        .addBox(0.0f, 0.0f, 1.0f, 15.0f, 10.0f, 14.0f, noWest),
                PartPose.ZERO);

        PartDefinition lid = root.addOrReplaceChild("lid",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(0.0f, 0.0f, 0.0f, 15.0f, 5.0f, 14.0f, noWest),
                PartPose.offset(0.0f, 9.0f, 1.0f));

        if (includeLock) {
            lid.addOrReplaceChild("lock",
                    CubeListBuilder.create()
                            .texOffs(0, 0)
                            .addBox(0.0f, -1.0f, 15.0f, 1.0f, 4.0f, 1.0f, noWest),
                    PartPose.offset(0.0f, -1.0f, -1.0f));
        }

        return bake(mesh);
    }

    /**
     * Right half of a double chest: 15 pixels wide (x 1..16), east face omitted
     * (the seam faces the left half and must not be rendered).
     */
    public static VaultChestModel createRight() { return createRight(true); }
    public static VaultChestModel createRight(boolean includeLock) {
        Set<Direction> noEast = Util.allOfEnumExcept(Direction.EAST);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("bottom",
                CubeListBuilder.create()
                        .texOffs(0, 19)
                        .addBox(1.0f, 0.0f, 1.0f, 15.0f, 10.0f, 14.0f, noEast),
                PartPose.ZERO);

        PartDefinition lid = root.addOrReplaceChild("lid",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(1.0f, 0.0f, 0.0f, 15.0f, 5.0f, 14.0f, noEast),
                PartPose.offset(0.0f, 9.0f, 1.0f));

        if (includeLock) {
            lid.addOrReplaceChild("lock",
                    CubeListBuilder.create()
                            .texOffs(0, 0)
                            .addBox(15.0f, -1.0f, 15.0f, 1.0f, 4.0f, 1.0f, noEast),
                    PartPose.offset(0.0f, -1.0f, -1.0f));
        }

        return bake(mesh);
    }

    private static VaultChestModel bake(MeshDefinition mesh) {
        return new VaultChestModel(LayerDefinition.create(mesh, 64, 64).bakeRoot());
    }
}
