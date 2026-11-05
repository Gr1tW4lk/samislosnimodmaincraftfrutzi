package com.hardest.events;

import com.hardest.net.Net;
import com.hardest.registry.ModItems;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * «Трипы» от странных грибов и ведьминых зелий: экранные безумия на полминуты.
 */
public final class Trips {
    public static final String[] IDS = {"australia", "inverted", "tiktok", "potato", "float", "turbo", "lag", "glitch"};

    public static void random(ServerPlayerEntity p, int ticks) {
        apply(p, IDS[p.getRandom().nextInt(IDS.length)], ticks);
    }

    public static void apply(ServerPlayerEntity p, String id, int ticks) {
        String msg;
        switch (id) {
            case "australia" -> {
                Net.fx(p, "australia", ticks, 0);
                msg = "Мир перевернулся!";
            }
            case "inverted" -> {
                Net.fx(p, "inverted", ticks, 0);
                msg = "Руки перепутались!";
            }
            case "tiktok" -> {
                Net.fx(p, "tiktok", ticks, 0);
                msg = "Ты стал вертикальным видео!";
            }
            case "potato" -> {
                Net.fx(p, "potato", ticks, 0);
                msg = "Графика: КАРТОШКА";
            }
            case "float" -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 60, 1, false, true));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 160, 0, false, true));
                msg = "Ты лёгкий, как пёрышко!";
            }
            case "turbo" -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 3, false, true));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, Math.min(ticks, 200), 0, false, false));
                Net.fx(p, "turbo", ticks, 0);
                msg = "ТУРБО!!!";
            }
            case "lag" -> {
                Net.fx(p, "lag", ticks / 2, 0);
                WorldEvents.lagSpike(p, ticks / 2);
                msg = "Пинг 999!";
            }
            default -> {
                Net.fx(p, "glitch", ticks / 2, 0);
                msg = "Реальность глючит...";
            }
        }
        Net.alert(p, Net.Alert.FUNNY, ModItems.STRANGE_MUSHROOM, msg.toUpperCase(), "Грибной трип");
    }

    private Trips() {
    }
}
