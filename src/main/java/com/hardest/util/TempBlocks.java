package com.hardest.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Временные блоки (паутина от пауков), которые сами исчезают.
 */
public final class TempBlocks {
    private record Entry(RegistryKey<World> world, BlockPos pos, Block block, long until) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static void add(ServerWorld w, BlockPos pos, Block block, int ticks) {
        ENTRIES.add(new Entry(w.getRegistryKey(), pos.toImmutable(), block, w.getServer().getTicks() + ticks));
    }

    public static void tick(MinecraftServer server) {
        if (ENTRIES.isEmpty() || server.getTicks() % 10 != 0) return;
        long now = server.getTicks();
        Iterator<Entry> it = ENTRIES.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.until > now) continue;
            ServerWorld w = server.getWorld(e.world);
            if (w != null && w.getBlockState(e.pos).isOf(e.block)) w.setBlockState(e.pos, Blocks.AIR.getDefaultState());
            it.remove();
        }
    }

    public static void clearAll(MinecraftServer server) {
        for (Entry e : ENTRIES) {
            ServerWorld w = server.getWorld(e.world);
            if (w != null && w.getBlockState(e.pos).isOf(e.block)) w.setBlockState(e.pos, Blocks.AIR.getDefaultState());
        }
        ENTRIES.clear();
    }

    private TempBlocks() {
    }
}
