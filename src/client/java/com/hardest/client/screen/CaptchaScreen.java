package com.hardest.client.screen;

import com.hardest.net.Net;
import com.hardest.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * «Докажите, что вы не робот». Говорит робот.
 */
public class CaptchaScreen extends Screen {
    private record Category(String name, Item[] items) {
    }

    private static final Category[] CATEGORIES = {
            new Category("ЕДА", new Item[]{Items.APPLE, Items.BREAD, Items.COOKED_BEEF, Items.CARROT, Items.CAKE, Items.COOKIE, Items.MELON_SLICE, Items.BAKED_POTATO, Items.PUMPKIN_PIE}),
            new Category("ИНСТРУМЕНТЫ", new Item[]{Items.IRON_PICKAXE, Items.DIAMOND_AXE, Items.STONE_SHOVEL, Items.GOLDEN_HOE, Items.WOODEN_PICKAXE, Items.SHEARS, Items.FLINT_AND_STEEL, Items.FISHING_ROD}),
            new Category("ОРУЖИЕ", new Item[]{Items.IRON_SWORD, Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.DIAMOND_SWORD, ModItems.SLIPPER, Items.WOODEN_SWORD}),
            new Category("ДРАГОЦЕННОСТИ", new Item[]{Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.AMETHYST_SHARD, Items.LAPIS_LAZULI, Items.NETHERITE_INGOT}),
            new Category("РАСТЕНИЯ", new Item[]{Items.OAK_SAPLING, Items.POPPY, Items.DANDELION, Items.CACTUS, Items.SUGAR_CANE, Items.FERN, Items.BAMBOO, Items.SUNFLOWER}),
    };
    private static final Item[] FILLER = {Items.COBBLESTONE, Items.STICK, Items.STRING, Items.BONE, Items.GUNPOWDER, Items.ROTTEN_FLESH, Items.FEATHER, Items.CLAY_BALL, Items.BRICK, Items.COAL, Items.PAPER, Items.GLASS_BOTTLE, Items.BUCKET, Items.TORCH};

    private final BlockPos pos;
    private final Category category;
    private final ItemStack[] cells = new ItemStack[9];
    private final boolean[] correct = new boolean[9];
    private final boolean[] selected = new boolean[9];
    private boolean sent;
    private int age;

    public CaptchaScreen(BlockPos pos, int seed) {
        super(Text.literal("Капча"));
        this.pos = pos;
        Random r = Random.create(seed);
        this.category = CATEGORIES[r.nextInt(CATEGORIES.length)];
        int good = 3 + r.nextInt(3);
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < 9; i++) idx.add(i);
        Collections.shuffle(idx, new java.util.Random(seed));
        for (int i = 0; i < 9; i++) {
            int cell = idx.get(i);
            if (i < good) {
                cells[cell] = new ItemStack(category.items[r.nextInt(category.items.length)]);
                correct[cell] = true;
            } else {
                cells[cell] = new ItemStack(FILLER[r.nextInt(FILLER.length)]);
            }
        }
    }

    private int cell() {
        return Math.min(56, (height - 120) / 3);
    }

    private int gx() {
        return width / 2 - cell() * 3 / 2;
    }

    private int gy() {
        return height / 2 - cell() * 3 / 2 + 12;
    }

    @Override
    public void tick() {
        age++;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        int c = cell();
        int gx = gx(), gy = gy();
        // панель шире сетки, если длинная строка задания не помещается
        int pw = Math.max(c * 3 + 16, textRenderer.getWidth("Выберите все изображения, где есть:") + 16);
        int px = width / 2 - pw / 2, py = gy - 56;
        ctx.fill(px - 1, py - 1, px + pw + 1, gy + c * 3 + 41, 0xFFB0B8C8);
        ctx.fill(px, py, px + pw, gy + c * 3 + 40, 0xFFFFFFFF);
        ctx.fill(px, py, px + pw, py + 46, 0xFF1A73E8);
        ctx.drawText(textRenderer, Text.literal("Докажите, что вы не робот."), px + 8, py + 6, 0xFFFFFFFF, false);
        ctx.drawText(textRenderer, Text.literal("Выберите все изображения, где есть:"), px + 8, py + 18, 0xFFDDE8FF, false);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(px + 8, py + 30, 0);
        ctx.getMatrices().scale(1.3f, 1.3f, 1);
        ctx.drawText(textRenderer, Text.literal(category.name), 0, 0, 0xFFFFFFFF, false);
        ctx.getMatrices().pop();

        for (int i = 0; i < 9; i++) {
            int x = gx + (i % 3) * c, y = gy + (i / 3) * c;
            boolean hover = mouseX >= x && mouseX < x + c && mouseY >= y && mouseY < y + c;
            ctx.fill(x + 1, y + 1, x + c - 1, y + c - 1, selected[i] ? 0xFFB3D4FF : hover ? 0xFFEFEFEF : 0xFFE0E0E0);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + c / 2f, y + c / 2f, 0);
            float s = (c - 14) / 16f;
            ctx.getMatrices().scale(s, s, 1);
            ctx.drawItem(cells[i], -8, -8);
            ctx.getMatrices().pop();
            if (selected[i]) {
                ctx.fill(x + 3, y + 3, x + 13, y + 13, 0xFF1A73E8);
                ctx.drawText(textRenderer, Text.literal("✔"), x + 4, y + 4, 0xFFFFFFFF, false);
            }
        }
        int bx = gx + c * 3 - 80, by = gy + c * 3 + 10;
        boolean hover = mouseX >= bx && mouseX < bx + 80 && mouseY >= by && mouseY < by + 20;
        ctx.fill(bx, by, bx + 80, by + 20, hover ? 0xFF1666C5 : 0xFF1A73E8);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("ПОДТВЕРДИТЬ"), bx + 40, by + 6, 0xFFFFFFFF);
        ctx.drawText(textRenderer, Text.literal("АнтиБот 3000"), gx, by + 6, 0xFF888888, false);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int c = cell();
        int gx = gx(), gy = gy();
        for (int i = 0; i < 9; i++) {
            int x = gx + (i % 3) * c, y = gy + (i / 3) * c;
            if (mouseX >= x && mouseX < x + c && mouseY >= y && mouseY < y + c) {
                selected[i] = !selected[i];
                return true;
            }
        }
        int bx = gx + c * 3 - 80, by = gy + c * 3 + 10;
        if (mouseX >= bx && mouseX < bx + 80 && mouseY >= by && mouseY < by + 20) {
            boolean ok = true;
            for (int i = 0; i < 9; i++) if (selected[i] != correct[i]) ok = false;
            send(ok);
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void send(boolean ok) {
        if (sent) return;
        sent = true;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(ok);
        buf.writeBlockPos(pos);
        ClientPlayNetworking.send(Net.CAPTCHA_RESULT, buf);
    }

    @Override
    public void removed() {
        send(false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
