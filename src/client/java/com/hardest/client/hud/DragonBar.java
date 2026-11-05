package com.hardest.client.hud;

import com.hardest.client.ClientState;
import com.hardest.dragon.DragonRage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Полоса здоровья Пробуждённого дракона: шире ванильной, с засечками фаз,
 * «следом урона», бликом и числом HP. Остальные боссы рисуются как обычно.
 */
public final class DragonBar {
    private static final float[] PHASES = {0.6f, 0.3f};

    private static final class Trail {
        float shown = 1f;
        float last = 1f;
        float flash;
    }

    private static final Map<UUID, Trail> TRAILS = new HashMap<>();
    /** Нижний край всех полос боссов в прошлом кадре — плашки встают ниже. */
    private static int bottom;

    public static void setBottom(int y) {
        bottom = y;
    }

    public static int hudHeight(MinecraftClient mc) {
        return bottom;
    }

    private static int color(BossBar.Color c) {
        return switch (c) {
            case PINK -> 0xFF4FD8;
            case RED -> 0xFF3030;
            case BLUE -> 0x4FA8FF;
            case GREEN -> 0x5BE07A;
            case YELLOW -> 0xF5C518;
            case WHITE -> 0xE8E8E8;
            default -> 0xB04CFF;
        };
    }

    /** Рисует полосу и возвращает y, с которого можно рисовать следующую. */
    public static int render(DrawContext ctx, TextRenderer tr, ClientBossBar bar, int screenW, int y, float tickDelta) {
        float t = ClientState.ticks + tickDelta;
        int accent = color(bar.getColor());
        float pct = MathHelper.clamp(bar.getPercent(), 0f, 1f);
        Trail tr2 = TRAILS.computeIfAbsent(bar.getUuid(), u -> new Trail());
        if (pct < tr2.last - 0.0005f) tr2.flash = t;
        if (pct > tr2.shown) tr2.shown = pct;
        tr2.last = pct;
        // «след урона» догоняет реальную полосу с задержкой
        if (t - tr2.flash > 12) tr2.shown = Math.max(pct, tr2.shown - 0.006f);

        int barW = Math.min(300, screenW - 40);
        int x = (screenW - barW) / 2;
        int phase = pct > 0.6f ? 1 : pct > 0.3f ? 2 : 3;

        // имя и фаза
        String full = bar.getName().getString();
        int dash = full.indexOf(" — ");
        String name = dash > 0 ? full.substring(0, dash) : full;
        Ui.outlined(ctx, tr, Text.literal(name.toUpperCase()).styled(s -> s.withBold(true)), screenW / 2f, y - 8, 1f, Ui.mix(accent, 0xFFFFFF, 0.55f), 1f, true);

        int by = y + 4;
        int bh = 9;
        // рамка с «рогами» по краям
        ctx.fill(x - 2, by - 2, x + barW + 2, by + bh + 2, 0xF0080510);
        Ui.frame(ctx, x - 2, by - 2, barW + 4, bh + 4, Ui.argb(accent, 0.75f));
        horn(ctx, x - 3, by + bh / 2, -1, accent);
        horn(ctx, x + barW + 2, by + bh / 2, 1, accent);

        // след урона
        int trailW = (int) (barW * tr2.shown);
        ctx.fill(x, by, x + trailW, by + bh, 0xFFFFE9B0);
        // сама полоса: светлая сверху, тёмная снизу
        int fillW = (int) (barW * pct);
        int top = Ui.mix(accent, 0xFFFFFF, 0.25f);
        int low = Ui.mix(accent, 0x000000, 0.45f);
        ctx.fillGradient(x, by, x + fillW, by + bh, 0xFF000000 | top, 0xFF000000 | low);
        ctx.fill(x, by, x + fillW, by + 1, Ui.argb(0xFFFFFF, 0.45f));
        // блик бежит по полосе
        if (fillW > 8) {
            int sx = x + (int) ((t * 3) % (barW + 40)) - 20;
            ctx.enableScissor(x, by, x + fillW, by + bh);
            for (int i = 0; i < 10; i++) {
                ctx.fill(sx + i, by, sx + i + 1, by + bh, Ui.argb(0xFFFFFF, 0.22f * (1 - Math.abs(i - 5) / 5f)));
            }
            ctx.disableScissor();
        }
        // вспышка от удара
        float hit = 1f - (t - tr2.flash) / 6f;
        if (hit > 0) ctx.fill(x, by, x + fillW, by + bh, Ui.argb(0xFFFFFF, hit * 0.6f));

        // засечки фаз
        for (float p : PHASES) {
            int mx = x + (int) (barW * p);
            boolean passed = pct <= p;
            int mc = passed ? 0xFF2A2030 : 0xFFFFFFFF;
            ctx.fill(mx, by - 3, mx + 1, by + bh + 3, mc);
            ctx.fill(mx - 1, by - 4, mx + 2, by - 3, mc);
            ctx.fill(mx - 1, by + bh + 3, mx + 2, by + bh + 4, mc);
        }

        // подписи под полосой
        int ny = by + bh + 5;
        String phaseText = switch (phase) {
            case 1 -> "ФАЗА I";
            case 2 -> "ФАЗА II · ЯРОСТЬ";
            default -> "ФАЗА III · ПОСЛЕДНИЙ РЫВОК";
        };
        float pulse = phase == 3 ? 0.65f + 0.35f * MathHelper.sin(t * 0.25f) : 1f;
        Ui.text(ctx, tr, Text.literal(phaseText), x, ny, phase == 1 ? Ui.MUTED : accent, pulse);
        String hp = Math.round(pct * DragonRage.MAX_HP) + " / " + (int) DragonRage.MAX_HP;
        Ui.text(ctx, tr, Text.literal(hp), x + barW - tr.getWidth(hp), ny, Ui.MUTED, 1f);
        return ny + 16;
    }

    /** Маленький шип-рог у края полосы. dir: -1 влево, 1 вправо. */
    private static void horn(DrawContext ctx, int x, int cy, int dir, int accent) {
        int c = 0xFF000000 | accent;
        for (int i = 0; i < 5; i++) {
            int len = 5 - i;
            int px = x + dir * i;
            ctx.fill(Math.min(px, px + dir), cy - len / 2 - 1, Math.max(px, px + dir) , cy + len / 2 + 1, c);
        }
    }

    private DragonBar() {
    }
}
