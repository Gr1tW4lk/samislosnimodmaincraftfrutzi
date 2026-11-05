package com.hardest.client.fx;

import com.hardest.HardestMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;

/**
 * «Фриз» для лагов: снимаем кадр и держим его на экране, пока мир живёт дальше.
 */
public final class FreezeFrame {
    private static final Identifier ID = HardestMod.id("freeze_frame");
    private static NativeImageBackedTexture texture;
    private static int texW, texH;
    private static boolean captured;

    public static void draw(DrawContext ctx, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!captured) {
            capture(mc);
            captured = true;
            return;
        }
        if (texture != null) {
            ctx.drawTexture(ID, 0, 0, w, h, 0, 0, texW, texH, texW, texH);
        }
    }

    private static void capture(MinecraftClient mc) {
        release();
        try {
            NativeImage img = ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());
            texW = img.getWidth();
            texH = img.getHeight();
            texture = new NativeImageBackedTexture(img);
            mc.getTextureManager().registerTexture(ID, texture);
        } catch (Exception e) {
            texture = null;
        }
    }

    /** Вызывается, когда эффект закончился. */
    public static void reset() {
        if (captured) {
            captured = false;
            release();
        }
    }

    private static void release() {
        if (texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(ID);
            texture = null;
        }
    }

    private FreezeFrame() {
    }
}
