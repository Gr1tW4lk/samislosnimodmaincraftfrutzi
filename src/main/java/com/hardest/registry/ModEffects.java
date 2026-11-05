package com.hardest.registry;

import com.hardest.HardestMod;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEffects {
    /** Простуда: чихаешь, тебя отбрасывает, роняешь вещи. Лечится молоком, чаем с малиной или у огня. */
    public static final StatusEffect COLD = Registry.register(Registries.STATUS_EFFECT, HardestMod.id("cold"),
            new StatusEffect(StatusEffectCategory.HARMFUL, 0x9AD8F0) {
            });

    public static void register() {
    }

    private ModEffects() {
    }
}
