package com.hardest.client.render;

import com.hardest.HardestMod;
import com.hardest.entity.GooseEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class GooseRenderer extends MobEntityRenderer<GooseEntity, GooseRenderer.Model> {
    private static final Identifier TEXTURE = HardestMod.id("textures/entity/goose.png");

    public GooseRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Model(ctx.getPart(ModLayers.GOOSE)), 0.4f);
        this.addFeature(new HeldLoot(this));
        this.addFeature(new GooseChute(this, ctx.getPart(ModLayers.PARACHUTE)));
    }

    @Override
    public Identifier getTexture(GooseEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(GooseEntity entity, MatrixStack matrices, float amount) {
        if (entity.isBaby()) matrices.scale(0.55f, 0.55f, 0.55f);
    }

    public static class Model extends SinglePartEntityModel<GooseEntity> {
        private final ModelPart root, body, tail, neck, head, jaw, leftWing, rightWing, leftLeg, rightLeg;

        public Model(ModelPart root) {
            this.root = root;
            this.body = root.getChild("body");
            this.tail = body.getChild("tail");
            this.neck = root.getChild("neck");
            this.head = neck.getChild("head");
            this.jaw = head.getChild("jaw");
            this.leftWing = root.getChild("left_wing");
            this.rightWing = root.getChild("right_wing");
            this.leftLeg = root.getChild("left_leg");
            this.rightLeg = root.getChild("right_leg");
        }

        @Override
        public ModelPart getPart() {
            return root;
        }

        @Override
        public void setAngles(GooseEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            root.traverse().forEach(ModelPart::resetTransform);
            float walk = limbAngle * 1.4f;
            leftLeg.pitch = MathHelper.cos(walk) * 1.2f * limbDistance;
            rightLeg.pitch = MathHelper.cos(walk + MathHelper.PI) * 1.2f * limbDistance;
            // вперевалочку
            body.roll = MathHelper.sin(walk) * 0.12f * limbDistance;
            neck.roll = body.roll * 0.6f;
            neck.pitch = MathHelper.sin(walk * 2) * 0.08f * limbDistance;
            neck.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE * 0.7f;
            head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE * 0.6f;
            tail.yaw = MathHelper.sin(age * 0.3f) * 0.15f;

            boolean hiss = e.isHissing();
            boolean loot = e.hasLoot();
            if (hiss) {
                // шея вперёд, голова к земле — классическая поза гуся
                neck.pitch = 1.1f;
                head.pitch = -0.9f;
                leftWing.roll = -0.5f - MathHelper.sin(age * 0.8f) * 0.15f;
                rightWing.roll = 0.5f + MathHelper.sin(age * 0.8f) * 0.15f;
            }
            if (loot && limbDistance > 0.05f) {
                // бежит с добычей и машет крыльями
                float flap = MathHelper.sin(age * 1.6f);
                leftWing.roll = -0.6f - flap * 0.7f;
                rightWing.roll = 0.6f + flap * 0.7f;
                neck.pitch = 0.35f;
            }
            float mouth = e.mouthOpenTicks > 0 || hiss ? 0.55f + MathHelper.sin(age * 1.3f) * 0.1f : 0f;
            jaw.pitch = mouth;
        }

        ModelPart neck() {
            return neck;
        }

        ModelPart head() {
            return head;
        }
    }

    /** Парашют гусиного десанта. */
    static class GooseChute extends FeatureRenderer<GooseEntity, Model> {
        private static final Identifier CHUTE = HardestMod.id("textures/entity/parachute.png");
        private final ModelPart chute;

        GooseChute(FeatureRendererContext<GooseEntity, Model> ctx, ModelPart chute) {
            super(ctx);
            this.chute = chute;
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, GooseEntity e, float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
            if (!e.hasParachute()) return;
            matrices.push();
            matrices.translate(0, 0.35, 0);
            matrices.scale(0.55f, 0.55f, 0.55f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(MathHelper.sin(age * 0.12f) * 0.15f));
            chute.render(matrices, vcp.getBuffer(net.minecraft.client.render.RenderLayer.getEntityCutoutNoCull(CHUTE)), light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
    }

    /** Украденный предмет в клюве. */
    static class HeldLoot extends FeatureRenderer<GooseEntity, Model> {
        HeldLoot(FeatureRendererContext<GooseEntity, Model> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, GooseEntity e, float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
            ItemStack stack = e.getStolen();
            if (stack.isEmpty()) return;
            Model m = getContextModel();
            matrices.push();
            m.neck().rotate(matrices);
            m.head().rotate(matrices);
            matrices.translate(0, -0.06, -0.42);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
            matrices.scale(0.55f, 0.55f, 0.55f);
            MinecraftClient.getInstance().getItemRenderer().renderItem(e, stack, ModelTransformationMode.GROUND, false, matrices, vcp, e.getWorld(), light, OverlayTexture.DEFAULT_UV, e.getId());
            matrices.pop();
        }
    }
}
