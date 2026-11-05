package com.hardest.client.render;

import com.hardest.HardestMod;
import com.hardest.entity.ParachuteCowEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.CowEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class ParachuteCowRenderer extends MobEntityRenderer<ParachuteCowEntity, CowEntityModel<ParachuteCowEntity>> {
    private static final Identifier COW = new Identifier("textures/entity/cow/cow.png");
    private static final Identifier CHUTE = HardestMod.id("textures/entity/parachute.png");

    public ParachuteCowRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new CowEntityModel<>(ctx.getPart(EntityModelLayers.COW)), 0.7f);
        this.addFeature(new Chute(this, ctx.getPart(ModLayers.PARACHUTE)));
    }

    @Override
    public Identifier getTexture(ParachuteCowEntity entity) {
        return COW;
    }

    @Override
    protected void setupTransforms(ParachuteCowEntity e, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta) {
        super.setupTransforms(e, matrices, animationProgress, bodyYaw, tickDelta);
        if (e.isSkydiving() && !e.hasParachute()) {
            // без парашюта — кувыркается в полёте
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(animationProgress * 25f));
        }
    }

    static class Chute extends FeatureRenderer<ParachuteCowEntity, CowEntityModel<ParachuteCowEntity>> {
        private final ModelPart root;

        Chute(FeatureRendererContext<ParachuteCowEntity, CowEntityModel<ParachuteCowEntity>> ctx, ModelPart root) {
            super(ctx);
            this.root = root;
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, ParachuteCowEntity e, float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
            if (!e.hasParachute()) return;
            matrices.push();
            matrices.translate(0, -0.2, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(MathHelper.sin(age * 0.1f) * 0.12f));
            matrices.multiply(RotationAxis.POSITIVE_X.rotation(MathHelper.cos(age * 0.09f) * 0.1f));
            root.render(matrices, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(CHUTE)), light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
    }
}
