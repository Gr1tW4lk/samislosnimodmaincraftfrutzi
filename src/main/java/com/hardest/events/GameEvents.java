package com.hardest.events;

import com.hardest.dragon.DragonRage;
import com.hardest.entity.MimicEntity;
import com.hardest.entity.RunawayBlockEntity;
import com.hardest.mobs.MobTweaks;
import com.hardest.net.Net;
import com.hardest.registry.ModItems;
import com.hardest.registry.ModSounds;
import com.hardest.util.Achievements;
import com.hardest.util.PositionHistory;
import com.hardest.util.Tasks;
import com.hardest.util.TempBlocks;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.BrewingStandBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.SmithingTableBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GameEvents {
    private static final String[] FOOD = {"food1", "food2", "food3", "food4", "food5"};
    private static final Map<UUID, Long> FOOD_COOLDOWN = new HashMap<>();
    private static final Map<UUID, BlockPos> CAPTCHA_PENDING = new HashMap<>();
    private static boolean bypass = false;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PositionHistory.tick(server);
            Tasks.tick(server);
            WorldEvents.tick(server);
            ColdSystem.tick(server);
            TempBlocks.tick(server);
            DragonRage.tick(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> WorldEvents.onJoin(handler.player)));
        ServerLifecycleEvents.SERVER_STOPPING.register(TempBlocks::clearAll);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            Tasks.clear();
            WorldEvents.onServerStopped();
            DragonRage.clear();
        });

        PlayerBlockBreakEvents.BEFORE.register(GameEvents::onBreak);
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return;
            if (state.isIn(BlockTags.LOGS)) Timber.onLogBroken((ServerWorld) world, sp, pos);
            else CaveIn.onStoneBroken((ServerWorld) world, sp, pos, state);
        });
        UseBlockCallback.EVENT.register(GameEvents::onUseBlock);
        UseItemCallback.EVENT.register(GameEvents::onUseItem);

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !(entity instanceof ServerPlayerEntity p) || !tryCtrlZ(p));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity p) {
                Achievements.grant(p, "wasted");
                PositionHistory.clear(p);
                ColdSystem.clear(p.getUuid());
            }
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p && source.getAttacker() instanceof EndermanEntity && p.getRandom().nextFloat() < 0.35f) {
                Tasks.schedule(p.getServer(), 1, () -> enderTeleport(p));
            }
            return true;
        });

        EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> {
            if (!(player instanceof ServerPlayerEntity sp)) return null;
            if (WorldEvents.isBloodMoon()) {
                Net.alert(sp, Net.Alert.DANGER, Items.RED_BED, "СПАТЬ НЕЛЬЗЯ", "В Кровавую луну спят только мёртвые");
                return PlayerEntity.SleepFailureReason.OTHER_PROBLEM;
            }
            if (sp.isCreative() || sp.getInventory().count(ModItems.GOOSE_PILLOW) > 0) return null;
            // кровать требует плату
            int slot = sp.getInventory().getSlotWithStack(new ItemStack(Items.DIAMOND));
            if (slot >= 0) {
                sp.getInventory().getStack(slot).decrement(1);
                Achievements.grant(sp, "sleep");
                Net.alert(sp, Net.Alert.INFO, Items.DIAMOND, "−1 АЛМАЗ ЗА СОН", "С гусиной подушкой спать бесплатно");
                return null;
            }
            Net.alert(sp, Net.Alert.INFO, Items.RED_BED, "СОН СТОИТ 1 АЛМАЗ", "Или скрафти гусиную подушку");
            return PlayerEntity.SleepFailureReason.OTHER_PROBLEM;
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            MobTweaks.onLoad(entity, world);
            if (entity instanceof ItemEntity ie && ie.getStack().isOf(ModItems.HOT_POTATO)) {
                // выбросил картошку — она взрывается сразу
                Tasks.schedule(world.getServer(), 30, () -> {
                    if (ie.isAlive()) {
                        world.createExplosion(null, ie.getX(), ie.getY(), ie.getZ(), 2.5f, World.ExplosionSourceType.NONE);
                        ie.discard();
                    }
                });
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(Net.CAPTCHA_RESULT, (server, player, handler, buf, sender) -> {
            boolean ok = buf.readBoolean();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> onCaptchaResult(player, ok, pos));
        });
    }

    // ---------------------------------------------------------------- блоки-беглецы

    private static boolean onBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity be) {
        if (world.isClient || !(player instanceof ServerPlayerEntity sp) || sp.isCreative()) return true;
        if (!RunawayBlockEntity.canRunAway(state)) return true;
        boolean diamond = state.isIn(BlockTags.DIAMOND_ORES);
        float chance;
        if (diamond) chance = 0.6f;
        else if (state.isIn(BlockTags.EMERALD_ORES) || state.isIn(BlockTags.GOLD_ORES)) chance = 0.35f;
        else if (state.isIn(BlockTags.IRON_ORES) || state.isIn(BlockTags.COAL_ORES) || state.isIn(BlockTags.REDSTONE_ORES)
                || state.isIn(BlockTags.LAPIS_ORES) || state.isIn(BlockTags.COPPER_ORES)) chance = 0.15f;
        else chance = 0.02f;
        if (world.random.nextFloat() >= chance) return true;
        RunawayBlockEntity.spawnFrom((ServerWorld) world, pos, state, player);
        if (diamond) Achievements.grant(sp, "diamond_run");
        return false;
    }

    // ---------------------------------------------------------------- мимики и капча

    private static boolean needsCaptcha(BlockState state) {
        return state.getBlock() instanceof EnchantingTableBlock || state.getBlock() instanceof AnvilBlock
                || state.getBlock() instanceof EnderChestBlock || state.getBlock() instanceof BrewingStandBlock
                || state.getBlock() instanceof SmithingTableBlock;
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (world.isClient || bypass || !(player instanceof ServerPlayerEntity sp) || sp.isSpectator() || hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        ServerWorld sw = (ServerWorld) world;
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (player.isSneaking() && !player.getMainHandStack().isEmpty()) return ActionResult.PASS;

        if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE
                && world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            boolean lootChest = chest.createNbt().contains("LootTable");
            if (lootChest && world.random.nextFloat() < 0.3f) {
                chest.checkLootInteraction(player);
                MimicEntity.fromChest(sw, pos, chest, state.get(ChestBlock.FACING), true);
                Net.alert(sp, Net.Alert.DANGER, Items.CHEST, "ЭТО МИМИК!", "Сундук кусается");
                return ActionResult.FAIL;
            }
        }

        if (needsCaptcha(state) && world.random.nextFloat() < 0.6f) {
            CAPTCHA_PENDING.put(sp.getUuid(), pos.toImmutable());
            Net.captcha(sp, pos, world.random.nextInt(1_000_000));
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    private static void onCaptchaResult(ServerPlayerEntity p, boolean ok, BlockPos pos) {
        BlockPos expected = CAPTCHA_PENDING.remove(p.getUuid());
        if (expected == null || !expected.equals(pos)) return;
        ServerWorld w = p.getServerWorld();
        if (ok) {
            Achievements.grant(p, "captcha_ok");
            w.playSound(null, p.getBlockPos(), ModSounds.sfx("captcha_ok"), SoundCategory.MASTER, 1f, 1f);
            BlockState state = w.getBlockState(pos);
            if (p.squaredDistanceTo(Vec3d.ofCenter(pos)) < 64) {
                bypass = true;
                try {
                    state.onUse(w, p, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
                } finally {
                    bypass = false;
                }
            }
        } else {
            Achievements.grant(p, "captcha_fail");
            w.playSound(null, p.getBlockPos(), ModSounds.sfx("captcha_fail"), SoundCategory.MASTER, 1f, 1f);
            Net.alert(p, Net.Alert.DANGER, Items.LIGHTNING_ROD, "ВЫ РОБОТ", "Роботов бьёт молнией");
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
            if (bolt != null) {
                bolt.refreshPositionAfterTeleport(p.getX() + 1.5, p.getY(), p.getZ() + 1.5);
                w.spawnEntity(bolt);
            }
            p.damage(p.getDamageSources().lightningBolt(), 4f);
        }
    }

    // ---------------------------------------------------------------- кричащая еда

    private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient || !stack.isFood() || !player.canConsume(stack.getItem().getFoodComponent().isAlwaysEdible())) {
            return TypedActionResult.pass(stack);
        }
        long now = world.getTime();
        if (now - FOOD_COOLDOWN.getOrDefault(player.getUuid(), 0L) > 50) {
            FOOD_COOLDOWN.put(player.getUuid(), now);
            String line = FOOD[world.random.nextInt(FOOD.length)];
            world.playSound(null, player.getBlockPos(), ModSounds.voice(line), SoundCategory.PLAYERS, 1.2f, 0.95f + world.random.nextFloat() * 0.15f);
            if (player instanceof ServerPlayerEntity sp) {
                Net.alert(sp, Net.Alert.FUNNY, stack.copy(), stack.getName().copy(), Text.literal("«").append(Text.translatable("hardest.voice." + line)).append("»"));
            }
        }
        return TypedActionResult.pass(stack);
    }

    // ---------------------------------------------------------------- эндермены

    private static void enderTeleport(ServerPlayerEntity p) {
        if (!p.isAlive()) return;
        double ox = p.getX(), oy = p.getY(), oz = p.getZ();
        for (int i = 0; i < 16; i++) {
            double x = ox + (p.getRandom().nextDouble() - 0.5) * 16;
            double y = MathHelper.clamp(oy + (p.getRandom().nextInt(16) - 8), p.getWorld().getBottomY(), p.getWorld().getTopY() - 1);
            double z = oz + (p.getRandom().nextDouble() - 0.5) * 16;
            if (p.teleport(x, y, z, true)) {
                p.getWorld().playSound(null, ox, oy, oz, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 0.8f);
                Net.alert(p, Net.Alert.BOSS, Items.ENDER_PEARL, "ЭНДЕРМЕН УТАЩИЛ ТЕБЯ", "");
                return;
            }
        }
    }

    // ---------------------------------------------------------------- Ctrl+Z

    private static boolean tryCtrlZ(ServerPlayerEntity p) {
        var inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(ModItems.CTRL_Z)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return false;
        inv.getStack(slot).decrement(1);
        Vec3d back = PositionHistory.get(p, 100);
        float hp = Math.max(8f, PositionHistory.health(p, 100));
        p.setHealth(Math.min(p.getMaxHealth(), hp));
        p.extinguish();
        p.setFireTicks(0);
        p.fallDistance = 0;
        p.clearStatusEffects();
        if (back != null) p.networkHandler.requestTeleport(back.x, back.y, back.z, p.getYaw(), p.getPitch());
        ServerWorld w = p.getServerWorld();
        w.playSound(null, p.getBlockPos(), ModSounds.sfx("rewind"), SoundCategory.MASTER, 1.5f, 1f);
        w.spawnParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 60, 0.4, 0.8, 0.4, 0.05);
        Net.fx(p, "ctrlz", 30, 0);
        Net.alert(p, Net.Alert.GOOD, ModItems.CTRL_Z, "CTRL+Z", "Смерть отменена. Откат на 5 секунд");
        Achievements.grant(p, "ctrl_z");
        return true;
    }

    private GameEvents() {
    }
}
