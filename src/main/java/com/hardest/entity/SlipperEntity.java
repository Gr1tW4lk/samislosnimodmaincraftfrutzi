package com.hardest.entity;

import com.hardest.net.Net;
import com.hardest.registry.ModEntities;
import com.hardest.registry.ModItems;
import com.hardest.registry.ModSounds;
import com.hardest.util.Achievements;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

/**
 * Летящий тапок. Работает как бумеранг: бьёт всех на пути и возвращается в руку.
 */
public class SlipperEntity extends ThrownItemEntity {
    private static final String[] MOM = {"mom1", "mom2", "mom3"};
    private boolean returning;
    private int flightTicks;
    private final Set<Integer> hitIds = new HashSet<>();

    public SlipperEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    public SlipperEntity(World world, LivingEntity owner, ItemStack stack) {
        super(ModEntities.SLIPPER, owner, world);
        setItem(stack.copy());
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.SLIPPER;
    }

    public ItemStack getStack() {
        return getItem();
    }

    @Override
    protected float getGravity() {
        return 0f;
    }

    @Override
    public void tick() {
        if (!getWorld().isClient) {
            flightTicks++;
            Entity owner = getOwner();
            if (flightTicks > 12) returning = true;
            if (returning) {
                if (owner == null || !owner.isAlive() || owner.getWorld() != getWorld()) {
                    dropAndDiscard();
                    return;
                }
                Vec3d to = owner.getEyePos().subtract(getPos());
                if (to.lengthSquared() < 2.0) {
                    giveBack(owner);
                    return;
                }
                this.noClip = true;
                setVelocity(to.normalize().multiply(Math.min(1.4, 0.6 + flightTicks * 0.02)));
            }
            if (flightTicks > 140) {
                dropAndDiscard();
                return;
            }
            if (flightTicks % 4 == 0) {
                getWorld().playSound(null, getBlockPos(), ModSounds.sfx("whoosh"), SoundCategory.PLAYERS, 0.5f, 1.4f);
            }
        }
        super.tick();
    }

    private void giveBack(Entity owner) {
        if (owner instanceof PlayerEntity p) {
            ItemStack s = getItem().copy();
            if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
        }
        discard();
    }

    private void dropAndDiscard() {
        dropStack(getItem().copy());
        discard();
    }

    @Override
    protected boolean canHit(Entity entity) {
        return entity != getOwner() && !hitIds.contains(entity.getId()) && super.canHit(entity);
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        Entity target = hit.getEntity();
        if (target == getOwner() || hitIds.contains(target.getId())) return;
        hitIds.add(target.getId());
        boolean crit = random.nextFloat() < 0.2f;
        float dmg = crit ? 20f : 7f;
        if (target instanceof net.minecraft.entity.boss.dragon.EnderDragonPart || target instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity) dmg *= 3f; // мамин тапок страшнее любого меча
        target.damage(getDamageSources().thrown(this, getOwner()), dmg);
        if (target instanceof LivingEntity le) {
            Vec3d push = getVelocity().normalize().multiply(crit ? 1.6 : 0.7);
            le.addVelocity(push.x, 0.35, push.z);
        }
        getWorld().playSound(null, target.getBlockPos(), ModSounds.sfx("slap"), SoundCategory.PLAYERS, 1.6f, crit ? 0.8f : 1.0f);
        if (getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(crit ? ParticleTypes.CRIT : ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.6), target.getZ(), crit ? 20 : 1, 0.3, 0.3, 0.3, 0.2);
        }
        if (crit && getOwner() instanceof ServerPlayerEntity sp) {
            Achievements.grant(sp, "slipper_crit");
            Net.alert(sp, Net.Alert.GOOD, ModItems.SLIPPER, "КРИТ!", "Тапок правосудия");
            getWorld().playSound(null, sp.getBlockPos(), ModSounds.voice(MOM[random.nextInt(MOM.length)]), SoundCategory.PLAYERS, 1.4f, 1.0f);
        }
        returning = true;
        // тапок изнашивается
        ItemStack s = getItem().copy();
        if (s.isDamageable()) {
            s.setDamage(s.getDamage() + 1);
            if (s.getDamage() >= s.getMaxDamage()) {
                discard();
                return;
            }
            setItem(s);
        }
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        if (!returning) {
            returning = true;
            getWorld().playSound(null, getBlockPos(), ModSounds.sfx("slap"), SoundCategory.PLAYERS, 0.6f, 1.6f);
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Returning", returning);
        nbt.putInt("Flight", flightTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        returning = nbt.getBoolean("Returning");
        flightTicks = nbt.getInt("Flight");
    }
}
