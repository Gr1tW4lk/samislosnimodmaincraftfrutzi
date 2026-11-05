package com.hardest.item;

import com.hardest.dragon.Fx;
import com.hardest.net.Net;
import com.hardest.registry.ModSounds;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Драконобой 9000: зажал ПКМ — непрерывная очередь лучей. Каждое попадание снимает дракону ровно 2 HP,
 * автоприцел цепляет дракона в радиусе 2 блоков от луча, дальность 128 блоков.
 */
public class DragonCannonItem extends Item {
    public static final double RANGE = 128;
    /** Выстрел каждые 3 тика — почти 7 в секунду. */
    private static final int RATE = 3;
    private static final float DRAGON_DAMAGE = 2f;
    private static final float MOB_DAMAGE = 8f;
    private static final DustParticleEffect CORE = new DustParticleEffect(new Vector3f(0.35f, 0.95f, 1.0f), 0.9f);
    private static final DustParticleEffect GLOW = new DustParticleEffect(new Vector3f(0.8f, 0.3f, 1.0f), 1.0f);

    public DragonCannonItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        user.setCurrentHand(hand);
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(world instanceof ServerWorld w) || !(user instanceof ServerPlayerEntity p)) return;
        if ((getMaxUseTime(stack) - remainingUseTicks) % RATE == 0) fire(w, p);
    }

    private static void fire(ServerWorld w, ServerPlayerEntity p) {
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVec(1f).add(
                (w.random.nextDouble() - 0.5) * 0.012, (w.random.nextDouble() - 0.5) * 0.012, (w.random.nextDouble() - 0.5) * 0.012).normalize();
        Vec3d far = eye.add(dir.multiply(RANGE));
        var blockHit = w.raycast(new RaycastContext(eye, far, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        Vec3d end = blockHit.getType() == HitResult.Type.MISS ? far : blockHit.getPos();
        double best = eye.distanceTo(end);
        Entity target = null;
        EnderDragonEntity dragon = null;

        // дракон: автоприцел по всем частям тела
        for (EnderDragonEntity d : w.getAliveEnderDragons()) {
            for (EnderDragonPart part : d.getBodyParts()) {
                Optional<Vec3d> hit = part.getBoundingBox().expand(2.0).raycast(eye, end);
                if (hit.isPresent() && eye.distanceTo(hit.get()) < best) {
                    best = eye.distanceTo(hit.get());
                    dragon = d;
                    target = part;
                }
            }
        }
        // остальные мобы и кристаллы Края
        List<Entity> others = w.getOtherEntities(p, new Box(eye, end).expand(2),
                e -> (e instanceof LivingEntity le && le.isAlive() && !(e instanceof EnderDragonEntity)) || e instanceof EndCrystalEntity);
        for (Entity e : others) {
            Optional<Vec3d> hit = e.getBoundingBox().expand(0.8).raycast(eye, end);
            if (hit.isPresent() && eye.distanceTo(hit.get()) < best) {
                best = eye.distanceTo(hit.get());
                dragon = null;
                target = e;
            }
        }
        Vec3d impact = eye.add(dir.multiply(best));
        DamageSource src = w.getDamageSources().playerAttack(p);
        if (dragon != null) {
            if (dragon.getPhaseManager().getCurrent().getType() != PhaseType.DYING) {
                // без неуязвимости после удара — иначе скорострельность бы не работала
                dragon.timeUntilRegen = 0;
                dragon.damagePart(dragon.head, src, DRAGON_DAMAGE);
            }
            Fx.particle(w, ParticleTypes.CRIT, impact.x, impact.y, impact.z, 12, 0.4, 0.4, 0.4, 0.5);
        } else if (target instanceof EndCrystalEntity crystal) {
            crystal.damage(src, 5f);
        } else if (target instanceof LivingEntity le) {
            le.timeUntilRegen = 0;
            le.damage(src, MOB_DAMAGE);
            le.takeKnockback(0.4, -dir.x, -dir.z);
        }
        effects(w, p, eye, dir, impact, target != null);
    }

    private static void effects(ServerWorld w, ServerPlayerEntity p, Vec3d eye, Vec3d dir, Vec3d impact, boolean hitEntity) {
        // дуло чуть правее и ниже глаз — из руки
        Vec3d right = dir.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d muzzle = eye.add(dir.multiply(0.9)).add(right.multiply(0.35)).add(0, -0.25, 0);
        double len = muzzle.distanceTo(impact);
        Vec3d step = impact.subtract(muzzle).normalize();
        // первые пару блоков луча не рисуем — частицы у самой камеры закрывают обзор
        for (double d = 2.2; d < len; d += 0.55) {
            Vec3d q = muzzle.add(step.multiply(d));
            Fx.particle(w, CORE, q.x, q.y, q.z, 1, 0.01, 0.01, 0.01, 0);
            if (d > 5 && ((int) (d / 0.55)) % 3 == 0) Fx.particle(w, ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0.005);
        }
        Fx.particle(w, ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 3, 0.05, 0.05, 0.05, 0.1);
        // попадание
        Fx.particle(w, ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
        Fx.particle(w, ParticleTypes.END_ROD, impact.x, impact.y, impact.z, 10, 0.1, 0.1, 0.1, 0.22);
        if (len > 6) Fx.particle(w, GLOW, impact.x, impact.y, impact.z, 6, 0.4, 0.4, 0.4, 0);
        Fx.particle(w, ParticleTypes.REVERSE_PORTAL, impact.x, impact.y, impact.z, 12, 0.2, 0.2, 0.2, 0.3);
        float pitch = 0.9f + w.random.nextFloat() * 0.2f;
        w.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.sfx("cannon_shot"), SoundCategory.PLAYERS, 1.1f, pitch);
        w.playSound(null, impact.x, impact.y, impact.z, hitEntity ? SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST : SoundEvents.BLOCK_AMETHYST_BLOCK_HIT,
                SoundCategory.PLAYERS, 1.4f, 1.3f + w.random.nextFloat() * 0.3f);
        Net.fx(p, "shake", 3, 1);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("hardest.lore.dragon_cannon.1").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("hardest.lore.dragon_cannon.2").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.translatable("hardest.lore.dragon_cannon.3").formatted(Formatting.GRAY, Formatting.ITALIC));
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }
}
