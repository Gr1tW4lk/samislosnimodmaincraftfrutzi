package com.hardest.client;

import com.hardest.HardestMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Сценарий автопроверки для разработки: создаёт мир, выполняет команды и делает скриншоты.
 * Включается только флагом -Dhardest.autotest=путь/к/сценарию — у игроков ничего не делает.
 */
public final class AutoTest {
    private static final String WORLD = "hardest_test";
    private static List<String> script = new ArrayList<>();
    private static int line;
    private static int wait;
    private static boolean worldStarted;
    private static KeyBinding held;
    private static int heldTicks;

    public static void init() {
        String file = System.getProperty("hardest.autotest");
        if (file == null || file.isEmpty()) return;
        try {
            script = Files.readAllLines(Path.of(file));
        } catch (Exception e) {
            HardestMod.LOG.error("autotest: не прочитан сценарий {}", file, e);
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
    }

    private static void tick(MinecraftClient mc) {
        if (held != null && --heldTicks <= 0) {
            held.setPressed(false);
            held = null;
        }
        if (mc.world == null || mc.player == null) {
            if (!worldStarted && mc.currentScreen instanceof TitleScreen && mc.getOverlay() == null) {
                worldStarted = true;
                startWorld(mc);
            }
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        while (line < script.size()) {
            String raw = script.get(line++).trim();
            if (raw.isEmpty() || raw.startsWith("#")) continue;
            HardestMod.LOG.info("autotest> {}", raw);
            String[] p = raw.split(" ", 2);
            String arg = p.length > 1 ? p[1] : "";
            switch (p[0]) {
                case "wait" -> {
                    wait = Integer.parseInt(arg);
                    return;
                }
                case "cmd" -> runCommand(mc, arg);
                case "shot" -> {
                    ScreenshotRecorder.saveScreenshot(mc.runDirectory, arg + ".png", mc.getFramebuffer(), t -> {
                    });
                    wait = 2;
                    return;
                }
                case "look" -> {
                    String[] a = arg.split(" ");
                    mc.player.setYaw(Float.parseFloat(a[0]));
                    mc.player.setPitch(Float.parseFloat(a[1]));
                }
                case "lookat" -> {
                    net.minecraft.entity.Entity best = null;
                    double bestD = Double.MAX_VALUE;
                    for (net.minecraft.entity.Entity e : mc.world.getEntities()) {
                        if (!net.minecraft.registry.Registries.ENTITY_TYPE.getId(e.getType()).toString().equals(arg)) continue;
                        double d = e.squaredDistanceTo(mc.player);
                        if (d < bestD) {
                            bestD = d;
                            best = e;
                        }
                    }
                    if (best != null) {
                        double dx = best.getX() - mc.player.getX();
                        double dy = best.getY() + best.getHeight() * 0.5 - mc.player.getEyeY();
                        double dz = best.getZ() - mc.player.getZ();
                        mc.player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90));
                        mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
                    }
                }
                case "hud" -> mc.options.hudHidden = arg.equals("off");
                case "screen" -> mc.setScreen(null);
                case "open" -> mc.setScreen(switch (arg) {
                    case "captcha" -> new com.hardest.client.screen.CaptchaScreen(mc.player.getBlockPos(), 7);
                    case "advancements" -> new net.minecraft.client.gui.screen.advancement.AdvancementsScreen(mc.player.networkHandler.getAdvancementHandler());
                    default -> new net.minecraft.client.gui.screen.ingame.InventoryScreen(mc.player);
                });
                case "press" -> {
                    String[] a = arg.split(" ");
                    held = switch (a[0]) {
                        case "forward" -> mc.options.forwardKey;
                        case "back" -> mc.options.backKey;
                        case "left" -> mc.options.leftKey;
                        case "right" -> mc.options.rightKey;
                        case "jump" -> mc.options.jumpKey;
                        case "attack" -> mc.options.attackKey;
                        default -> mc.options.useKey;
                    };
                    held.setPressed(true);
                    heldTicks = Integer.parseInt(a[1]);
                }
                case "click" -> {
                    String[] a = arg.split(" ");
                    if (mc.currentScreen != null) mc.currentScreen.mouseClicked(Double.parseDouble(a[0]), Double.parseDouble(a[1]), 0);
                }
                case "respawn" -> {
                    mc.player.requestRespawn();
                    mc.setScreen(null);
                }
                case "quit" -> mc.scheduleStop();
                default -> HardestMod.LOG.warn("autotest: неизвестная команда {}", raw);
            }
        }
    }

    private static void runCommand(MinecraftClient mc, String command) {
        IntegratedServer server = mc.getServer();
        if (server == null || mc.player == null) return;
        var uuid = mc.player.getUuid();
        server.execute(() -> {
            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(uuid);
            if (sp != null) server.getCommandManager().executeWithPrefix(sp.getCommandSource().withLevel(4), command);
        });
    }

    private static void startWorld(MinecraftClient mc) {
        try {
            if (Files.exists(mc.getLevelStorage().getSavesDirectory().resolve(WORLD))) {
                mc.createIntegratedServerLoader().start(new TitleScreen(), WORLD);
            } else {
                LevelInfo info = new LevelInfo(WORLD, GameMode.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), DataConfiguration.SAFE_MODE);
                mc.createIntegratedServerLoader().createAndStart(WORLD, info, new GeneratorOptions(20261024L, true, false), WorldPresets::createDemoOptions);
            }
        } catch (Exception e) {
            HardestMod.LOG.error("autotest: не удалось создать мир", e);
        }
    }

    private AutoTest() {
    }
}
