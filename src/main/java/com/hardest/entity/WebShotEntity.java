package com.hardest.entity;

import com.hardest.registry.ModEntities;
import com.hardest.util.TempBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Комок паутины, которым плюётся паук. Попал — стоишь в паутине.
 */
public class WebShotEntity extends ThrownItemEntity {
    public WebShotEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    public WebShotEntity(World world, LivingEntity owner) {
        super(ModEntities.WEB_SHOT, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.COBWEB;
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        Entity e = hit.getEntity();
        if (e == getOwner()) return;
        if (e instanceof LivingEntity le) {
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
            web(e.getBlockPos());
        }
        discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        web(hit.getBlockPos().offset(hit.getSide()));
        discard();
    }

    private void web(BlockPos pos) {
        if (getWorld() instanceof ServerWorld sw && sw.getBlockState(pos).isReplaceable() && sw.getFluidState(pos).isEmpty()) {
            sw.setBlockState(pos, Blocks.COBWEB.getDefaultState());
            TempBlocks.add(sw, pos, Blocks.COBWEB, 20 * 25);
        }
    }
}
