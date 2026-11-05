package com.hardest.mobs;

import com.hardest.entity.SniperSkeletonEntity;
import com.hardest.entity.WebShotEntity;
import com.hardest.mixin.MobEntityAccessor;
import com.hardest.registry.ModEntities;
import com.hardest.registry.ModSounds;
import com.hardest.util.Tasks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Обычные мобы, но злее: больше здоровья и урона, плюс особые разновидности.
 */
public final class MobTweaks {
    public static final String TAG_ROLLED = "hard_rolled";
    public static final String TAG_BLOOD = "hard_blood";
    public static final String TAG_LEAPER = "hard_leaper";
    public static final String TAG_WEBBER = "hard_webber";
    private static final String TAG_EXTRA = "hard_extra";
    private static final UUID HP_ID = UUID.fromString("6a3c1b2e-4f5d-4e8a-9b1c-2d3e4f5a6b7c");
    private static final UUID DMG_ID = UUID.fromString("7b4d2c3f-5a6e-4f9b-8c2d-3e4f5a6b7c8d");
    private static final Set<MobEntity> GOALED = Collections.newSetFromMap(new WeakHashMap<>());

    public static void onLoad(Entity e, ServerWorld w) {
        if (e instanceof FireballEntity fb && fb.getOwner() instanceof GhastEntity ghast && !fb.getCommandTags().contains(TAG_EXTRA)) {
            fb.addCommandTag(TAG_EXTRA);
            if (w.random.nextFloat() < 0.35f) Tasks.schedule(w.getServer(), 1, () -> ghastVolley(w, ghast, fb));
            return;
        }
        if (!(e instanceof MobEntity mob)) return;
        if (!mob.getCommandTags().contains(TAG_ROLLED)) {
            mob.addCommandTag(TAG_ROLLED);
            roll(mob, w);
        }
        if (GOALED.add(mob)) attachGoals(mob);
    }

    private static void roll(MobEntity mob, ServerWorld w) {
        boolean blood = mob.getCommandTags().contains(TAG_BLOOD);
        float r = w.random.nextFloat();
        if (mob instanceof HostileEntity && !(mob instanceof WitherEntity) && !(mob instanceof ElderGuardianEntity)) {
            buff(mob, blood ? 0.5 : 0.25, blood ? 0.4 : 0.2);
        }
        Class<?> c = mob.getClass();
        if ((c == ZombieEntity.class || c == HuskEntity.class) && !mob.isBaby() && r < (blood ? 0.5f : 0.2f)) {
            ZombieBuilders.makeBuilder((ZombieEntity) mob);
        } else if (c == SkeletonEntity.class && r < (blood ? 0.3f : 0.1f)) {
            Tasks.schedule(w.getServer(), 1, () -> toSniper(w, (SkeletonEntity) mob));
        } else if (mob instanceof CreeperEntity creeper) {
            if (r < (blood ? 0.4f : 0.15f)) creeper.addCommandTag(TAG_LEAPER);
            if (blood && w.random.nextFloat() < 0.1f) {
                NbtCompound n = new NbtCompound();
                creeper.writeCustomDataToNbt(n);
                n.putBoolean("powered", true);
                creeper.readCustomDataFromNbt(n);
            }
        } else if (mob instanceof SpiderEntity && r < 0.5f) {
            mob.addCommandTag(TAG_WEBBER);
        }
    }

    private static void buff(MobEntity mob, double hp, double dmg) {
        EntityAttributeInstance h = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (h != null && h.getModifier(HP_ID) == null) {
            h.addPersistentModifier(new EntityAttributeModifier(HP_ID, "hardest", hp, EntityAttributeModifier.Operation.MULTIPLY_BASE));
            mob.setHealth(mob.getMaxHealth());
        }
        EntityAttributeInstance d = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (d != null && d.getModifier(DMG_ID) == null) {
            d.addPersistentModifier(new EntityAttributeModifier(DMG_ID, "hardest", dmg, EntityAttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private static void attachGoals(MobEntity mob) {
        var goals = ((MobEntityAccessor) mob).hardest$getGoalSelector();
        if (mob instanceof ZombieEntity z && mob.getCommandTags().contains(ZombieBuilders.TAG)) ZombieBuilders.addGoal(z);
        if (mob instanceof CreeperEntity cr && mob.getCommandTags().contains(TAG_LEAPER)) goals.add(1, new LeapGoal(cr));
        if (mob instanceof SpiderEntity sp && mob.getCommandTags().contains(TAG_WEBBER)) goals.add(2, new WebShotGoal(sp));
    }

    private static void toSniper(ServerWorld w, SkeletonEntity sk) {
        if (sk.isRemoved() || !sk.isAlive()) return;
        SniperSkeletonEntity s = ModEntities.SNIPER.create(w);
        if (s == null) return;
        s.refreshPositionAndAngles(sk.getX(), sk.getY(), sk.getZ(), sk.getYaw(), sk.getPitch());
        s.initialize(w, w.getLocalDifficulty(sk.getBlockPos()), net.minecraft.entity.SpawnReason.CONVERSION, null, null);
        SniperSkeletonEntity.equipSniper(s);
        for (String t : sk.getCommandTags()) s.addCommandTag(t);
        if (sk.getTarget() != null) s.setTarget(sk.getTarget());
        sk.discard();
        w.spawnEntity(s);
    }

    private static void ghastVolley(ServerWorld w, GhastEntity ghast, FireballEntity fb) {
        if (fb.isRemoved() || ghast.isRemoved()) return;
        Vec3d dir = new Vec3d(fb.powerX, fb.powerY, fb.powerZ);
        for (int s = -1; s <= 1; s += 2) {
            Vec3d d = dir.rotateY(s * 0.22f);
            FireballEntity extra = new FireballEntity(w, ghast, d.x, d.y, d.z, ghast.getFireballStrength());
            extra.setPosition(fb.getX(), fb.getY(), fb.getZ());
            extra.addCommandTag(TAG_EXTRA);
            w.spawnEntity(extra);
        }
    }

    /** Крипер-прыгун: с нескольких блоков прыгает прямо в лицо. */
    static class LeapGoal extends Goal {
        private final CreeperEntity creeper;
        private int cooldown = 40;

        LeapGoal(CreeperEntity creeper) {
            this.creeper = creeper;
            setControls(EnumSet.of(Control.MOVE, Control.JUMP));
        }

        @Override
        public boolean canStart() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity t = creeper.getTarget();
            if (t == null || !creeper.isOnGround()) return false;
            double d = creeper.squaredDistanceTo(t);
            return d > 9 && d < 81 && creeper.getVisibilityCache().canSee(t);
        }

        @Override
        public void start() {
            LivingEntity t = creeper.getTarget();
            if (t == null) return;
            Vec3d to = t.getPos().subtract(creeper.getPos());
            Vec3d h = new Vec3d(to.x, 0, to.z).normalize().multiply(Math.min(1.1, 0.25 + to.horizontalLength() * 0.11));
            creeper.setVelocity(h.x, 0.6, h.z);
            creeper.velocityModified = true;
            creeper.getWorld().playSound(null, creeper.getBlockPos(), SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1.2f, 1.4f);
            if (creeper.getRandom().nextBoolean()) creeper.ignite();
            cooldown = 80;
        }

        @Override
        public boolean shouldContinue() {
            return false;
        }
    }

    /** Паук плюётся паутиной. */
    static class WebShotGoal extends Goal {
        private final SpiderEntity spider;
        private int cooldown = 60;

        WebShotGoal(SpiderEntity spider) {
            this.spider = spider;
            setControls(EnumSet.noneOf(Control.class));
        }

        @Override
        public boolean canStart() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity t = spider.getTarget();
            if (t == null) return false;
            double d = spider.squaredDistanceTo(t);
            return d > 16 && d < 196 && spider.getVisibilityCache().canSee(t);
        }

        @Override
        public void start() {
            LivingEntity t = spider.getTarget();
            if (t == null) return;
            WebShotEntity shot = new WebShotEntity(spider.getWorld(), spider);
            Vec3d to = t.getEyePos().subtract(spider.getEyePos());
            shot.setVelocity(to.x, to.y + to.horizontalLength() * 0.12, to.z, 1.3f, 2.0f);
            spider.getWorld().spawnEntity(shot);
            spider.getWorld().playSound(null, spider.getBlockPos(), ModSounds.sfx("web_shot"), SoundCategory.HOSTILE, 1.2f, 1.0f);
            cooldown = 100 + spider.getRandom().nextInt(60);
        }

        @Override
        public boolean shouldContinue() {
            return false;
        }
    }

    private MobTweaks() {
    }
}
