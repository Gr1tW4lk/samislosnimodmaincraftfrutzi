package com.hardest.registry;

import com.hardest.HardestMod;
import com.hardest.entity.CourierCreeperEntity;
import com.hardest.entity.DoppelgangerEntity;
import com.hardest.entity.GooseEntity;
import com.hardest.entity.MimicEntity;
import com.hardest.entity.ParachuteCowEntity;
import com.hardest.entity.RunawayBlockEntity;
import com.hardest.entity.SlipperEntity;
import com.hardest.entity.SniperSkeletonEntity;
import com.hardest.entity.TaxInspectorEntity;
import com.hardest.entity.WebShotEntity;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.world.Heightmap;

public final class ModEntities {
    public static final EntityType<GooseEntity> GOOSE = register("goose",
            EntityType.Builder.create(GooseEntity::new, SpawnGroup.CREATURE).setDimensions(0.6f, 1.1f).maxTrackingRange(10));
    public static final EntityType<RunawayBlockEntity> RUNAWAY_BLOCK = register("runaway_block",
            EntityType.Builder.create(RunawayBlockEntity::new, SpawnGroup.MISC).setDimensions(0.8f, 1.25f).maxTrackingRange(10));
    public static final EntityType<TaxInspectorEntity> TAX_INSPECTOR = register("tax_inspector",
            EntityType.Builder.create(TaxInspectorEntity::new, SpawnGroup.MISC).setDimensions(0.6f, 1.95f).maxTrackingRange(10));
    public static final EntityType<MimicEntity> MIMIC = register("mimic",
            EntityType.Builder.create(MimicEntity::new, SpawnGroup.MONSTER).setDimensions(0.9f, 0.9f).maxTrackingRange(10));
    public static final EntityType<CourierCreeperEntity> COURIER = register("courier",
            EntityType.Builder.create(CourierCreeperEntity::new, SpawnGroup.MISC).setDimensions(0.6f, 1.7f).maxTrackingRange(10));
    public static final EntityType<DoppelgangerEntity> DOPPELGANGER = register("doppelganger",
            EntityType.Builder.create(DoppelgangerEntity::new, SpawnGroup.MONSTER).setDimensions(0.6f, 1.8f).maxTrackingRange(10));
    public static final EntityType<ParachuteCowEntity> PARACHUTE_COW = register("parachute_cow",
            EntityType.Builder.create(ParachuteCowEntity::new, SpawnGroup.CREATURE).setDimensions(0.9f, 1.4f).maxTrackingRange(12));
    public static final EntityType<SniperSkeletonEntity> SNIPER = register("sniper_skeleton",
            EntityType.Builder.create(SniperSkeletonEntity::new, SpawnGroup.MONSTER).setDimensions(0.6f, 1.99f).maxTrackingRange(12));
    public static final EntityType<SlipperEntity> SLIPPER = register("slipper",
            EntityType.Builder.<SlipperEntity>create(SlipperEntity::new, SpawnGroup.MISC).setDimensions(0.5f, 0.5f).maxTrackingRange(6).trackingTickInterval(1));
    public static final EntityType<WebShotEntity> WEB_SHOT = register("web_shot",
            EntityType.Builder.<WebShotEntity>create(WebShotEntity::new, SpawnGroup.MISC).setDimensions(0.4f, 0.4f).maxTrackingRange(6).trackingTickInterval(2));

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        return Registry.register(Registries.ENTITY_TYPE, HardestMod.id(name), builder.build(name));
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(GOOSE, GooseEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RUNAWAY_BLOCK, RunawayBlockEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(TAX_INSPECTOR, TaxInspectorEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MIMIC, MimicEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(COURIER, CourierCreeperEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(DOPPELGANGER, DoppelgangerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(PARACHUTE_COW, ParachuteCowEntity.createCowAttributes());
        FabricDefaultAttributeRegistry.register(SNIPER, SniperSkeletonEntity.createSniperAttributes());

        SpawnRestriction.register(GOOSE, SpawnRestriction.Location.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AnimalEntity::isValidNaturalSpawn);
        BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_OVERWORLD).and(BiomeSelectors.spawnsOneOf(EntityType.COW)),
                SpawnGroup.CREATURE, GOOSE, 7, 2, 4);
    }

    private ModEntities() {
    }
}
