package com.hardest.client.render;

import com.hardest.HardestMod;
import com.hardest.client.ClientState;
import com.hardest.dragon.DragonRage;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.EnderDragonEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

/**
 * Пробуждённый Эндер-дракон: модель из Blender, вдвое больше обычного.
 * Движение шеи и хвоста повторяет ванильного дракона (по истории сегментов),
 * сверху — рёв с раскрытой пастью, вдох и пульсирующее свечение.
 */
public class DragonRenderer extends EntityRenderer<EnderDragonEntity> {
    private static final Identifier TEXTURE = HardestMod.id("textures/entity/dragon.png");
    private static final Identifier GLOW = HardestMod.id("textures/entity/dragon_glow.png");
    private static final Identifier GLOW_RAGE = HardestMod.id("textures/entity/dragon_glow_rage.png");
    private static final Identifier EXPLOSION = new Identifier("textures/entity/enderdragon/dragon_exploding.png");
    private static final float HALF_SQRT_3 = (float) (Math.sqrt(3.0) / 2.0);
    private static final float RAD = MathHelper.RADIANS_PER_DEGREE;

    private final ModelPart head, jaw, neck, body, tailBig, tailSmall, tailEnd;
    private final ModelPart wingL, wingLTip, wingR, wingRTip;
    private final ModelPart[] legsL, legsR;

    public DragonRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.8f;
        ModelPart root = ctx.getPart(ModLayers.DRAGON);
        head = root.getChild("head");
        jaw = head.getChild("jaw");
        neck = root.getChild("neck");
        body = root.getChild("body");
        tailBig = root.getChild("tail_big");
        tailSmall = root.getChild("tail_small");
        tailEnd = root.getChild("tail_end");
        wingL = root.getChild("wing_l");
        wingLTip = wingL.getChild("wing_l_tip");
        wingR = root.getChild("wing_r");
        wingRTip = wingR.getChild("wing_r_tip");
        legsL = legs(root, "l");
        legsR = legs(root, "r");
    }

    private static ModelPart[] legs(ModelPart root, String s) {
        ModelPart fl = root.getChild("front_leg_" + s);
        ModelPart flt = fl.getChild("front_leg_" + s + "_tip");
        ModelPart ff = flt.getChild("front_foot_" + s);
        ModelPart hl = root.getChild("hind_leg_" + s);
        ModelPart hlt = hl.getChild("hind_leg_" + s + "_tip");
        ModelPart hf = hlt.getChild("hind_foot_" + s);
        return new ModelPart[]{fl, flt, ff, hl, hlt, hf};
    }

    @Override
    public boolean shouldRender(EnderDragonEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().expand(30));
    }

    @Override
    protected boolean hasLabel(EnderDragonEntity entity) {
        return false;
    }

    @Override
    public void render(EnderDragonEntity d, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        matrices.push();
        float h = (float) d.getSegmentProperties(7, tickDelta)[0];
        float j = (float) (d.getSegmentProperties(5, tickDelta)[1] - d.getSegmentProperties(10, tickDelta)[1]);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-h));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(j * 10.0f));
        matrices.translate(0.0f, 0.0f, 1.0f);
        float s = DragonRage.SCALE;
        matrices.scale(-s, -s, s);
        matrices.translate(0.0f, -1.501f, 0.0f);
        int overlay = OverlayTexture.getUv(0.0f, d.hurtTime > 0);
        if (d.ticksSinceDeath > 0) {
            float k = d.ticksSinceDeath / 200.0f;
            renderModel(d, tickDelta, matrices, vcp.getBuffer(RenderLayer.getEntityAlpha(EXPLOSION)), light, OverlayTexture.DEFAULT_UV, k, 1f);
            renderModel(d, tickDelta, matrices, vcp.getBuffer(RenderLayer.getEntityDecal(TEXTURE)), light, overlay, 1f, 1f);
        } else {
            renderModel(d, tickDelta, matrices, vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, overlay, 1f, 1f);
        }
        boolean rage = d.getMaxHealth() > 0 && d.getHealth() / d.getMaxHealth() <= 0.3f;
        // свечение «дышит»; в ярости — быстрее, во время рёва горит в полную силу
        float t = d.age + tickDelta;
        float glow = rage ? 0.72f + 0.28f * MathHelper.sin(t * 0.33f) : 0.62f + 0.38f * MathHelper.sin(t * 0.11f);
        glow = Math.max(glow, roar(tickDelta));
        renderModel(d, tickDelta, matrices, vcp.getBuffer(RenderLayer.getEyes(rage ? GLOW_RAGE : GLOW)), light, OverlayTexture.DEFAULT_UV, 1f, glow);
        if (d.ticksSinceDeath > 0) deathRays(d, tickDelta, matrices, vcp);
        matrices.pop();
        if (d.connectedCrystal != null) {
            matrices.push();
            float dx = (float) (d.connectedCrystal.getX() - MathHelper.lerp(tickDelta, d.prevX, d.getX()));
            float dy = (float) (d.connectedCrystal.getY() - MathHelper.lerp(tickDelta, d.prevY, d.getY()));
            float dz = (float) (d.connectedCrystal.getZ() - MathHelper.lerp(tickDelta, d.prevZ, d.getZ()));
            EnderDragonEntityRenderer.renderCrystalBeam(dx, dy + EndCrystalEntityRenderer.getYOffset(d.connectedCrystal, tickDelta), dz,
                    tickDelta, d.age, matrices, vcp, light);
            matrices.pop();
        }
        super.render(d, yaw, tickDelta, matrices, vcp, light);
    }

    /** 0..1: насколько сейчас открыта пасть в рёве (плавно открывается и закрывается). */
    private static float roar(float td) {
        if (!ClientState.active("dragon_roar")) return 0f;
        float p = ClientState.progress("dragon_roar");
        return MathHelper.clamp(MathHelper.sin(p * (float) Math.PI) * 1.6f, 0f, 1f);
    }

    private static float inhale() {
        if (!ClientState.active("dragon_inhale")) return 0f;
        return MathHelper.sin(ClientState.progress("dragon_inhale") * (float) Math.PI * 0.5f);
    }

    private void renderModel(EnderDragonEntity d, float td, MatrixStack m, VertexConsumer vc, int light, int overlay, float alpha, float c) {
        m.push();
        float f = MathHelper.lerp(td, d.prevWingPosition, d.wingPosition);
        float tau = (float) Math.PI * 2;
        float roar = d.ticksSinceDeath > 0 ? 1f : roar(td);
        float inhale = inhale();
        float time = d.age + td;
        jaw.pitch = (float) (Math.sin(f * tau) + 1.0) * 0.22f + 0.08f + roar * 0.85f + inhale * 0.2f;
        float g = (float) (Math.sin(f * tau - 1.0f) + 1.0);
        g = (g * g + g * 2.0f) * 0.05f;
        m.translate(0.0f, g - 2.0f, -3.0f);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * 2.0f));
        float h = 0.0f, i = 20.0f, j = -12.0f;
        double[] ds = d.getSegmentProperties(6, td);
        float l = MathHelper.wrapDegrees((float) (d.getSegmentProperties(5, td)[0] - d.getSegmentProperties(10, td)[0]));
        float mm = MathHelper.wrapDegrees((float) (d.getSegmentProperties(5, td)[0] + (double) (l / 2.0f)));
        float n = f * tau;
        for (int o = 0; o < 5; ++o) {
            double[] es = d.getSegmentProperties(5 - o, td);
            float p = (float) Math.cos(o * 0.45f + n) * 0.15f;
            neck.yaw = MathHelper.wrapDegrees((float) (es[0] - ds[0])) * RAD * 1.5f;
            neck.pitch = p + d.getChangeInNeckPitch(o, ds, es) * RAD * 1.5f * 5.0f;
            neck.roll = -MathHelper.wrapDegrees((float) (es[0] - (double) mm)) * RAD * 1.5f;
            neck.pivotY = i;
            neck.pivotZ = j;
            neck.pivotX = h;
            i += MathHelper.sin(neck.pitch) * 10.0f;
            j -= MathHelper.cos(neck.yaw) * MathHelper.cos(neck.pitch) * 10.0f;
            h -= MathHelper.sin(neck.yaw) * MathHelper.cos(neck.pitch) * 10.0f;
            neck.render(m, vc, light, overlay, c, c, c, alpha);
        }
        head.pivotY = i;
        head.pivotZ = j;
        head.pivotX = h;
        double[] fs = d.getSegmentProperties(0, td);
        head.yaw = MathHelper.wrapDegrees((float) (fs[0] - ds[0])) * RAD;
        head.pitch = MathHelper.wrapDegrees(d.getChangeInNeckPitch(6, ds, fs)) * RAD * 1.5f * 5.0f;
        head.roll = -MathHelper.wrapDegrees((float) (fs[0] - (double) mm)) * RAD;
        // рёв: голова задрана и трясётся; вдох: голова оттянута назад
        head.pitch += -0.45f * roar + 0.3f * inhale;
        head.yaw += MathHelper.sin(time * 1.9f) * 0.12f * roar;
        head.roll += MathHelper.cos(time * 2.3f) * 0.1f * roar;
        head.render(m, vc, light, overlay, c, c, c, alpha);

        m.push();
        m.translate(0.0f, 1.0f, 0.0f);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-l * 1.5f));
        m.translate(0.0f, -1.0f, 0.0f);
        body.roll = 0.0f;
        body.render(m, vc, light, overlay, c, c, c, alpha);
        float q = f * tau;
        wingL.pitch = 0.125f - (float) Math.cos(q) * 0.2f;
        wingL.yaw = -0.25f;
        wingL.roll = -((float) (Math.sin(q) + 0.125)) * 0.8f;
        wingLTip.roll = (float) (Math.sin(q + 2.0f) + 0.5) * 0.75f;
        wingR.pitch = wingL.pitch;
        wingR.yaw = -wingL.yaw;
        wingR.roll = -wingL.roll;
        wingRTip.roll = -wingLTip.roll;
        // на рёве крылья распахиваются шире
        wingL.roll -= roar * 0.35f;
        wingR.roll += roar * 0.35f;
        limbs(m, vc, light, overlay, g, wingL, legsL, alpha, c);
        limbs(m, vc, light, overlay, g, wingR, legsR, alpha, c);
        m.pop();

        float p = 0.0f;
        i = 10.0f;
        j = 60.0f;
        h = 0.0f;
        ds = d.getSegmentProperties(11, td);
        for (int r = 0; r < 12; ++r) {
            fs = d.getSegmentProperties(12 + r, td);
            ModelPart seg = r < 6 ? tailBig : r < 11 ? tailSmall : tailEnd;
            seg.yaw = (MathHelper.wrapDegrees((float) (fs[0] - ds[0])) * 1.5f + 180.0f) * RAD;
            seg.pitch = (p += MathHelper.sin(r * 0.45f + n) * 0.05f) + (float) (fs[1] - ds[1]) * RAD * 1.5f * 5.0f;
            seg.roll = MathHelper.wrapDegrees((float) (fs[0] - (double) mm)) * RAD * 1.5f;
            seg.pivotY = i;
            seg.pivotZ = j;
            seg.pivotX = h;
            i += MathHelper.sin(seg.pitch) * 10.0f;
            j -= MathHelper.cos(seg.yaw) * MathHelper.cos(seg.pitch) * 10.0f;
            h -= MathHelper.sin(seg.yaw) * MathHelper.cos(seg.pitch) * 10.0f;
            seg.render(m, vc, light, overlay, c, c, c, alpha);
        }
        m.pop();
    }

    private static void limbs(MatrixStack m, VertexConsumer vc, int light, int overlay, float offset, ModelPart wing, ModelPart[] legs, float alpha, float c) {
        ModelPart frontLeg = legs[0], frontLegTip = legs[1], frontFoot = legs[2], hindLeg = legs[3], hindLegTip = legs[4], hindFoot = legs[5];
        hindLeg.pitch = 1.0f + offset * 0.1f;
        hindLegTip.pitch = 0.5f + offset * 0.1f;
        hindFoot.pitch = 0.75f + offset * 0.1f;
        frontLeg.pitch = 1.3f + offset * 0.1f;
        frontLegTip.pitch = -0.5f - offset * 0.1f;
        frontFoot.pitch = 0.75f + offset * 0.1f;
        wing.render(m, vc, light, overlay, c, c, c, alpha);
        frontLeg.render(m, vc, light, overlay, c, c, c, alpha);
        hindLeg.render(m, vc, light, overlay, c, c, c, alpha);
    }

    /** Лучи света при смерти — как у ванильного дракона. */
    private static void deathRays(EnderDragonEntity d, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp) {
        float l = (d.ticksSinceDeath + tickDelta) / 200.0f;
        float fade = Math.min(l > 0.8f ? (l - 0.8f) / 0.2f : 0.0f, 1.0f);
        Random random = Random.create(432L);
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLightning());
        matrices.push();
        matrices.translate(0.0f, -1.0f, -2.0f);
        for (int n = 0; n < (l + l * l) / 2.0f * 60.0f; ++n) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(random.nextFloat() * 360.0f));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(random.nextFloat() * 360.0f));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(random.nextFloat() * 360.0f));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(random.nextFloat() * 360.0f));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(random.nextFloat() * 360.0f));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(random.nextFloat() * 360.0f + l * 90.0f));
            float o = random.nextFloat() * 20.0f + 5.0f + fade * 10.0f;
            float p = random.nextFloat() * 2.0f + 1.0f + fade * 2.0f;
            Matrix4f mat = matrices.peek().getPositionMatrix();
            int a = (int) (255.0f * (1.0f - fade));
            source(vc, mat, a);
            negX(vc, mat, o, p);
            posX(vc, mat, o, p);
            source(vc, mat, a);
            posX(vc, mat, o, p);
            posZ(vc, mat, o, p);
            source(vc, mat, a);
            posZ(vc, mat, o, p);
            negX(vc, mat, o, p);
        }
        matrices.pop();
    }

    private static void source(VertexConsumer vc, Matrix4f m, int alpha) {
        vc.vertex(m, 0.0f, 0.0f, 0.0f).color(255, 255, 255, alpha).next();
    }

    private static void negX(VertexConsumer vc, Matrix4f m, float radius, float width) {
        vc.vertex(m, -HALF_SQRT_3 * width, radius, -0.5f * width).color(200, 0, 255, 0).next();
    }

    private static void posX(VertexConsumer vc, Matrix4f m, float radius, float width) {
        vc.vertex(m, HALF_SQRT_3 * width, radius, -0.5f * width).color(200, 0, 255, 0).next();
    }

    private static void posZ(VertexConsumer vc, Matrix4f m, float radius, float width) {
        vc.vertex(m, 0.0f, radius, width).color(200, 0, 255, 0).next();
    }

    @Override
    public Identifier getTexture(EnderDragonEntity entity) {
        return TEXTURE;
    }
}
