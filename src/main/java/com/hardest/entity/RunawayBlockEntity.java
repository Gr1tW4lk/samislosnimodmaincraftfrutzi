package com.hardest.entity;

import com.hardest.util.Achievements;
import com.hardest.registry.ModEntities;
import com.hardest.registry.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;

/**
 * Блок, который отрастил ножки и убежал. Поймаешь — получишь двойной дроп.
 */
public class RunawayBlockEntity extends PathAwareEntity {
    private static final TrackedData<Optional<BlockState>> STATE = DataTracker.registerData(RunawayBlockEntity.class, TrackedDataHandlerRegistry.OPTIONAL_BLOCK_STATE);
    private static final String[] SCREAMS = {"block1", "block2", "block3", "block4"};

    private int screamCooldown = 0;
    private int lifetime = 0;

    public RunawayBlockEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setStepHeight(1.0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 8.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 20.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(STATE, Optional.of(Blocks.STONE.getDefaultState()));
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new FleeEntityGoal<>(this, PlayerEntity.class, 14.0f, 1.4, 1.85));
        this.goalSelector.add(4, new WanderAroundFarGoal(this, 1.0));
        this.goalSelector.add(5, new LookAroundGoal(this));
    }

    public BlockState getBlockState() {
        return this.dataTracker.get(STATE).orElse(Blocks.STONE.getDefaultState());
    }

    public void setBlockState(BlockState state) {
        this.dataTracker.set(STATE, Optional.of(state));
    }

    public boolean isPrecious() {
        BlockState s = getBlockState();
        return s.isIn(BlockTags.DIAMOND_ORES) || s.isIn(BlockTags.EMERALD_ORES) || s.isIn(BlockTags.GOLD_ORES);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        lifetime++;
        if (screamCooldown > 0) screamCooldown--;
        PlayerEntity near = getWorld().getClosestPlayer(this, 7.0);
        if (near != null && screamCooldown == 0) {
            screamCooldown = 50 + random.nextInt(60);
            this.playSound(ModSounds.voice(SCREAMS[random.nextInt(SCREAMS.length)]), 1.3f, 0.9f + random.nextFloat() * 0.3f);
        }
        if (this.age % 4 == 0 && this.getVelocity().horizontalLengthSquared() > 0.002 && this.isOnGround()) {
            this.playSound(ModSounds.sfx("tiny_steps"), 0.5f, 1.2f + random.nextFloat() * 0.4f);
        }
        // через 2 минуты блок «закапывается» обратно в землю
        if (lifetime > 2400 && near == null) {
            BlockPos pos = getBlockPos();
            if (getWorld().getBlockState(pos).isAir() && getWorld().getBlockState(pos.down()).isSolidBlock(getWorld(), pos.down())) {
                getWorld().setBlockState(pos, getBlockState());
            }
            ((ServerWorld) getWorld()).spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, getBlockState()),
                    getX(), getY() + 0.5, getZ(), 30, 0.3, 0.3, 0.3, 0.1);
            discard();
        }
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        if (!(getWorld() instanceof ServerWorld sw)) return;
        BlockState state = getBlockState();
        List<ItemStack> drops = Block.getDroppedStacks(state, sw, getBlockPos(), null);
        // поймал — получай двойной дроп
        for (int i = 0; i < 2; i++) {
            for (ItemStack s : drops) this.dropStack(s.copy());
        }
        if (isPrecious() || state.isIn(BlockTags.REDSTONE_ORES) || state.isIn(BlockTags.LAPIS_ORES) || state.isIn(BlockTags.COAL_ORES)) {
            ExperienceOrbEntity.spawn(sw, getPos(), 6 + random.nextInt(8));
        }
        sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), getX(), getY() + 0.6, getZ(), 40, 0.3, 0.3, 0.3, 0.15);
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        Achievements.grantTo(source.getAttacker(), "catch_block");
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return getBlockState().getSoundGroup().getHitSound();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return getBlockState().getSoundGroup().getBreakSound();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("BlockState", NbtHelper.fromBlockState(getBlockState()));
        nbt.putInt("Lifetime", lifetime);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("BlockState")) {
            setBlockState(NbtHelper.toBlockState(getWorld().createCommandRegistryWrapper(RegistryKeys.BLOCK), nbt.getCompound("BlockState")));
        }
        lifetime = nbt.getInt("Lifetime");
    }

    /** Блок под киркой превращается в беглеца. */
    public static RunawayBlockEntity spawnFrom(ServerWorld world, BlockPos pos, BlockState state, PlayerEntity player) {
        RunawayBlockEntity e = ModEntities.RUNAWAY_BLOCK.create(world);
        if (e == null) return null;
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
        e.setBlockState(state);
        float yaw = player.getYaw();
        e.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        if (e.isPrecious()) {
            e.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.38);
        }
        e.setVelocity(player.getRotationVector().multiply(0.3).add(0, 0.42, 0));
        world.spawnEntity(e);
        world.playSound(null, pos, ModSounds.voice("block1"), SoundCategory.NEUTRAL, 1.5f, 1.0f);
        world.spawnParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.02);
        return e;
    }

    /** Какие блоки умеют убегать. */
    public static boolean canRunAway(BlockState state) {
        if (state.hasBlockEntity() || state.isAir() || !state.isFullCube(net.minecraft.world.EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) return false;
        return state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isIn(BlockTags.DIRT)
                || state.isIn(BlockTags.SAND) || state.isIn(BlockTags.COAL_ORES) || state.isIn(BlockTags.IRON_ORES)
                || state.isIn(BlockTags.GOLD_ORES) || state.isIn(BlockTags.DIAMOND_ORES) || state.isIn(BlockTags.EMERALD_ORES)
                || state.isIn(BlockTags.REDSTONE_ORES) || state.isIn(BlockTags.LAPIS_ORES) || state.isIn(BlockTags.COPPER_ORES)
                || state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.CRAFTING_TABLE)
                || state.isOf(Blocks.PUMPKIN) || state.isOf(Blocks.MELON);
    }
}
