package com.hardest.mobs;

import com.hardest.mixin.MobEntityAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.DyeableItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Зомби, прошедшие «курсы строительства»: строят столбы и мосты, чтобы добраться до игрока.
 */
public final class ZombieBuilders {
    public static final String TAG = "hard_builder";

    public static void makeBuilder(ZombieEntity z) {
        z.addCommandTag(TAG);
        ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
        ((DyeableItem) Items.LEATHER_HELMET).setColor(helmet, 0xF5C518);
        z.equipStack(EquipmentSlot.HEAD, helmet);
        z.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIRT, 64));
        z.setEquipmentDropChance(EquipmentSlot.HEAD, 0f);
        z.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.2f);
        z.setCustomName(Text.literal("Зомби-строитель").formatted(Formatting.YELLOW));
    }

    public static void addGoal(ZombieEntity z) {
        ((MobEntityAccessor) z).hardest$getGoalSelector().add(1, new BuildGoal(z));
    }

    static class BuildGoal extends Goal {
        private final ZombieEntity z;
        private int cooldown;
        private int stuck;
        private double lastDist;

        BuildGoal(ZombieEntity z) {
            this.z = z;
            this.setControls(EnumSet.noneOf(Control.class));
        }

        @Override
        public boolean canStart() {
            return z.getTarget() != null && z.getTarget().isAlive();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = z.getTarget();
            if (t == null) return;
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            World w = z.getWorld();
            double dy = t.getY() - z.getY();
            double horiz = Math.sqrt(Math.pow(t.getX() - z.getX(), 2) + Math.pow(t.getZ() - z.getZ(), 2));
            double dist = z.distanceTo(t);
            stuck = (Math.abs(dist - lastDist) < 0.05) ? stuck + 1 : 0;
            lastDist = dist;

            // столб: цель выше и рядом — прыгаем и ставим блок под себя
            if (dy > 1.5 && horiz < 5 && z.isOnGround()) {
                z.getJumpControl().setActive();
                cooldown = 6;
                return;
            }
            if (dy > 1.5 && horiz < 5 && !z.isOnGround() && z.getVelocity().y < 0.1) {
                BlockPos under = z.getBlockPos();
                if (place(w, under)) {
                    z.setPosition(z.getX(), under.getY() + 1.0, z.getZ());
                    cooldown = 8;
                }
                return;
            }
            // мост: застряли или впереди пропасть — кладём блок перед собой
            if (stuck > 20 || (z.isOnGround() && horiz > 1.5)) {
                Direction dir = Direction.getFacing(t.getX() - z.getX(), 0, t.getZ() - z.getZ());
                BlockPos front = z.getBlockPos().offset(dir);
                BlockPos below = front.down();
                if (w.getBlockState(below).isAir() && w.getBlockState(below.down()).isAir()) {
                    if (place(w, below)) cooldown = 10;
                } else if (stuck > 20 && w.getBlockState(front).isAir() && dy > 0.5) {
                    // ступенька вверх
                    if (place(w, front)) {
                        z.getJumpControl().setActive();
                        cooldown = 10;
                    }
                }
            }
        }

        private boolean place(World w, BlockPos pos) {
            BlockState s = w.getBlockState(pos);
            if (!s.isReplaceable()) return false;
            w.setBlockState(pos, Blocks.DIRT.getDefaultState());
            w.playSound(null, pos, SoundEvents.BLOCK_GRAVEL_PLACE, SoundCategory.HOSTILE, 1f, 1f);
            z.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            return true;
        }
    }

    private ZombieBuilders() {
    }
}
