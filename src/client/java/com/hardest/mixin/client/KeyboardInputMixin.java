package com.hardest.mixin.client;

import com.hardest.client.ClientState;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void hardest$mess(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        Input in = (Input) (Object) this;
        if (ClientState.active("loading")) {
            // «не двигайтесь»
            in.movementForward = 0;
            in.movementSideways = 0;
            in.pressingForward = in.pressingBack = in.pressingLeft = in.pressingRight = false;
            in.jumping = false;
            return;
        }
        if (ClientState.active("inverted")) {
            in.movementForward = -in.movementForward;
            in.movementSideways = -in.movementSideways;
            boolean f = in.pressingForward;
            in.pressingForward = in.pressingBack;
            in.pressingBack = f;
            boolean l = in.pressingLeft;
            in.pressingLeft = in.pressingRight;
            in.pressingRight = l;
        }
    }
}
