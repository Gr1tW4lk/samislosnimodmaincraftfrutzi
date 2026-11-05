package com.hardest.client.render;

import com.hardest.HardestMod;
import com.hardest.entity.MimicEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class MimicRenderer extends MobEntityRenderer<MimicEntity, MimicRenderer.Model> {
    private static final Identifier TEXTURE = HardestMod.id("textures/entity/mimic.png");

    public MimicRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Model(ctx.getPart(ModLayers.MIMIC)), 0.5f);
    }

    @Override
    public Identifier getTexture(MimicEntity entity) {
        return TEXTURE;
    }

    public static class Model extends SinglePartEntityModel<MimicEntity> {
        private final ModelPart root, base, lid, tongue, lowerTeeth, upperTeeth, eyes;
        private final ModelPart[] legs;

        public Model(ModelPart root) {
            super(RenderLayer::getEntityCutoutNoCull);
            this.root = root;
            this.base = root.getChild("base");
            this.lid = root.getChild("lid");
            this.tongue = base.getChild("tongue");
            this.lowerTeeth = base.getChild("lower_teeth");
            this.upperTeeth = lid.getChild("upper_teeth");
            this.eyes = lid.getChild("eyes");
            this.legs = new ModelPart[]{root.getChild("leg_fl"), root.getChild("leg_fr"), root.getChild("leg_bl"), root.getChild("leg_br")};
        }

        @Override
        public ModelPart getPart() {
            return root;
        }

        @Override
        public void setAngles(MimicEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            root.traverse().forEach(ModelPart::resetTransform);
            boolean awake = e.isAwake();
            if (!awake) {
                // притворяется обычным сундуком: ножки спрятаны, крышка закрыта
                base.pivotY += 3;
                lid.pivotY += 3;
                for (ModelPart l : legs) l.visible = false;
                tongue.visible = false;
                lowerTeeth.visible = false;
                upperTeeth.visible = false;
                eyes.visible = false;
                return;
            }
            for (ModelPart l : legs) l.visible = true;
            tongue.visible = lowerTeeth.visible = upperTeeth.visible = eyes.visible = true;
            float open = 0.45f + MathHelper.sin(age * 0.25f) * 0.12f;
            if (e.chompTicks > 0) {
                float k = e.chompTicks / 10f;
                open = MathHelper.abs(MathHelper.sin(k * MathHelper.PI)) * 1.1f;
            }
            lid.pitch = -open;
            tongue.pitch = -0.2f + MathHelper.sin(age * 0.5f) * 0.35f;
            tongue.yaw = MathHelper.sin(age * 0.3f) * 0.25f;
            float walk = limbAngle * 1.5f;
            for (int i = 0; i < legs.length; i++) {
                legs[i].pitch = MathHelper.cos(walk + (i % 2 == 0 ? 0 : MathHelper.PI)) * 0.9f * limbDistance;
            }
        }
    }
}
