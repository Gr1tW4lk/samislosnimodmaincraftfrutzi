package com.hardest.mixin.client;

import com.hardest.client.ClientState;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    public abstract float getYaw();

    @Shadow
    public abstract float getPitch();

    /** Тряска камеры от ударов босса, взмахов его крыльев и глюков. */
    @Inject(method = "update", at = @At("TAIL"))
    private void hardest$shake(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        float power = 0;
        if (ClientState.active("shake")) {
            power = Math.max(1, ClientState.param("shake")) * (1f - ClientState.progress("shake"));
        }
        if (ClientState.active("quake")) {
            power = Math.max(power, ClientState.param("quake") * (1f - ClientState.progress("quake")));
        }
        if (ClientState.active("glitch")) power = Math.max(power, 0.6f);
        if (power <= 0) return;
        double t = (ClientState.ticks + tickDelta) * 1.7;
        float dy = (float) (Math.sin(t * 3.1) + Math.sin(t * 7.3) * 0.5) * power;
        float dp = (float) (Math.cos(t * 2.7) + Math.sin(t * 5.9) * 0.5) * power;
        setRotation(getYaw() + dy, getPitch() + dp);
    }
}
