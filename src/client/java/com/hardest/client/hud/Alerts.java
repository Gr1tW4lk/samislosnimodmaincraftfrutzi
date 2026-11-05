package com.hardest.client.hud;

import com.hardest.client.ClientState;
import com.hardest.net.Net;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Плашки-предупреждения сверху экрана вместо строки над хотбаром:
 * иконка, заголовок, подсказка «что делать». Опасные мигают и с косой штриховкой.
 */
public final class Alerts {
    private static final int MAX = 3;
    private static final int LIFE = 70;

    private record Alert(Net.Alert kind, ItemStack icon, Text title, Text sub, long start) {
    }

    private static final List<Alert> LIST = new ArrayList<>();

    public static int color(Net.Alert kind) {
        return switch (kind) {
            case DANGER -> 0xFF3B3B;
            case BOSS -> 0xC26BFF;
            case INFO -> 0xF5C518;
            case GOOD -> 0x5BE07A;
            case FUNNY -> 0xFF6FD2;
            case COLD -> 0x7FD3FF;
        };
    }

    public static void add(Net.Alert kind, ItemStack icon, Text title, Text sub) {
        // то же сообщение ещё на экране — просто продлеваем
        LIST.removeIf(a -> a.title.getString().equals(title.getString()));
        LIST.add(0, new Alert(kind, icon, title, sub, ClientState.ticks));
        while (LIST.size() > MAX) LIST.remove(LIST.size() - 1);
    }

    public static void clear() {
        LIST.clear();
    }

    public static void render(DrawContext ctx, TextRenderer tr, int w, int top, float tickDelta) {
        float now = ClientState.ticks + tickDelta;
        int y = top;
        for (Iterator<Alert> it = LIST.iterator(); it.hasNext(); ) {
            Alert a = it.next();
            float age = now - a.start;
            if (age > LIFE) {
                it.remove();
                continue;
            }
            y += draw(ctx, tr, w, y, a, age) + 3;
        }
    }

    /** Возвращает высоту нарисованной плашки. */
    private static int draw(DrawContext ctx, TextRenderer tr, int w, int y, Alert a, float age) {
        int accent = color(a.kind);
        float alpha = Ui.fade(age, LIFE, 4, 10);
        float slide = Ui.easeOut(age / 6f);
        boolean hasSub = !a.sub.getString().isEmpty();
        Text title = a.title.copy().styled(s -> s.withBold(true));
        int textW = Math.max(tr.getWidth(title), hasSub ? tr.getWidth(a.sub) : 0);
        int boxH = hasSub ? 30 : 22;
        int boxW = Math.min(w - 16, textW + 46);
        int x = (w - boxW) / 2;
        int yy = y - (int) ((1 - slide) * 14);

        Ui.panel(ctx, x, yy, boxW, boxH, accent, alpha);
        boolean danger = a.kind == Net.Alert.DANGER;
        if (danger) {
            // мигающая кромка и штриховка снизу
            float blink = 0.5f + 0.5f * MathHelper.sin(age * 0.9f);
            Ui.frame(ctx, x, yy, boxW, boxH, Ui.argb(accent, alpha * (0.35f + 0.65f * blink)));
            Ui.hazard(ctx, x + 2, yy + boxH - 3, boxW - 4, 2, accent, alpha * 0.8f, age * 0.6f);
        }
        if (age < 5) {
            // вспышка при появлении
            ctx.fill(x + 1, yy + 1, x + boxW - 1, yy + boxH - 1, Ui.argb(accent, (1 - age / 5f) * 0.35f * alpha));
        }
        int iconSize = boxH - 8;
        Ui.iconBox(ctx, x + 4, yy + 4, iconSize, a.icon, accent, alpha);
        int tx = x + 8 + iconSize;
        Ui.text(ctx, tr, title, tx, yy + (hasSub ? 5 : 7), accent, alpha);
        if (hasSub) Ui.text(ctx, tr, a.sub, tx, yy + 17, Ui.MUTED, alpha);
        return boxH;
    }

    /** Сколько места сверху занимают полосы боссов — плашки встают под них. */
    public static int topOffset(MinecraftClient mc) {
        int top = 6 + DragonBar.hudHeight(mc);
        // под кинополосой и под вкладками «ТикТока»
        int bars = (int) (mc.getWindow().getScaledHeight() * 0.12f * Cards.bars(mc.getTickDelta()));
        if (bars > 0) top = Math.max(top, bars + 6);
        if (ClientState.active("tiktok")) top += 18;
        return top;
    }

    private Alerts() {
    }
}
