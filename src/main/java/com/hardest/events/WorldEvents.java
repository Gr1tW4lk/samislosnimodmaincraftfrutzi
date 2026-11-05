package com.hardest.events;

import com.hardest.entity.CourierCreeperEntity;
import com.hardest.entity.DoppelgangerEntity;
import com.hardest.entity.ParachuteCowEntity;
import com.hardest.entity.TaxInspectorEntity;
import com.hardest.mobs.MobTweaks;
import com.hardest.net.Net;
import com.hardest.registry.ModEntities;
import com.hardest.registry.ModSounds;
import com.hardest.util.Achievements;
import com.hardest.util.PositionHistory;
import com.hardest.util.Spots;
import com.hardest.util.Tasks;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Мировые события без всякого ведущего: налоговая, курьер, реклама, погода из коров и наковален,
 * Кровавая луна, лаги в Незере и «загрузочные экраны».
 */
public final class WorldEvents {
    private static final long MIN = 20L * 60;
    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1f, 0.1f, 0.1f), 1.3f);

    private static boolean wasRaining, wasThundering;
    private static long cowfallUntil, anvilsUntil;
    private static boolean bloodMoon;
    private static long forcedBloodMoonDay = -1;
    private static final Set<UUID> DOPPEL_DONE = new HashSet<>();
    private static final Map<UUID, Long> NEXT_LAG = new HashMap<>();
    private record Marker(ServerWorld world, BlockPos pos, long until) {
    }
    private static final List<Marker> MARKERS = new ArrayList<>();

    public static void register() {
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> onChangeWorld(player, destination));
    }

    private static long now(MinecraftServer server) {
        return server.getOverworld().getTime();
    }

    public static void onJoin(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        EventState st = EventState.get(server);
        EventState.PlayerData d = st.get(p.getUuid());
        long t = now(server);
        if (d.nextTax == 0) {
            d.nextTax = t + 20 * MIN;
            d.nextCourier = t + 12 * MIN;
            d.nextAd = t + 8 * MIN;
            st.markDirty();
        }
        if (bloodMoon && p.getWorld().getRegistryKey() == World.OVERWORLD) {
            Net.fx(p, "bloodmoon", bloodMoonTicksLeft(server.getOverworld()), 0);
        }
    }

    // ---------------------------------------------------------------- главный цикл

    public static void tick(MinecraftServer server) {
        long t = now(server);
        ServerWorld ow = server.getOverworld();
        tickWeather(server, ow, t);
        tickBloodMoon(server, ow, t);
        tickMarkers(server.getTicks());
        if (server.getTicks() % 20 != 0) return;
        EventState st = EventState.get(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            EventState.PlayerData d = st.get(p.getUuid());
            boolean inEnd = p.getWorld().getRegistryKey() == World.END;
            if (d.nextTax != 0 && t >= d.nextTax && !inEnd) {
                if (countItems(p) >= 16) {
                    startTax(p);
                    d.nextTax = t + (25 + p.getRandom().nextInt(16)) * MIN;
                } else {
                    d.nextTax = t + 5 * MIN;
                }
                st.markDirty();
            }
            if (d.nextCourier != 0 && t >= d.nextCourier && !inEnd) {
                startCourier(p);
                d.nextCourier = t + (20 + p.getRandom().nextInt(16)) * MIN;
                st.markDirty();
            }
            if (d.nextAd != 0 && t >= d.nextAd) {
                Net.ad(p, p.getRandom().nextInt(6));
                d.nextAd = t + (15 + p.getRandom().nextInt(11)) * MIN;
                st.markDirty();
            }
            if (p.getWorld().getRegistryKey() == World.NETHER) {
                long next = NEXT_LAG.getOrDefault(p.getUuid(), (long) server.getTicks() + 20 * 60);
                if (server.getTicks() >= next) {
                    lagSpike(p, 120);
                    next = server.getTicks() + 20 * (90 + p.getRandom().nextInt(90));
                }
                NEXT_LAG.put(p.getUuid(), next);
            }
        }
    }

    private static int countItems(ServerPlayerEntity p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) n += p.getInventory().getStack(i).getCount();
        return n;
    }

    // ---------------------------------------------------------------- налоговая, курьер

    public static void startTax(ServerPlayerEntity p) {
        Net.card(p, "НАЛОГОВАЯ ПРОВЕРКА", "Инспектор уже идёт к вам", 0x8FB4FF, 70, false);
        p.getServerWorld().playSound(null, p.getBlockPos(), ModSounds.sfx("siren"), SoundCategory.MASTER, 0.8f, 1.0f);
        TaxInspectorEntity.spawnFor(p);
    }

    public static void startCourier(ServerPlayerEntity p) {
        Net.card(p, "ДОСТАВКА", "Вам посылка. Отправитель неизвестен", 0xF5C518, 70, false);
        p.getServerWorld().playSound(null, p.getBlockPos(), ModSounds.sfx("courier_bell"), SoundCategory.MASTER, 1.0f, 1.0f);
        CourierCreeperEntity.spawnFor(p);
    }

    // ---------------------------------------------------------------- лаги и загрузки

    /** Лаг-спайк: пинг 999, тебя пару раз откидывает назад. */
    public static void lagSpike(ServerPlayerEntity p, int ticks) {
        Net.fx(p, "lag", ticks, 0);
        UUID id = p.getUuid();
        MinecraftServer server = p.getServer();
        for (int delay : new int[]{20, 55, 95}) {
            if (delay >= ticks) break;
            Tasks.schedule(server, delay, () -> {
                ServerPlayerEntity pl = server.getPlayerManager().getPlayer(id);
                if (pl == null || !pl.isAlive()) return;
                Vec3d back = PositionHistory.get(pl, 20);
                if (back != null) {
                    pl.networkHandler.requestTeleport(back.x, back.y, back.z, pl.getYaw(), pl.getPitch());
                    Net.fx(pl, "freeze", 10, 0);
                }
            });
        }
    }

    private static void onChangeWorld(ServerPlayerEntity p, ServerWorld dest) {
        EventState st = EventState.get(p.getServer());
        EventState.PlayerData d = st.get(p.getUuid());
        if (dest.getRegistryKey() != World.OVERWORLD) Net.fx(p, "bloodmoon", 0, 0);
        else if (bloodMoon) Net.fx(p, "bloodmoon", bloodMoonTicksLeft(dest), 0);
        if (dest.getRegistryKey() == World.NETHER && !d.visitedNether) {
            d.visitedNether = true;
            st.markDirty();
            loadingScreen(p, 1);
        } else if (dest.getRegistryKey() == World.END && !d.visitedEnd) {
            d.visitedEnd = true;
            st.markDirty();
            loadingScreen(p, 2);
        }
    }

    /** «Загрузка Незера... Не выключайте компьютер». Пока грузится — стоишь как вкопанный. */
    public static void loadingScreen(ServerPlayerEntity p, int which) {
        int ticks = 160;
        Net.fx(p, "loading", ticks, which);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 3, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 6, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, ticks, 200, false, false));
        Achievements.grant(p, "loading");
    }

    // ---------------------------------------------------------------- погода из коров и наковален

    private static void tickWeather(MinecraftServer server, ServerWorld ow, long t) {
        boolean raining = ow.isRaining();
        boolean thunder = ow.isThundering();
        if (raining && !wasRaining && ow.getRandom().nextFloat() < 0.4f) startCowfall(server, 40);
        if (thunder && !wasThundering) startAnvils(server, 30);
        wasRaining = raining;
        wasThundering = thunder;
        int ticks = server.getTicks();
        if (ticks < cowfallUntil && ticks % 7 == 0) {
            for (ServerPlayerEntity p : ow.getPlayers()) {
                if (p.isSpectator() || !ow.isSkyVisible(p.getBlockPos())) continue;
                BlockPos sky = Spots.sky(ow, p.getBlockPos(), 14, 28);
                if (!ow.getBlockState(sky).isAir()) continue;
                ParachuteCowEntity cow = ModEntities.PARACHUTE_COW.create(ow);
                if (cow == null) continue;
                cow.refreshPositionAndAngles(sky.getX() + 0.5, sky.getY(), sky.getZ() + 0.5, ow.random.nextFloat() * 360, 0);
                cow.setup(ow.random.nextFloat() < 0.65f);
                ow.spawnEntity(cow);
            }
        }
        if (ticks < anvilsUntil && ticks % 25 == 0) {
            for (ServerPlayerEntity p : ow.getPlayers()) {
                if (p.isSpectator() || !ow.isSkyVisible(p.getBlockPos())) continue;
                dropAnvil(ow, p.getBlockPos(), 7);
            }
        }
    }

    public static void dropAnvil(ServerWorld w, BlockPos around, int radius) {
        BlockPos sky = Spots.sky(w, around, radius, 22);
        if (!w.getBlockState(sky).isAir()) return;
        FallingBlockEntity anvil = FallingBlockEntity.spawnFromBlock(w, sky, Blocks.ANVIL.getDefaultState());
        anvil.setHurtEntities(2.0f, 40);
        int ground = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sky.getX(), sky.getZ());
        MARKERS.add(new Marker(w, new BlockPos(sky.getX(), ground, sky.getZ()), w.getServer().getTicks() + 45));
    }

    /** Красная метка на земле там, куда что-то сейчас упадёт. */
    public static void marker(ServerWorld w, BlockPos pos, int ticks) {
        MARKERS.add(new Marker(w, pos, w.getServer().getTicks() + ticks));
    }

    private static void tickMarkers(int ticks) {
        if (MARKERS.isEmpty() || ticks % 3 != 0) return;
        Iterator<Marker> it = MARKERS.iterator();
        while (it.hasNext()) {
            Marker m = it.next();
            if (ticks > m.until) {
                it.remove();
                continue;
            }
            m.world.spawnParticles(RED, m.pos.getX() + 0.5, m.pos.getY() + 0.1, m.pos.getZ() + 0.5, 8, 0.6, 0.02, 0.6, 0);
        }
    }

    public static void startCowfall(MinecraftServer server, int seconds) {
        cowfallUntil = server.getTicks() + seconds * 20L;
        for (ServerPlayerEntity p : server.getOverworld().getPlayers()) {
            Net.card(p, "КОРОВОПАД", "Осадки: коровы. Местами без парашюта", 0xF5C518, 70, false);
        }
    }

    public static void startAnvils(MinecraftServer server, int seconds) {
        anvilsUntil = server.getTicks() + seconds * 20L;
        for (ServerPlayerEntity p : server.getOverworld().getPlayers()) {
            Net.card(p, "НАКОВАЛЬНЕПАД", "Гроза принесла наковальни. Смотри наверх", 0xB8B8C8, 70, false);
        }
    }

    // ---------------------------------------------------------------- Кровавая луна

    public static boolean isBloodMoon() {
        return bloodMoon;
    }

    private static boolean bloodMoonNow(ServerWorld ow) {
        long tod = ow.getTimeOfDay();
        long day = tod / 24000L;
        long t = tod % 24000L;
        boolean night = t >= 13000 && t < 23000;
        return night && (day % 3 == 2 || day == forcedBloodMoonDay);
    }

    private static int bloodMoonTicksLeft(ServerWorld ow) {
        return (int) Math.max(20, 23000 - ow.getTimeOfDay() % 24000L);
    }

    /** Для съёмки: прямо сейчас наступает Кровавая луна. */
    public static void forceBloodMoon(MinecraftServer server) {
        ServerWorld ow = server.getOverworld();
        long day = ow.getTimeOfDay() / 24000L;
        forcedBloodMoonDay = day;
        ow.setTimeOfDay(day * 24000L + 13000L);
    }

    private static void tickBloodMoon(MinecraftServer server, ServerWorld ow, long t) {
        boolean now = bloodMoonNow(ow);
        if (now && !bloodMoon) {
            bloodMoon = true;
            DOPPEL_DONE.clear();
            for (ServerPlayerEntity p : ow.getPlayers()) {
                Net.card(p, "КРОВАВАЯ ЛУНА", "Этой ночью не спится", 0xE02020, 110, true);
                Net.fx(p, "bloodmoon", bloodMoonTicksLeft(ow), 0);
                ow.playSound(null, p.getBlockPos(), ModSounds.sfx("blood_moon"), SoundCategory.MASTER, 1.5f, 1.0f);
            }
        } else if (!now && bloodMoon) {
            bloodMoon = false;
            EventState st = EventState.get(server);
            for (ServerPlayerEntity p : ow.getPlayers()) {
                Net.fx(p, "bloodmoon", 0, 0);
                if (p.isAlive()) {
                    st.get(p.getUuid()).bloodMoons++;
                    Achievements.grant(p, "blood_moon");
                    Net.card(p, "РАССВЕТ", "Ты пережил Кровавую луну", 0xFFC233, 70, false);
                }
            }
            st.markDirty();
        }
        if (!bloodMoon || server.getTicks() % 100 != 0) return;
        Random r = ow.getRandom();
        long tod = ow.getTimeOfDay() % 24000L;
        for (ServerPlayerEntity p : ow.getPlayers()) {
            if (p.isSpectator() || p.isCreative()) continue;
            int hostiles = ow.getEntitiesByClass(HostileEntity.class, p.getBoundingBox().expand(32), e -> true).size();
            if (hostiles < 22) {
                for (int i = 0; i < 1 + r.nextInt(2); i++) spawnNightmare(ow, p, r);
            }
            if (tod > 16000 && DOPPEL_DONE.add(p.getUuid())) {
                DoppelgangerEntity.spawnFor(p, 8, 14, true);
            }
        }
    }

    private static void spawnNightmare(ServerWorld w, ServerPlayerEntity p, Random r) {
        BlockPos pos = Spots.ground(w, p.getBlockPos(), 16, 28);
        if (pos == null || w.getLightLevel(LightType.BLOCK, pos) > 7) return;
        float roll = r.nextFloat();
        EntityType<? extends MobEntity> type = roll < 0.4f ? EntityType.ZOMBIE : roll < 0.65f ? EntityType.SKELETON
                : roll < 0.85f ? EntityType.CREEPER : EntityType.SPIDER;
        MobEntity mob = type.create(w);
        if (mob == null) return;
        mob.addCommandTag(MobTweaks.TAG_BLOOD);
        Spots.place(w, mob, pos, r.nextFloat() * 360);
        mob.setTarget(p);
    }

    public static void onServerStopped() {
        wasRaining = wasThundering = false;
        cowfallUntil = anvilsUntil = 0;
        bloodMoon = false;
        forcedBloodMoonDay = -1;
        MARKERS.clear();
        NEXT_LAG.clear();
        DOPPEL_DONE.clear();
    }

    private WorldEvents() {
    }
}
