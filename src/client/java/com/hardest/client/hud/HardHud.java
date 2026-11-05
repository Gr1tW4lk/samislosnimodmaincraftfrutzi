package com.hardest.client.hud;

import com.hardest.client.ClientState;
import com.hardest.client.fx.FreezeFrame;
import com.hardest.registry.ModBlocks;
import com.hardest.registry.ModEffects;
import com.hardest.registry.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.List;

/**
 * Всё, что мод рисует поверх игры: реплики персонажей, плашки, заголовки и экранные безумия.
 */
public final class HardHud {
    private static final String[] COMMENTS = {
            "бро упал", "ахахахах", "он не пройдёт", "кто в 2026?", "мод имба", "гусь лучший персонаж",
            "где алмазы", "ставлю лайк не глядя", "это постанова", "автор живи", "легенда", "я бы прошёл",
            "чел...", "F", "налоговая уже едет", "мама с тапком > дракон", "минус уши",
            "подписался", "а где гусь", "ахах крипер сзади", "скилл ишью", "го 2 часть", "дракон тебя ждёт"
    };
    private static final String[] TIPS_NETHER = {
            "Гасты стреляют тройным залпом. Прячься за углом.",
            "Лаги в Незере — это не баг. Это фича. Не стой у лавы.",
            "Если пинг 999 — замри и молись.",
    };
    private static final String[] TIPS_END = {
            "Красный круг на земле — место удара. Не стой в нём.",
            "Перед рёвом дракон вдыхает. Подними щит.",
            "Ударную волну можно перепрыгнуть.",
            "Пока дракон сидит на портале — бей в голову.",
            "Мамин тапок наносит дракону тройной урон.",
    };

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        float t = ClientState.ticks + tickDelta;

        if (ClientState.active("freeze")) FreezeFrame.draw(ctx, w, h);
        if (ClientState.active("bloodmoon")) vignette(ctx, w, h, (int) (110 + 40 * MathHelper.sin(t * 0.05f)) << 24 | 0x8A0000);
        if (mc.player.hasStatusEffect(ModEffects.COLD)) vignette(ctx, w, h, 0x6080C8F0);
        if (ClientState.active("end_rage") && mc.world.getRegistryKey() == World.END) {
            vignette(ctx, w, h, (int) (70 + 45 * MathHelper.sin(t * 0.08f)) << 24 | 0x700010);
        }
        if (ClientState.active("dragon_roar")) {
            float k = MathHelper.sin(ClientState.progress("dragon_roar") * (float) Math.PI);
            vignette(ctx, w, h, (int) (k * 120) << 24 | 0x3A0050);
        }
        if (ClientState.active("flash")) {
            float k = 1f - ClientState.progress("flash");
            ctx.fill(0, 0, w, h, (int) (k * k * 235) << 24 | 0xFFF4FF);
        }
        if (ClientState.active("sneeze")) {
            float k = 1f - ClientState.progress("sneeze");
            ctx.fill(0, 0, w, h, (int) (k * 170) << 24 | 0xFFFFFF);
        }
        if (ClientState.active("loading")) {
            // поверх хотбара и чата: у предметов в GUI своя глубина, поэтому поднимаем слой
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 400);
            renderLoading(ctx, tr, w, h, t);
            Alerts.render(ctx, tr, w, Alerts.topOffset(mc), tickDelta);
            ctx.getMatrices().pop();
            return;
        }
        if (ClientState.active("glitch")) renderGlitch(ctx, tr, w, h);
        if (ClientState.active("tiktok")) renderTikTok(ctx, tr, w, h, t);
        if (ClientState.active("potato")) renderPotato(ctx, tr, t);
        if (ClientState.active("inverted")) renderInverted(ctx, tr, w, h, t);
        if (ClientState.active("turbo")) renderTurbo(ctx, w, h, t);
        if (ClientState.active("lag")) renderLag(ctx, tr, w, h, t);
        if (ClientState.active("ctrlz")) renderCtrlZ(ctx, tr, w, h, t);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 400);
        Cards.render(ctx, tr, w, h, tickDelta);
        Alerts.render(ctx, tr, w, Alerts.topOffset(mc), tickDelta);
        ctx.getMatrices().pop();
        renderSubtitle(ctx, tr, w, h);
    }

    private static int safeWidth(int w, int h) {
        return ClientState.active("tiktok") ? Math.min(w, (int) (h * 9f / 16f)) : w;
    }

    // ---------------------------------------------------------------- реплики персонажей

    private static void renderSubtitle(DrawContext ctx, TextRenderer tr, int w, int h) {
        if (!ClientState.talking()) return;
        long age = ClientState.ticks - ClientState.lineStart;
        long left = ClientState.lineDuration - age;
        float alpha = Math.min(1f, Math.min(age / 4f, left / 8f));
        if (!Ui.visible(alpha)) return;
        int accent;
        String name;
        ItemStack icon;
        switch (ClientState.speaker) {
            case "tax" -> {
                accent = 0x8FB4FF;
                name = "Налоговый инспектор";
                icon = new ItemStack(ModItems.TAX_RECEIPT);
            }
            case "courier" -> {
                accent = 0xF5C518;
                name = "Курьер «Быстро и Взрывно»";
                icon = new ItemStack(ModBlocks.GIFT_BOX);
            }
            default -> {
                accent = 0xFF4A4A;
                name = MinecraftClient.getInstance().player.getName().getString() + "?";
                icon = new ItemStack(Items.PLAYER_HEAD);
            }
        }
        String full = Text.translatable("hardest.voice." + ClientState.lineId).getString();
        int boxW = Math.min(300, safeWidth(w, h) - 20);
        List<OrderedText> lines = tr.wrapLines(StringVisitable.plain(full), boxW - 46);
        int boxH = Math.max(34, 20 + lines.size() * 10);
        int x = (w - boxW) / 2;
        // выше хотбара, полоски опыта и строки над хотбаром
        int y = h - 78 - boxH;
        float slide = Ui.easeOut(age / 5f);
        y += (int) ((1 - slide) * 8);

        Ui.panel(ctx, x, y, boxW, boxH, accent, alpha);
        Ui.iconBox(ctx, x + 6, y + 6, 26, icon, accent, alpha);
        // табличка с именем, «вросшая» в верхнюю кромку
        Text nameText = Text.literal(name).styled(s -> s.withBold(true));
        int nw = tr.getWidth(nameText) + 10;
        ctx.fill(x + 38, y - 5, x + 38 + nw, y + 7, Ui.argb(Ui.mix(Ui.BG, accent, 0.35f), alpha));
        Ui.frame(ctx, x + 38, y - 5, nw, 12, Ui.argb(accent, alpha));
        Ui.text(ctx, tr, nameText, x + 43, y - 3, accent, alpha);

        int ly = y + 11;
        int reveal = (int) (age * 1.8f + 2);
        int shown = 0;
        int total = 0;
        for (OrderedText line : lines) {
            String s = plain(line);
            total += s.length();
            int can = Math.max(0, Math.min(s.length(), reveal - shown));
            shown += s.length();
            if (can > 0) Ui.text(ctx, tr, Text.literal(s.substring(0, can)), x + 40, ly, Ui.WHITE, alpha);
            ly += 10;
        }
        if (reveal >= total && (ClientState.ticks / 8) % 2 == 0) {
            Ui.text(ctx, tr, Text.literal("▼"), x + boxW - 11, y + boxH - 11, accent, alpha);
        }
    }

    private static String plain(OrderedText line) {
        StringBuilder sb = new StringBuilder();
        line.accept((index, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        return sb.toString();
    }

    // ---------------------------------------------------------------- эффекты

    private static void vignette(DrawContext ctx, int w, int h, int color) {
        int edge = Math.min(w, h) / 4;
        int transparent = color & 0x00FFFFFF;
        ctx.fillGradient(0, 0, w, edge, color, transparent);
        ctx.fillGradient(0, h - edge, w, h, transparent, color);
        for (int i = 0; i < edge; i += 2) {
            int a = (int) (((color >>> 24) & 0xFF) * (1f - i / (float) edge));
            ctx.fill(i, 0, i + 2, h, a << 24 | transparent);
            ctx.fill(w - i - 2, 0, w - i, h, a << 24 | transparent);
        }
    }

    private static void renderGlitch(DrawContext ctx, TextRenderer tr, int w, int h) {
        Random r = Random.create(ClientState.ticks / 2);
        int bars = 3 + r.nextInt(6);
        for (int i = 0; i < bars; i++) {
            int y = r.nextInt(h);
            int bh = 1 + r.nextInt(6);
            int x = r.nextInt(w / 2);
            int col = switch (r.nextInt(4)) {
                case 0 -> 0x60FF00FF;
                case 1 -> 0x6000FFFF;
                case 2 -> 0x50FFFFFF;
                default -> 0x70000000;
            };
            ctx.fill(x, y, x + r.nextInt(w), y + bh, col);
        }
        if (r.nextInt(3) == 0) {
            // окно ошибки в стиле Windows
            String[] errs = {"Реальность не найдена", "Недостаточно памяти", "Файл «гриб.exe» повреждён", "Гравитация не отвечает"};
            String err = errs[r.nextInt(errs.length)];
            int bw = Math.max(150, tr.getWidth(err) + 40), bh = 44;
            int bx = r.nextInt(Math.max(1, w - bw));
            int by = r.nextInt(Math.max(1, h - bh));
            ctx.fill(bx, by, bx + bw, by + bh, 0xF0E8E8E8);
            ctx.fill(bx, by, bx + bw, by + 11, 0xFF1060C0);
            ctx.fill(bx + bw - 11, by + 1, bx + bw - 1, by + 10, 0xFFD04040);
            ctx.drawText(tr, Text.literal("x"), bx + bw - 8, by + 1, 0xFFFFFFFF, false);
            ctx.drawText(tr, Text.literal("Майнкрафт.exe"), bx + 3, by + 2, 0xFFFFFFFF, false);
            ctx.fill(bx + 6, by + 16, bx + 18, by + 28, 0xFFD03030);
            ctx.drawText(tr, Text.literal("!"), bx + 11, by + 18, 0xFFFFFFFF, false);
            ctx.drawText(tr, Text.literal(err), bx + 24, by + 18, 0xFF000000, false);
            ctx.fill(bx + bw - 34, by + 31, bx + bw - 6, by + 41, 0xFFD0D0D0);
            Ui.frame(ctx, bx + bw - 34, by + 31, 28, 10, 0xFF808080);
            ctx.drawText(tr, Text.literal("ОК"), bx + bw - 25, by + 32, 0xFF000000, false);
        }
    }

    private static void renderTikTok(DrawContext ctx, TextRenderer tr, int w, int h, float t) {
        int vw = Math.min(w, (int) (h * 9f / 16f));
        int bar = (w - vw) / 2;
        ctx.fill(0, 0, bar, h, 0xFF000000);
        ctx.fill(w - bar, 0, w, h, 0xFF000000);
        // верхние вкладки
        Ui.centered(ctx, tr, Text.literal("Подписки   ").append(Text.literal("Рекомендации").styled(s -> s.withBold(true))), w / 2, 8, 0xFFFFFF, 1f);
        ctx.fill(w / 2 + 6, 19, w / 2 + 40, 20, 0xFFFFFFFF);
        int right = w - bar - 16;
        int iy = h / 2 - 54;
        // аватар с плюсиком
        ctx.fill(right - 8, iy - 4, right + 8, iy + 12, 0xFFFFFFFF);
        ctx.fill(right - 7, iy - 3, right + 7, iy + 11, 0xFF6A3FA0);
        ctx.drawItem(new ItemStack(ModItems.SLIPPER), right - 8, iy - 4);
        ctx.fill(right - 4, iy + 9, right + 4, iy + 15, 0xFFFF2D55);
        ctx.drawText(tr, Text.literal("+"), right - 2, iy + 8, 0xFFFFFFFF, false);
        String[] labels = {"1.3M", "48K", "12K"};
        for (int i = 0; i < 3; i++) {
            int cy = iy + 30 + i * 34;
            switch (i) {
                case 0 -> drawHeart(ctx, right - 7, cy, 2, 0xFFFF2D55);
                case 1 -> drawBubble(ctx, right - 7, cy);
                default -> drawShare(ctx, right - 7, cy);
            }
            Ui.centered(ctx, tr, Text.literal(labels[i]), right, cy + 15, 0xFFFFFF, 1f);
        }
        int lx = bar + 6;
        // подписи — над хотбаром и сердечками
        int capY = h - 92;
        Ui.text(ctx, tr, Text.literal("@самый_сложный_мод").styled(s -> s.withBold(true)), lx, capY, 0xFFFFFF, 1f);
        Ui.text(ctx, tr, Text.literal("человек пытается выжить #майнкрафт"), lx, capY + 11, 0xDDDDDD, 1f);
        Ui.text(ctx, tr, Text.literal("♫ оригинальный звук — гусь"), lx, capY + 22, 0xBBBBBB, 1f);
        int base = (int) (t / 25);
        for (int i = 0; i < 3; i++) {
            String c = COMMENTS[(base + i * 7) % COMMENTS.length];
            int cy = h - 136 + i * 12;
            Text line = Text.literal("user" + (base * 31 + i * 17) % 9000 + "  ").styled(s -> s.withColor(0xAAAAAA)).append(Text.literal(c).styled(s -> s.withColor(0xFFFFFF)));
            int lw = tr.getWidth(line) + 6;
            ctx.fill(lx - 2, cy - 2, lx - 2 + lw, cy + 9, 0x50000000);
            ctx.drawText(tr, line, lx + 1, cy, 0xFFFFFFFF, true);
        }
        for (int i = 0; i < 6; i++) {
            float life = ((t + i * 13) % 60) / 60f;
            int hx = right - 18 - (int) (MathHelper.sin((t + i * 40) * 0.1f) * 8);
            int hy = (int) (h - 60 - life * 140);
            drawHeart(ctx, hx, hy, 1, (int) ((1 - life) * 255) << 24 | 0xFF2D55);
        }
        ctx.fill(bar, h - 3, w - bar, h - 1, 0x40FFFFFF);
        ctx.fill(bar, h - 3, bar + (int) (vw * ClientState.progress("tiktok")), h - 1, 0xFFFFFFFF);
    }

    private static void drawHeart(DrawContext ctx, int x, int y, int s, int color) {
        int[][] px = {{1, 0}, {2, 0}, {4, 0}, {5, 0}, {0, 1}, {1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1}, {6, 1},
                {0, 2}, {1, 2}, {2, 2}, {3, 2}, {4, 2}, {5, 2}, {6, 2}, {1, 3}, {2, 3}, {3, 3}, {4, 3}, {5, 3},
                {2, 4}, {3, 4}, {4, 4}, {3, 5}};
        for (int[] p : px) ctx.fill(x + p[0] * s, y + p[1] * s, x + p[0] * s + s, y + p[1] * s + s, color);
    }

    /** Облачко комментариев. */
    private static void drawBubble(DrawContext ctx, int x, int y) {
        ctx.fill(x + 1, y, x + 13, y + 9, 0xFFFFFFFF);
        ctx.fill(x, y + 1, x + 14, y + 8, 0xFFFFFFFF);
        ctx.fill(x + 3, y + 9, x + 6, y + 11, 0xFFFFFFFF);
        ctx.fill(x + 3, y + 4, x + 5, y + 6, 0xFF222222);
        ctx.fill(x + 6, y + 4, x + 8, y + 6, 0xFF222222);
        ctx.fill(x + 9, y + 4, x + 11, y + 6, 0xFF222222);
    }

    /** Стрелка «поделиться». */
    private static void drawShare(DrawContext ctx, int x, int y) {
        ctx.fill(x + 8, y, x + 9, y + 11, 0xFFFFFFFF);
        for (int i = 0; i < 6; i++) ctx.fill(x + 8 + i, y + i, x + 9 + i, y + 11 - i, 0xFFFFFFFF);
        ctx.fill(x + 2, y + 4, x + 8, y + 7, 0xFFFFFFFF);
        ctx.fill(x, y + 6, x + 3, y + 11, 0xFFFFFFFF);
    }

    private static void renderPotato(DrawContext ctx, TextRenderer tr, float t) {
        int x = 6, y = 6;
        Ui.panel(ctx, x, y, 132, 34, 0xD27D2D, 1f);
        Ui.iconBox(ctx, x + 5, y + 5, 24, new ItemStack(Items.BAKED_POTATO), 0xD27D2D, 1f);
        Ui.text(ctx, tr, Text.literal("Графика: КАРТОШКА").styled(s -> s.withBold(true)), x + 34, y + 7, 0xF0B060, 1f);
        int fps = 5 + (int) (Math.abs(MathHelper.sin(t * 0.13f)) * 4);
        Ui.text(ctx, tr, Text.literal("FPS: " + fps + "   Тени: нет"), x + 34, y + 19, Ui.MUTED, 1f);
    }

    private static void renderInverted(DrawContext ctx, TextRenderer tr, int w, int h, float t) {
        Text s = Text.literal("W ⇄ S     A ⇄ D").styled(st -> st.withBold(true));
        int bw = tr.getWidth(s) + 16;
        int x = (w - bw) / 2, y = h / 2 + 14;
        Ui.panel(ctx, x, y, bw, 15, 0xFF5555, 0.9f);
        Ui.centered(ctx, tr, s, w / 2, y + 4, 0xFF7070, 0.75f + 0.25f * MathHelper.sin(t * 0.4f));
    }

    private static void renderTurbo(DrawContext ctx, int w, int h, float t) {
        Random r = Random.create((long) (t / 2));
        for (int i = 0; i < 24; i++) {
            int y = r.nextInt(h);
            int len = 20 + r.nextInt(60);
            boolean left = r.nextBoolean();
            int x = left ? r.nextInt(w / 4) : w - r.nextInt(w / 4) - len;
            ctx.fill(x, y, x + len, y + 1, 0x80FFFFFF);
        }
    }

    /** Значок плохой связи в углу и мигающее «Соединение потеряно». */
    private static void renderLag(DrawContext ctx, TextRenderer tr, int w, int h, float t) {
        int ping = 800 + (int) (Math.abs(MathHelper.sin(t * 0.07f)) * 1400);
        Text lost = Text.literal("Потеряно пакетов: " + (30 + (int) (t * 1.3) % 40) + "%");
        int pw = tr.getWidth(lost) + 40, ph = 30;
        int x = w - pw - 6, y = h / 3;
        Ui.panel(ctx, x, y, pw, ph, 0xFF4040, 1f);
        // полоски сигнала: горит одна
        for (int i = 0; i < 4; i++) {
            int bh = 4 + i * 4;
            ctx.fill(x + 7 + i * 5, y + 23 - bh, x + 10 + i * 5, y + 23, i == 0 ? 0xFFFF4040 : 0xFF3A3040);
        }
        Ui.text(ctx, tr, Text.literal("ПИНГ " + ping + " мс").styled(s -> s.withBold(true)), x + 32, y + 6, 0xFF5050, 1f);
        Ui.text(ctx, tr, lost, x + 32, y + 17, Ui.MUTED, 1f);
        if ((ClientState.ticks / 15) % 2 == 0) {
            Ui.outlined(ctx, tr, Text.literal("Соединение потеряно..."), w / 2f, h / 2f - 34, 1.3f, 0xFFE070, 1f, true);
        }
    }

    private static void renderLoading(DrawContext ctx, TextRenderer tr, int w, int h, float t) {
        int which = ClientState.param("loading");
        float prog = ClientState.progress("loading");
        int percent = prog < 0.85f ? (int) (prog / 0.85f * 99) : (prog > 0.985f ? 100 : 99);
        int top = which == 1 ? 0xFF3A0A06 : which == 2 ? 0xFF1C0A30 : 0xFF0A3A7A;
        int low = which == 1 ? 0xFF0C0202 : which == 2 ? 0xFF05020A : 0xFF041A3A;
        int accent = which == 1 ? 0xFF6A3A : which == 2 ? 0xC26BFF : 0x6FB8FF;
        ctx.fillGradient(0, 0, w, h, top, low);
        int cx = w / 2;
        int cy = h / 2 - 34;
        // спиннер как у обновлений Windows: точки догоняют друг друга
        for (int i = 0; i < 5; i++) {
            float phase = (t * 0.045f - i * 0.075f) % 1f;
            if (phase < 0) phase += 1f;
            float eased = phase < 0.5f ? 2 * phase * phase : 1 - (float) Math.pow(-2 * phase + 2, 2) / 2;
            double ang = eased * Math.PI * 2 - Math.PI / 2;
            int dx = (int) (Math.cos(ang) * 16);
            int dy = (int) (Math.sin(ang) * 16);
            ctx.fill(cx + dx - 2, cy + dy - 2, cx + dx + 2, cy + dy + 2, 0xFFFFFFFF);
        }
        String title = which == 1 ? "Загружаем Незер" : which == 2 ? "Загружаем Край" : "Работаем с обновлениями";
        String sub = which == 1 ? "Устанавливается: «Лава, гасты и страдания 2.0»"
                : which == 2 ? "Дракон уже проснулся. Он ждёт." : "Не выключайте компьютер";
        Ui.outlined(ctx, tr, Text.literal(title), cx, cy + 26, 1.3f, 0xFFFFFF, 1f, true);
        Ui.outlined(ctx, tr, Text.literal(percent + "% выполнено"), cx, cy + 42, 2.4f, 0xFFFFFF, 1f, true);
        // полоса прогресса со штриховкой
        int bw = Math.min(220, w - 60), bx = cx - bw / 2, by = cy + 70;
        ctx.fill(bx - 1, by - 1, bx + bw + 1, by + 6, 0x80000000);
        int fw = (int) (bw * percent / 100f);
        ctx.fill(bx, by, bx + fw, by + 5, 0xFF000000 | accent);
        Ui.hazard(ctx, bx, by, fw, 5, 0xFFFFFF, 0.18f, t * 0.8f);
        Ui.centered(ctx, tr, Text.literal("Не выключайте компьютер. И не двигайтесь."), cx, by + 13, 0xDDDDDD, 1f);
        Ui.centered(ctx, tr, Text.literal(sub), cx, by + 25, 0xAAAAAA, 1f);
        // совет внизу
        String[] tips = which == 2 ? TIPS_END : TIPS_NETHER;
        String tip = tips[(int) (t / 70) % tips.length];
        Text tipText = Text.literal("СОВЕТ  ").styled(s -> s.withBold(true).withColor(accent)).append(Text.literal(tip).styled(s -> s.withColor(0xE0E0E0)));
        int tw = tr.getWidth(tipText) + 20;
        int tx = cx - tw / 2, ty = h - 52;
        Ui.panel(ctx, tx, ty, tw, 18, accent, 1f);
        ctx.drawText(tr, tipText, tx + 10, ty + 5, 0xFFFFFFFF, true);
    }

    /** Ctrl+Z: перемотка как на VHS — полосы, шум, таймкод. */
    private static void renderCtrlZ(DrawContext ctx, TextRenderer tr, int w, int h, float t) {
        float p = ClientState.progress("ctrlz");
        float a = 1f - p;
        ctx.fill(0, 0, w, h, Ui.argb(0x3A1070, 0.45f * a));
        for (int y = 0; y < h; y += 3) ctx.fill(0, y, w, y + 1, Ui.argb(0x000000, 0.25f * a));
        // полоса «трекинга» ползёт вверх
        int band = (int) (h - (t * 9) % (h + 40));
        Random r = Random.create((long) t);
        for (int i = 0; i < 18; i++) {
            int y = band + r.nextInt(24);
            int x = r.nextInt(w);
            ctx.fill(x, y, x + 10 + r.nextInt(60), y + 1, Ui.argb(0xFFFFFF, 0.5f * a));
        }
        Ui.outlined(ctx, tr, Text.literal("◀◀ ПЕРЕМОТКА"), 16, 16, 2f, 0xFFFFFF, a, false);
        Ui.outlined(ctx, tr, Text.literal("CTRL+Z"), w / 2f, h / 2f - 40, 3.5f + p, 0xE8C8FF, a, true);
        int sec = Math.max(0, (int) (5 - p * 5));
        Ui.outlined(ctx, tr, Text.literal(String.format("00:00:%02d", sec)), w - 16 - tr.getWidth("00:00:00") * 1.5f, h - 34, 1.5f, 0xFFFFFF, a, false);
    }

    private HardHud() {
    }
}
