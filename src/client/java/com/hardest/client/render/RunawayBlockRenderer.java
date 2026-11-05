package com.hardest.client.render;

import com.hardest.HardestMod;
import com.hardest.entity.RunawayBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class RunawayBlockRenderer extends MobEntityRenderer<RunawayBlockEntity, RunawayBlockRenderer.Model> {
    private static final Identifier TEXTURE = HardestMod.id("textures/entity/runaway_block.png");

    public RunawayBlockRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Model(ctx.getPart(ModLayers.RUNAWAY)), 0.45f);
        this.addFeature(new BlockBody(this));
    }

    @Override
    public Identifier getTexture(RunawayBlockEntity entity) {
        return TEXTURE;
    }

    /** Подпрыгивание тела при беге — используется и моделью, и блоком. */
    static float bob(RunawayBlockEntity e, float limbAngle, float limbDistance) {
        return Math.abs(MathHelper.sin(limbAngle * 1.3f)) * limbDistance * 1.8f;
    }

    public static class Model extends SinglePartEntityModel<RunawayBlockEntity> {
        private final ModelPart root, leftLeg, rightLeg, leftArm, rightArm, face;

        public Model(ModelPart root) {
            this.root = root;
            this.leftLeg = root.getChild("left_leg");
            this.rightLeg = root.getChild("right_leg");
            this.leftArm = root.getChild("left_arm");
            this.rightArm = root.getChild("right_arm");
            this.face = root.getChild("face");
        }

        @Override
        public ModelPart getPart() {
            return root;
        }

        @Override
        public void setAngles(RunawayBlockEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            root.traverse().forEach(ModelPart::resetTransform);
            float run = limbAngle * 1.3f;
            leftLeg.pitch = MathHelper.cos(run) * 1.4f * limbDistance;
            rightLeg.pitch = MathHelper.cos(run + MathHelper.PI) * 1.4f * limbDistance;
            float b = -bob(e, limbAngle, limbDistance);
            // ручки в панике машут над головой
            float flail = MathHelper.sin(age * 0.9f);
            leftArm.roll = -0.9f - flail * 0.6f;
            rightArm.roll = 0.9f + flail * 0.6f;
            leftArm.pivotY += b;
            rightArm.pivotY += b;
            face.pivotY += b;
            face.pivotX = MathHelper.sin(age * 0.5f) * 0.3f;
        }
    }

    /** Сам блок на ножках. */
    static class BlockBody extends FeatureRenderer<RunawayBlockEntity, Model> {
        BlockBody(FeatureRendererContext<RunawayBlockEntity, Model> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, RunawayBlockEntity e, float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
            matrices.push();
            float b = bob(e, limbAngle, limbDistance);
            matrices.translate(0, (18 - b) / 16f, 0);
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotation(MathHelper.sin(age * 0.4f) * 0.06f));
            matrices.scale(-0.75f, -0.75f, 0.75f);
            matrices.translate(-0.5, 0, -0.5);
            MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(e.getBlockState(), matrices, vcp, light,
                    LivingEntityRenderer.getOverlay(e, 0f));
            matrices.pop();
        }
    }
}
