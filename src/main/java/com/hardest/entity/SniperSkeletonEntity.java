package com.hardest.entity;

import com.hardest.registry.ModEntities;
import com.hardest.registry.ModSounds;
import com.hardest.util.Spots;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Скелет со «снайперкой»: сначала светит на тебя красным лазером, потом стреляет очень больно и очень точно.
 */
public class SniperSkeletonEntity extends SkeletonEntity {
    public static final int AIM_TIME = 45;
    private static final TrackedData<Integer> AIM = DataTracker.registerData(SniperSkeletonEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> AIM_TARGET = DataTracker.registerData(SniperSkeletonEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public SniperSkeletonEntity(EntityType<? extends SkeletonEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 12;
    }

    public static DefaultAttributeContainer.Builder createSniperAttributes() {
        return AbstractSkeletonEntity.createAbstractSkeletonAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(AIM, 0);
        this.dataTracker.startTracking(AIM_TARGET, -1);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.add(1, new SniperGoal(this));
    }

    @Override
    public void updateAttackType() {
        // своё поведение вместо обычного лука
    }

    @Override
    protected boolean isAffectedByDaylight() {
        return false;
    }

    public int getAim() {
        return this.dataTracker.get(AIM);
    }

    public int getAimTargetId() {
        return this.dataTracker.get(AIM_TARGET);
    }

    @Override
    protected boolean isDisallowedInPeaceful() {
        return false;
    }

    void shoot(LivingEntity target) {
        ItemStack arrow = new ItemStack(Items.ARROW);
        PersistentProjectileEntity p = ProjectileUtil.createArrowProjectile(this, arrow, 1.0f);
        double dx = target.getX() - getX();
        double dy = target.getBodyY(0.5) - p.getY();
        double dz = target.getZ() - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        p.setVelocity(dx, dy + d * 0.04, dz, 3.4f, 0.0f);
        p.setDamage(2.2);
        p.setCritical(true);
        getWorld().spawnEntity(p);
        getWorld().playSound(null, getBlockPos(), ModSounds.sfx("sniper_shot"), SoundCategory.HOSTILE, 3.0f, 1.0f);
    }

    static class SniperGoal extends Goal {
        private final SniperSkeletonEntity mob;
        private int cooldown;
        private int seen;

        SniperGoal(SniperSkeletonEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void stop() {
            mob.dataTracker.set(AIM, 0);
            mob.dataTracker.set(AIM_TARGET, -1);
            mob.setAttacking(false);
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            double dist = mob.squaredDistanceTo(t);
            boolean visible = mob.getVisibilityCache().canSee(t);
            seen = visible ? seen + 1 : 0;
            mob.getLookControl().lookAt(t, 30f, 30f);
            if (dist > 30 * 30 || !visible) {
                mob.getNavigation().startMovingTo(t, 1.0);
            } else {
                mob.getNavigation().stop();
            }
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            int aim = mob.getAim();
            if (visible && dist < 44 * 44) {
                if (aim == 0) mob.playSound(ModSounds.sfx("laser_charge"), 1.2f, 1.0f);
                aim++;
                mob.dataTracker.set(AIM_TARGET, t.getId());
                mob.setAttacking(true);
                if (aim >= AIM_TIME) {
                    mob.shoot(t);
                    aim = 0;
                    cooldown = 50;
                    mob.dataTracker.set(AIM_TARGET, -1);
                }
            } else {
                aim = Math.max(0, aim - 2);
                if (aim == 0) mob.dataTracker.set(AIM_TARGET, -1);
            }
            mob.dataTracker.set(AIM, aim);
        }
    }

    /** Каска цвета хаки, лук и подпись «Снайпер». */
    public static void equipSniper(SniperSkeletonEntity s) {
        ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
        ((DyeableItem) Items.LEATHER_HELMET).setColor(helmet, 0x2b3a1e);
        s.equipStack(EquipmentSlot.HEAD, helmet);
        s.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        s.setEquipmentDropChance(EquipmentSlot.HEAD, 0f);
        s.setCustomName(Text.literal("Снайпер").formatted(Formatting.DARK_RED));
    }

    public static void spawnFor(ServerPlayerEntity p, int count) {
        ServerWorld sw = p.getServerWorld();
        for (int i = 0; i < count; i++) {
            BlockPos pos = Spots.ground(sw, p.getBlockPos(), 18, 26);
            if (pos == null) continue;
            SniperSkeletonEntity s = ModEntities.SNIPER.create(sw);
            if (s == null) continue;
            Spots.place(sw, s, pos, Spots.yawTowards(pos.getX(), pos.getZ(), p.getX(), p.getZ()));
            equipSniper(s);
            s.setTarget(p);
        }
    }
}
