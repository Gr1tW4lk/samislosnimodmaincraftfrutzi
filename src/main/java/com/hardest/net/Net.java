package com.hardest.net;

import com.hardest.HardestMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Пакеты сервер ↔ клиент.
 */
public final class Net {
    /** Реплика персонажа: кто говорит + id реплики (для субтитров). */
    public static final Identifier LINE = HardestMod.id("line");
    /** Эффект на экране: id, длительность в тиках, параметр. */
    public static final Identifier FX = HardestMod.id("fx");
    /** Открыть рекламу. */
    public static final Identifier AD = HardestMod.id("ad");
    /** Открыть капчу для блока. */
    public static final Identifier CAPTCHA = HardestMod.id("captcha");
    /** Клиент -> сервер: результат капчи. */
    public static final Identifier CAPTCHA_RESULT = HardestMod.id("captcha_result");
    /** Плашка-предупреждение сверху экрана: тип, иконка, заголовок, подсказка. */
    public static final Identifier ALERT = HardestMod.id("alert");
    /** Большой заголовок: в кинорежиме — с чёрными полосами сверху и снизу. */
    public static final Identifier CARD = HardestMod.id("card");

    /** Тип плашки — от него зависят цвет и анимация. */
    public enum Alert {
        /** Красная, мигает: сейчас прилетит. */
        DANGER,
        /** Фиолетовая: атака или событие босса. */
        BOSS,
        /** Жёлтая: событие мира. */
        INFO,
        /** Зелёная: повезло. */
        GOOD,
        /** Розовая: абсурд. */
        FUNNY,
        /** Голубая: простуда. */
        COLD
    }

    public static void line(ServerPlayerEntity p, String speakerKey, String line) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(speakerKey);
        buf.writeString(line);
        ServerPlayNetworking.send(p, LINE, buf);
    }

    public static void fx(ServerPlayerEntity p, String id, int durationTicks, int param) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(id);
        buf.writeVarInt(durationTicks);
        buf.writeVarInt(param);
        ServerPlayNetworking.send(p, FX, buf);
    }

    public static void ad(ServerPlayerEntity p, int adIndex) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(adIndex);
        ServerPlayNetworking.send(p, AD, buf);
    }

    public static void captcha(ServerPlayerEntity p, BlockPos pos, int seed) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeVarInt(seed);
        ServerPlayNetworking.send(p, CAPTCHA, buf);
    }

    public static void alert(PlayerEntity player, Alert kind, ItemStack icon, Text title, Text sub) {
        if (!(player instanceof ServerPlayerEntity p)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeEnumConstant(kind);
        buf.writeItemStack(icon);
        buf.writeText(title);
        buf.writeText(sub);
        ServerPlayNetworking.send(p, ALERT, buf);
    }

    public static void alert(PlayerEntity player, Alert kind, ItemConvertible icon, String title, String sub) {
        alert(player, kind, new ItemStack(icon), Text.literal(title), Text.literal(sub));
    }

    /** Большой заголовок в стиле мода. letterbox — чёрные полосы как в кино (для босса и Кровавой луны). */
    public static void card(ServerPlayerEntity p, String title, String sub, int rgb, int ticks, boolean letterbox) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(title);
        buf.writeString(sub);
        buf.writeInt(rgb);
        buf.writeVarInt(ticks);
        buf.writeBoolean(letterbox);
        ServerPlayNetworking.send(p, CARD, buf);
    }

    /** Большой заголовок на экране (обычный майнкрафтовский title). */
    public static void title(ServerPlayerEntity p, Text title, Text subtitle) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 50, 15));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        p.networkHandler.sendPacket(new TitleS2CPacket(title));
    }

    private Net() {
    }
}
