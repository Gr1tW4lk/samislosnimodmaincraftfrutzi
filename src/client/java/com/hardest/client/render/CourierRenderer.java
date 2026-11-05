package com.hardest.client.render;

import com.hardest.HardestMod;
import com.hardest.entity.CourierCreeperEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.CreeperEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class CourierRenderer extends MobEntityRenderer<CourierCreeperEntity, CreeperEntityModel<CourierCreeperEntity>> {
    private static final Identifier TEXTURE = HardestMod.id("textures/entity/courier.png");
    private static final Identifier EXTRA = HardestMod.id("textures/entity/courier_extra.png");

    public CourierRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new CreeperEntityModel<>(ctx.getPart(EntityModelLayers.CREEPER)), 0.5f);
        this.addFeature(new Gear(this, ctx.getPart(ModLayers.COURIER_EXTRA)));
    }

    @Override
    public Identifier getTexture(CourierCreeperEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(CourierCreeperEntity e, MatrixStack matrices, float tickDelta) {
        float f = e.getFuse(tickDelta);
        float g = 1.0f + MathHelper.sin(f * 100.0f) * f * 0.01f;
        f = MathHelper.clamp(f, 0.0f, 1.0f);
        f *= f;
        f *= f;
        float h = (1.0f + f * 0.4f) * g;
        float i = (1.0f + f * 0.1f) / g;
        matrices.scale(h, i, h);
    }

    @Override
    protected float getAnimationCounter(CourierCreeperEntity e, float tickDelta) {
        float g = e.getFuse(tickDelta);
        if ((int) (g * 10.0f) % 2 == 0) return 0.0f;
        return MathHelper.clamp(g, 0.5f, 1.0f);
    }

    /** Кепка и термо-рюкзак. */
    static class Gear extends FeatureRenderer<CourierCreeperEntity, CreeperEntityModel<CourierCreeperEntity>> {
        private final ModelPart cap, pack;

        Gear(FeatureRendererContext<CourierCreeperEntity, CreeperEntityModel<CourierCreeperEntity>> ctx, ModelPart root) {
            super(ctx);
            this.cap = root.getChild("cap");
            this.pack = root.getChild("backpack");
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, CourierCreeperEntity e, float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
            ModelPart head = getContextModel().getPart().getChild("head");
            cap.copyTransform(head);
            var vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(EXTRA));
            int overlay = getOverlay(e, 0f);
            cap.render(matrices, vc, light, overlay);
            if (e.hasParcel() || e.getMode() != CourierCreeperEntity.FUSE) {
                pack.pitch = MathHelper.sin(limbAngle * 0.6662f) * 0.05f * limbDistance;
                pack.render(matrices, vc, light, overlay);
            }
        }
    }

    static int getOverlay(CourierCreeperEntity e, float white) {
        return OverlayTexture.packUv(OverlayTexture.getU(white), OverlayTexture.getV(e.hurtTime > 0 || e.deathTime > 0));
    }
}
