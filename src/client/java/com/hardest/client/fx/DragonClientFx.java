package com.hardest.client.fx;

import com.hardest.client.ClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

/**
 * Ощущение огромного босса на клиенте: каждый взмах крыльев рядом трясёт экран
 * и бухает басом, с тела и крыльев сыплются искры Края (в ярости — огонь и пепел).
 */
public final class DragonClientFx {
    private static final DustParticleEffect RAGE_DUST = new DustParticleEffect(new Vector3f(1.0f, 0.15f, 0.1f), 1.0f);
    private static final DustParticleEffect VOID_DUST = new DustParticleEffect(new Vector3f(0.6f, 0.2f, 0.95f), 0.9f);

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null || mc.isPaused()) return;
        Random r = mc.world.random;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EnderDragonEntity d) || d.isRemoved() || d.ticksSinceDeath > 0) continue;
            double dist = d.distanceTo(mc.player);
            if (dist > 180) continue;
            boolean rage = d.getMaxHealth() > 0 && d.getHealth() / d.getMaxHealth() <= 0.3f;

            // та же точка цикла крыла, где ванилла играет звук взмаха
            float now = MathHelper.cos(d.wingPosition * 6.2831855f);
            float before = MathHelper.cos(d.prevWingPosition * 6.2831855f);
            if (before <= -0.3f && now >= -0.3f && dist < 80) {
                float k = (float) (1 - dist / 80);
                ClientState.fx("quake", 10, Math.max(1, Math.round(3.5f * k)));
                mc.world.playSound(d.getX(), d.getY(), d.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 2f + 6f * k, 0.45f, false);
            }

            EnderDragonPart[] parts = d.getBodyParts();
            for (EnderDragonPart part : parts) {
                if (r.nextFloat() > 0.3f) continue;
                Box b = part.getBoundingBox();
                double x = MathHelper.lerp(r.nextDouble(), b.minX, b.maxX);
                double y = MathHelper.lerp(r.nextDouble(), b.minY, b.maxY);
                double z = MathHelper.lerp(r.nextDouble(), b.minZ, b.maxZ);
                // у самой камеры не сыплем — иначе в ближнем бою ничего не видно
                if (mc.player.squaredDistanceTo(x, y, z) < 8 * 8) continue;
                ParticleEffect p = rage ? (r.nextBoolean() ? RAGE_DUST : ParticleTypes.SMOKE) : (r.nextInt(3) == 0 ? VOID_DUST : ParticleTypes.PORTAL);
                mc.world.addParticle(p, x, y, z, (r.nextDouble() - 0.5) * 0.2, -0.05, (r.nextDouble() - 0.5) * 0.2);
            }
            // шлейф с концов крыльев
            for (int i = parts.length - 2; i < parts.length; i++) {
                Box b = parts[i].getBoundingBox();
                double x = MathHelper.lerp(r.nextDouble(), b.minX, b.maxX);
                double z = MathHelper.lerp(r.nextDouble(), b.minZ, b.maxZ);
                mc.world.addParticle(rage ? ParticleTypes.FLAME : ParticleTypes.REVERSE_PORTAL, x, b.minY, z, 0, -0.02, 0);
            }
            // в ярости из пасти валит дым и искры
            if (rage) {
                Box h = d.head.getBoundingBox();
                mc.world.addParticle(ParticleTypes.LARGE_SMOKE, h.getCenter().x, h.minY, h.getCenter().z, (r.nextDouble() - 0.5) * 0.1, -0.08, (r.nextDouble() - 0.5) * 0.1);
                if (r.nextInt(3) == 0) mc.world.addParticle(ParticleTypes.LAVA, h.getCenter().x, h.minY, h.getCenter().z, 0, 0, 0);
            }
        }
    }

    private DragonClientFx() {
    }
}
