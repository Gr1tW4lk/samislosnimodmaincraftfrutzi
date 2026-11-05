package com.hardest.item;

import com.hardest.net.Net;
import com.hardest.registry.ModEffects;
import com.hardest.registry.ModItems;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Бабушкин чай с малиной: лечит простуду.
 */
public class RaspberryTeaItem extends Item {
    public RaspberryTeaItem(Settings settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient) {
            user.removeStatusEffect(ModEffects.COLD);
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 0));
            if (user instanceof ServerPlayerEntity sp) {
                Criteria.CONSUME_ITEM.trigger(sp, stack);
                Net.alert(sp, Net.Alert.GOOD, ModItems.RASPBERRY_TEA, "БАБУШКА БЫЛА БЫ ДОВОЛЬНА", "Простуда прошла");
            }
        }
        if (user instanceof PlayerEntity p && !p.getAbilities().creativeMode) {
            stack.decrement(1);
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (stack.isEmpty()) return bottle;
            if (!p.getInventory().insertStack(bottle)) p.dropItem(bottle, false);
        }
        return stack;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 32;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return ItemUsage.consumeHeldItem(world, user, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("hardest.lore.raspberry_tea.1").formatted(Formatting.GRAY, Formatting.ITALIC));
    }
}
