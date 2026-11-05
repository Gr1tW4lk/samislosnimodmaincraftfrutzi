package com.hardest.dragon;

import com.hardest.entity.GooseEntity;
import com.hardest.entity.ParachuteCowEntity;
import com.hardest.events.WorldEvents;
import com.hardest.net.Net;
import com.hardest.registry.ModEntities;
import com.hardest.registry.ModItems;
import com.hardest.registry.ModSounds;
import com.hardest.util.Spots;
import com.hardest.util.Tasks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.EndermiteEntity;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Атаки Пробуждённого Эндер-дракона. У каждой есть предупреждение и способ увернуться:
 * метка на земле, звук или сообщение. Каждая возвращает, сколько тиков она занимает дракона.
 */
public final class DragonAttacks {
    private static final BlockStateParticleEffect DEBRIS = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.END_STONE.getDefaultState());

    /** То, что живёт несколько тиков: волны, вихри, метеориты. */
    interface Ticker {
        /** false — закончилось. */
        boolean tick();
    }

    private static final List<Ticker> ACTIVE = new ArrayList<>();
    /** Атаки, запущенные во время тика других атак (метеорит порождает волну), — со следующего тика. */
    private static final List<Ticker> PENDING = new ArrayList<>();

    private static void start(Ticker t) {
        PENDING.add(t);
    }

    static void tickAll() {
        ACTIVE.addAll(PENDING);
        PENDING.clear();
        for (Iterator<Ticker> it = ACTIVE.iterator(); it.hasNext(); ) {
            Ticker t = it.next();
            boolean alive;
            try {
                alive = t.tick();
            } catch (Exception e) {
                com.hardest.HardestMod.LOG.error("Ошибка в атаке дракона", e);
                alive = false;
            }
            if (!alive) it.remove();
        }
    }

    static void clear() {
        ACTIVE.clear();
        PENDING.clear();
    }

    private static void sound(ServerWorld w, Vec3d at, SoundEvent ev, float volume, float pitch) {
        w.playSound(null, at.x, at.y, at.z, ev, SoundCategory.HOSTILE, volume, pitch);
    }

    private static boolean hittable(ServerPlayerEntity p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative();
    }

    private static DamageSource hit(ServerWorld w, EnderDragonEntity d) {
        return w.getDamageSources().mobAttack(d);
    }

    static Vec3d headPos(EnderDragonEntity d) {
        EnderDragonPart head = d.head;
        return head.getBoundingBox().getCenter();
    }

    static Vec3d groundUnder(ServerWorld w, Vec3d at) {
        int g = Fx.ground(w, at.x, at.z);
        return new Vec3d(at.x, Math.max(g, w.getBottomY()), at.z);
    }

    // ================================================================ ударная волна

    /** Кольцо бежит по земле от центра. Кто стоит на земле — получает удар и подлетает. Перепрыгни его. */
    public static void shockwave(ServerWorld w, EnderDragonEntity d, Vec3d c, double maxR, double speed, float damage, boolean rage) {
        Set<UUID> hitSet = new HashSet<>();
        double[] r = {1.0};
        ParticleEffect dust = rage ? Fx.RED : Fx.PURPLE;
        start(() -> {
            r[0] += speed;
            int points = (int) Math.min(96, 12 + r[0] * 4);
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points + r[0] * 0.07;
                double x = c.x + Math.cos(a) * r[0], z = c.z + Math.sin(a) * r[0];
                int g = Fx.ground(w, x, z);
                if (g <= w.getBottomY()) continue;
                Fx.particle(w, dust, x, g + 0.2, z, 1, 0.05, 0.05, 0.05, 0);
                if (i % 3 == 0) Fx.particle(w, DEBRIS, x, g + 0.1, z, 2, 0.15, 0.05, 0.15, 0.2);
                if (i % 8 == 0) Fx.particle(w, ParticleTypes.POOF, x, g + 0.3, z, 1, 0.1, 0.1, 0.1, 0.02);
            }
            if (damage > 0) {
                for (ServerPlayerEntity p : w.getPlayers()) {
                    if (!hittable(p) || hitSet.contains(p.getUuid())) continue;
                    double dist = Math.hypot(p.getX() - c.x, p.getZ() - c.z);
                    if (Math.abs(dist - r[0]) > 1.2) continue;
                    int g = Fx.ground(w, p.getX(), p.getZ());
                    // в прыжке волна проходит под ногами
                    if (!p.isOnGround() && p.getY() - g > 0.3) continue;
                    if (p.getY() - g > 1.5) continue;
                    hitSet.add(p.getUuid());
                    p.damage(hit(w, d), damage);
                    Vec3d out = new Vec3d(p.getX() - c.x, 0, p.getZ() - c.z).normalize().multiply(0.7);
                    p.addVelocity(out.x, 0.8, out.z);
                    p.velocityModified = true;
                }
            }
            return r[0] < maxR;
        });
        sound(w, c, ModSounds.sfx("shockwave"), 8f, rage ? 0.8f : 1.0f);
    }

    // ================================================================ рёв

    /** Вдох (частицы летят в пасть) и рёв, сносящий всех вокруг. Щит держит удар. */
    public static int roar(ServerWorld w, EnderDragonEntity d, List<ServerPlayerEntity> players) {
        Vec3d head0 = headPos(d);
        Fx.fxNear(w, head0, 150, "dragon_inhale", 30, 0);
        for (ServerPlayerEntity p : players) {
            if (p.getPos().distanceTo(head0) < 70) {
                Net.alert(p, Net.Alert.DANGER, Items.DRAGON_HEAD, "ДРАКОН НАБИРАЕТ ВОЗДУХ", "Подними щит или отойди от края!");
            }
        }
        sound(w, head0, ModSounds.sfx("dragon_inhale"), 8f, 1f);
        Random rnd = w.random;
        for (int t = 0; t < 28; t += 2) {
            Tasks.schedule(w.getServer(), t, () -> {
                if (!d.isAlive()) return;
                Vec3d head = headPos(d);
                for (int i = 0; i < 16; i++) {
                    Vec3d off = new Vec3d(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize().multiply(6 + rnd.nextDouble() * 6);
                    // частица портала летит от head+off к head
                    Fx.flow(w, ParticleTypes.PORTAL, head, off);
                }
            });
        }
        Tasks.schedule(w.getServer(), 30, () -> {
            if (!d.isAlive()) return;
            Vec3d head = headPos(d);
            sound(w, head, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 14f, 0.42f);
            sound(w, head, ModSounds.sfx("boss_boom"), 10f, 0.9f);
            Fx.fxNear(w, head, 150, "dragon_roar", 40, 0);
            Fx.particle(w, ParticleTypes.EXPLOSION_EMITTER, head.x, head.y, head.z, 1, 0, 0, 0, 0);
            for (int k = 0; k < 4; k++) {
                int kk = k;
                Tasks.schedule(w.getServer(), k * 3, () -> Fx.ring(w, head, 4 + kk * 6, ParticleTypes.SONIC_BOOM, 10 + kk * 4, false));
            }
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (!hittable(p)) continue;
                double dist = p.getPos().distanceTo(head);
                if (dist > 42) continue;
                Vec3d toDragon = head.subtract(p.getEyePos()).normalize();
                boolean shield = p.isBlocking() && p.getRotationVec(1f).dotProduct(toDragon) > 0.2;
                double k = shield ? 0.12 : 1.7 - dist / 42 * 1.1;
                Vec3d push = new Vec3d(p.getX() - head.x, 0, p.getZ() - head.z).normalize().multiply(k);
                p.addVelocity(push.x, shield ? 0.1 : 0.55, push.z);
                p.velocityModified = true;
                Fx.shake(w, p.getPos(), 1, 35, 7);
                if (shield) {
                    Net.alert(p, Net.Alert.GOOD, Items.SHIELD, "ЩИТ ВЫДЕРЖАЛ РЁВ", "");
                } else {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 1));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 40, 0, false, false));
                }
            }
            Fx.shake(w, head, 120, 30, 4);
        });
        return 50;
    }

    // ================================================================ пикирование

    /** Красный круг на месте игрока, потом дракон таранит это место и пускает ударную волну. */
    public static int dive(ServerWorld w, EnderDragonEntity d, ServerPlayerEntity target, DragonRage.State st) {
        Vec3d aim = groundUnder(w, target.getPos());
        if (aim.y <= w.getBottomY()) return 0;
        Net.alert(target, Net.Alert.DANGER, Items.ELYTRA, "ПИКИРОВАНИЕ", "Уйди из красного круга и перепрыгни волну");
        sound(w, headPos(d), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 10f, 1.35f);
        sound(w, aim, ModSounds.sfx("meteor_fall"), 4f, 1.4f);
        st.diveAim = aim;
        st.diveTicks = -30;
        Tasks.schedule(w.getServer(), 30, () -> {
            if (!d.isAlive() || st.diveAim == null || d.getPhaseManager().getCurrent().getType() == PhaseType.DYING) return;
            d.getPhaseManager().setPhase(PhaseType.CHARGING_PLAYER);
            d.getPhaseManager().create(PhaseType.CHARGING_PLAYER).setPathTarget(aim.add(0, 3, 0));
            sound(w, headPos(d), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 12f, 0.7f);
        });
        return 160;
    }

    /** Вызывается из контроллера каждый тик, пока идёт пикирование. */
    static void diveTick(ServerWorld w, EnderDragonEntity d, DragonRage.State st) {
        Vec3d aim = st.diveAim;
        st.diveTicks++;
        if (st.diveTicks % 4 == 0) {
            Fx.ring(w, aim.add(0, 0.2, 0), 4.0, Fx.RED, 28, true);
            Fx.ring(w, aim.add(0, 0.2, 0), 1.8, Fx.RED, 12, true);
        }
        if (st.diveTicks <= 0) return;
        double dist = d.getPos().distanceTo(aim);
        boolean charging = d.getPhaseManager().getCurrent().getType() == PhaseType.CHARGING_PLAYER;
        if (charging) {
            // ванильный таран слишком ленивый: разгоняем дракона к цели, за ним тянется шлейф
            Vec3d to = aim.add(0, 3, 0).subtract(d.getPos());
            double len = to.length();
            if (len > 1) {
                Vec3d v = d.getVelocity().add(to.multiply(0.1 / len));
                double sp = v.length();
                if (sp > 1.6) v = v.multiply(1.6 / sp);
                d.setVelocity(v);
            }
            Vec3d tail = d.getPos();
            Fx.particle(w, st.phase >= 3 ? Fx.RED : Fx.PURPLE, tail.x, tail.y + 1, tail.z, 6, 2.5, 1.5, 2.5, 0);
            if (st.diveTicks % 2 == 0) Fx.particle(w, ParticleTypes.REVERSE_PORTAL, tail.x, tail.y + 1, tail.z, 10, 3, 2, 3, 0.1);
        }
        if (dist < 18) {
            diveImpact(w, d, aim, st.phase);
            st.diveAim = null;
            st.busy = Math.min(st.busy, 40);
        } else if (st.diveTicks > 140 || (!charging && st.diveTicks > 8)) {
            // дракона отвлекли — промах без волны
            st.diveAim = null;
            st.busy = Math.min(st.busy, 20);
        }
    }

    private static void diveImpact(ServerWorld w, EnderDragonEntity d, Vec3d aim, int phase) {
        sound(w, aim, ModSounds.sfx("boss_boom"), 12f, 0.8f);
        sound(w, aim, SoundEvents.ENTITY_GENERIC_EXPLODE, 6f, 0.6f);
        Fx.particle(w, ParticleTypes.EXPLOSION_EMITTER, aim.x, aim.y + 1, aim.z, 2, 1.5, 0.5, 1.5, 0);
        Fx.particle(w, DEBRIS, aim.x, aim.y + 0.5, aim.z, 160, 2.5, 0.5, 2.5, 0.6);
        Fx.particle(w, ParticleTypes.DRAGON_BREATH, aim.x, aim.y + 1, aim.z, 80, 3, 1, 3, 0.05);
        Fx.shake(w, aim, 80, 30, 8);
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (hittable(p) && p.getPos().distanceTo(aim) < 4.5) {
                p.damage(hit(w, d), 6f);
                p.addVelocity(0, 1.0, 0);
                p.velocityModified = true;
            }
        }
        shockwave(w, d, aim, phase >= 3 ? 26 : 20, 0.75, phase >= 3 ? 8f : 6f, phase >= 3);
    }

    // ================================================================ кристальные шипы

    /** Волна кристаллов вырывается из земли по линии к игроку. Трещины на земле — за полсекунды до удара. */
    public static int spikes(ServerWorld w, EnderDragonEntity d, ServerPlayerEntity target, int phase) {
        Random r = w.random;
        Vec3d tp = target.getPos();
        Vec3d fromDragon = new Vec3d(d.getX() - tp.x, 0, d.getZ() - tp.z);
        if (fromDragon.lengthSquared() < 1) fromDragon = new Vec3d(1, 0, 0);
        Vec3d start = tp.add(fromDragon.normalize().multiply(Math.min(22, fromDragon.length())));
        Vec3d dir = new Vec3d(tp.x - start.x, 0, tp.z - start.z).normalize();
        double len = start.distanceTo(new Vec3d(tp.x, start.y, tp.z)) + 10;
        int n = (int) Math.min(30, len / 1.5);
        Net.alert(target, Net.Alert.DANGER, Items.AMETHYST_CLUSTER, "ЗЕМЛЯ ТРЕСКАЕТСЯ", "Отойди в сторону от линии");
        sound(w, tp, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 4f, 0.5f);
        boolean dark = phase >= 3;
        for (int i = 0; i < n; i++) {
            double side = (r.nextDouble() - 0.5) * 1.0;
            Vec3d p0 = start.add(dir.multiply(i * 1.5)).add(-dir.z * side, 0, dir.x * side);
            int g = Fx.ground(w, p0.x, p0.z);
            if (g <= w.getBottomY()) continue;
            Vec3d base = new Vec3d(p0.x, g, p0.z);
            int delay = i * 2;
            Tasks.schedule(w.getServer(), delay, () -> {
                Fx.particle(w, DEBRIS, base.x, base.y + 0.1, base.z, 8, 0.4, 0.05, 0.4, 0.1);
                Fx.particle(w, dark ? Fx.RED : Fx.PURPLE, base.x, base.y + 0.15, base.z, 4, 0.4, 0.02, 0.4, 0);
            });
            Tasks.schedule(w.getServer(), delay + 12, () -> erupt(w, d, base, r, dark));
        }
        return n * 2 + 30;
    }

    private static void erupt(ServerWorld w, EnderDragonEntity d, Vec3d base, Random r, boolean dark) {
        Quaternionf rot = Fx.tilt(r, 14);
        float h = 3.2f + r.nextFloat() * 1.6f;
        BlockState core = dark ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.AMETHYST_BLOCK.getDefaultState();
        DisplayEntity spike = Fx.block(w, base, core, Fx.tf(0.7f, 0.01f, 0.7f, rot, -0.3f), true);
        DisplayEntity crystal = Fx.block(w, base, Blocks.AMETHYST_CLUSTER.getDefaultState(), Fx.tf(1.8f, 0.01f, 1.8f, rot, -0.3f), true);
        Tasks.schedule(w.getServer(), 1, () -> {
            Fx.animate(spike, Fx.tf(0.7f, h, 0.7f, rot, -0.3f), 3);
            Fx.animate(crystal, Fx.tf(1.8f, 2.4f, 1.8f, rot, -0.3f), 3);
        });
        sound(w, base, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 2.5f, 0.5f + r.nextFloat() * 0.3f);
        if (r.nextInt(3) == 0) sound(w, base, ModSounds.sfx("crystal_rise"), 2.5f, 0.8f + r.nextFloat() * 0.4f);
        Fx.particle(w, ParticleTypes.END_ROD, base.x, base.y + 1, base.z, 6, 0.3, 0.8, 0.3, 0.05);
        Fx.particle(w, DEBRIS, base.x, base.y + 0.3, base.z, 14, 0.4, 0.2, 0.4, 0.25);
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (!hittable(p)) continue;
            if (Math.hypot(p.getX() - base.x, p.getZ() - base.z) < 1.4 && p.getY() > base.y - 0.5 && p.getY() < base.y + h) {
                p.damage(hit(w, d), dark ? 8f : 6f);
                p.addVelocity(0, 1.1, 0);
                p.velocityModified = true;
            }
        }
        Tasks.schedule(w.getServer(), 34, () -> {
            Fx.animate(spike, Fx.tf(0.7f, 0.01f, 0.7f, rot, -0.3f), 8);
            Fx.animate(crystal, Fx.tf(1.8f, 0.01f, 1.8f, rot, -0.3f), 8);
        });
        Tasks.schedule(w.getServer(), 44, () -> {
            Fx.remove(spike);
            Fx.remove(crystal);
        });
    }

    // ================================================================ вихрь Пустоты

    /** Чёрная дыра тянет к себе 3,5 секунды и взрывается. Беги от неё и не прыгай — в воздухе тянет сильнее. */
    public static int vortex(ServerWorld w, EnderDragonEntity d, ServerPlayerEntity target, int phase) {
        Random r = w.random;
        // вихрь появляется в 3–5 блоках от игрока: его видно, и от него можно убежать
        double side = r.nextDouble() * Math.PI * 2, off = 3 + r.nextDouble() * 2;
        Vec3d g0 = groundUnder(w, target.getPos().add(Math.cos(side) * off, 0, Math.sin(side) * off));
        Vec3d g = g0.y <= w.getBottomY() ? target.getPos() : g0;
        Vec3d c = g.add(0, 2.2, 0);
        double radius = 14;
        int life = 70;
        Net.alert(target, Net.Alert.DANGER, Items.CRYING_OBSIDIAN, "ВИХРЬ ПУСТОТЫ", "Беги прочь и не прыгай");
        sound(w, c, ModSounds.sfx("vortex"), 6f, 1f);
        Quaternionf q0 = new Quaternionf();
        DisplayEntity core = Fx.block(w, c, Blocks.CRYING_OBSIDIAN.getDefaultState(), Fx.tf(0.01f, 0.01f, 0.01f, q0, -0.7f), true);
        DisplayEntity shell = Fx.block(w, c, Blocks.BLACK_STAINED_GLASS.getDefaultState(), Fx.tf(0.01f, 0.01f, 0.01f, q0, -1.2f), false);
        int[] t = {0};
        start(() -> {
            int now = t[0]++;
            if (now % 10 == 0 && now < life) {
                float grow = Math.min(1f, now / 20f + 0.5f);
                float a = now * 0.12f;
                Fx.animate(core, Fx.tf(1.4f * grow, 1.4f * grow, 1.4f * grow, new Quaternionf().rotateY(a + 1.2f).rotateX(a * 0.7f + 0.6f), -0.7f * grow), 10);
                Fx.animate(shell, Fx.tf(2.4f * grow, 2.4f * grow, 2.4f * grow, new Quaternionf().rotateY(-a - 1.2f).rotateZ(a * 0.5f + 0.4f), -1.2f * grow), 10);
            }
            if (now < life) {
                // три спиральных рукава: частицы затягивает в центр
                for (int arm = 0; arm < 3; arm++) {
                    for (int j = 0; j < 5; j++) {
                        double rr = 2.0 + j * 2.4;
                        double ang = now * 0.22 + arm * (Math.PI * 2 / 3) + rr * 0.35;
                        Vec3d p = c.add(Math.cos(ang) * rr, (r.nextDouble() - 0.5) * 1.5, Math.sin(ang) * rr);
                        Vec3d v = c.subtract(p).multiply(0.08).add(-Math.sin(ang) * 0.25, 0, Math.cos(ang) * 0.25);
                        Fx.flow(w, j % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.SQUID_INK, p, v.multiply(0.6));
                    }
                }
                if (now % 4 == 0) Fx.ring(w, g.add(0, 0.2, 0), radius, Fx.PURPLE, 40, true);
                for (ServerPlayerEntity p : w.getPlayers()) {
                    if (!hittable(p)) continue;
                    Vec3d to = c.subtract(p.getPos());
                    double dist = to.length();
                    if (dist > radius || dist < 0.5) continue;
                    double k = 0.045 + 0.04 * (1 - dist / radius);
                    Vec3d pull = new Vec3d(to.x, 0, to.z).normalize().multiply(k);
                    p.addVelocity(pull.x, p.isOnGround() ? 0 : 0.01, pull.z);
                    p.velocityModified = true;
                }
                return true;
            }
            if (now == life) {
                Fx.animate(core, Fx.tf(0.01f, 0.01f, 0.01f, q0, 0f), 4);
                Fx.animate(shell, Fx.tf(0.01f, 0.01f, 0.01f, q0, 0f), 4);
                sound(w, c, SoundEvents.BLOCK_BEACON_DEACTIVATE, 4f, 0.5f);
                return true;
            }
            if (now == life + 5) {
                Fx.remove(core);
                Fx.remove(shell);
                sound(w, c, ModSounds.sfx("boss_boom"), 9f, 1.1f);
                sound(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE, 5f, 0.7f);
                Fx.particle(w, ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
                Fx.particle(w, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                Fx.particle(w, ParticleTypes.END_ROD, c.x, c.y, c.z, 140, 0.3, 0.3, 0.3, 0.7);
                Fx.particle(w, ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 200, 0.5, 0.5, 0.5, 1.2);
                Fx.shake(w, c, 50, 25, 7);
                for (ServerPlayerEntity p : w.getPlayers()) {
                    if (!hittable(p)) continue;
                    double dist = p.getPos().distanceTo(c);
                    if (dist > 6) continue;
                    p.damage(hit(w, d), (float) (3 + 9 * (1 - dist / 6)) + (phase >= 3 ? 2 : 0));
                    Vec3d out = p.getPos().subtract(c).normalize().multiply(1.3);
                    p.addVelocity(out.x, 0.6, out.z);
                    p.velocityModified = true;
                }
                return false;
            }
            return true;
        });
        return life + 25;
    }

    // ================================================================ гигантский метеорит

    /** Огромная глыба магмы падает с неба. Большой красный круг — место удара. */
    public static int bigMeteor(ServerWorld w, EnderDragonEntity d, ServerPlayerEntity target) {
        Random r = w.random;
        Vec3d g = groundUnder(w, target.getPos().add((r.nextDouble() - 0.5) * 6, 0, (r.nextDouble() - 0.5) * 6));
        if (g.y <= w.getBottomY()) return 0;
        int fall = 50;
        float height = 48;
        double blast = 7.5;
        Net.alert(target, Net.Alert.DANGER, Items.MAGMA_BLOCK, "МЕТЕОРИТ", "Беги из большого красного круга");
        sound(w, g, ModSounds.sfx("meteor_fall"), 10f, 0.8f);
        Quaternionf[] rot = {Fx.tilt(r, 40)};
        DisplayEntity rock = Fx.block(w, g, Blocks.MAGMA_BLOCK.getDefaultState(), Fx.tf(5, 5, 5, rot[0], height), true);
        DisplayEntity crust = Fx.block(w, g, Blocks.CRYING_OBSIDIAN.getDefaultState(), Fx.tf(3.6f, 3.6f, 3.6f, rot[0], height + 0.7f), true);
        int[] t = {0};
        start(() -> {
            int now = t[0]++;
            if (now < fall) {
                if (now % 10 == 0) {
                    int to = Math.min(fall, now + 10);
                    float h = height * (1 - to / (float) fall);
                    rot[0] = new Quaternionf(rot[0]).rotateY(0.9f).rotateX(0.5f);
                    Fx.animate(rock, Fx.tf(5, 5, 5, rot[0], h), 10);
                    Fx.animate(crust, Fx.tf(3.6f, 3.6f, 3.6f, rot[0], h + 0.7f), 10);
                }
                double h = height * (1 - now / (double) fall) + 2.5;
                Fx.particle(w, ParticleTypes.FLAME, g.x, g.y + h, g.z, 10, 1.6, 1.6, 1.6, 0.05);
                Fx.particle(w, ParticleTypes.LARGE_SMOKE, g.x, g.y + h + 3, g.z, 6, 1.5, 2, 1.5, 0.02);
                if (now % 2 == 0) Fx.particle(w, ParticleTypes.LAVA, g.x, g.y + h, g.z, 3, 1.5, 1.5, 1.5, 0);
                if (now % 3 == 0) {
                    Fx.ring(w, g.add(0, 0.2, 0), blast, Fx.RED, 44, true);
                    Fx.ring(w, g.add(0, 0.2, 0), blast * (now / (double) fall), Fx.RED, 24, true);
                }
                return true;
            }
            if (now == fall) {
                sound(w, g, ModSounds.sfx("boss_boom"), 16f, 0.65f);
                sound(w, g, SoundEvents.ENTITY_GENERIC_EXPLODE, 8f, 0.5f);
                Fx.particle(w, ParticleTypes.EXPLOSION_EMITTER, g.x, g.y + 1.5, g.z, 4, 2.5, 1, 2.5, 0);
                Fx.particle(w, ParticleTypes.FLASH, g.x, g.y + 2, g.z, 3, 1, 1, 1, 0);
                Fx.particle(w, ParticleTypes.LAVA, g.x, g.y + 1, g.z, 70, 2, 1, 2, 0);
                Fx.particle(w, ParticleTypes.FLAME, g.x, g.y + 1, g.z, 200, 1, 0.5, 1, 0.5);
                Fx.particle(w, DEBRIS, g.x, g.y + 1, g.z, 220, 3, 1, 3, 0.8);
                Fx.shake(w, g, 110, 40, 10);
                Fx.fxNear(w, g, 24, "flash", 12, 0);
                for (ServerPlayerEntity p : w.getPlayers()) {
                    if (!hittable(p)) continue;
                    double dist = p.getPos().distanceTo(g);
                    if (dist > blast) continue;
                    p.damage(w.getDamageSources().explosion(d, d), (float) (2 + 16 * (1 - dist / blast)));
                    p.setOnFireFor(4);
                    Vec3d out = new Vec3d(p.getX() - g.x, 0, p.getZ() - g.z).normalize().multiply(1.1);
                    p.addVelocity(out.x, 0.9, out.z);
                    p.velocityModified = true;
                }
                shockwave(w, d, g, 18, 0.8, 5f, true);
                Fx.animate(rock, Fx.tf(5.5f, 1.2f, 5.5f, rot[0], -0.6f), 3);
                Fx.animate(crust, Fx.tf(3.8f, 1.6f, 3.8f, rot[0], -0.2f), 3);
                return true;
            }
            if (now < fall + 60) {
                if (now % 3 == 0) Fx.particle(w, ParticleTypes.LARGE_SMOKE, g.x, g.y + 1, g.z, 4, 1.8, 0.5, 1.8, 0.03);
                if (now == fall + 40) {
                    Fx.animate(rock, Fx.tf(0.01f, 0.01f, 0.01f, rot[0], -0.6f), 20);
                    Fx.animate(crust, Fx.tf(0.01f, 0.01f, 0.01f, rot[0], -0.2f), 20);
                }
                return true;
            }
            Fx.remove(rock);
            Fx.remove(crust);
            return false;
        });
        return fall + 30;
    }

    // ================================================================ старые атаки

    /** Метеоритный дождь из драконьего пламени с красными метками на земле. */
    public static int meteors(ServerWorld w, EnderDragonEntity d, ServerPlayerEntity p, int count) {
        Random r = w.random;
        for (int i = 0; i < count; i++) {
            int delay = i * 6;
            double x = p.getX() + (r.nextDouble() - 0.5) * 16;
            double z = p.getZ() + (r.nextDouble() - 0.5) * 16;
            int ground = Fx.ground(w, x, z);
            if (ground <= w.getBottomY()) continue;
            WorldEvents.marker(w, new BlockPos((int) Math.floor(x), ground, (int) Math.floor(z)), 50 + delay);
            Tasks.schedule(w.getServer(), delay, () -> {
                DragonFireballEntity fb = new DragonFireballEntity(w, d, (r.nextDouble() - 0.5) * 0.1, -1.0, (r.nextDouble() - 0.5) * 0.1);
                fb.refreshPositionAndAngles(x, ground + 32, z, 0, 0);
                w.spawnEntity(fb);
            });
        }
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_SHOOT, SoundCategory.HOSTILE, 2f, 0.6f);
        Net.alert(p, Net.Alert.BOSS, Items.FIRE_CHARGE, "МЕТЕОРИТНЫЙ ДОЖДЬ", "Следи за красными метками");
        return count * 6 + 20;
    }

    public static int swarm(ServerWorld w, ServerPlayerEntity p, int count) {
        for (int i = 0; i < count; i++) {
            BlockPos pos = Spots.ground(w, p.getBlockPos(), 3, 7);
            if (pos == null) continue;
            EndermiteEntity m = EntityType.ENDERMITE.create(w);
            if (m == null) continue;
            Spots.place(w, m, pos, w.random.nextFloat() * 360);
            m.setTarget(p);
            w.spawnParticles(ParticleTypes.PORTAL, m.getX(), m.getY() + 0.3, m.getZ(), 20, 0.3, 0.3, 0.3, 0.3);
        }
        return 20;
    }

    /** Гусиный десант: гуси на парашютах, которые воруют вещи прямо во время боя. */
    public static int geese(ServerWorld w, ServerPlayerEntity p, int count) {
        for (int i = 0; i < count; i++) {
            GooseEntity g = ModEntities.GOOSE.create(w);
            if (g == null) continue;
            double x = p.getX() + (w.random.nextDouble() - 0.5) * 10;
            double z = p.getZ() + (w.random.nextDouble() - 0.5) * 10;
            g.refreshPositionAndAngles(x, p.getY() + 18 + w.random.nextInt(6), z, w.random.nextFloat() * 360, 0);
            g.setParachute(true);
            g.stealCooldown = 0;
            w.spawnEntity(g);
        }
        w.playSound(null, p.getBlockPos(), ModSounds.sfx("goose_honk"), SoundCategory.HOSTILE, 2f, 0.8f);
        Net.alert(p, Net.Alert.FUNNY, ModItems.GOOSE_FEATHER, "ГУСИНЫЙ ДЕСАНТ", "Держи вещи крепче");
        return 30;
    }

    /** Коровья бомбардировка: коровы без парашютов прямо над головой. */
    public static int cows(ServerWorld w, ServerPlayerEntity p, int count) {
        for (int i = 0; i < count; i++) {
            ParachuteCowEntity cow = ModEntities.PARACHUTE_COW.create(w);
            if (cow == null) continue;
            double x = p.getX() + (w.random.nextDouble() - 0.5) * 7;
            double z = p.getZ() + (w.random.nextDouble() - 0.5) * 7;
            int ground = Fx.ground(w, x, z);
            if (ground <= w.getBottomY()) continue;
            cow.refreshPositionAndAngles(x, Math.max(p.getY(), ground) + 16, z, w.random.nextFloat() * 360, 0);
            cow.setup(false);
            w.spawnEntity(cow);
            WorldEvents.marker(w, new BlockPos((int) Math.floor(x), ground, (int) Math.floor(z)), 40);
        }
        Net.alert(p, Net.Alert.FUNNY, Items.LEATHER, "КОРОВЬЯ БОМБАРДИРОВКА", "Красные метки — туда падают коровы");
        return 40;
    }

    /** Лазер Пустоты: фиолетовая полоса на земле, через полторы секунды по ней бьёт стена света. */
    public static int laser(ServerWorld w, EnderDragonEntity d, ServerPlayerEntity target) {
        Random r = w.random;
        double ang = r.nextDouble() * Math.PI * 2;
        Vec3d dir = new Vec3d(Math.cos(ang), 0, Math.sin(ang));
        Vec3d center = target.getPos();
        int warn = 30;
        for (int t = 0; t < warn; t += 3) {
            Tasks.schedule(w.getServer(), t, () -> {
                for (int i = -16; i <= 16; i++) {
                    Vec3d q = center.add(dir.multiply(i));
                    Fx.particle(w, ParticleTypes.WITCH, q.x, q.y + 0.2, q.z, 2, 0.15, 0.05, 0.15, 0);
                }
            });
        }
        w.playSound(null, target.getBlockPos(), ModSounds.sfx("laser_charge"), SoundCategory.HOSTILE, 2.5f, 0.7f);
        Net.alert(target, Net.Alert.DANGER, Items.END_ROD, "ЛАЗЕР ПУСТОТЫ", "Уйди с фиолетовой линии");
        Tasks.schedule(w.getServer(), warn, () -> {
            for (int i = -16; i <= 16; i++) {
                Vec3d q = center.add(dir.multiply(i));
                Fx.particle(w, ParticleTypes.DRAGON_BREATH, q.x, q.y + 0.5, q.z, 6, 0.2, 0.4, 0.2, 0.02);
                for (int k = 0; k < 6; k++) Fx.particle(w, ParticleTypes.END_ROD, q.x, q.y + k * 1.5, q.z, 1, 0.08, 0.6, 0.08, 0.01);
                if (i % 4 == 0) Fx.particle(w, ParticleTypes.FLASH, q.x, q.y + 1, q.z, 1, 0, 0, 0, 0);
            }
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (!hittable(p)) continue;
                Vec3d rel = p.getPos().subtract(center);
                double along = rel.dotProduct(dir);
                double side = rel.subtract(dir.multiply(along)).horizontalLength();
                if (Math.abs(along) <= 16.5 && side < 1.7 && Math.abs(rel.y) < 4) {
                    p.damage(w.getDamageSources().magic(), 9f);
                    p.setOnFireFor(3);
                }
            }
            w.playSound(null, target.getBlockPos(), ModSounds.sfx("boss_boom"), SoundCategory.HOSTILE, 4f, 1.5f);
            w.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5f, 1.4f);
            Fx.shake(w, center, 40, 15, 4);
        });
        return warn + 20;
    }

    public static int gravity(List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity p : players) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 40, 1, false, true));
            Net.alert(p, Net.Alert.BOSS, Items.PHANTOM_MEMBRANE, "ГРАВИТАЦИЯ СЛОМАЛАСЬ", "Сейчас подбросит. Не стой у края");
        }
        return 30;
    }

    static double clampDist(double v) {
        return MathHelper.clamp(v, 0, 200);
    }

    private DragonAttacks() {
    }
}
