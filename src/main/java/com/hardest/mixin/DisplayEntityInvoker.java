package com.hardest.mixin;

import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Анимированные блоки для атак дракона (шипы, метеорит, вихрь). */
@Mixin(DisplayEntity.class)
public interface DisplayEntityInvoker {
    @Invoker("setTransformation")
    void hardest$setTransformation(AffineTransformation transformation);

    @Invoker("setInterpolationDuration")
    void hardest$setInterpolationDuration(int ticks);

    @Invoker("setStartInterpolation")
    void hardest$setStartInterpolation(int ticks);

    @Invoker("setBrightness")
    void hardest$setBrightness(Brightness brightness);

    @Invoker("setViewRange")
    void hardest$setViewRange(float range);
}
