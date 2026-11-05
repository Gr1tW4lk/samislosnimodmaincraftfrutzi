package com.hardest.entity;

import com.hardest.net.Net;
import com.hardest.registry.ModBlocks;
import com.hardest.registry.ModEntities;
import com.hardest.registry.ModSounds;
import com.hardest.util.Achievements;
import com.hardest.util.Spots;
import com.hardest.util.Voice;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Крипер-курьер. Приносит посылку, просит расписаться, уходит... и взрывается. Доставка завершена.
 */
public class CourierCreeperEntity extends PathAwareEntity {
    public static final int DELIVER = 0, SIGN = 1, LEAVE = 2, FUSE = 3;
    private static final TrackedData<Integer> MODE = DataTracker.registerData(CourierCreeperEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> FUSE_TICKS = DataTracker.registerData(CourierCreeperEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final int MAX_FUSE = 30;

    @Nullable
    private UUID targetId;
    private int modeTicks;
    public int lastFuse;

    public CourierCreeperEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(MODE, DELIVER);
        this.dataTracker.startTracking(FUSE_TICKS, 0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
    }

    public int getMode() {
        return dataTracker.get(MODE);
    }

    public boolean hasParcel() {
        return getMode() == DELIVER;
    }

    public float getFuse(float tickDelta) {
        int f = dataTracker.get(FUSE_TICKS);
        return Math.min(1f, (lastFuse + (f - lastFuse) * tickDelta) / (float) MAX_FUSE);
    }

    private void setMode(int m) {
        dataTracker.set(MODE, m);
        modeTicks = 0;
    }

    @Nullable
    private ServerPlayerEntity target() {
        if (targetId == null || !(getWorld() instanceof ServerWorld sw)) return null;
        return (ServerPlayerEntity) sw.getPlayerByUuid(targetId);
    }

    @Override
    public void tick() {
        lastFuse = dataTracker.get(FUSE_TICKS);
        super.tick();
        if (getWorld().isClient) return;
        modeTicks++;
        ServerPlayerEntity p = target();
        switch (getMode()) {
            case DELIVER -> {
                if (p == null) {
                    setMode(LEAVE);
                    return;
                }
                getLookControl().lookAt(p, 30f, 30f);
                if (modeTicks % 10 == 1) getNavigation().startMovingTo(p, 1.15);
                if (modeTicks % 30 == 0) playSound(ModSounds.sfx("courier_bell"), 0.8f, 1.0f);
                if (squaredDistanceTo(p) < 3.2 * 3.2) {
                    getNavigation().stop();
                    setMode(SIGN);
                    Voice.speak(this, "courier", "courier_hi");
                    dropParcel(p);
                }
                if (modeTicks > 20 * 50) {
                    Vec3d dir = p.getRotationVector().multiply(3.0);
                    requestTeleport(p.getX() + dir.x, p.getY(), p.getZ() + dir.z);
                    modeTicks = 0;
                }
            }
            case SIGN -> {
                if (p != null) getLookControl().lookAt(p, 30f, 30f);
                if (modeTicks > Voice.ticks("courier_hi") + 10) setMode(LEAVE);
            }
            case LEAVE -> {
                if (modeTicks % 30 == 1) {
                    Vec3d away = p != null ? NoPenaltyTargeting.findFrom(this, 12, 5, p.getPos()) : NoPenaltyTargeting.find(this, 12, 5);
                    if (away != null) getNavigation().startMovingTo(away.x, away.y, away.z, 1.1);
                }
                if (modeTicks > 70) {
                    setMode(FUSE);
                    getNavigation().stop();
                    Voice.speak(this, "courier", "courier_bye");
                    playSound(SoundEvents.ENTITY_CREEPER_PRIMED, 1.0f, 0.8f);
                }
            }
            case FUSE -> {
                if (p != null) getLookControl().lookAt(p, 30f, 30f);
                int fuse = dataTracker.get(FUSE_TICKS) + 1;
                dataTracker.set(FUSE_TICKS, fuse);
                if (fuse >= MAX_FUSE + Voice.ticks("courier_bye") / 2) explode();
            }
        }
    }

    private void dropParcel(ServerPlayerEntity p) {
        ServerWorld sw = (ServerWorld) getWorld();
        BlockPos between = BlockPos.ofFloored(getPos().add(p.getPos()).multiply(0.5));
        BlockPos spot = Spots.column(sw, between.getX(), between.getY(), between.getZ(), 3);
        if (spot == null) spot = getBlockPos();
        if (sw.getBlockState(spot).isReplaceable()) {
            sw.setBlockState(spot, ModBlocks.GIFT_BOX.getDefaultState());
        } else {
            dropStack(new net.minecraft.item.ItemStack(ModBlocks.GIFT_BOX));
        }
        sw.playSound(null, spot, ModSounds.sfx("courier_bell"), SoundCategory.NEUTRAL, 1.4f, 1.0f);
        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, spot.getX() + 0.5, spot.getY() + 0.8, spot.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0);
        Achievements.grant(p, "courier");
        Net.alert(p, Net.Alert.INFO, ModBlocks.GIFT_BOX, "ВАМ ПОСЫЛКА", "Нажмите ПКМ, чтобы открыть");
    }

    private void explode() {
        if (getWorld() instanceof ServerWorld sw) {
            sw.createExplosion(this, getX(), getY(), getZ(), 2.5f, World.ExplosionSourceType.NONE);
        }
        discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // ударил курьера — получай досрочную доставку
        if (!getWorld().isClient && getMode() != FUSE && source.getAttacker() != null) {
            if (hasParcel() && source.getAttacker() instanceof ServerPlayerEntity sp) dropParcel(sp);
            setMode(FUSE);
            dataTracker.set(FUSE_TICKS, MAX_FUSE / 2);
            playSound(SoundEvents.ENTITY_CREEPER_PRIMED, 1.0f, 1.2f);
        }
        return super.damage(source, amount);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_CREEPER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_CREEPER_DEATH;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Mode", getMode());
        if (targetId != null) nbt.putUuid("Target", targetId);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        dataTracker.set(MODE, nbt.getInt("Mode"));
        if (nbt.containsUuid("Target")) targetId = nbt.getUuid("Target");
    }

    public static void spawnFor(ServerPlayerEntity p) {
        ServerWorld sw = p.getServerWorld();
        BlockPos pos = Spots.ground(sw, p.getBlockPos(), 14, 20);
        if (pos == null) pos = p.getBlockPos().add(5, 0, 5);
        CourierCreeperEntity c = ModEntities.COURIER.create(sw);
        if (c == null) return;
        c.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Spots.yawTowards(pos.getX(), pos.getZ(), p.getX(), p.getZ()), 0);
        c.targetId = p.getUuid();
        c.setCustomName(Text.literal("Курьер «Быстро и Взрывно»").formatted(Formatting.GREEN));
        sw.spawnEntity(c);
    }
}
