package com.hardest.entity;

import com.hardest.registry.ModSounds;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Корова, падающая с неба. Повезёт — с парашютом. Не повезёт — сразу стейк.
 */
public class ParachuteCowEntity extends CowEntity {
    private static final TrackedData<Boolean> PARACHUTE = DataTracker.registerData(ParachuteCowEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> FALLING = DataTracker.registerData(ParachuteCowEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public ParachuteCowEntity(EntityType<? extends CowEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(PARACHUTE, false);
        this.dataTracker.startTracking(FALLING, false);
    }

    public boolean hasParachute() {
        return this.dataTracker.get(PARACHUTE);
    }

    public boolean isSkydiving() {
        return this.dataTracker.get(FALLING);
    }

    public void setup(boolean parachute) {
        this.dataTracker.set(PARACHUTE, parachute);
        this.dataTracker.set(FALLING, true);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (!isSkydiving()) return;
        if (hasParachute()) {
            Vec3d v = getVelocity();
            if (v.y < -0.12) setVelocity(v.x * 0.9, -0.12, v.z * 0.9);
            this.fallDistance = 0;
            // покачивание
            double sway = Math.sin(this.age * 0.15) * 0.03;
            addVelocity(sway, 0, Math.cos(this.age * 0.13) * 0.03);
            if (this.age % 40 == 0) playSound(SoundEvents.ENTITY_COW_AMBIENT, 1.0f, 1.3f);
        } else if (this.age % 10 == 0) {
            playSound(SoundEvents.ENTITY_COW_HURT, 1.5f, 1.6f);
        }
        if (isOnGround() || isTouchingWater()) {
            if (hasParachute()) {
                playSound(ModSounds.sfx("parachute"), 1.0f, 0.8f);
                ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 2, getZ(), 12, 0.8, 0.3, 0.8, 0.02);
            }
            this.dataTracker.set(PARACHUTE, false);
            this.dataTracker.set(FALLING, false);
        }
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        if (fallDistance > 6 && getWorld() instanceof ServerWorld sw) {
            // ШЛЕП. Прожарилась при входе в атмосферу.
            sw.playSound(null, getBlockPos(), ModSounds.sfx("splat"), SoundCategory.NEUTRAL, 1.6f, 1.0f);
            sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()),
                    getX(), getY() + 0.3, getZ(), 50, 0.6, 0.2, 0.6, 0.2);
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 10, 0.5, 0.3, 0.5, 0.02);
            dropStack(new ItemStack(Items.COOKED_BEEF, 2 + random.nextInt(2)));
            // корова, упавшая на голову, — это больно
            for (net.minecraft.server.network.ServerPlayerEntity p : sw.getPlayers()) {
                if (p.squaredDistanceTo(this) < 2.2 * 2.2 && !p.isSpectator()) {
                    p.damage(getDamageSources().fallingBlock(this), 6.0f);
                    p.sendMessage(net.minecraft.text.Text.literal("На тебя упала корова.").formatted(net.minecraft.util.Formatting.GOLD), true);
                }
            }
            dropStack(new ItemStack(Items.LEATHER, 1));
            discard();
            return true;
        }
        return super.handleFallDamage(fallDistance, damageMultiplier, damageSource);
    }

    @Override
    public CowEntity createChild(ServerWorld world, PassiveEntity entity) {
        return EntityType.COW.create(world);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Parachute", hasParachute());
        nbt.putBoolean("Skydiving", isSkydiving());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(PARACHUTE, nbt.getBoolean("Parachute"));
        this.dataTracker.set(FALLING, nbt.getBoolean("Skydiving"));
    }
}
