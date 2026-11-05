package com.hardest.mixin.client;

import com.hardest.client.ClientState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    /** Глюк «дышит» полем зрения, турбо — растягивает его. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void hardest$fov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        double fov = cir.getReturnValue();
        float t = ClientState.ticks + tickDelta;
        if (ClientState.active("glitch")) fov += MathHelper.sin(t * 0.3f) * 18.0;
        if (ClientState.active("turbo")) fov += 30.0;
        cir.setReturnValue(fov);
    }
}
