package com.hardest.mixin.client;

import com.hardest.client.hud.DragonBar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

/**
 * Полоса боя с драконом рисуется по-своему (её узнаём по флагу драконьей музыки),
 * остальные — как в ванилле. Заодно запоминаем, сколько места заняли полосы.
 */
@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    Map<UUID, ClientBossBar> bossBars;

    @Shadow
    protected abstract void renderBossBar(DrawContext context, int x, int y, BossBar bossBar);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hardest$render(DrawContext ctx, CallbackInfo ci) {
        ci.cancel();
        if (bossBars.isEmpty()) {
            DragonBar.setBottom(0);
            return;
        }
        int w = ctx.getScaledWindowWidth();
        int y = 12;
        float delta = client.getTickDelta();
        for (ClientBossBar bar : bossBars.values()) {
            if (bar.hasDragonMusic()) {
                y = DragonBar.render(ctx, client.textRenderer, bar, w, y + 6, delta);
            } else {
                renderBossBar(ctx, w / 2 - 91, y, bar);
                Text name = bar.getName();
                ctx.drawTextWithShadow(client.textRenderer, name, w / 2 - client.textRenderer.getWidth(name) / 2, y - 9, 0xFFFFFF);
                y += 10 + client.textRenderer.fontHeight;
            }
            if (y >= ctx.getScaledWindowHeight() / 3) break;
        }
        DragonBar.setBottom(y);
    }
}
