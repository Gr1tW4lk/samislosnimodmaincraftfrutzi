package com.hardest.item;

import com.hardest.events.Trips;
import com.hardest.util.Achievements;
import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AliasedBlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Странный гриб: сытный, но после него начинается «трип» — экран переворачивается, кнопки путаются и т.д.
 */
public class StrangeMushroomItem extends AliasedBlockItem {
    public StrangeMushroomItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        ItemStack r = super.finishUsing(stack, world, user);
        if (user instanceof ServerPlayerEntity sp) {
            Trips.random(sp, 20 * 30);
            Achievements.grant(sp, "mushroom");
        }
        return r;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("hardest.lore.strange_mushroom.1").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.translatable("hardest.lore.strange_mushroom.2").formatted(Formatting.GRAY, Formatting.ITALIC));
    }
}
