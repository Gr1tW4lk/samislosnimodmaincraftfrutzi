package com.hardest.entity;

import com.hardest.util.Achievements;
import com.hardest.registry.ModEntities;
import com.hardest.registry.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Сундук с зубами. Притворяется обычным, пока не подойдёшь.
 */
public class MimicEntity extends HostileEntity {
    private static final TrackedData<Boolean> AWAKE = DataTracker.registerData(MimicEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private final List<ItemStack> loot = new ArrayList<>();
    private int hopCooldown;
    public int chompTicks;

    public MimicEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 12;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 34.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(AWAKE, false);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.2, false) {
            @Override
            public boolean canStart() {
                return isAwake() && super.canStart();
            }
        });
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true) {
            @Override
            public boolean canStart() {
                return isAwake() && super.canStart();
            }
        });
    }

    public boolean isAwake() {
        return this.dataTracker.get(AWAKE);
    }

    public void wakeUp() {
        if (isAwake()) return;
        this.dataTracker.set(AWAKE, true);
        if (getWorld().getClosestPlayer(this, 10) instanceof net.minecraft.server.network.ServerPlayerEntity sp) Achievements.grant(sp, "mimic");
        this.playSound(ModSounds.sfx("mimic_growl"), 1.6f, 1.0f);
        this.setVelocity(0, 0.55, 0);
        if (getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 1.1, getZ(), 4, 0.4, 0.2, 0.4, 0);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (chompTicks > 0) chompTicks--;
        if (getWorld().isClient) return;
        if (!isAwake()) {
            this.setVelocity(0, getVelocity().y, 0);
            PlayerEntity p = getWorld().getClosestPlayer(this, 2.6);
            if (p != null && !p.isSpectator() && !p.isCreative()) wakeUp();
            return;
        }
        // мимик не ходит — он прыгает
        if (hopCooldown > 0) hopCooldown--;
        if (isOnGround() && hopCooldown == 0 && getTarget() != null) {
            hopCooldown = 12 + random.nextInt(8);
            this.jump();
            this.playSound(ModSounds.sfx("mimic_chomp"), 0.7f, 1.3f);
            getWorld().sendEntityStatus(this, (byte) 102);
        }
    }

    @Override
    public void handleStatus(byte status) {
        if (status == 102) chompTicks = 10;
        else super.handleStatus(status);
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean r = super.tryAttack(target);
        if (r) {
            this.playSound(ModSounds.sfx("mimic_chomp"), 1.4f, 0.9f);
            getWorld().sendEntityStatus(this, (byte) 102);
        }
        return r;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!getWorld().isClient && !isAwake()) wakeUp();
        return super.damage(source, amount);
    }

    @Override
    protected boolean isDisallowedInPeaceful() {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    protected void dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops) {
        super.dropEquipment(source, lootingMultiplier, allowDrops);
        for (ItemStack s : loot) dropStack(s);
        loot.clear();
        dropStack(new ItemStack(Items.CHEST));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isAwake() ? ModSounds.sfx("mimic_growl") : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BLOCK_WOOD_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLOCK_CHEST_CLOSE;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Awake", isAwake());
        NbtList list = new NbtList();
        for (ItemStack s : loot) list.add(s.writeNbt(new NbtCompound()));
        nbt.put("Loot", list);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(AWAKE, nbt.getBoolean("Awake"));
        NbtList list = nbt.getList("Loot", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) loot.add(ItemStack.fromNbt(list.getCompound(i)));
    }

    /** Превратить сундук на месте в мимика, забрав его содержимое. */
    public static MimicEntity fromChest(ServerWorld world, BlockPos pos, Inventory inv, Direction facing, boolean awake) {
        MimicEntity m = ModEntities.MIMIC.create(world);
        if (m == null) return null;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty()) m.loot.add(s.copy());
        }
        inv.clear();
        world.removeBlock(pos, false);
        m.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, facing.asRotation(), 0);
        m.setHeadYaw(facing.asRotation());
        m.setBodyYaw(facing.asRotation());
        world.spawnEntity(m);
        if (awake) m.wakeUp();
        world.playSound(null, pos, ModSounds.sfx("mimic_growl"), SoundCategory.HOSTILE, 2.0f, 0.8f);
        return m;
    }

    public static MimicEntity spawnDormant(ServerWorld world, BlockPos pos, float yaw, LivingEntity lookAt) {
        MimicEntity m = ModEntities.MIMIC.create(world);
        if (m == null) return null;
        float snapped = Math.round(yaw / 90f) * 90f;
        m.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, snapped, 0);
        m.setHeadYaw(snapped);
        m.setBodyYaw(snapped);
        m.loot.add(new ItemStack(Items.DIAMOND, 1 + world.random.nextInt(3)));
        m.loot.add(new ItemStack(Items.GOLD_INGOT, 2 + world.random.nextInt(4)));
        world.spawnEntity(m);
        return m;
    }
}
