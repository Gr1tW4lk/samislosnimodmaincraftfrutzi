package com.hardest.client.render;

import com.hardest.HardestMod;
import com.hardest.entity.DoppelgangerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class DoppelgangerRenderer extends BipedEntityRenderer<DoppelgangerEntity, PlayerEntityModel<DoppelgangerEntity>> {
    private static final RenderLayer EYES = RenderLayer.getEyes(HardestMod.id("textures/entity/clone_eyes.png"));

    public DoppelgangerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.addFeature(new ArmorFeatureRenderer<>(this,
                new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
        this.addFeature(new EyesFeatureRenderer<>(this) {
            @Override
            public RenderLayer getEyesTexture() {
                return EYES;
            }
        });
        this.addFeature(new Hologram(this));
    }

    @Override
    public Identifier getTexture(DoppelgangerEntity entity) {
        UUID owner = entity.getOwnerId().orElse(null);
        if (owner != null && MinecraftClient.getInstance().getNetworkHandler() != null) {
            PlayerListEntry entry = MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(owner);
            if (entry != null) return entry.getSkinTexture();
            return DefaultSkinHelper.getTexture(owner);
        }
        return DefaultSkinHelper.getTexture();
    }

    @Override
    public void render(DoppelgangerEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        PlayerEntityModel<DoppelgangerEntity> m = getModel();
        m.sneaking = e.isInSneakingPose();
        m.rightArmPose = e.getMainHandStack().isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
        m.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
        super.render(e, yaw, tickDelta, matrices, vcp, light);
    }

    /** Иногда клон «мерцает» голубой копией — он же цифровой. */
    static class Hologram extends FeatureRenderer<DoppelgangerEntity, PlayerEntityModel<DoppelgangerEntity>> {
        Hologram(FeatureRendererContext<DoppelgangerEntity, PlayerEntityModel<DoppelgangerEntity>> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, DoppelgangerEntity e, float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
            if (((int) age + e.getId()) % 40 > 3) return;
            var vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(getTexture(e)));
            matrices.push();
            matrices.translate(0.08 * Math.sin(age), 0, 0);
            getContextModel().render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0.2f, 1f, 1f, 0.45f);
            matrices.pop();
        }
    }
}
