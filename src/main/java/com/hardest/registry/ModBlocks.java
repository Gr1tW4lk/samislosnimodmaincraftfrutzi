package com.hardest.registry;

import com.hardest.HardestMod;
import com.hardest.block.GiftBoxBlock;
import com.hardest.block.StrangeMushroomBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
    public static final Block GIFT_BOX = register("gift_box", new GiftBoxBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.RED).strength(0.6f).sounds(BlockSoundGroup.WOOL).nonOpaque()), true);
    public static final Block STRANGE_MUSHROOM = register("strange_mushroom", new StrangeMushroomBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.PINK).noCollision().breakInstantly().sounds(BlockSoundGroup.GRASS).luminance(s -> 5)
            .offset(AbstractBlock.OffsetType.XZ).pistonBehavior(PistonBehavior.DESTROY)), false);

    private static Block register(String name, Block block, boolean item) {
        Registry.register(Registries.BLOCK, HardestMod.id(name), block);
        if (item) Registry.register(Registries.ITEM, HardestMod.id(name), new BlockItem(block, new Item.Settings()));
        return block;
    }

    public static void register() {
    }

    private ModBlocks() {
    }
}
