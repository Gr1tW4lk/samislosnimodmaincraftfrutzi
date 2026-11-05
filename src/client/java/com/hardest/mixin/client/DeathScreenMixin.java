package com.hardest.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Ванильная красная заливка экрана смерти перекрашивает серый мир в розовый —
 * для «ПОТРАЧЕНО» нужна тёмно-серая, как в GTA.
 */
@Mixin(DeathScreen.class)
public class DeathScreenMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fillGradient(IIIIII)V"))
    private void hardest$grayGradient(DrawContext ctx, int x1, int y1, int x2, int y2, int top, int bottom) {
        ctx.fillGradient(x1, y1, x2, y2, 0x40000000, 0x90101010);
    }
}
