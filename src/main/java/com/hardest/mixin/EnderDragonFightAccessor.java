package com.hardest.mixin;

import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Босс-бар дракона меняет цвет и название по фазам. */
@Mixin(EnderDragonFight.class)
public interface EnderDragonFightAccessor {
    @Accessor("bossBar")
    ServerBossBar hardest$bossBar();
}
