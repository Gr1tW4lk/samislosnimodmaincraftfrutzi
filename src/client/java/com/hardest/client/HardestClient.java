package com.hardest.client;

import com.hardest.HardestMod;
import com.hardest.client.fx.DragonClientFx;
import com.hardest.client.fx.FreezeFrame;
import com.hardest.client.fx.PostFx;
import com.hardest.client.hud.Alerts;
import com.hardest.client.hud.Cards;
import com.hardest.client.hud.HardHud;
import com.hardest.client.render.CourierRenderer;
import com.hardest.client.render.DoppelgangerRenderer;
import com.hardest.client.render.DragonRenderer;
import com.hardest.client.render.GooseRenderer;
import com.hardest.client.render.MimicRenderer;
import com.hardest.client.render.ModLayers;
import com.hardest.client.render.ParachuteCowRenderer;
import com.hardest.client.render.RunawayBlockRenderer;
import com.hardest.client.render.SlipperRenderer;
import com.hardest.client.render.SniperRenderer;
import com.hardest.client.render.TaxInspectorRenderer;
import com.hardest.client.screen.AdScreen;
import com.hardest.client.screen.CaptchaScreen;
import com.hardest.client.screen.WastedScreen;
import com.hardest.client.sound.ClientSounds;
import com.hardest.item.HotPotatoItem;
import com.hardest.net.Net;
import com.hardest.registry.ModBlocks;
import com.hardest.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class HardestClient implements ClientModInitializer {
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onInitializeClient() {
        ModLayers.register();
        EntityRendererRegistry.register(ModEntities.GOOSE, GooseRenderer::new);
        EntityRendererRegistry.register(ModEntities.RUNAWAY_BLOCK, RunawayBlockRenderer::new);
        EntityRendererRegistry.register(ModEntities.TAX_INSPECTOR, TaxInspectorRenderer::new);
        EntityRendererRegistry.register(ModEntities.MIMIC, MimicRenderer::new);
        EntityRendererRegistry.register(ModEntities.COURIER, CourierRenderer::new);
        EntityRendererRegistry.register(ModEntities.DOPPELGANGER, DoppelgangerRenderer::new);
        EntityRendererRegistry.register(ModEntities.PARACHUTE_COW, ParachuteCowRenderer::new);
        EntityRendererRegistry.register(ModEntities.SNIPER, (EntityRendererFactory) SniperRenderer::new);
        EntityRendererRegistry.register(ModEntities.SLIPPER, SlipperRenderer::new);
        EntityRendererRegistry.register(ModEntities.WEB_SHOT, FlyingItemEntityRenderer::new);
        // новый Эндер-дракон вместо ванильного
        EntityRendererRegistry.register(EntityType.ENDER_DRAGON, DragonRenderer::new);

        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.GIFT_BOX, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.STRANGE_MUSHROOM, RenderLayer.getCutout());
        HotPotatoItem.ClientWorldHolder.supplier = () -> MinecraftClient.getInstance().world;

        registerNetworking();

        HudRenderCallback.EVENT.register(HardHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.world == null) return;
            ClientState.tick();
            PostFx.tick();
            ClientSounds.tick();
            DragonClientFx.tick(mc);
            if (!ClientState.active("freeze")) FreezeFrame.reset();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> {
            ClientState.reset();
            Alerts.clear();
            Cards.clear();
            PostFx.reset();
            ClientSounds.stopAll();
            FreezeFrame.reset();
        }));
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof DeathScreen) {
                ClientSounds.ui("wasted", 1.0f);
                WastedScreen.opened(mc.world != null && mc.world.getLevelProperties().isHardcore());
                ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> WastedScreen.render(ctx, mc.textRenderer, s.width, s.height, delta));
            }
        });
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (Registries.ITEM.getId(stack.getItem()).getNamespace().equals(HardestMod.MOD_ID)) {
                lines.add(Text.literal("Самый сложный мод").styled(st -> st.withColor(0x8A6FD8).withItalic(true)));
            }
        });
        AutoTest.init();
    }

    /** Открыть экран мода поверх игры: не трогаем экран смерти, корректно закрываем сундуки. */
    private static void openOver(MinecraftClient mc, Screen screen) {
        if (mc.player == null || mc.player.isDead() || mc.currentScreen instanceof DeathScreen) return;
        if (mc.currentScreen instanceof HandledScreen<?>) mc.player.closeHandledScreen();
        mc.setScreen(screen);
    }

    private static void registerNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(Net.LINE, (mc, handler, buf, sender) -> {
            String who = buf.readString();
            String line = buf.readString();
            mc.execute(() -> ClientState.line(who, line));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.FX, (mc, handler, buf, sender) -> {
            String id = buf.readString();
            int duration = buf.readVarInt();
            int param = buf.readVarInt();
            mc.execute(() -> {
                ClientState.fx(id, duration, param);
                if (duration > 0) {
                    switch (id) {
                        case "inverted", "australia", "potato" -> ClientSounds.ui("glitch_short", 0.8f);
                        case "loading" -> ClientSounds.ui("update_chime", 0.9f);
                        case "tiktok" -> ClientSounds.ui("tiktok_swoosh", 0.9f);
                        case "lag", "freeze" -> ClientSounds.ui("lag_stutter", 0.7f);
                        case "turbo" -> ClientSounds.ui("whoosh", 1.0f, 0.7f);
                        default -> {
                        }
                    }
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.ALERT, (mc, handler, buf, sender) -> {
            Net.Alert kind = buf.readEnumConstant(Net.Alert.class);
            ItemStack icon = buf.readItemStack();
            Text title = buf.readText();
            Text sub = buf.readText();
            mc.execute(() -> Alerts.add(kind, icon, title, sub));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.CARD, (mc, handler, buf, sender) -> {
            String title = buf.readString();
            String sub = buf.readString();
            int color = buf.readInt();
            int ticks = buf.readVarInt();
            boolean letterbox = buf.readBoolean();
            mc.execute(() -> Cards.show(title, sub, color, ticks, letterbox));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.AD, (mc, handler, buf, sender) -> {
            int ad = buf.readVarInt();
            mc.execute(() -> openOver(mc, new AdScreen(ad)));
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.CAPTCHA, (mc, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            int seed = buf.readVarInt();
            mc.execute(() -> openOver(mc, new CaptchaScreen(pos, seed)));
        });
    }
}
