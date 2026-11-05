package com.hardest.client.screen;

import com.hardest.client.ClientState;
import com.hardest.client.hud.Ui;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Экран смерти в духе GTA: мир серый (пост-шейдер), поперёк экрана тёмная полоса
 * и огромное «ПОТРАЧЕНО», которое падает сверху с лёгким ударом.
 */
public final class WastedScreen {
    private static final String[] JOKES = {
            "Налоговая всё равно найдёт", "Гусь доволен", "Мама будет ругаться", "Дракон смеётся где-то в Крае",
            "Это был скилл ишью", "Попробуй Ctrl+Z", "Зато красиво", "Курьер уже везёт новую посылку"
    };
    private static long opened;
    private static String joke = JOKES[0];
    private static String title = "ПОТРАЧЕНО";

    public static void opened(boolean hardcore) {
        opened = ClientState.ticks;
        title = hardcore ? "ПОТРАЧЕНО НАВСЕГДА" : "ПОТРАЧЕНО";
        joke = JOKES[(int) (Math.random() * JOKES.length)];
    }

    public static void render(DrawContext ctx, TextRenderer tr, int w, int h, float delta) {
        float age = ClientState.ticks - opened + delta;
        float in = Ui.easeOut(age / 10f);
        int cy = 58;
        int band = (int) (26 * in);
        // полоса
        ctx.fill(0, cy - band, w, cy + band, Ui.argb(0x000000, 0.62f * in));
        ctx.fill(0, cy - band, w, cy - band + 1, Ui.argb(0x8A0E0E, in));
        ctx.fill(0, cy + band - 1, w, cy + band, Ui.argb(0x8A0E0E, in));
        // надпись падает сверху и чуть «отпружинивает»
        float drop = age < 8 ? 1f - Ui.easeOut(age / 8f) : 0f;
        float bounce = age >= 8 && age < 14 ? MathHelper.sin((age - 8) / 6f * (float) Math.PI) * 0.06f : 0f;
        float scale = Math.min(4.2f, (w - 40f) / Math.max(1, tr.getWidth(title))) * (1f + bounce);
        float y = cy - 4.5f * scale - drop * 60;
        Ui.outlined(ctx, tr, Text.literal(title), w / 2f, y, scale, 0xC8352C, Math.min(1f, age / 4f), true);
        float ja = MathHelper.clamp((age - 14) / 10f, 0f, 1f);
        // под строками «был убит» и «Счёт» ванильного экрана
        Ui.outlined(ctx, tr, Text.literal(joke), w / 2f, 114, 1f, 0xD8D0D0, ja, true);
    }

    private WastedScreen() {
    }
}
