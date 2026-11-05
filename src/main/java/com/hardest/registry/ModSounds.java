package com.hardest.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hardest.HardestMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Все звуки мода регистрируются прямо из sounds.json — чтобы не дублировать сотню реплик в коде.
 */
public final class ModSounds {
    private static final Map<String, SoundEvent> SOUNDS = new HashMap<>();

    public static SoundEvent get(String name) {
        SoundEvent s = SOUNDS.get(name);
        if (s == null) {
            s = SoundEvent.of(HardestMod.id(name));
            SOUNDS.put(name, s);
        }
        return s;
    }

    public static SoundEvent voice(String lineId) {
        return get("voice." + lineId);
    }

    public static SoundEvent sfx(String name) {
        return get("sfx." + name);
    }

    public static void register() {
        try (InputStream in = ModSounds.class.getResourceAsStream("/assets/hardest/sounds.json")) {
            if (in == null) {
                HardestMod.LOG.error("sounds.json не найден");
                return;
            }
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : obj.keySet()) {
                Identifier id = HardestMod.id(key);
                SoundEvent ev = SoundEvent.of(id);
                Registry.register(Registries.SOUND_EVENT, id, ev);
                SOUNDS.put(key, ev);
            }
            HardestMod.LOG.info("Зарегистрировано звуков: {}", SOUNDS.size());
        } catch (Exception e) {
            HardestMod.LOG.error("Не удалось загрузить звуки", e);
        }
    }

    private ModSounds() {
    }
}
