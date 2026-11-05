package com.hardest.mixin;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnderDragonPart.class)
public interface EnderDragonPartAccessor {
    @Mutable
    @Accessor("partDimensions")
    void hardest$setPartDimensions(EntityDimensions dimensions);
}
