package com.hardest.mixin;

import com.hardest.dragon.DragonRage;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Дракон вдвое больше — и хитбоксы тоже, чтобы по нему честно попадать.
 */
@Mixin(EnderDragonEntity.class)
public abstract class EnderDragonEntityMixin {
    @Shadow
    @Final
    private EnderDragonPart[] parts;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void hardest$scaleParts(CallbackInfo ci) {
        for (EnderDragonPart part : parts) {
            ((EnderDragonPartAccessor) part).hardest$setPartDimensions(part.getDimensions(net.minecraft.entity.EntityPose.STANDING).scaled(DragonRage.SCALE));
            part.calculateDimensions();
        }
    }

    @ModifyArgs(method = "tickMovement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/boss/dragon/EnderDragonEntity;movePart(Lnet/minecraft/entity/boss/dragon/EnderDragonPart;DDD)V"))
    private void hardest$scaleOffsets(Args args) {
        args.set(1, (double) args.get(1) * DragonRage.SCALE);
        args.set(2, (double) args.get(2) * DragonRage.SCALE);
        args.set(3, (double) args.get(3) * DragonRage.SCALE);
    }
}
