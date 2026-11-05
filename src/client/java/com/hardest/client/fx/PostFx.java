package com.hardest.client.fx;

import com.hardest.client.ClientState;
import com.hardest.mixin.client.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/**
 * Пост-шейдеры Minecraft (переворот экрана, инверсия, пиксели) — для трипов и глюков.
 */
public final class PostFx {
    private static final String[] GLITCH = {"deconverge", "invert", "wobble", "bits", "ntsc", "flip", "green", "phosphor"};
    private static final Random RNG = Random.create();
    private static String current;
    private static String glitchPick;
    private static long nextGlitchSwitch;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            current = null;
            return;
        }
        String want = null;
        // ПОТРАЧЕНО: мир становится серым, как в GTA
        if (mc.player != null && mc.player.isDead()) want = "desaturate";
        else if (ClientState.active("australia")) want = "flip";
        else if (ClientState.active("potato")) want = "bits";
        else if (ClientState.active("glitch")) {
            if (ClientState.ticks >= nextGlitchSwitch) {
                // иногда «нормальная» картинка, чтобы было ещё неприятнее
                glitchPick = RNG.nextInt(4) == 0 ? null : GLITCH[RNG.nextInt(GLITCH.length)];
                int len = "flip".equals(glitchPick) ? 8 : 10 + RNG.nextInt(25);
                nextGlitchSwitch = ClientState.ticks + len;
            }
            want = glitchPick;
        }
        if (want == null ? current == null : want.equals(current)) return;
        apply(mc, want);
    }

    private static void apply(MinecraftClient mc, String name) {
        current = name;
        if (name == null) {
            mc.gameRenderer.disablePostProcessor();
        } else {
            ((GameRendererAccessor) mc.gameRenderer).hardest$loadPostProcessor(new Identifier("shaders/post/" + name + ".json"));
        }
    }

    public static void reset() {
        if (current != null) {
            MinecraftClient.getInstance().gameRenderer.disablePostProcessor();
            current = null;
        }
    }

    private PostFx() {
    }
}
