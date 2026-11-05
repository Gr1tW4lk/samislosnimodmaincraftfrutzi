package com.hardest.registry;

import com.hardest.HardestMod;
import com.hardest.item.DragonCannonItem;
import com.hardest.item.HotPotatoItem;
import com.hardest.item.LoreItem;
import com.hardest.item.RaspberryTeaItem;
import com.hardest.item.SlipperItem;
import com.hardest.item.StrangeMushroomItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.List;

public final class ModItems {
    private static final List<Item> ALL = new ArrayList<>();

    public static final Item SLIPPER = register("slipper", new SlipperItem(new Item.Settings().maxDamage(300)));
    public static final Item CTRL_Z = register("ctrl_z", new LoreItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE), 2));
    public static final Item HOT_POTATO = register("hot_potato", new HotPotatoItem(new Item.Settings().maxCount(1)));
    public static final Item GOOSE_FEATHER = register("goose_feather", new LoreItem(new Item.Settings(), 1));
    public static final Item GOOSE_PILLOW = register("goose_pillow", new LoreItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON), 2));
    public static final Item TAX_RECEIPT = register("tax_receipt", new LoreItem(new Item.Settings().rarity(Rarity.UNCOMMON), 2));
    public static final Item RASPBERRY_TEA = register("raspberry_tea", new RaspberryTeaItem(new Item.Settings().maxCount(16)));
    public static final Item DRAGON_CANNON = register("dragon_cannon", new DragonCannonItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));
    public static final Item STRANGE_MUSHROOM = register("strange_mushroom", new StrangeMushroomItem(ModBlocks.STRANGE_MUSHROOM,
            new Item.Settings().food(new FoodComponent.Builder().hunger(7).saturationModifier(1.0f).alwaysEdible().build())));

    public static final Item GOOSE_EGG = egg("goose_spawn_egg", ModEntities.GOOSE, 0xF2F2F2, 0xF59B1C);
    public static final Item TAX_EGG = egg("tax_inspector_spawn_egg", ModEntities.TAX_INSPECTOR, 0x2B2F3A, 0xC9A227);
    public static final Item MIMIC_EGG = egg("mimic_spawn_egg", ModEntities.MIMIC, 0x9B6A2F, 0xC22B2B);
    public static final Item COURIER_EGG = egg("courier_spawn_egg", ModEntities.COURIER, 0x3C9A3C, 0xF5C518);
    public static final Item DOPPEL_EGG = egg("doppelganger_spawn_egg", ModEntities.DOPPELGANGER, 0x00B3B3, 0x2D3F8F);
    public static final Item COW_EGG = egg("parachute_cow_spawn_egg", ModEntities.PARACHUTE_COW, 0x443626, 0xE84040);
    public static final Item SNIPER_EGG = egg("sniper_skeleton_spawn_egg", ModEntities.SNIPER, 0xC1C1C1, 0xD01010);
    public static final Item RUNAWAY_EGG = egg("runaway_block_spawn_egg", ModEntities.RUNAWAY_BLOCK, 0x7F7F7F, 0x4AEDD9);

    public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, HardestMod.id("main"),
            FabricItemGroup.builder()
                    .displayName(Text.literal("Самый сложный мод"))
                    .icon(() -> new ItemStack(SLIPPER))
                    .entries((ctx, entries) -> {
                        for (Item i : ALL) entries.add(i);
                        entries.add(ModBlocks.GIFT_BOX);
                    })
                    .build());

    private static Item register(String name, Item item) {
        Registry.register(Registries.ITEM, HardestMod.id(name), item);
        ALL.add(item);
        return item;
    }

    private static Item egg(String name, EntityType<? extends MobEntity> type, int a, int b) {
        return register(name, new SpawnEggItem(type, a, b, new Item.Settings()));
    }

    public static void register() {
    }

    private ModItems() {
    }
}
