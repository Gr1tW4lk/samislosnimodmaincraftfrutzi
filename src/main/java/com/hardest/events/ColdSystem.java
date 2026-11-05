package com.hardest.events;

import com.hardest.net.Net;
import com.hardest.registry.ModEffects;
import com.hardest.util.Achievements;
import com.hardest.util.Voice;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Простуда. Промок — простыл. Простыл — чихаешь так, что улетаешь назад и роняешь вещи из рук.
 * Лечится молоком, чаем с малиной или если постоять у огня.
 */
public final class ColdSystem {
    private static final String[] SNEEZE = {"sneeze1", "sneeze2", "sneeze3"};
    private static final Map<UUID, Float> WET = new HashMap<>();
    private static final Map<UUID, Integer> WARM = new HashMap<>();

    public static void tick(MinecraftServer server) {
        int t = server.getTicks();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) continue;
            boolean cold = p.hasStatusEffect(ModEffects.COLD);
            if (cold && p.getRandom().nextInt(500) == 0) sneeze(p);
            if (t % 20 != 0) continue;
            UUID id = p.getUuid();
            float wet = WET.getOrDefault(id, 0f);
            boolean warm = nearHeat(p);
            if (p.isSubmergedInWater()) wet += 4;
            else if (p.isWet()) wet += 2;
            else if (warm) wet -= 8;
            else wet -= 0.5f;
            wet = Math.max(0, wet);
            if (!cold && wet >= 100) {
                wet = 0;
                p.addStatusEffect(new StatusEffectInstance(ModEffects.COLD, 20 * 60 * 5, 0, false, true, true));
                Net.alert(p, Net.Alert.COLD, Items.SNOWBALL, "ТЫ ПРОСТУДИЛСЯ", "Чай с малиной, молоко или костёр");
            }
            if (wet > 60 && !cold && t % 200 == 0) {
                Net.alert(p, Net.Alert.COLD, Items.WATER_BUCKET, "ТЫ ВЕСЬ ПРОМОК", "Ещё чуть-чуть — и простуда");
            }
            WET.put(id, wet);
            if (cold && warm) {
                int w = WARM.getOrDefault(id, 0) + 1;
                if (w >= 12) {
                    p.removeStatusEffect(ModEffects.COLD);
                    Net.alert(p, Net.Alert.GOOD, Items.CAMPFIRE, "СОГРЕЛСЯ", "Простуда прошла");
                    w = 0;
                }
                WARM.put(id, w);
            } else {
                WARM.remove(id);
            }
        }
    }

    private static boolean nearHeat(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        BlockPos c = p.getBlockPos();
        for (BlockPos q : BlockPos.iterate(c.add(-3, -1, -3), c.add(3, 2, 3))) {
            BlockState s = w.getBlockState(q);
            if (s.isIn(BlockTags.FIRE) || s.isOf(Blocks.LAVA) || s.isOf(Blocks.MAGMA_BLOCK)
                    || (s.getBlock() instanceof CampfireBlock && s.get(CampfireBlock.LIT))) return true;
        }
        return false;
    }

    public static void sneeze(ServerPlayerEntity p) {
        Voice.shout(p, SNEEZE[p.getRandom().nextInt(SNEEZE.length)]);
        Vec3d look = p.getRotationVector();
        p.addVelocity(-look.x * 0.9, 0.35, -look.z * 0.9);
        p.velocityModified = true;
        p.getServerWorld().spawnParticles(ParticleTypes.ITEM_SLIME, p.getX() + look.x * 0.6, p.getEyeY() - 0.2, p.getZ() + look.z * 0.6,
                12, 0.15, 0.1, 0.15, 0.15);
        Net.fx(p, "shake", 12, 4);
        Net.fx(p, "sneeze", 8, 0);
        if (p.getRandom().nextFloat() < 0.35f && !p.getMainHandStack().isEmpty()) {
            p.dropSelectedItem(true);
            Net.alert(p, Net.Alert.COLD, Items.SLIME_BALL, "АПЧХИ!", "Ты выронил предмет");
        }
        Achievements.grant(p, "sneeze");
    }

    public static void clear(UUID id) {
        WET.remove(id);
        WARM.remove(id);
    }

    private ColdSystem() {
    }
}
