package com.hardest.client.hud;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Общий стиль интерфейса мода: тёмные панели со срезанными углами и цветной кромкой,
 * текст с обводкой, рамки для иконок. Всё рисуется кодом, без текстур.
 */
public final class Ui {
    public static final int BG = 0x0E0A14;
    public static final int WHITE = 0xFFFFFF;
    public static final int MUTED = 0xB8B0C8;

    /** Цвет с прозрачностью alpha (0..1). Текст почти без альфы Minecraft рисует непрозрачным, поэтому его прячем через visible(). */
    public static int argb(int rgb, float alpha) {
        int a = MathHelper.clamp((int) (alpha * 255), 0, 255);
        return a << 24 | (rgb & 0xFFFFFF);
    }

    public static boolean visible(float alpha) {
        return alpha > 0.03f;
    }

    /** Смешать два цвета. */
    public static int mix(int a, int b, float t) {
        int r = (int) MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) MathHelper.lerp(t, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }

    /** Панель: тёмный фон, срезанные углы, тонкая внутренняя рамка в цвет акцента, яркая полоса сверху. */
    public static void panel(DrawContext ctx, int x, int y, int w, int h, int accent, float alpha) {
        int bg = argb(BG, 0.86f * alpha);
        ctx.fill(x + 1, y, x + w - 1, y + h, bg);
        ctx.fill(x, y + 1, x + 1, y + h - 1, bg);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, bg);
        frame(ctx, x + 1, y + 1, w - 2, h - 2, argb(accent, 0.28f * alpha));
        ctx.fill(x + 2, y, x + w - 2, y + 1, argb(accent, alpha));
        // уголки-скобы — «прицельная» рамка
        int c = argb(accent, alpha);
        ctx.fill(x, y + 1, x + 4, y + 2, c);
        ctx.fill(x + 1, y + 1, x + 2, y + 4, c);
        ctx.fill(x + w - 4, y + 1, x + w, y + 2, c);
        ctx.fill(x + w - 2, y + 1, x + w - 1, y + 4, c);
        ctx.fill(x, y + h - 2, x + 4, y + h - 1, c);
        ctx.fill(x + 1, y + h - 4, x + 2, y + h - 1, c);
        ctx.fill(x + w - 4, y + h - 2, x + w, y + h - 1, c);
        ctx.fill(x + w - 2, y + h - 4, x + w - 1, y + h - 1, c);
    }

    public static void frame(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Квадратная рамка под иконку предмета. */
    public static void iconBox(DrawContext ctx, int x, int y, int size, ItemStack icon, int accent, float alpha) {
        ctx.fill(x, y, x + size, y + size, argb(Ui.mix(BG, accent, 0.22f), 0.95f * alpha));
        frame(ctx, x, y, size, size, argb(accent, 0.9f * alpha));
        if (alpha > 0.5f && !icon.isEmpty()) {
            ctx.getMatrices().push();
            float s = (size - 4) / 16f;
            ctx.getMatrices().translate(x + size / 2f, y + size / 2f, 0);
            ctx.getMatrices().scale(s, s, 1);
            ctx.drawItem(icon, -8, -8);
            ctx.getMatrices().pop();
        }
    }

    /** Текст с чёрной обводкой. x — центр (centered) или левый край; scale — масштаб. */
    public static void outlined(DrawContext ctx, TextRenderer tr, Text text, float x, float y, float scale, int rgb, float alpha, boolean centered) {
        if (!visible(alpha)) return;
        Text plain = Text.literal(text.getString());
        int w = tr.getWidth(text);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        int x0 = centered ? -w / 2 : 0;
        int outline = argb(0x000000, alpha * 0.9f);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) ctx.drawText(tr, plain, x0 + dx, dy, outline, false);
            }
        }
        ctx.drawText(tr, text, x0, 0, argb(rgb, alpha), false);
        ctx.getMatrices().pop();
    }

    /** Обычный текст с тенью и прозрачностью. */
    public static void text(DrawContext ctx, TextRenderer tr, Text text, int x, int y, int rgb, float alpha) {
        if (visible(alpha)) ctx.drawText(tr, text, x, y, argb(rgb, alpha), true);
    }

    public static void centered(DrawContext ctx, TextRenderer tr, Text text, int cx, int y, int rgb, float alpha) {
        if (visible(alpha)) ctx.drawText(tr, text, cx - tr.getWidth(text) / 2, y, argb(rgb, alpha), true);
    }

    /** Полоска с косой штриховкой («опасная зона»). */
    public static void hazard(DrawContext ctx, int x, int y, int w, int h, int color, float alpha, float shift) {
        ctx.enableScissor(x, y, x + w, y + h);
        int off = (int) shift % 8;
        for (int i = -h - 8; i < w + h; i += 8) {
            for (int j = 0; j < h; j++) {
                int px = x + i + off + j;
                ctx.fill(px, y + h - 1 - j, px + 4, y + h - j, argb(color, alpha));
            }
        }
        ctx.disableScissor();
    }

    /** Плавное появление: 0 → 1 за fadeIn тиков, 1 → 0 за fadeOut тиков до конца. */
    public static float fade(float age, float total, float fadeIn, float fadeOut) {
        float a = Math.min(1f, age / Math.max(1f, fadeIn));
        float b = Math.min(1f, (total - age) / Math.max(1f, fadeOut));
        return MathHelper.clamp(Math.min(a, b), 0f, 1f);
    }

    /** Ease-out (быстро в начале, мягко в конце). */
    public static float easeOut(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private Ui() {
    }
}
