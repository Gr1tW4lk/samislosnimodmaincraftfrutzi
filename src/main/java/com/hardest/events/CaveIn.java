package com.hardest.events;

import com.hardest.net.Net;
import com.hardest.registry.ModSounds;
import com.hardest.util.Achievements;
import com.hardest.util.Tasks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Обвал в шахте: копаешь глубоко — потолок может рухнуть. Секунда на то, чтобы убежать.
 */
public final class CaveIn {
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    public static void onStoneBroken(ServerWorld w, ServerPlayerEntity p, BlockPos pos, BlockState state) {
        if (p.isCreative() || pos.getY() > 48 || w.isSkyVisible(pos)) return;
        if (!(state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isOf(Blocks.COBBLESTONE))) return;
        long now = w.getServer().getTicks();
        if (now < COOLDOWN.getOrDefault(p.getUuid(), 0L) || w.random.nextFloat() > 0.035f) return;
        COOLDOWN.put(p.getUuid(), now + 20 * 90);
        trigger(w, p);
    }

    public static void trigger(ServerWorld w, ServerPlayerEntity p) {
        BlockPos c = p.getBlockPos();
        List<BlockPos> ceiling = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 2; dy <= 7; dy++) {
                    BlockPos q = c.add(dx, dy, dz);
                    BlockState s = w.getBlockState(q);
                    if (s.isAir()) continue;
                    if ((s.isIn(BlockTags.BASE_STONE_OVERWORLD) || s.isOf(Blocks.DIRT) || s.isOf(Blocks.GRAVEL)) && w.getBlockState(q.down()).isAir()) {
                        ceiling.add(q);
                    }
                    break;
                }
            }
        }
        if (ceiling.size() < 3) return;
        w.playSound(null, c, ModSounds.sfx("rumble"), SoundCategory.BLOCKS, 2.0f, 0.8f);
        Net.alert(p, Net.Alert.DANGER, Items.GRAVEL, "ОБВАЛ", "Беги! Потолок падает");
        for (int i = 0; i < 6; i++) {
            int delay = i * 4;
            Tasks.schedule(w.getServer(), delay, () -> {
                for (BlockPos q : ceiling) {
                    w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, w.getBlockState(q)),
                            q.getX() + 0.5, q.getY() - 0.05, q.getZ() + 0.5, 2, 0.4, 0, 0.4, 0);
                }
            });
        }
        Tasks.schedule(w.getServer(), 28, () -> {
            int n = 0;
            for (BlockPos q : ceiling) {
                if (n++ > 14 || !w.getBlockState(q.down()).isAir()) continue;
                FallingBlockEntity f = FallingBlockEntity.spawnFromBlock(w, q, Blocks.GRAVEL.getDefaultState());
                f.setHurtEntities(1.0f, 6);
            }
            w.playSound(null, c, ModSounds.sfx("slam"), SoundCategory.BLOCKS, 1.5f, 0.6f);
            Achievements.grant(p, "cave_in");
        });
    }

    private CaveIn() {
    }
}
