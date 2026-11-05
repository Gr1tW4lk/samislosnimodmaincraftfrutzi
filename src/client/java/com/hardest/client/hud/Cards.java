package com.hardest.client.hud;

import com.hardest.client.ClientState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Большие заголовки мода вместо ванильного title: текст с обводкой «влетает» с увеличением,
 * под ним расходится линия и подзаголовок. В кинорежиме сверху и снизу выезжают чёрные полосы.
 */
public final class Cards {
    private static String title;
    private static String sub;
    private static int color;
    private static int duration;
    private static boolean letterbox;
    private static long start = -1000;

    public static void show(String t, String s, int rgb, int ticks, boolean cinema) {
        title = t;
        sub = s;
        color = rgb;
        duration = Math.max(20, ticks);
        letterbox = cinema;
        start = ClientState.ticks;
    }

    public static void clear() {
        start = -1000;
    }

    /** Насколько выехали полосы (0..1) — HUD под ними можно приглушить. */
    public static float bars(float tickDelta) {
        if (!letterbox) return 0f;
        float age = ClientState.ticks + tickDelta - start;
        if (age < 0 || age > duration) return 0f;
        return Ui.easeOut(Math.min(age / 10f, (duration - age) / 12f));
    }

    public static void render(DrawContext ctx, TextRenderer tr, int w, int h, float tickDelta) {
        float age = ClientState.ticks + tickDelta - start;
        if (title == null || age < 0 || age > duration) return;

        float bars = bars(tickDelta);
        if (bars > 0) {
            int bh = (int) (h * 0.12f * bars);
            ctx.fill(0, 0, w, bh, 0xFF000000);
            ctx.fill(0, h - bh, w, h, 0xFF000000);
            ctx.fill(0, bh, w, bh + 1, Ui.argb(color, 0.6f * bars));
            ctx.fill(0, h - bh - 1, w, h - bh, Ui.argb(color, 0.6f * bars));
        }

        float alpha = Ui.fade(age - 3, duration - 3, 5, 14);
        if (!Ui.visible(alpha)) return;
        int cy = (int) (h * 0.34f);
        // заголовок: влетает с увеличением и чуть дрожит первые тики
        float fit = Math.min(3.2f, (w - 30f) / Math.max(1, tr.getWidth(title) + 2));
        float punch = 1f + 0.35f * (1f - Ui.easeOut((age - 3) / 7f));
        float scale = fit * punch;
        float shake = age < 12 ? (12 - age) / 12f * 1.5f : 0f;
        float sx = MathHelper.sin(age * 3.1f) * shake;
        float sy = MathHelper.cos(age * 2.7f) * shake;
        // мягкая тень-подложка под текстом
        int glowW = (int) (tr.getWidth(title) * fit / 2) + 40;
        ctx.fillGradient(w / 2 - glowW, cy - 14, w / 2 + glowW, cy + 6, Ui.argb(0x000000, 0f), Ui.argb(0x000000, 0.45f * alpha));
        ctx.fillGradient(w / 2 - glowW, cy + 6, w / 2 + glowW, cy + 26, Ui.argb(0x000000, 0.45f * alpha), Ui.argb(0x000000, 0f));
        Ui.outlined(ctx, tr, Text.literal(title), w / 2f + sx, cy - 4.5f * scale + sy, scale, color, alpha, true);

        // линия расходится от центра
        float line = Ui.easeOut((age - 6) / 12f);
        int half = (int) ((tr.getWidth(title) * fit / 2 + 10) * line);
        int ly = cy + (int) (5 * fit) + 2;
        if (half > 0) {
            ctx.fill(w / 2 - half, ly, w / 2 + half, ly + 1, Ui.argb(color, alpha));
            ctx.fill(w / 2 - 2, ly - 1, w / 2 + 2, ly + 2, Ui.argb(color, alpha));
        }
        if (sub != null && !sub.isEmpty()) {
            float sa = alpha * MathHelper.clamp((age - 10) / 8f, 0f, 1f);
            Ui.outlined(ctx, tr, Text.literal(sub), w / 2f, ly + 6, 1.15f, 0xEDE6F5, sa, true);
        }
    }

    private Cards() {
    }
}
