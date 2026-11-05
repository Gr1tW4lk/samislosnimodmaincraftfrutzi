package com.hardest.client;

import com.hardest.util.Voice;

import java.util.HashMap;
import java.util.Map;

/**
 * Что сейчас показывать на экране: субтитры персонажей и активные эффекты.
 */
public final class ClientState {
    public static long ticks;

    // ---------------------------------------------------------------- субтитры персонажей
    public static String speaker;
    public static String lineId;
    public static long lineStart;
    public static int lineDuration;

    public static void line(String who, String id) {
        speaker = who;
        lineId = id;
        lineStart = ticks;
        lineDuration = Math.max(50, Voice.ticks(id) + 25);
    }

    public static boolean talking() {
        return lineId != null && ticks - lineStart < lineDuration;
    }

    // ---------------------------------------------------------------- эффекты
    private static final Map<String, Long> FX_END = new HashMap<>();
    private static final Map<String, Long> FX_START = new HashMap<>();
    private static final Map<String, Integer> FX_PARAM = new HashMap<>();

    public static void fx(String id, int duration, int param) {
        if (duration <= 0) {
            FX_END.remove(id);
            return;
        }
        FX_END.put(id, ticks + duration);
        FX_START.put(id, ticks);
        FX_PARAM.put(id, param);
    }

    public static boolean active(String id) {
        Long end = FX_END.get(id);
        return end != null && ticks < end;
    }

    public static long left(String id) {
        return Math.max(0, FX_END.getOrDefault(id, ticks) - ticks);
    }

    public static float progress(String id) {
        long start = FX_START.getOrDefault(id, ticks);
        long end = FX_END.getOrDefault(id, ticks);
        if (end <= start) return 1f;
        return Math.min(1f, (ticks - start) / (float) (end - start));
    }

    public static int param(String id) {
        return FX_PARAM.getOrDefault(id, 0);
    }

    public static void tick() {
        ticks++;
    }

    public static void reset() {
        FX_END.clear();
        FX_START.clear();
        lineId = null;
    }

    private ClientState() {
    }
}
