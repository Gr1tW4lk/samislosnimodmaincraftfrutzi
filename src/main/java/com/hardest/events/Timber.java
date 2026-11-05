package com.hardest.events;

import com.hardest.net.Net;
import com.hardest.registry.ModSounds;
import com.hardest.util.Achievements;
import com.hardest.util.Tasks;
import com.hardest.util.Voice;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ДЕРЕВО ПАДАЕТ! Срубил нижнее бревно — всё дерево валится целиком. Может и на тебя.
 */
public final class Timber {

    public static void onLogBroken(ServerWorld w, ServerPlayerEntity p, BlockPos pos) {
        if (p.isSneaking() || p.isCreative()) return;
        List<BlockPos> logs = collect(w, pos.up(), pos);
        if (logs.size() < 3 || !hasLeaves(w, logs)) return;
        w.playSound(null, pos, ModSounds.sfx("tree_creak"), SoundCategory.BLOCKS, 1.6f, 1.0f);
        Net.alert(p, Net.Alert.DANGER, Items.OAK_LOG, "ДЕРЕВО ПАДАЕТ!", "Отбегай в сторону");
        Voice.shout(p, "timber");
        // половина деревьев падает прямо на того, кто рубил
        Direction dir = w.random.nextBoolean() ? p.getHorizontalFacing() : p.getHorizontalFacing().getOpposite();
        Tasks.schedule(w.getServer(), 22, () -> fall(w, logs, pos, dir, p));
    }

    private static List<BlockPos> collect(ServerWorld w, BlockPos start, BlockPos base) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (!isLog(w.getBlockState(start))) return out;
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && out.size() < 96) {
            BlockPos q = queue.poll();
            out.add(q);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = q.add(dx, dy, dz);
                        if (seen.contains(n)) continue;
                        if (Math.abs(n.getX() - base.getX()) > 6 || Math.abs(n.getZ() - base.getZ()) > 6 || n.getY() - base.getY() > 32) continue;
                        if (isLog(w.getBlockState(n))) {
                            seen.add(n);
                            queue.add(n);
                        }
                    }
                }
            }
        }
        return out;
    }

    private static boolean isLog(BlockState s) {
        return s.isIn(BlockTags.LOGS);
    }

    private static boolean hasLeaves(ServerWorld w, List<BlockPos> logs) {
        BlockPos top = logs.stream().max(Comparator.comparingInt(BlockPos::getY)).orElse(logs.get(0));
        for (BlockPos q : BlockPos.iterate(top.add(-2, -1, -2), top.add(2, 2, 2))) {
            if (w.getBlockState(q).isIn(BlockTags.LEAVES)) return true;
        }
        return false;
    }

    private static void fall(ServerWorld w, List<BlockPos> logs, BlockPos base, Direction dir, PlayerEntity p) {
        List<BlockPos> crown = crown(w, logs);
        for (BlockPos q : logs) launch(w, q, base, dir, true);
        for (BlockPos q : crown) launch(w, q, base, dir, false);
        w.playSound(null, base, ModSounds.sfx("tree_fall"), SoundCategory.BLOCKS, 2.0f, 0.9f);
        if (p instanceof ServerPlayerEntity sp) Achievements.grant(sp, "timber");
    }

    /** Дерево поворачивается вокруг пня: блок на высоте h ложится в h блоках от него. */
    private static void launch(ServerWorld w, BlockPos q, BlockPos base, Direction dir, boolean log) {
        BlockState s = w.getBlockState(q);
        if (log ? !isLog(s) : !s.isIn(BlockTags.LEAVES)) return;
        int h = Math.max(1, q.getY() - base.getY());
        double along = (q.getX() - base.getX()) * dir.getOffsetX() + (q.getZ() - base.getZ()) * dir.getOffsetZ();
        double speed = Math.max(0.0, h - along) / reach(h);
        // бревно ложится вдоль направления падения, а не стоит пеньком
        if (log && s.contains(Properties.AXIS)) s = s.with(Properties.AXIS, dir.getAxis());
        FallingBlockEntity f = FallingBlockEntity.spawnFromBlock(w, q, s);
        f.setVelocity(dir.getOffsetX() * speed, 0.0, dir.getOffsetZ() * speed);
        if (log) {
            f.setHurtEntities(1.5f, 10);
            f.dropItem = true;
        } else {
            f.dropItem = false;
        }
    }

    /** Сколько блоков по горизонтали пролетит падающий блок со скоростью 1 за падение с высоты h. */
    private static double reach(int h) {
        double y = h, vy = 0, drag = 1, sum = 0;
        for (int t = 0; t < 200 && y > 0; t++) {
            vy -= 0.04;
            y += vy;
            sum += drag;
            drag *= 0.98;
            vy *= 0.98;
        }
        return Math.max(sum, 1.0);
    }

    /** Природная листва, касающаяся ствола (поставленную игроком не трогаем). */
    private static List<BlockPos> crown(ServerWorld w, List<BlockPos> logs) {
        List<BlockPos> out = new ArrayList<>();
        // глубина от ствола: дальше 4 блоков начинаются кроны соседних деревьев
        Map<BlockPos, Integer> depth = new HashMap<>();
        for (BlockPos q : logs) depth.put(q, 0);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(logs);
        while (!queue.isEmpty() && out.size() < 220) {
            BlockPos q = queue.poll();
            int dq = depth.get(q);
            if (dq >= 4) continue;
            for (Direction d : Direction.values()) {
                BlockPos n = q.offset(d);
                if (depth.containsKey(n)) continue;
                depth.put(n, dq + 1);
                BlockState s = w.getBlockState(n);
                if (s.isIn(BlockTags.LEAVES) && s.contains(LeavesBlock.PERSISTENT) && !s.get(LeavesBlock.PERSISTENT)) {
                    out.add(n);
                    queue.add(n);
                }
            }
        }
        return out;
    }

    private Timber() {
    }
}
