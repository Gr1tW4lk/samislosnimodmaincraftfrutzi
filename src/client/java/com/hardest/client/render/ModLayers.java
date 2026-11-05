package com.hardest.client.render;

import com.hardest.HardestMod;
import com.hardest.client.model.ModelDefs;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.render.entity.model.EntityModelLayer;

public final class ModLayers {
    public static final EntityModelLayer GOOSE = layer("goose");
    public static final EntityModelLayer RUNAWAY = layer("runaway_block");
    public static final EntityModelLayer TAX = layer("tax_inspector");
    public static final EntityModelLayer MIMIC = layer("mimic");
    public static final EntityModelLayer COURIER_EXTRA = layer("courier_extra");
    public static final EntityModelLayer PARACHUTE = layer("parachute");
    public static final EntityModelLayer DRAGON = layer("dragon");

    private static EntityModelLayer layer(String name) {
        return new EntityModelLayer(HardestMod.id(name), "main");
    }

    public static void register() {
        EntityModelLayerRegistry.registerModelLayer(GOOSE, ModelDefs::goose);
        EntityModelLayerRegistry.registerModelLayer(RUNAWAY, ModelDefs::runawayBlock);
        EntityModelLayerRegistry.registerModelLayer(TAX, ModelDefs::taxInspector);
        EntityModelLayerRegistry.registerModelLayer(MIMIC, ModelDefs::mimic);
        EntityModelLayerRegistry.registerModelLayer(COURIER_EXTRA, ModelDefs::courierExtra);
        EntityModelLayerRegistry.registerModelLayer(PARACHUTE, ModelDefs::parachute);
        EntityModelLayerRegistry.registerModelLayer(DRAGON, ModelDefs::dragon);
    }

    private ModLayers() {
    }
}
