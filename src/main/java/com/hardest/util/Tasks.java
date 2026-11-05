package com.hardest.util;

import com.hardest.HardestMod;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Отложенные действия на сервере (в тиках).
 */
public final class Tasks {
    private record Task(long tick, Runnable action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();

    public static void schedule(MinecraftServer server, int delayTicks, Runnable r) {
        TASKS.add(new Task(server.getTicks() + delayTicks, r));
    }

    public static void tick(MinecraftServer server) {
        if (TASKS.isEmpty()) return;
        long now = server.getTicks();
        List<Task> due = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.tick <= now) {
                due.add(t);
                it.remove();
            }
        }
        for (Task t : due) {
            try {
                t.action.run();
            } catch (Exception e) {
                HardestMod.LOG.error("Ошибка в отложенном действии", e);
            }
        }
    }

    public static void clear() {
        TASKS.clear();
    }

    private Tasks() {
    }
}
