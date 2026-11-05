package com.hardest.mixin;

import com.hardest.events.Trips;
import com.hardest.util.Achievements;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ведьмины зелья теперь ещё и «мемные»: кроме яда — переворот экрана, ТикТок-режим и прочий трип.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Unique
    private long hardest$lastTrip;

    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"))
    private void hardest$witchTrip(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerPlayerEntity p) || !(source instanceof WitchEntity)) return;
        long now = p.getWorld().getTime();
        if (now - hardest$lastTrip < 100 || p.getRandom().nextFloat() > 0.5f) return;
        hardest$lastTrip = now;
        Trips.random(p, 20 * 20);
        Achievements.grant(p, "witch");
    }
}
