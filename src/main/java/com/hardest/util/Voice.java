package com.hardest.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hardest.HardestMod;
import com.hardest.net.Net;
import com.hardest.registry.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Короткие реплики персонажей (налоговик, курьер, двойник): звук из точки, где стоит персонаж, + субтитры.
 */
public final class Voice {
    private static final Map<String, Integer> TICKS = new HashMap<>();

    public static void load() {
        try (InputStream in = Voice.class.getResourceAsStream("/assets/hardest/voice_durations.json")) {
            if (in == null) return;
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String k : obj.keySet()) TICKS.put(k, obj.get(k).getAsInt());
        } catch (Exception e) {
            HardestMod.LOG.warn("Нет длительностей реплик", e);
        }
    }

    public static int ticks(String line) {
        return TICKS.getOrDefault(line, 60);
    }

    /** Персонаж говорит: звук от него и субтитр игрокам рядом. */
    public static void speak(Entity speaker, String speakerKey, String line) {
        if (!(speaker.getWorld() instanceof ServerWorld sw)) return;
        sw.playSound(null, speaker.getX(), speaker.getEyeY(), speaker.getZ(), ModSounds.voice(line), SoundCategory.NEUTRAL, 2.0f, 1.0f);
        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (p.squaredDistanceTo(speaker) < 32 * 32) Net.line(p, speakerKey, line);
        }
    }

    /** Игрок «сам» что-то выкрикивает (чих, «берегись»). */
    public static void shout(ServerPlayerEntity p, String line) {
        p.getServerWorld().playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.voice(line), SoundCategory.PLAYERS, 1.4f,
                0.95f + p.getRandom().nextFloat() * 0.1f);
    }

    private Voice() {
    }
}
