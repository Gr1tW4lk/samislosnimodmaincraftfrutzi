package com.hardest.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Запоминает, где игрок был последние 10 секунд — для «Ctrl+Z» и лагов.
 */
public final class PositionHistory {
    private static final int SIZE = 200;

    private static final class Ring {
        final Vec3d[] pos = new Vec3d[SIZE];
        final float[] health = new float[SIZE];
        int head = 0;
        int count = 0;
    }

    private static final Map<UUID, Ring> RINGS = new HashMap<>();

    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            Ring r = RINGS.computeIfAbsent(p.getUuid(), u -> new Ring());
            if (!p.isAlive()) {
                r.count = 0;
                continue;
            }
            r.pos[r.head] = p.getPos();
            r.health[r.head] = p.getHealth();
            r.head = (r.head + 1) % SIZE;
            r.count = Math.min(SIZE, r.count + 1);
        }
    }

    /** Позиция игрока ticksAgo тиков назад (или самая старая из известных). */
    @Nullable
    public static Vec3d get(ServerPlayerEntity p, int ticksAgo) {
        Ring r = RINGS.get(p.getUuid());
        if (r == null || r.count == 0) return null;
        int back = Math.min(ticksAgo, r.count - 1);
        int idx = ((r.head - 1 - back) % SIZE + SIZE) % SIZE;
        return r.pos[idx];
    }

    public static float health(ServerPlayerEntity p, int ticksAgo) {
        Ring r = RINGS.get(p.getUuid());
        if (r == null || r.count == 0) return p.getMaxHealth();
        int back = Math.min(ticksAgo, r.count - 1);
        int idx = ((r.head - 1 - back) % SIZE + SIZE) % SIZE;
        return r.health[idx];
    }

    public static void clear(ServerPlayerEntity p) {
        RINGS.remove(p.getUuid());
    }

    private PositionHistory() {
    }
}
