package com.hardest.dragon;

import com.hardest.mixin.EnderDragonFightAccessor;
import com.hardest.net.Net;
import com.hardest.registry.ModSounds;
import com.hardest.util.Achievements;
import com.hardest.util.Tasks;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.EndSpikeFeature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Пробуждённый Эндер-дракон: вдвое больше, 500 HP, три фазы с катсценами
 * и очередь атак — одна большая атака за раз, чтобы от каждой можно было увернуться.
 */
public final class DragonRage {
    public static final float SCALE = 2.0f;
    public static final float MAX_HP = 500f;
    private static final String TAG_INIT = "hard_init";
    private static final String TAG_PHASE = "hard_phase";

    enum Attack {
        METEORS, ROAR, DIVE, SPIKES, VORTEX, SWARM, GEESE, COWS, LASER, BIG_METEOR, GRAVITY
    }

    /** Вес атаки по фазам 1, 2, 3 (0 — в этой фазе её нет). */
    private static final Map<Attack, int[]> WEIGHTS = new HashMap<>();

    static {
        WEIGHTS.put(Attack.METEORS, new int[]{3, 2, 1});
        WEIGHTS.put(Attack.ROAR, new int[]{2, 2, 2});
        WEIGHTS.put(Attack.DIVE, new int[]{3, 3, 3});
        WEIGHTS.put(Attack.SPIKES, new int[]{0, 3, 3});
        WEIGHTS.put(Attack.VORTEX, new int[]{0, 2, 2});
        WEIGHTS.put(Attack.SWARM, new int[]{0, 1, 1});
        WEIGHTS.put(Attack.GEESE, new int[]{0, 1, 1});
        WEIGHTS.put(Attack.COWS, new int[]{0, 0, 2});
        WEIGHTS.put(Attack.LASER, new int[]{0, 0, 2});
        WEIGHTS.put(Attack.BIG_METEOR, new int[]{0, 0, 3});
        WEIGHTS.put(Attack.GRAVITY, new int[]{0, 0, 1});
    }

    public static final class State {
        public int phase = 1;
        /** Сколько тиков ещё идёт текущая атака. */
        int busy;
        /** Пауза до следующей атаки. Первая — после экрана «Загружаем Край» (160 тиков) и пары секунд на осмотреться. */
        int next = 300;
        /** Катсцена смены фазы. */
        int cinema;
        Attack last;
        Vec3d diveAim;
        int diveTicks;
        final Set<UUID> seen = new HashSet<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static void register() {
        Fx.register();
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof EnderDragonEntity d && d.getWorld() instanceof ServerWorld w) {
                STATES.remove(d.getUuid());
                for (ServerPlayerEntity p : w.getPlayers()) {
                    Achievements.grant(p, "dragon");
                    Net.fx(p, "end_rage", 0, 0);
                    Net.card(p, "ДРАКОН ПОВЕРЖЕН", "Самый сложный мод пройден. Ты страдал не зря", 0xFFC233, 160, true);
                }
            }
        });
    }

    public static void tick(MinecraftServer server) {
        ServerWorld end = server.getWorld(World.END);
        if (end == null) return;
        DragonAttacks.tickAll();
        for (EnderDragonEntity d : end.getEntitiesByType(TypeFilter.instanceOf(EnderDragonEntity.class), e -> !e.isRemoved())) {
            try {
                tickDragon(end, d);
            } catch (Exception e) {
                com.hardest.HardestMod.LOG.error("Ошибка в бою с драконом", e);
            }
        }
    }

    public static State state(EnderDragonEntity d) {
        return STATES.computeIfAbsent(d.getUuid(), u -> {
            State fresh = new State();
            // фаза переживает перезаход в мир: кристаллы второй фазы появляются только один раз
            if (d.getCommandTags().contains(TAG_PHASE + 3)) fresh.phase = 3;
            else if (d.getCommandTags().contains(TAG_PHASE + 2)) fresh.phase = 2;
            return fresh;
        });
    }

    private static void tickDragon(ServerWorld w, EnderDragonEntity d) {
        if (!d.getCommandTags().contains(TAG_INIT)) {
            d.addCommandTag(TAG_INIT);
            d.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(MAX_HP);
            d.setHealth(MAX_HP);
        }
        // в фазе смерти ванильный дракон держит 1 HP — это уже не бой, а финальное шоу
        if (d.ticksSinceDeath > 0 || d.getPhaseManager().getCurrent().getType() == PhaseType.DYING) {
            dying(w, d);
            return;
        }
        if (!d.isAlive()) return;
        State st = state(d);
        List<ServerPlayerEntity> players = w.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(d) < 220 * 220);
        for (ServerPlayerEntity p : players) {
            // пока на экране «Загружаем Край», вступление не показываем — его бы никто не увидел
            if (!loading(p) && st.seen.add(p.getUuid())) intro(w, d, p);
        }
        if (w.getTime() % 20 == 0) bossBar(w, st.phase);
        // красный туман последней фазы: обновляем, чтобы он пережил перезаход в мир
        if (st.phase >= 3 && w.getTime() % 100 == 0) {
            for (ServerPlayerEntity p : players) Net.fx(p, "end_rage", 140, 1);
        }
        if (players.isEmpty()) return;

        float frac = d.getHealth() / d.getMaxHealth();
        int want = frac > 0.6f ? 1 : frac > 0.3f ? 2 : 3;
        if (want > st.phase) {
            st.phase = want;
            d.addCommandTag(TAG_PHASE + want);
            st.diveAim = null;
            cinematic(w, d, st, want);
            bossBar(w, want);
        }
        if (st.cinema > 0) {
            if (--st.cinema == 0) d.getPhaseManager().setPhase(PhaseType.HOLDING_PATTERN);
            else if (d.getPhaseManager().getCurrent().getType() != PhaseType.HOVER) d.getPhaseManager().setPhase(PhaseType.HOVER);
            return;
        }
        if (st.diveAim != null) DragonAttacks.diveTick(w, d, st);
        if (st.busy > 0) {
            st.busy--;
            return;
        }
        PhaseType<?> type = d.getPhaseManager().getCurrent().getType();
        boolean perched = type == PhaseType.SITTING_FLAMING || type == PhaseType.SITTING_SCANNING
                || type == PhaseType.SITTING_ATTACKING || type == PhaseType.LANDING || type == PhaseType.TAKEOFF
                || type == PhaseType.LANDING_APPROACH;
        if (--st.next > 0) return;

        Random r = w.random;
        ServerPlayerEntity target = players.get(r.nextInt(players.size()));
        Attack a = pick(r, st.phase, st.last, perched);
        st.last = a;
        st.busy = run(w, d, st, a, target, players);
        // в последней фазе дракон иногда добавляет вторую атаку сверху
        if (st.phase >= 3 && r.nextFloat() < 0.3f && a != Attack.METEORS && a != Attack.COWS) {
            run(w, d, st, r.nextBoolean() ? Attack.METEORS : Attack.COWS, target, players);
        }
        int gap = st.phase == 1 ? 150 + r.nextInt(90) : st.phase == 2 ? 110 + r.nextInt(70) : 75 + r.nextInt(55);
        // пока дракон сидит на портале — это окно для удара, атак меньше
        st.next = perched ? gap * 3 / 2 : gap;
    }

    private static Attack pick(Random r, int phase, Attack last, boolean perched) {
        List<Attack> pool = new ArrayList<>();
        for (Map.Entry<Attack, int[]> e : WEIGHTS.entrySet()) {
            Attack a = e.getKey();
            if (a == last || (perched && a == Attack.DIVE)) continue;
            for (int i = 0; i < e.getValue()[phase - 1]; i++) pool.add(a);
        }
        if (pool.isEmpty()) return Attack.METEORS;
        return pool.get(r.nextInt(pool.size()));
    }

    static int run(ServerWorld w, EnderDragonEntity d, State st, Attack a, ServerPlayerEntity target, List<ServerPlayerEntity> players) {
        int phase = st.phase;
        int busy = 0;
        switch (a) {
            case METEORS -> {
                for (ServerPlayerEntity p : players) busy = Math.max(busy, DragonAttacks.meteors(w, d, p, 3 + phase));
            }
            case ROAR -> busy = DragonAttacks.roar(w, d, players);
            case DIVE -> busy = DragonAttacks.dive(w, d, target, st);
            case SPIKES -> busy = DragonAttacks.spikes(w, d, target, phase);
            case VORTEX -> busy = DragonAttacks.vortex(w, d, target, phase);
            case SWARM -> {
                for (ServerPlayerEntity p : players) busy = DragonAttacks.swarm(w, p, 3 + w.random.nextInt(3));
            }
            case GEESE -> {
                for (ServerPlayerEntity p : players) busy = DragonAttacks.geese(w, p, 3);
            }
            case COWS -> {
                for (ServerPlayerEntity p : players) busy = DragonAttacks.cows(w, p, 4);
            }
            case LASER -> busy = DragonAttacks.laser(w, d, target);
            case BIG_METEOR -> busy = DragonAttacks.bigMeteor(w, d, target);
            case GRAVITY -> busy = DragonAttacks.gravity(players);
        }
        return busy;
    }

    /** Экран загрузки держит игрока прыгучестью 200 (см. WorldEvents.loadingScreen). */
    private static boolean loading(ServerPlayerEntity p) {
        StatusEffectInstance jump = p.getStatusEffect(StatusEffects.JUMP_BOOST);
        return jump != null && jump.getAmplifier() >= 200;
    }

    // ---------------------------------------------------------------- шоу

    private static void intro(ServerWorld w, EnderDragonEntity d, ServerPlayerEntity p) {
        Net.card(p, "ПРОБУЖДЁННЫЙ ЭНДЕР-ДРАКОН", "Он больше, злее и помнит каждого убитого предка", 0xC26BFF, 120, true);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 3f, 0.45f);
        w.playSound(null, p.getBlockPos(), ModSounds.sfx("boss_boom"), SoundCategory.HOSTILE, 2f, 0.7f);
        Net.fx(p, "dragon_roar", 45, 0);
        Net.fx(p, "shake", 40, 4);
        Achievements.grant(p, "dragon_seen");
    }

    /** Смена фазы: дракон зависает, ревёт, по острову бьют молнии, по земле идёт волна. */
    private static void cinematic(ServerWorld w, EnderDragonEntity d, State st, int phase) {
        st.cinema = 70;
        st.busy = 0;
        st.next = 60;
        d.getPhaseManager().setPhase(PhaseType.HOVER);
        Vec3d c = d.getPos();
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.getPos().distanceTo(c) > 260) continue;
            if (phase == 2) {
                Net.card(p, "ДРАКОН В ЯРОСТИ", "Кристаллы загораются снова", 0xFF4FD8, 80, true);
            } else {
                Net.card(p, "ПОСЛЕДНИЙ РЫВОК", "Небо падает на голову", 0xFF3030, 80, true);
                Net.fx(p, "end_rage", 140, 1);
            }
            Net.fx(p, "dragon_roar", 60, 0);
            Net.fx(p, "shake", 60, 6);
            w.playSound(null, p.getBlockPos(), ModSounds.sfx("dragon_phase"), SoundCategory.HOSTILE, 2f, 1f);
        }
        w.playSound(null, d.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 16f, 0.4f);
        Random r = w.random;
        for (int i = 0; i < 12; i++) {
            Tasks.schedule(w.getServer(), 4 + i * 4, () -> {
                double a = r.nextDouble() * Math.PI * 2, rr = 8 + r.nextDouble() * 30;
                double x = Math.cos(a) * rr, z = Math.sin(a) * rr;
                int g = Fx.ground(w, x, z);
                if (g > w.getBottomY()) Fx.lightning(w, new Vec3d(x, g, z));
            });
        }
        Tasks.schedule(w.getServer(), 20, () -> {
            if (!d.isAlive()) return;
            Vec3d body = d.getPos();
            Fx.particle(w, ParticleTypes.EXPLOSION_EMITTER, body.x, body.y, body.z, 4, 6, 3, 6, 0);
            Fx.particle(w, ParticleTypes.END_ROD, body.x, body.y, body.z, 200, 2, 2, 2, 0.8);
            Fx.particle(w, phase >= 3 ? Fx.RED : Fx.PURPLE, body.x, body.y, body.z, 200, 8, 4, 8, 0);
            Vec3d g = DragonAttacks.groundUnder(w, body);
            if (g.y > w.getBottomY()) DragonAttacks.shockwave(w, d, g, 42, 0.9, 4f, phase >= 3);
            w.playSound(null, body.x, body.y, body.z, ModSounds.sfx("boss_boom"), SoundCategory.HOSTILE, 16f, 0.6f);
        });
        if (phase == 2) Tasks.schedule(w.getServer(), 30, () -> respawnCrystals(w, 4));
    }

    /** Смерть: тряска нарастает, тело рвут взрывы, в конце — вспышка и волна света. */
    private static void dying(ServerWorld w, EnderDragonEntity d) {
        Vec3d c = d.getPos();
        int t = d.ticksSinceDeath;
        if (w.getTime() % 5 == 0) {
            float power = t > 0 ? 2 + t / 40f : 2;
            Fx.shake(w, c, 220, 8, power);
            Random r = w.random;
            Fx.particle(w, ParticleTypes.EXPLOSION, c.x + (r.nextDouble() - 0.5) * 12, c.y + (r.nextDouble() - 0.5) * 6, c.z + (r.nextDouble() - 0.5) * 12, 2, 1, 1, 1, 0);
            Fx.particle(w, ParticleTypes.END_ROD, c.x, c.y, c.z, 12, 4, 3, 4, 0.2);
        }
        if (t == 196) {
            Fx.fxNear(w, c, 260, "flash", 30, 0);
            Fx.shake(w, c, 260, 40, 9);
            w.playSound(null, c.x, c.y, c.z, ModSounds.sfx("boss_boom"), SoundCategory.HOSTILE, 30f, 0.5f);
            Vec3d g = DragonAttacks.groundUnder(w, c);
            if (g.y > w.getBottomY()) DragonAttacks.shockwave(w, d, g, 60, 1.2, 0f, false);
        }
    }

    private static void bossBar(ServerWorld w, int phase) {
        EnderDragonFight fight = w.getEnderDragonFight();
        if (fight == null) return;
        ServerBossBar bar = ((EnderDragonFightAccessor) fight).hardest$bossBar();
        BossBar.Color color = phase == 1 ? BossBar.Color.PURPLE : phase == 2 ? BossBar.Color.PINK : BossBar.Color.RED;
        if (bar.getColor() != color) bar.setColor(color);
        if (bar.getStyle() != BossBar.Style.NOTCHED_10) bar.setStyle(BossBar.Style.NOTCHED_10);
        Text name = switch (phase) {
            case 1 -> Text.literal("Пробуждённый Эндер-дракон").formatted(Formatting.LIGHT_PURPLE);
            case 2 -> Text.literal("Пробуждённый Эндер-дракон — ЯРОСТЬ").formatted(Formatting.RED);
            default -> Text.literal("Пробуждённый Эндер-дракон — ПОСЛЕДНИЙ РЫВОК").formatted(Formatting.DARK_RED, Formatting.BOLD);
        };
        if (!name.equals(bar.getName())) bar.setName(name);
    }

    /** Возвращает кристаллы на вершины обсидиановых столбов. */
    public static int respawnCrystals(ServerWorld w, int max) {
        int made = 0;
        List<EndSpikeFeature.Spike> spikes = new ArrayList<>(EndSpikeFeature.getSpikes(w));
        java.util.Collections.shuffle(spikes, new java.util.Random(w.random.nextLong()));
        for (EndSpikeFeature.Spike s : spikes) {
            if (made >= max) break;
            BlockPos top = new BlockPos(s.getCenterX(), s.getHeight(), s.getCenterZ());
            Box near = new Box(top).expand(3, 4, 3);
            if (!w.getEntitiesByClass(EndCrystalEntity.class, near, e -> true).isEmpty()) continue;
            w.setBlockState(top, Blocks.BEDROCK.getDefaultState());
            EndCrystalEntity c = EntityType.END_CRYSTAL.create(w);
            if (c == null) continue;
            c.refreshPositionAndAngles(top.getX() + 0.5, top.getY() + 1, top.getZ() + 0.5, w.random.nextFloat() * 360f, 0);
            c.setShowBottom(true);
            w.spawnEntity(c);
            Fx.lightning(w, Vec3d.ofBottomCenter(top.up()));
            made++;
        }
        return made;
    }

    public static void clear() {
        STATES.clear();
        DragonAttacks.clear();
    }

    private DragonRage() {
    }
}
