package com.hardest.dragon;

import com.hardest.mixin.BlockDisplayInvoker;
import com.hardest.mixin.DisplayEntityInvoker;
import com.hardest.net.Net;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Частицы, тряска и анимированные блоки для боя с драконом.
 */
public final class Fx {
    public static final DustParticleEffect PURPLE = new DustParticleEffect(new Vector3f(0.78f, 0.25f, 1.0f), 2.5f);
    public static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.1f, 0.08f), 2.5f);
    public static final DustParticleEffect DARK = new DustParticleEffect(new Vector3f(0.15f, 0.0f, 0.25f), 3.0f);

    private static final String TAG = "hard_fx";
    /** Живые анимированные блоки. Всё, что осталось с прошлого запуска, удаляется при загрузке. */
    private static final Set<UUID> LIVE = new HashSet<>();

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((e, w) -> {
            if (e instanceof DisplayEntity && e.getCommandTags().contains(TAG) && !LIVE.contains(e.getUuid())) e.discard();
        });
    }

    /** Частицы, которые видно издалека: дракон огромный, его атаки тоже. */
    public static void particle(ServerWorld w, ParticleEffect e, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.squaredDistanceTo(x, y, z) < 200 * 200) w.spawnParticles(p, e, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** Частица, летящая со скоростью v (режим count = 0). */
    public static void flow(ServerWorld w, ParticleEffect e, Vec3d at, Vec3d v) {
        particle(w, e, at.x, at.y, at.z, 0, v.x, v.y, v.z, 1.0);
    }

    public static void ring(ServerWorld w, Vec3d c, double r, ParticleEffect e, int points, boolean followGround) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
            double y = c.y;
            if (followGround) {
                int g = ground(w, x, z);
                if (g <= w.getBottomY()) continue;
                y = g + 0.15;
            }
            particle(w, e, x, y, z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    /** Верх земли в точке; bottomY — если под точкой пустота. */
    public static int ground(ServerWorld w, double x, double z) {
        return w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
    }

    /** Тряска камеры: чем ближе, тем сильнее. */
    public static void shake(ServerWorld w, Vec3d c, double radius, int ticks, float power) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            double d = p.getPos().distanceTo(c);
            if (d > radius) continue;
            int pw = Math.max(1, Math.round(power * (float) (1 - d / radius)));
            Net.fx(p, "shake", ticks, pw);
        }
    }

    public static void fxNear(ServerWorld w, Vec3d c, double radius, String id, int ticks, int param) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.getPos().distanceTo(c) <= radius) Net.fx(p, id, ticks, param);
        }
    }

    public static void lightning(ServerWorld w, Vec3d at) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
        if (bolt == null) return;
        bolt.refreshPositionAfterTeleport(at);
        bolt.setCosmetic(true);
        w.spawnEntity(bolt);
    }

    // ---------------------------------------------------------------- анимированные блоки

    /** Блок с центром основания в точке pos: масштаб (sx, sy, sz), поворот rot, сдвиг по высоте lift. */
    public static AffineTransformation tf(float sx, float sy, float sz, Quaternionf rot, float lift) {
        Matrix4f m = new Matrix4f().translate(0, lift, 0).rotate(rot).scale(sx, sy, sz).translate(-0.5f, 0f, -0.5f);
        return new AffineTransformation(m);
    }

    public static DisplayEntity.BlockDisplayEntity block(ServerWorld w, Vec3d pos, BlockState state, AffineTransformation t, boolean glow) {
        DisplayEntity.BlockDisplayEntity e = EntityType.BLOCK_DISPLAY.create(w);
        if (e == null) return null;
        e.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
        ((BlockDisplayInvoker) e).hardest$setBlockState(state);
        DisplayEntityInvoker inv = (DisplayEntityInvoker) e;
        inv.hardest$setTransformation(t);
        inv.hardest$setViewRange(4f);
        if (glow) inv.hardest$setBrightness(new Brightness(15, 15));
        e.addCommandTag(TAG);
        LIVE.add(e.getUuid());
        w.spawnEntity(e);
        return e;
    }

    /** Плавно перевести блок в новое положение за ticks тиков (интерполяция на клиенте). */
    public static void animate(DisplayEntity e, AffineTransformation t, int ticks) {
        if (e == null || e.isRemoved()) return;
        DisplayEntityInvoker inv = (DisplayEntityInvoker) e;
        inv.hardest$setTransformation(t);
        inv.hardest$setInterpolationDuration(ticks);
        inv.hardest$setStartInterpolation(0);
    }

    public static void remove(DisplayEntity e) {
        if (e == null) return;
        LIVE.remove(e.getUuid());
        e.discard();
    }

    public static Quaternionf tilt(net.minecraft.util.math.random.Random r, float maxDeg) {
        float a = (float) Math.toRadians((r.nextFloat() * 2 - 1) * maxDeg);
        float b = (float) Math.toRadians((r.nextFloat() * 2 - 1) * maxDeg);
        return new Quaternionf().rotateY(r.nextFloat() * (float) Math.PI * 2).rotateX(a).rotateZ(b);
    }

    public static BlockPos at(Vec3d v) {
        return BlockPos.ofFloored(v);
    }

    private Fx() {
    }
}
