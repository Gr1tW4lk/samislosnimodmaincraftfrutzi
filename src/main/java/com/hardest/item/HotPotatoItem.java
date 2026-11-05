package com.hardest.item;

import com.hardest.net.Net;
import com.hardest.registry.ModItems;
import com.hardest.registry.ModSounds;
import com.hardest.util.Achievements;
import com.hardest.util.Tasks;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Горячая картошка: взорвётся через 30 секунд. Ударь ей моба, чтобы передать.
 * Время взрыва хранится как абсолютное время мира — так предмет не «дёргается» в руке каждый тик.
 */
public class HotPotatoItem extends Item {
    public static final int FUSE = 20 * 30;

    public HotPotatoItem(Settings settings) {
        super(settings);
    }

    public static ItemStack create(World world) {
        ItemStack s = new ItemStack(ModItems.HOT_POTATO);
        s.getOrCreateNbt().putLong("ExplodeAt", world.getTime() + FUSE);
        return s;
    }

    public static int remaining(ItemStack s, @Nullable World world) {
        if (world == null || !s.hasNbt() || !s.getNbt().contains("ExplodeAt")) return FUSE;
        return (int) Math.max(0, s.getNbt().getLong("ExplodeAt") - world.getTime());
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient) return;
        if (!stack.hasNbt() || !stack.getNbt().contains("ExplodeAt")) {
            stack.getOrCreateNbt().putLong("ExplodeAt", world.getTime() + FUSE);
        }
        int left = remaining(stack, world);
        if (left % 20 == 0 || (left < 100 && left % 5 == 0)) {
            world.playSound(null, entity.getBlockPos(), ModSounds.sfx("beep"), SoundCategory.PLAYERS, 0.8f, left < 100 ? 1.6f : 1.0f);
        }
        if (left % 4 == 0 && world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.FLAME, entity.getX(), entity.getY() + 1, entity.getZ(), 2, 0.3, 0.3, 0.3, 0.01);
        }
        if (left <= 0) {
            stack.setCount(0);
            world.createExplosion(null, entity.getX(), entity.getY() + 0.5, entity.getZ(), 3.0f, World.ExplosionSourceType.NONE);
            if (entity instanceof PlayerEntity p) Net.alert(p, Net.Alert.DANGER, ModItems.HOT_POTATO, "КАРТОШКА ВЗОРВАЛАСЬ", "");
        }
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        World world = target.getWorld();
        if (world.isClient || !(world instanceof ServerWorld sw)) return true;
        // передал картошку мобу — теперь взорвётся он
        stack.setCount(0);
        attacker.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0));
        target.setOnFireFor(3);
        world.playSound(null, target.getBlockPos(), ModSounds.sfx("beep"), SoundCategory.PLAYERS, 1.2f, 2.0f);
        Achievements.grantTo(attacker, "potato");
        if (attacker instanceof PlayerEntity p) Net.alert(p, Net.Alert.GOOD, ModItems.HOT_POTATO, "КАРТОШКА ПЕРЕДАНА", "Теперь это чужая проблема");
        Tasks.schedule(sw.getServer(), 50, () -> {
            if (target.isAlive()) {
                world.createExplosion(null, target.getX(), target.getY() + 0.5, target.getZ(), 2.5f, World.ExplosionSourceType.NONE);
            }
        });
        return true;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round(13f * remaining(stack, clientWorld()) / FUSE);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return 0xFF4010;
    }

    @Nullable
    private static World clientWorld() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT
                ? ClientWorldHolder.get() : null;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Взорвётся через " + remaining(stack, world) / 20 + " сек.").formatted(Formatting.RED));
        tooltip.add(Text.translatable("hardest.lore.hot_potato.1").formatted(Formatting.GRAY, Formatting.ITALIC));
    }

    /** Клиентский мир задаётся из клиентской части мода (чтобы общий код не трогал клиентские классы). */
    public static final class ClientWorldHolder {
        public static java.util.function.Supplier<World> supplier = () -> null;

        static World get() {
            return supplier.get();
        }
    }
}
