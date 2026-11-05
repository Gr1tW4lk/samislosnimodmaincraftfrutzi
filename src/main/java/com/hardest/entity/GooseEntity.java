package com.hardest.entity;

import com.hardest.net.Net;
import com.hardest.registry.ModEntities;
import com.hardest.registry.ModSounds;
import com.hardest.util.Achievements;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Гусь. Подкрадывается, вырывает предмет из руки и с гоготом убегает.
 */
public class GooseEntity extends AnimalEntity {
    private static final TrackedData<ItemStack> STOLEN = DataTracker.registerData(GooseEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    private static final TrackedData<Boolean> HISSING = DataTracker.registerData(GooseEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> PARACHUTE = DataTracker.registerData(GooseEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public int stealCooldown = 100;
    private int honkTimer;
    /** Для клиента: таймер «раскрытого клюва». */
    public int mouthOpenTicks;

    public GooseEntity(EntityType<? extends AnimalEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(STOLEN, ItemStack.EMPTY);
        this.dataTracker.startTracking(HISSING, false);
        this.dataTracker.startTracking(PARACHUTE, false);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new FleeWithLootGoal(this));
        this.goalSelector.add(2, new StealGoal(this));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 1.0));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));
    }

    public ItemStack getStolen() {
        return this.dataTracker.get(STOLEN);
    }

    public void setStolen(ItemStack stack) {
        this.dataTracker.set(STOLEN, stack);
    }

    public boolean hasLoot() {
        return !getStolen().isEmpty();
    }

    /** Гусиный десант: падает с неба на парашюте. */
    public boolean hasParachute() {
        return this.dataTracker.get(PARACHUTE);
    }

    public void setParachute(boolean b) {
        this.dataTracker.set(PARACHUTE, b);
    }

    public boolean isHissing() {
        return this.dataTracker.get(HISSING);
    }

    public void setHissing(boolean b) {
        this.dataTracker.set(HISSING, b);
    }

    public void honk(float pitch) {
        this.playSound(ModSounds.sfx("goose_honk"), 1.4f, pitch);
        this.getWorld().sendEntityStatus(this, (byte) 101);
    }

    @Override
    public void handleStatus(byte status) {
        if (status == 101) {
            mouthOpenTicks = 8;
        } else {
            super.handleStatus(status);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (mouthOpenTicks > 0) mouthOpenTicks--;
        if (!this.getWorld().isClient && hasParachute()) {
            Vec3d v = getVelocity();
            if (v.y < -0.1) setVelocity(v.x * 0.9, -0.1, v.z * 0.9);
            this.fallDistance = 0;
            if (isOnGround() || isTouchingWater()) {
                setParachute(false);
                playSound(ModSounds.sfx("parachute"), 0.8f, 1.3f);
                stealCooldown = 0;
            }
        }
        if (!this.getWorld().isClient) {
            if (stealCooldown > 0) stealCooldown--;
            if (hasLoot() && --honkTimer <= 0) {
                honkTimer = 25 + random.nextInt(30);
                honk(1.0f + random.nextFloat() * 0.3f);
            }
        }
    }

    /** Вырвать предмет из инвентаря игрока. */
    public boolean stealFrom(PlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        ItemStack hand = player.getMainHandStack();
        ItemStack taken;
        if (!hand.isEmpty()) {
            taken = hand.copy();
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        } else {
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < inv.main.size(); i++) if (!inv.main.get(i).isEmpty()) slots.add(i);
            if (slots.isEmpty()) return false;
            int slot = slots.get(random.nextInt(slots.size()));
            taken = inv.main.get(slot).copy();
            inv.main.set(slot, ItemStack.EMPTY);
        }
        setStolen(taken);
        setPersistent();
        honk(1.3f);
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, taken), getX(), getY() + 0.8, getZ(), 12, 0.2, 0.2, 0.2, 0.1);
            sw.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 6, 0.3, 0.2, 0.3, 0.02);
        }
        if (player instanceof ServerPlayerEntity sp) Achievements.grant(sp, "goose");
        Net.alert(player, Net.Alert.FUNNY, taken.copy(), Text.literal("ГУСЬ УКРАЛ"), taken.getName().copy());
        return true;
    }

    @Override
    protected void dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops) {
        super.dropEquipment(source, lootingMultiplier, allowDrops);
        if (hasLoot()) {
            ItemEntity e = this.dropStack(getStolen().copy());
            if (e != null) e.setVelocity(e.getVelocity().add(0, 0.25, 0));
            setStolen(ItemStack.EMPTY);
        }
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean r = super.damage(source, amount);
        if (r && !getWorld().isClient && !hasLoot()) {
            // обиженный гусь шипит и уходит в бег
            stealCooldown = 0;
        }
        return r;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (hasLoot()) nbt.put("Stolen", getStolen().writeNbt(new NbtCompound()));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Stolen")) setStolen(ItemStack.fromNbt(nbt.getCompound("Stolen")));
    }

    @Override
    public boolean cannotDespawn() {
        return hasLoot() || super.cannotDespawn();
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return random.nextInt(3) == 0 ? ModSounds.sfx("goose_honk") : null;
    }

    @Override
    public void playAmbientSound() {
        super.playAmbientSound();
        this.getWorld().sendEntityStatus(this, (byte) 101);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.sfx("goose_hiss");
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.sfx("goose_honk");
    }

    @Override
    protected float getSoundVolume() {
        return 1.2f;
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return stack.isOf(Items.BREAD);
    }

    @Nullable
    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return ModEntities.GOOSE.create(world);
    }

    // ---------------------------------------------------------------- ИИ гуся

    static boolean isVictim(PlayerEntity p) {
        return p.isAlive() && !p.isSpectator();
    }

    /** Подойти к игроку, зашипеть и вырвать предмет. */
    static class StealGoal extends Goal {
        private final GooseEntity goose;
        private PlayerEntity target;
        private int ticks;

        StealGoal(GooseEntity goose) {
            this.goose = goose;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (goose.hasLoot() || goose.stealCooldown > 0 || goose.isBaby()) return false;
            PlayerEntity p = goose.getWorld().getClosestPlayer(goose, 20.0);
            if (p == null || !isVictim(p)) return false;
            if (p.getInventory().isEmpty()) return false;
            target = p;
            return true;
        }

        @Override
        public boolean shouldContinue() {
            return target != null && isVictim(target) && !goose.hasLoot() && ticks < 400
                    && goose.squaredDistanceTo(target) < 26 * 26;
        }

        @Override
        public void start() {
            ticks = 0;
            goose.setHissing(true);
            goose.playSound(ModSounds.sfx("goose_hiss"), 1.0f, 1.0f);
        }

        @Override
        public void stop() {
            goose.setHissing(false);
            goose.getNavigation().stop();
            if (!goose.hasLoot()) goose.stealCooldown = 200;
            target = null;
        }

        @Override
        public void tick() {
            ticks++;
            goose.getLookControl().lookAt(target, 30f, 30f);
            if (ticks % 10 == 0) goose.getNavigation().startMovingTo(target, 1.55);
            if (goose.squaredDistanceTo(target) < 2.2) {
                if (goose.stealFrom(target)) {
                    target.damage(goose.getDamageSources().mobAttack(goose), 1.0f);
                    goose.stealCooldown = 600;
                }
            }
        }
    }

    /** С добычей — бежать от людей и гоготать. */
    static class FleeWithLootGoal extends Goal {
        private final GooseEntity goose;

        FleeWithLootGoal(GooseEntity goose) {
            this.goose = goose;
            this.setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            return goose.hasLoot() && goose.getWorld().getClosestPlayer(goose, 14.0) != null;
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void tick() {
            PlayerEntity p = goose.getWorld().getClosestPlayer(goose, 14.0);
            if (p == null) return;
            if (goose.getNavigation().isIdle() || goose.age % 15 == 0) {
                Vec3d away = NoPenaltyTargeting.findFrom(goose, 16, 6, p.getPos());
                if (away != null) goose.getNavigation().startMovingTo(away.x, away.y, away.z, 1.75);
            }
        }

        @Override
        public void stop() {
            goose.getNavigation().stop();
        }
    }

    public static void spawnAround(ServerWorld world, ServerPlayerEntity p, int count) {
        for (int i = 0; i < count; i++) {
            var pos = com.hardest.util.Spots.ground(world, p.getBlockPos(), 9, 15);
            if (pos == null) continue;
            GooseEntity g = ModEntities.GOOSE.create(world);
            if (g == null) continue;
            g.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360f, 0);
            g.stealCooldown = 20 + world.random.nextInt(60);
            world.spawnEntity(g);
            world.playSound(null, pos, ModSounds.sfx("goose_honk"), SoundCategory.NEUTRAL, 1.5f, 0.9f + world.random.nextFloat() * 0.3f);
        }
    }
}
