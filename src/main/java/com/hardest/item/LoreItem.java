package com.hardest.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Предмет с шуточным описанием из файла локализации (hardest.lore.<id>.N).
 */
public class LoreItem extends Item {
    private final int lines;

    public LoreItem(Settings settings, int lines) {
        super(settings);
        this.lines = lines;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        String key = getTranslationKey().replace("item.hardest.", "hardest.lore.");
        for (int i = 1; i <= lines; i++) {
            tooltip.add(Text.translatable(key + "." + i).formatted(Formatting.GRAY, Formatting.ITALIC));
        }
    }
}
