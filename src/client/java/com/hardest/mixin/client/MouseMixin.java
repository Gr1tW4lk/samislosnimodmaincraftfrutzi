package com.hardest.mixin.client;

import com.hardest.client.ClientState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mouse.class)
public class MouseMixin {
    /** Перепутанное управление переворачивает и мышь. */
    @WrapOperation(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void hardest$invertLook(ClientPlayerEntity player, double dx, double dy, Operation<Void> original) {
        if (ClientState.active("inverted")) {
            original.call(player, -dx, -dy);
        } else {
            original.call(player, dx, dy);
        }
    }
}
