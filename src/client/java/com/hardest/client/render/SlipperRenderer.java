package com.hardest.client.render;

import com.hardest.entity.SlipperEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Летящий тапок крутится, как бумеранг.
 */
public class SlipperRenderer extends EntityRenderer<SlipperEntity> {
    public SlipperRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SlipperEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        matrices.push();
        matrices.translate(0, 0.15, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((e.age + tickDelta) * 45f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        matrices.scale(1.3f, 1.3f, 1.3f);
        MinecraftClient.getInstance().getItemRenderer().renderItem(e.getStack(), ModelTransformationMode.FIXED, light, OverlayTexture.DEFAULT_UV,
                matrices, vcp, e.getWorld(), e.getId());
        matrices.pop();
        super.render(e, yaw, tickDelta, matrices, vcp, light);
    }

    @Override
    public Identifier getTexture(SlipperEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
