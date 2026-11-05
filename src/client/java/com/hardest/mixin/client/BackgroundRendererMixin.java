package com.hardest.mixin.client;

import com.hardest.client.ClientState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Кровавая луна и ярость дракона красят туман и цвет «неба у горизонта» в красный.
 */
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {
    @Shadow
    private static float red;
    @Shadow
    private static float green;
    @Shadow
    private static float blue;

    @Inject(method = "render", at = @At("TAIL"))
    private static void hardest$bloodFog(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
        if (ClientState.active("end_rage") && world.getRegistryKey() == World.END) {
            // последний рывок дракона: туман Края наливается кровью
            float pulse = 0.5f + 0.5f * MathHelper.sin((ClientState.ticks + tickDelta) * 0.06f);
            red = Math.min(1f, red * 0.5f + 0.22f + 0.08f * pulse);
            green = green * 0.3f;
            blue = blue * 0.5f + 0.03f;
            RenderSystem.clearColor(red, green, blue, 0.0f);
            return;
        }
        if (!ClientState.active("bloodmoon")) return;
        red = Math.min(1f, red * 0.5f + 0.35f);
        green = green * 0.25f;
        blue = blue * 0.25f;
        RenderSystem.clearColor(red, green, blue, 0.0f);
    }
}
