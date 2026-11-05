package com.hardest;

import com.hardest.command.HardCommand;
import com.hardest.dragon.DragonRage;
import com.hardest.events.GameEvents;
import com.hardest.events.WorldEvents;
import com.hardest.registry.ModBlocks;
import com.hardest.registry.ModEffects;
import com.hardest.registry.ModEntities;
import com.hardest.registry.ModItems;
import com.hardest.registry.ModSounds;
import com.hardest.util.Voice;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HardestMod implements ModInitializer {
    public static final String MOD_ID = "hardest";
    public static final Logger LOG = LoggerFactory.getLogger("Самый сложный мод");

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModSounds.register();
        ModEffects.register();
        ModBlocks.register();
        ModEntities.register();
        ModItems.register();
        Voice.load();
        GameEvents.register();
        WorldEvents.register();
        DragonRage.register();
        HardCommand.register();
        // странные грибы растут в лесах и на равнинах
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.VEGETAL_DECORATION,
                RegistryKey.of(RegistryKeys.PLACED_FEATURE, id("strange_mushroom")));
        LOG.info("Самый сложный мод загружен. Удачи. Она понадобится.");
    }
}
