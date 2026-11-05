package com.hardest.entity;

import com.hardest.net.Net;
import com.hardest.registry.ModEntities;
import com.hardest.registry.ModItems;
import com.hardest.registry.ModSounds;
import com.hardest.util.Achievements;
import com.hardest.util.Spots;
import com.hardest.util.Tasks;
import com.hardest.util.Voice;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
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
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Налоговый инспектор. Приходит, здоровается, изымает 13% от всего, что у тебя есть.
 */
public class TaxInspectorEntity extends PathAwareEntity {
    public static final int APPROACH = 0, AUDIT = 1, LEAVE = 2, ANGRY = 3;
    private static final TrackedData<Integer> MODE = DataTracker.registerData(TaxInspectorEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> STAMP = DataTracker.registerData(TaxInspectorEntity.class, TrackedDataHandlerRegistry.INTEGER);

    @Nullable
    private UUID targetId;
    private int modeTicks;
    private boolean bailiffsCalled;
    private final List<ItemStack> collected = new ArrayList<>();

    public TaxInspectorEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.8)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(MODE, APPROACH);
        this.dataTracker.startTracking(STAMP, 0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
    }

    public int getMode() {
        return this.dataTracker.get(MODE);
    }

    private void setMode(int mode) {
        this.dataTracker.set(MODE, mode);
        modeTicks = 0;
    }

    /** Для анимации штампа на клиенте. */
    public int getStampTicks() {
        return this.dataTracker.get(STAMP);
    }

    public void setTarget(ServerPlayerEntity p) {
        this.targetId = p.getUuid();
    }

    @Nullable
    private ServerPlayerEntity target() {
        if (targetId == null || !(getWorld() instanceof ServerWorld sw)) return null;
        return (ServerPlayerEntity) sw.getPlayerByUuid(targetId);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        int stamp = getStampTicks();
        if (stamp > 0) this.dataTracker.set(STAMP, stamp - 1);
        modeTicks++;
        ServerPlayerEntity p = target();
        switch (getMode()) {
            case APPROACH -> {
                if (p == null || !p.isAlive()) {
                    setMode(LEAVE);
                    return;
                }
                getLookControl().lookAt(p, 20f, 20f);
                if (modeTicks % 10 == 1) getNavigation().startMovingTo(p, 1.0);
                if (squaredDistanceTo(p) < 2.8 * 2.8) {
                    getNavigation().stop();
                    setMode(AUDIT);
                    Voice.speak(this, "tax", "tax_greet");
                }
                if (modeTicks > 20 * 60) {
                    // не дошёл за минуту — телепортируется прямо к игроку. Налоговая найдёт везде.
                    Vec3d dir = p.getRotationVector().multiply(2.0);
                    requestTeleport(p.getX() + dir.x, p.getY(), p.getZ() + dir.z);
                    modeTicks = 0;
                }
            }
            case AUDIT -> {
                if (p == null) {
                    setMode(LEAVE);
                    return;
                }
                getLookControl().lookAt(p, 30f, 30f);
                int greet = Voice.ticks("tax_greet");
                if (modeTicks == greet) collectTaxes(p);
                if (modeTicks > greet + Voice.ticks("tax_take") + 10) setMode(LEAVE);
            }
            case LEAVE -> {
                if (modeTicks % 40 == 1) {
                    Vec3d away = p != null ? NoPenaltyTargeting.findFrom(this, 20, 6, p.getPos()) : NoPenaltyTargeting.find(this, 20, 6);
                    if (away != null) getNavigation().startMovingTo(away.x, away.y, away.z, 0.9);
                }
                if (modeTicks > 20 * 15) {
                    poof();
                }
            }
            case ANGRY -> {
                if (modeTicks % 30 == 1 && p != null) {
                    Vec3d away = NoPenaltyTargeting.findFrom(this, 16, 6, p.getPos());
                    if (away != null) getNavigation().startMovingTo(away.x, away.y, away.z, 1.3);
                }
                if (modeTicks > 20 * 40) poof();
            }
        }
    }

    private void poof() {
        if (getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.02);
        }
        discard();
    }

    private void collectTaxes(ServerPlayerEntity p) {
        ServerWorld sw = (ServerWorld) getWorld();
        int taken = 0;
        var inv = p.getInventory();
        // квитанция об уплате спасает от одной проверки
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(ModItems.TAX_RECEIPT)) {
                inv.getStack(i).decrement(1);
                this.dataTracker.set(STAMP, 12);
                sw.playSound(null, getBlockPos(), ModSounds.sfx("stamp"), SoundCategory.NEUTRAL, 1.5f, 1.0f);
                Voice.speak(this, "tax", "tax_receipt");
                Net.alert(p, Net.Alert.GOOD, ModItems.TAX_RECEIPT, "КВИТАНЦИЯ ПРИНЯТА", "В этот раз налог не взяли");
                return;
            }
        }
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            int amount;
            if (s.getCount() >= 2) {
                amount = MathHelper.ceil(s.getCount() * 0.13f);
            } else {
                amount = random.nextFloat() < 0.13f ? 1 : 0;
            }
            if (amount <= 0) continue;
            ItemStack part = s.split(amount);
            collected.add(part);
            taken += amount;
            // предмет красиво улетает в портфель
            ItemEntity fly = new ItemEntity(sw, p.getX(), p.getEyeY() - 0.3, p.getZ(), part.copy());
            fly.setPickupDelayInfinite();
            fly.setNoGravity(true);
            Vec3d to = getPos().add(0, 1.0, 0).subtract(fly.getPos()).multiply(0.12);
            fly.setVelocity(to.add((random.nextDouble() - 0.5) * 0.1, 0.15, (random.nextDouble() - 0.5) * 0.1));
            sw.spawnEntity(fly);
            Tasks.schedule(sw.getServer(), 10, fly::discard);
        }
        this.dataTracker.set(STAMP, 12);
        Achievements.grant(p, "tax");
        sw.playSound(null, getBlockPos(), ModSounds.sfx("stamp"), SoundCategory.NEUTRAL, 1.5f, 1.0f);
        if (taken > 0) {
            sw.playSound(null, getBlockPos(), ModSounds.sfx("cash"), SoundCategory.NEUTRAL, 1.3f, 1.0f);
            Voice.speak(this, "tax", "tax_take");
            Net.alert(p, Net.Alert.DANGER, Items.EMERALD, "НАЛОГ УПЛАЧЕН", "Изъято " + taken + " предм. (13%)");
        } else {
            Voice.speak(this, "tax", "tax_empty");
            p.getHungerManager().setFoodLevel(Math.max(0, p.getHungerManager().getFoodLevel() - 8));
            p.getHungerManager().setSaturationLevel(0);
            Net.alert(p, Net.Alert.DANGER, Items.BREAD, "НАЛОГ НА ЕДУ", "−4 голода");
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean r = super.damage(source, amount);
        if (r && !getWorld().isClient && source.getAttacker() instanceof ServerPlayerEntity sp && !bailiffsCalled) {
            bailiffsCalled = true;
            setMode(ANGRY);
            targetId = sp.getUuid();
            Voice.speak(this, "tax", "tax_angry");
            callBailiffs(sp);
        }
        return r;
    }

    private void callBailiffs(ServerPlayerEntity p) {
        ServerWorld sw = (ServerWorld) getWorld();
        for (int i = 0; i < 3; i++) {
            BlockPos pos = Spots.ground(sw, p.getBlockPos(), 6, 10);
            if (pos == null) continue;
            VindicatorEntity v = EntityType.VINDICATOR.create(sw);
            if (v == null) continue;
            v.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            v.initialize(sw, sw.getLocalDifficulty(pos), SpawnReason.EVENT, null, null);
            v.setCustomName(Text.literal("Судебный пристав").formatted(Formatting.DARK_RED));
            v.setCustomNameVisible(true);
            v.setTarget(p);
            sw.spawnEntity(v);
            sw.spawnParticles(ParticleTypes.POOF, v.getX(), v.getY() + 1, v.getZ(), 15, 0.3, 0.6, 0.3, 0.02);
        }
        sw.playSound(null, getBlockPos(), ModSounds.sfx("siren"), SoundCategory.HOSTILE, 2.0f, 1.0f);
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (source.getAttacker() instanceof ServerPlayerEntity) Voice.speak(this, "tax", "tax_death");
    }

    @Override
    protected void dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops) {
        super.dropEquipment(source, lootingMultiplier, allowDrops);
        for (ItemStack s : collected) dropStack(s);
        collected.clear();
        dropStack(new ItemStack(ModItems.TAX_RECEIPT));
        if (random.nextInt(3) == 0) dropStack(new ItemStack(Items.PAPER, 3));
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Mode", getMode());
        nbt.putBoolean("Bailiffs", bailiffsCalled);
        if (targetId != null) nbt.putUuid("Target", targetId);
        NbtList list = new NbtList();
        for (ItemStack s : collected) list.add(s.writeNbt(new NbtCompound()));
        nbt.put("Collected", list);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(MODE, nbt.getInt("Mode"));
        bailiffsCalled = nbt.getBoolean("Bailiffs");
        if (nbt.containsUuid("Target")) targetId = nbt.getUuid("Target");
        NbtList list = nbt.getList("Collected", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) collected.add(ItemStack.fromNbt(list.getCompound(i)));
    }

    public static void spawnFor(ServerPlayerEntity p) {
        ServerWorld sw = p.getServerWorld();
        BlockPos pos = Spots.ground(sw, p.getBlockPos(), 12, 18);
        if (pos == null) pos = p.getBlockPos().add(4, 0, 4);
        TaxInspectorEntity t = ModEntities.TAX_INSPECTOR.create(sw);
        if (t == null) return;
        t.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Spots.yawTowards(pos.getX(), pos.getZ(), p.getX(), p.getZ()), 0);
        t.setTarget(p);
        t.setCustomName(Text.literal("Налоговый инспектор").formatted(Formatting.GRAY));
        sw.spawnEntity(t);
        sw.spawnParticles(ParticleTypes.POOF, t.getX(), t.getY() + 1, t.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
    }
}
