package com.hardest.client.screen;

import com.hardest.client.sound.ClientSounds;
import com.hardest.entity.GooseEntity;
import com.hardest.registry.ModEntities;
import com.hardest.registry.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Реклама, которую нельзя пропустить. Игра при этом НЕ на паузе.
 */
public class AdScreen extends Screen {
    private static final String[] VOICE = {"ad_dirt", "ad_course", "ad_slipper", "ad_goose", "ad_premium", "ad_credit"};
    private static final String[][] TEXT = {
            {"ПРЕМИУМ-ЗЕМЛЯ", "Как обычная, но дороже!", "64 алмаза", "ХИТ!"},
            {"КУРС «КАК НЕ УМИРАТЬ»", "Урок 1: не умирай. Урок 2: см. урок 1", "99 алмазов", "-0%"},
            {"ТАПОК 3000", "Убивает всё. Мамы рекомендуют!", "кожа + нитка", "★★★★★"},
            {"ЗАВЕДИ ГУСЯ!", "Он заберёт у тебя всё. Кроме одиночества.", "БЕСПЛАТНО*", "*гусь платный"},
            {"МАЙНКРАФТ ПРЕМИУМ", "Убирает рекламу. Добавляет больше рекламы.", "1 душа", "NEW"},
            {"КРЕДИТ НА АЛМАЗЫ", "900% годовых. Одобрим даже криперу!", "0 справок", "*коллекторы — гуси"},
    };
    private static final int[] BG = {0xFF7A4A21, 0xFF1F3A93, 0xFFB0306A, 0xFF2E7D32, 0xFF4A148C, 0xFF8D6E00};
    private static final int DURATION = 20 * 13;
    private static final int SKIP_AFTER = 20 * 5;
    private static final String SKIP = "Пропустить ▶";

    private final int ad;
    private int age;
    private int skipX, skipY;
    private int dodges;
    private GooseEntity goose;
    private final Random rng = Random.create();

    public AdScreen(int ad) {
        super(Text.literal("Реклама"));
        this.ad = MathHelper.clamp(ad, 0, 5);
    }

    @Override
    protected void init() {
        skipX = width / 2 + 110;
        skipY = height / 2 + 70;
        if (age == 0) {
            ClientSounds.voiceLine(VOICE[ad], 1.0f);
            ClientSounds.ui("ad_jingle", 0.6f);
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (ad == 3 && mc.world != null && goose == null) goose = ModEntities.GOOSE.create(mc.world);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void tick() {
        age++;
        if (age >= DURATION) close();
    }

    private boolean skipVisible() {
        return age >= SKIP_AFTER;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float t = age + delta;
        int pw = Math.min(340, width - 20), ph = Math.min(190, height - 40);
        int x = (width - pw) / 2, y = (height - ph) / 2;
        ctx.fill(0, 0, width, height, 0x90000000);

        // фон с лучами
        ctx.fill(x, y, x + pw, y + ph, BG[ad]);
        int cx = x + pw / 3, cy = y + ph / 2;
        for (int i = 0; i < 12; i++) {
            double a = t * 0.02 + i * Math.PI / 6;
            int ex = (int) (cx + Math.cos(a) * 300), ey = (int) (cy + Math.sin(a) * 300);
            drawRay(ctx, cx, cy, ex, ey, x, y, pw, ph);
        }
        ctx.fill(x, y, x + pw, y + 2, 0xFFFFD700);
        ctx.fill(x, y + ph - 2, x + pw, y + ph, 0xFFFFD700);

        // картинка товара
        ctx.getMatrices().push();
        float bob = MathHelper.sin(t * 0.15f) * 4;
        switch (ad) {
            case 3 -> {
                if (goose != null) {
                    InventoryScreen.drawEntity(ctx, cx, cy + 50, 45, (float) (cx - mouseX), (float) (cy - mouseY), goose);
                }
            }
            default -> {
                ItemStack icon = switch (ad) {
                    case 0 -> new ItemStack(Items.DIRT);
                    case 1 -> new ItemStack(Items.TOTEM_OF_UNDYING);
                    case 4 -> new ItemStack(Items.NETHER_STAR);
                    case 2 -> new ItemStack(ModItems.SLIPPER);
                    default -> new ItemStack(Items.EMERALD);
                };
                ctx.getMatrices().translate(cx, cy + bob, 0);
                ctx.getMatrices().scale(5f, 5f, 1f);
                ctx.drawItem(icon, -8, -8);
            }
        }
        ctx.getMatrices().pop();

        // тексты
        String[] tx = TEXT[ad];
        int tx0 = x + pw / 2 + 4;
        float wob = 1.0f + MathHelper.sin(t * 0.3f) * 0.06f;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + pw * 0.66f, y + 26, 0);
        float headScale = Math.min(1.9f, (pw * 0.62f) / Math.max(1, textRenderer.getWidth(tx[0]))) * wob;
        ctx.getMatrices().scale(headScale, headScale, 1);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(tx[0]), 0, 0, 0xFFFFEB3B);
        ctx.getMatrices().pop();
        int ly = y + 54;
        for (var line : textRenderer.wrapLines(Text.literal(tx[1]), pw / 2 - 12)) {
            ctx.drawTextWithShadow(textRenderer, line, tx0, ly, 0xFFFFFFFF);
            ly += 10;
        }
        // ценник
        int px = tx0, py = y + ph - 62;
        ctx.fill(px, py, px + pw / 2 - 14, py + 26, 0xFFE53935);
        ctx.drawTextWithShadow(textRenderer, Text.literal("ЦЕНА:"), px + 4, py + 3, 0xFFFFFFFF);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(px + 4, py + 13, 0);
        ctx.getMatrices().scale(1.3f, 1.3f, 1);
        ctx.drawTextWithShadow(textRenderer, Text.literal(tx[2]), 0, 0, 0xFFFFFF00);
        ctx.getMatrices().pop();
        // наклейка
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 30, y + 22, 0);
        ctx.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-15));
        int sw = textRenderer.getWidth(tx[3]) + 8;
        ctx.fill(-sw / 2, -6, sw / 2, 8, 0xFFFFD700);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(tx[3]), 0, -3, 0xFFD50000);
        ctx.getMatrices().pop();

        // плашка «Реклама»
        ctx.fill(x, y - 14, x + 60, y, 0xFFFFC107);
        ctx.drawText(textRenderer, Text.literal("Реклама"), x + 6, y - 11, 0xFF000000, false);
        int left = (DURATION - age) / 20 + 1;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Реклама закончится через " + left), x + 66, y - 11, 0xFFFFFFFF);

        // кнопка «Пропустить»
        if (skipVisible()) {
            String label = SKIP;
            int bw = textRenderer.getWidth(label) + 12, bh = 16;
            boolean near = Math.abs(mouseX - (skipX + bw / 2)) < bw / 2 + 18 && Math.abs(mouseY - (skipY + bh / 2)) < bh / 2 + 14;
            if (near && dodges < 4) {
                // кнопка убегает от курсора
                dodges++;
                skipX = x + 10 + rng.nextInt(Math.max(1, pw - bw - 20));
                skipY = y + 10 + rng.nextInt(Math.max(1, ph - bh - 20));
                ClientSounds.ui("popup_close", 0.6f, 1.5f);
            }
            ctx.fill(skipX, skipY, skipX + bw, skipY + bh, 0xE0000000);
            ctx.fill(skipX, skipY, skipX + bw, skipY + 1, 0xFFFFFFFF);
            ctx.drawTextWithShadow(textRenderer, Text.literal(label), skipX + 6, skipY + 4, 0xFFFFFFFF);
        } else {
            int s = (SKIP_AFTER - age) / 20 + 1;
            ctx.fill(x + pw - 110, y + ph - 22, x + pw - 6, y + ph - 6, 0xC0000000);
            ctx.drawTextWithShadow(textRenderer, Text.literal("Пропустить через " + s), x + pw - 104, y + ph - 18, 0xFFAAAAAA);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawRay(DrawContext ctx, int cx, int cy, int ex, int ey, int x, int y, int pw, int ph) {
        int steps = 40;
        for (int i = 0; i < steps; i++) {
            int px = cx + (ex - cx) * i / steps;
            int py = cy + (ey - cy) * i / steps;
            if (px < x || py < y || px > x + pw - 6 || py > y + ph - 6) continue;
            int s = 2 + i / 6;
            ctx.fill(px, py, px + s, py + s, 0x22FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (skipVisible() && dodges >= 4) {
            String label = SKIP;
            int bw = textRenderer.getWidth(label) + 12;
            if (mouseX >= skipX && mouseX <= skipX + bw && mouseY >= skipY && mouseY <= skipY + 16) {
                ClientSounds.ui("popup_close", 0.8f);
                close();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
