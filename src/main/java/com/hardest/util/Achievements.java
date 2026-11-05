package com.hardest.util;

import com.hardest.HardestMod;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Шуточные достижения мода — выдаются кодом, всплывают тостом.
 */
public final class Achievements {
    public static void grant(ServerPlayerEntity p, String id) {
        if (p == null || p.getServer() == null) return;
        Advancement adv = p.getServer().getAdvancementLoader().get(HardestMod.id("main/" + id));
        if (adv == null) return;
        AdvancementProgress progress = p.getAdvancementTracker().getProgress(adv);
        if (progress.isDone()) return;
        for (String criterion : progress.getUnobtainedCriteria()) {
            p.getAdvancementTracker().grantCriterion(adv, criterion);
        }
    }

    /** Выдать игроку, если он виновник (атакующий) события. */
    public static void grantTo(Entity maybePlayer, String id) {
        if (maybePlayer instanceof ServerPlayerEntity p) grant(p, id);
    }

    private Achievements() {
    }
}
