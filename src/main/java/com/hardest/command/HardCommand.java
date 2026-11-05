package com.hardest.command;

import com.hardest.dragon.DragonAttacks;
import com.hardest.dragon.DragonRage;
import com.hardest.events.CaveIn;
import com.hardest.events.ColdSystem;
import com.hardest.events.EventState;
import com.hardest.events.Trips;
import com.hardest.events.WorldEvents;
import com.hardest.net.Net;
import com.hardest.registry.ModEffects;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.function.Consumer;

/**
 * /hard — пульт для съёмки: вызвать любое событие или атаку дракона прямо сейчас.
 */
public final class HardCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> dispatcher.register(
                CommandManager.literal("hard")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("event")
                                .then(ev("tax", WorldEvents::startTax))
                                .then(ev("courier", WorldEvents::startCourier))
                                .then(ev("ad", p -> Net.ad(p, p.getRandom().nextInt(6))))
                                .then(ev("cowfall", p -> WorldEvents.startCowfall(p.getServer(), 40)))
                                .then(ev("anvils", p -> WorldEvents.startAnvils(p.getServer(), 30)))
                                .then(ev("bloodmoon", p -> WorldEvents.forceBloodMoon(p.getServer())))
                                .then(ev("lag", p -> WorldEvents.lagSpike(p, 120)))
                                .then(ev("loading_nether", p -> WorldEvents.loadingScreen(p, 1)))
                                .then(ev("loading_end", p -> WorldEvents.loadingScreen(p, 2)))
                                .then(ev("cavein", p -> CaveIn.trigger(p.getServerWorld(), p)))
                                .then(ev("cold", p -> p.addStatusEffect(new StatusEffectInstance(ModEffects.COLD, 20 * 60 * 5, 0, false, true, true))))
                                .then(ev("sneeze", ColdSystem::sneeze)))
                        .then(CommandManager.literal("trip")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .suggests((c, b) -> CommandSource.suggestMatching(Trips.IDS, b))
                                        .executes(c -> {
                                            Trips.apply(c.getSource().getPlayerOrThrow(), StringArgumentType.getString(c, "id"), 20 * 30);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("dragon")
                                .then(dr("meteors", (p, d) -> DragonAttacks.meteors(p.getServerWorld(), d, p, 6)))
                                .then(dr("roar", (p, d) -> DragonAttacks.roar(p.getServerWorld(), d, List.of(p))))
                                .then(dr("dive", (p, d) -> DragonAttacks.dive(p.getServerWorld(), d, p, DragonRage.state(d))))
                                .then(dr("spikes", (p, d) -> DragonAttacks.spikes(p.getServerWorld(), d, p, DragonRage.state(d).phase)))
                                .then(dr("vortex", (p, d) -> DragonAttacks.vortex(p.getServerWorld(), d, p, DragonRage.state(d).phase)))
                                .then(dr("bigmeteor", (p, d) -> DragonAttacks.bigMeteor(p.getServerWorld(), d, p)))
                                .then(dr("shockwave", (p, d) -> DragonAttacks.shockwave(p.getServerWorld(), d, p.getPos(), 20, 0.75, 6f, false)))
                                .then(dr("swarm", (p, d) -> DragonAttacks.swarm(p.getServerWorld(), p, 5)))
                                .then(dr("geese", (p, d) -> DragonAttacks.geese(p.getServerWorld(), p, 3)))
                                .then(dr("cows", (p, d) -> DragonAttacks.cows(p.getServerWorld(), p, 4)))
                                .then(dr("laser", (p, d) -> DragonAttacks.laser(p.getServerWorld(), d, p)))
                                .then(dr("crystals", (p, d) -> DragonRage.respawnCrystals(p.getServerWorld(), 4)))
                                .then(dr("perch", (p, d) -> d.getPhaseManager().setPhase(PhaseType.LANDING_APPROACH)))
                                .then(dr("info", HardCommand::dragonInfo))
                                .then(dr("phase2", (p, d) -> d.setHealth(d.getMaxHealth() * 0.55f)))
                                .then(dr("phase3", (p, d) -> d.setHealth(d.getMaxHealth() * 0.25f))))
                        .then(CommandManager.literal("fx")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .suggests((c, b) -> CommandSource.suggestMatching(new String[]{"inverted", "tiktok", "loading", "lag", "glitch",
                                                "australia", "potato", "ctrlz", "shake", "bloodmoon", "turbo", "sneeze"}, b))
                                        .then(CommandManager.argument("seconds", IntegerArgumentType.integer(0, 600)).executes(c -> {
                                            Net.fx(c.getSource().getPlayerOrThrow(), StringArgumentType.getString(c, "id"), IntegerArgumentType.getInteger(c, "seconds") * 20, 1);
                                            return 1;
                                        }))))
                        .then(CommandManager.literal("timers").executes(HardCommand::timers))
        ));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> ev(String name, Consumer<ServerPlayerEntity> action) {
        return CommandManager.literal(name).executes(c -> {
            action.accept(c.getSource().getPlayerOrThrow());
            return 1;
        });
    }

    private interface DragonAction {
        void run(ServerPlayerEntity p, EnderDragonEntity d);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> dr(String name, DragonAction action) {
        return CommandManager.literal(name).executes(c -> {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
            EnderDragonEntity d = nearestDragon(p.getServerWorld(), p);
            if (d == null) {
                c.getSource().sendError(Text.literal("Рядом нет дракона (нужно быть в Крае)"));
                return 0;
            }
            action.run(p, d);
            return 1;
        });
    }

    private static EnderDragonEntity nearestDragon(ServerWorld w, ServerPlayerEntity p) {
        EnderDragonEntity best = null;
        for (EnderDragonEntity d : w.getAliveEnderDragons()) {
            if (best == null || d.squaredDistanceTo(p) < best.squaredDistanceTo(p)) best = d;
        }
        return best;
    }

    /** Где голова дракона относительно земли — чтобы проверить, что её можно достать мечом. */
    private static void dragonInfo(ServerPlayerEntity p, EnderDragonEntity d) {
        ServerWorld w = p.getServerWorld();
        Box head = d.head.getBoundingBox();
        int ground = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(head.getCenter().x), (int) Math.floor(head.getCenter().z));
        String s = String.format("Дракон: фаза %s, HP %.0f/%.0f, тело y=%.1f; голова y=%.1f..%.1f, земля под головой y=%d",
                d.getPhaseManager().getCurrent().getType(), d.getHealth(), d.getMaxHealth(), d.getY(), head.minY, head.maxY, ground);
        p.sendMessage(Text.literal(s).formatted(Formatting.AQUA), false);
        com.hardest.HardestMod.LOG.info("[dragon-info] {}", s);
    }

    private static int timers(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        EventState.PlayerData d = EventState.get(p.getServer()).get(p.getUuid());
        long now = p.getServer().getOverworld().getTime();
        String s = "Налоговая через " + (d.nextTax - now) / 20 + " с, курьер через " + (d.nextCourier - now) / 20
                + " с, реклама через " + (d.nextAd - now) / 20 + " с. Кровавая луна: " + (WorldEvents.isBloodMoon() ? "СЕЙЧАС" : "каждую 3-ю ночь");
        c.getSource().sendFeedback(() -> Text.literal(s).formatted(Formatting.YELLOW), false);
        return 1;
    }

    private HardCommand() {
    }
}
