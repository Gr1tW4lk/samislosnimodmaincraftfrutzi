package com.hardest.entity;

import com.hardest.util.Voice;
import com.hardest.registry.ModEntities;
import com.hardest.registry.ModItems;
import com.hardest.registry.ModSounds;
import com.hardest.util.Spots;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

/**
 * Двойник: твой скин, твоя броня, твоё оружие — и красные глаза. Приходит в Кровавую луну.
 */
public class DoppelgangerEntity extends HostileEntity {
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(DoppelgangerEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final DustParticleEffect CYAN = new DustParticleEffect(new Vector3f(0.6f, 0f, 0f), 0.8f);

    public DoppelgangerEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 15;
        for (EquipmentSlot slot : EquipmentSlot.values()) this.setEquipmentDropChance(slot, 0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(OWNER, Optional.empty());
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.25, false));
        this.goalSelector.add(6, new WanderAroundFarGoal(this, 1.0));
        this.goalSelector.add(7, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false,
                e -> getOwnerId().map(id -> id.equals(e.getUuid())).orElse(true)));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    public Optional<UUID> getOwnerId() {
        return this.dataTracker.get(OWNER);
    }

    public void copyFrom(PlayerEntity p) {
        this.dataTracker.set(OWNER, Optional.of(p.getUuid()));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack s = p.getEquippedStack(slot);
            this.equipStack(slot, s.isEmpty() ? ItemStack.EMPTY : s.copy());
        }
        if (getMainHandStack().isEmpty()) equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        this.setCustomName(Text.literal(p.getName().getString() + "?").formatted(Formatting.DARK_RED));
        this.setCustomNameVisible(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        // повторяет за оригиналом: ты присел — и он присел
        if (getTarget() instanceof PlayerEntity p && getOwnerId().map(id -> id.equals(p.getUuid())).orElse(false)) {
            boolean sneak = p.isSneaking();
            if (sneak != isSneaking()) {
                setSneaking(sneak);
                setPose(sneak ? EntityPose.CROUCHING : EntityPose.STANDING);
            }
        }
        if (this.age % 4 == 0 && getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(CYAN, getX(), getY() + random.nextDouble() * 1.8, getZ(), 1, 0.25, 0.1, 0.25, 0);
        }
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        if (random.nextInt(4) == 0) dropStack(new ItemStack(ModItems.CTRL_Z));
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.sfx("glitch_long");
    }

    @Override
    protected boolean isDisallowedInPeaceful() {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        getOwnerId().ifPresent(id -> nbt.putUuid("Owner", id));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Owner")) this.dataTracker.set(OWNER, Optional.of(nbt.getUuid("Owner")));
    }

    public static DoppelgangerEntity spawnFor(ServerPlayerEntity p, int minR, int maxR, boolean greet) {
        ServerWorld sw = p.getServerWorld();
        BlockPos pos = Spots.ground(sw, p.getBlockPos(), minR, maxR);
        if (pos == null) pos = p.getBlockPos();
        DoppelgangerEntity c = ModEntities.DOPPELGANGER.create(sw);
        if (c == null) return null;
        c.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Spots.yawTowards(pos.getX(), pos.getZ(), p.getX(), p.getZ()), 0);
        c.copyFrom(p);
        c.setTarget(p);
        sw.spawnEntity(c);
        sw.spawnParticles(CYAN, c.getX(), c.getY() + 1, c.getZ(), 40, 0.3, 0.8, 0.3, 0);
        sw.playSound(null, pos, ModSounds.sfx("glitch_long"), net.minecraft.sound.SoundCategory.HOSTILE, 1.2f, 1.0f);
        if (greet) com.hardest.util.Tasks.schedule(sw.getServer(), 30, () -> Voice.speak(c, "twin", "twin_hi"));
        return c;
    }
}
