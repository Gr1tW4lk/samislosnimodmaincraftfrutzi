package com.hardest.client.render;

import com.hardest.HardestMod;
import com.hardest.entity.TaxInspectorEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class TaxInspectorRenderer extends MobEntityRenderer<TaxInspectorEntity, TaxInspectorRenderer.Model> {
    private static final Identifier TEXTURE = HardestMod.id("textures/entity/tax_inspector.png");

    public TaxInspectorRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Model(ctx.getPart(ModLayers.TAX)), 0.5f);
    }

    @Override
    public Identifier getTexture(TaxInspectorEntity entity) {
        return TEXTURE;
    }

    public static class Model extends SinglePartEntityModel<TaxInspectorEntity> {
        private final ModelPart root, head, body, rightArm, leftArm, rightLeg, leftLeg;

        public Model(ModelPart root) {
            super(RenderLayer::getEntityCutoutNoCull);
            this.root = root;
            this.head = root.getChild("head");
            this.body = root.getChild("body");
            this.rightArm = root.getChild("right_arm");
            this.leftArm = root.getChild("left_arm");
            this.rightLeg = root.getChild("right_leg");
            this.leftLeg = root.getChild("left_leg");
        }

        @Override
        public ModelPart getPart() {
            return root;
        }

        @Override
        public void setAngles(TaxInspectorEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            root.traverse().forEach(ModelPart::resetTransform);
            head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
            head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;
            float walk = limbAngle * 0.6662f;
            rightLeg.pitch = MathHelper.cos(walk) * 1.2f * limbDistance;
            leftLeg.pitch = MathHelper.cos(walk + MathHelper.PI) * 1.2f * limbDistance;
            rightArm.pitch = MathHelper.cos(walk + MathHelper.PI) * 0.5f * limbDistance;
            leftArm.pitch = MathHelper.cos(walk) * 0.5f * limbDistance;
            int mode = e.getMode();
            if (mode == TaxInspectorEntity.AUDIT) {
                // смотрит в бумаги и стучит штампом
                leftArm.pitch = -1.1f;
                leftArm.yaw = 0.3f;
                head.pitch = 0.25f + head.pitch * 0.3f;
            }
            int stamp = e.getStampTicks();
            if (stamp > 0) {
                float k = stamp / 12f;
                leftArm.pitch = -2.2f * k;
            }
            if (mode == TaxInspectorEntity.ANGRY) {
                leftArm.pitch = -2.6f + MathHelper.sin(age * 0.8f) * 0.5f;
                rightArm.pitch = -0.4f + MathHelper.sin(age * 0.8f + 1) * 0.3f;
            }
        }
    }
}
