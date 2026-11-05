package com.hardest.mixin.client;

import com.hardest.registry.ModItems;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Пока стреляешь из Драконобоя, игрок не замедляется — иначе от атак дракона не увернуться.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean hardest$noCannonSlowdown(ClientPlayerEntity self) {
        return self.isUsingItem() && !self.getActiveItem().isOf(ModItems.DRAGON_CANNON);
    }
}
