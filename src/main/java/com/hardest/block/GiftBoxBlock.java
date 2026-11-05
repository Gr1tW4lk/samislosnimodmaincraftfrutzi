package com.hardest.block;

import com.hardest.entity.GooseEntity;
import com.hardest.entity.TaxInspectorEntity;
import com.hardest.net.Net;
import com.hardest.registry.ModBlocks;
import com.hardest.registry.ModEntities;
import com.hardest.registry.ModItems;
import com.hardest.registry.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Посылка от ИИгоря. Внутри может быть что угодно. Чаще всего — неприятности.
 */
public class GiftBoxBlock extends Block {
    private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 14, 15);

    public GiftBoxBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        ServerWorld sw = (ServerWorld) world;
        sw.removeBlock(pos, false);
        confetti(sw, pos);
        sw.playSound(null, pos, ModSounds.sfx("gift_open"), SoundCategory.BLOCKS, 1.5f, 1.0f);
        open(sw, pos, (ServerPlayerEntity) player);
        return ActionResult.CONSUME;
    }

    private static void confetti(ServerWorld sw, BlockPos pos) {
        Random r = sw.random;
        for (int i = 0; i < 6; i++) {
            DustParticleEffect d = new DustParticleEffect(new Vector3f(r.nextFloat(), r.nextFloat(), r.nextFloat()), 1.2f);
            sw.spawnParticles(d, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.5, 0.6, 0.5, 0.2);
        }
        sw.spawnParticles(ParticleTypes.FIREWORK, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.2, 0.3, 0.2, 0.15);
    }

    private static void give(ServerWorld sw, BlockPos pos, ItemStack stack) {
        ItemEntity e = new ItemEntity(sw, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, stack);
        e.setVelocity(0, 0.3, 0);
        sw.spawnEntity(e);
    }

    private static void msg(ServerPlayerEntity p, String text, Formatting color) {
        Net.alert(p, Net.Alert.INFO, new ItemStack(ModBlocks.GIFT_BOX), Text.literal("ПОСЫЛКА"), Text.literal(text).formatted(color));
    }

    public static void open(ServerWorld sw, BlockPos pos, ServerPlayerEntity p) {
        Random r = sw.random;
        int roll = r.nextInt(100);
        if (roll < 12) {
            give(sw, pos, new ItemStack(Items.DIAMOND, 3 + r.nextInt(4)));
            msg(p, "АЛМАЗЫ! ...Подозрительно.", Formatting.AQUA);
        } else if (roll < 22) {
            give(sw, pos, new ItemStack(Items.GOLDEN_APPLE, 2));
            give(sw, pos, new ItemStack(Items.IRON_INGOT, 8));
            msg(p, "Золотые яблоки и железо", Formatting.GOLD);
        } else if (roll < 30) {
            give(sw, pos, new ItemStack(ModItems.SLIPPER));
            msg(p, "Тапок! Мама одобряет.", Formatting.GOLD);
        } else if (roll < 34) {
            give(sw, pos, new ItemStack(ModItems.CTRL_Z));
            msg(p, "Клавиша Ctrl+Z! Одна отмена смерти.", Formatting.LIGHT_PURPLE);
        } else if (roll < 44) {
            // матрёшка
            sw.setBlockState(pos, ModBlocks.GIFT_BOX.getDefaultState());
            msg(p, "Внутри... ещё одна коробка.", Formatting.YELLOW);
        } else if (roll < 53) {
            for (int i = 0; i < 10; i++) {
                ChickenEntity c = EntityType.CHICKEN.create(sw);
                if (c == null) continue;
                c.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, r.nextFloat() * 360, 0);
                c.setVelocity((r.nextDouble() - 0.5) * 0.8, 0.6, (r.nextDouble() - 0.5) * 0.8);
                sw.spawnEntity(c);
            }
            msg(p, "10 куриц. Зачем? Потому что.", Formatting.WHITE);
        } else if (roll < 61) {
            for (int i = 0; i < 2; i++) {
                CreeperEntity c = EntityType.CREEPER.create(sw);
                if (c == null) continue;
                c.refreshPositionAndAngles(pos.getX() + 0.5 + i - 0.5, pos.getY(), pos.getZ() + 0.5, r.nextFloat() * 360, 0);
                c.initialize(sw, sw.getLocalDifficulty(pos), SpawnReason.EVENT, null, null);
                c.setTarget(p);
                sw.spawnEntity(c);
            }
            msg(p, "Два крипера. С любовью, отправитель неизвестен.", Formatting.GREEN);
        } else if (roll < 69) {
            BlockPos above = p.getBlockPos().up(7);
            if (sw.getBlockState(above).isAir()) {
                FallingBlockEntity anvil = FallingBlockEntity.spawnFromBlock(sw, above, Blocks.ANVIL.getDefaultState());
                anvil.setHurtEntities(2.0f, 40);
            }
            msg(p, "Подними голову.", Formatting.DARK_GRAY);
        } else if (roll < 78) {
            ItemStack dirt = new ItemStack(Items.DIRT);
            dirt.setCustomName(Text.literal("Премиум-земля (64 алмаза)").formatted(Formatting.GOLD));
            give(sw, pos, dirt);
            msg(p, "Премиум-земля. Поздравляю.", Formatting.GOLD);
        } else if (roll < 85) {
            GooseEntity g = ModEntities.GOOSE.create(sw);
            if (g != null) {
                g.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 0, 0);
                g.stealCooldown = 0;
                sw.spawnEntity(g);
                g.honk(1.0f);
            }
            msg(p, "ГУСЬ.", Formatting.WHITE);
        } else if (roll < 91) {
            sw.createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2.0f, World.ExplosionSourceType.NONE);
            msg(p, "Это был подарок от налоговой.", Formatting.RED);
        } else if (roll < 95) {
            TaxInspectorEntity.spawnFor(p);
            msg(p, "Вы получили подарок. Подарки облагаются налогом.", Formatting.RED);
        } else {
            give(sw, pos, new ItemStack(Items.CAKE));
            msg(p, "Торт. Он не ложь.", Formatting.LIGHT_PURPLE);
        }
    }
}
