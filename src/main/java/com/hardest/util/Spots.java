package com.hardest.util;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Поиск мест для появления мобов рядом с игроком (в том числе в пещерах).
 */
public final class Spots {

    /** Место на земле на расстоянии [minR, maxR] от центра, примерно на той же высоте. */
    @Nullable
    public static BlockPos ground(ServerWorld world, BlockPos center, int minR, int maxR) {
        Random r = world.getRandom();
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = r.nextDouble() * Math.PI * 2;
            int dist = minR + r.nextInt(Math.max(1, maxR - minR + 1));
            int x = center.getX() + MathHelper.floor(Math.cos(angle) * dist);
            int z = center.getZ() + MathHelper.floor(Math.sin(angle) * dist);
            BlockPos found = column(world, x, center.getY(), z, 10);
            if (found != null) return found;
        }
        return null;
    }

    /** Ищет в столбце позицию: твёрдый блок снизу, два блока воздуха сверху. */
    @Nullable
    public static BlockPos column(ServerWorld world, int x, int y, int z, int range) {
        for (int dy = 0; dy <= range; dy++) {
            for (int s = -1; s <= 1; s += 2) {
                BlockPos p = new BlockPos(x, y + dy * s, z);
                if (isStandable(world, p)) return p;
                if (dy == 0) break;
            }
        }
        return null;
    }

    public static boolean isStandable(ServerWorld world, BlockPos p) {
        if (!world.isInBuildLimit(p)) return false;
        BlockState below = world.getBlockState(p.down());
        return below.isSolidBlock(world, p.down())
                && world.getBlockState(p).getCollisionShape(world, p).isEmpty()
                && world.getBlockState(p.up()).getCollisionShape(world, p.up()).isEmpty()
                && world.getFluidState(p).isEmpty();
    }

    /** Позиция в воздухе над игроком (для падающих коров и наковален). */
    public static BlockPos sky(ServerWorld world, BlockPos center, int radius, int height) {
        Random r = world.getRandom();
        int x = center.getX() + r.nextInt(radius * 2 + 1) - radius;
        int z = center.getZ() + r.nextInt(radius * 2 + 1) - radius;
        int top = world.getTopY() - 2;
        return new BlockPos(x, Math.min(top, center.getY() + height), z);
    }

    public static <T extends Entity> T place(ServerWorld world, T entity, BlockPos pos, float yaw) {
        entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        if (entity instanceof MobEntity mob) {
            mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null, null);
        }
        world.spawnEntity(entity);
        return entity;
    }

    public static float yawTowards(double fromX, double fromZ, double toX, double toZ) {
        return (float) (MathHelper.atan2(toZ - fromZ, toX - fromX) * 180.0 / Math.PI) - 90f;
    }

    private Spots() {
    }
}
