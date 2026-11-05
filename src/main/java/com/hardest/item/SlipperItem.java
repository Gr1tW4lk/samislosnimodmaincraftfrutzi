package com.hardest.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.hardest.entity.SlipperEntity;
import com.hardest.registry.ModSounds;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Мамин тапок. Бьёт больно, летит бумерангом, по ИИгорю — тройной урон.
 */
public class SlipperItem extends Item {
    private final Multimap<EntityAttribute, EntityAttributeModifier> modifiers;

    public SlipperItem(Settings settings) {
        super(settings);
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> b = ImmutableMultimap.builder();
        b.put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(ATTACK_DAMAGE_MODIFIER_ID, "Тапок", 5.0, EntityAttributeModifier.Operation.ADDITION));
        b.put(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(ATTACK_SPEED_MODIFIER_ID, "Тапок", -2.0, EntityAttributeModifier.Operation.ADDITION));
        this.modifiers = b.build();
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getAttributeModifiers(slot);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            SlipperEntity e = new SlipperEntity(world, user, stack);
            e.setVelocity(user, user.getPitch(), user.getYaw(), 0f, 1.6f, 0.5f);
            world.spawnEntity(e);
            world.playSound(null, user.getBlockPos(), ModSounds.sfx("whoosh"), SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
        user.setStackInHand(hand, ItemStack.EMPTY);
        user.getItemCooldownManager().set(this, 10);
        return TypedActionResult.success(ItemStack.EMPTY, world.isClient);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.damage(1, attacker, e -> e.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND));
        target.getWorld().playSound(null, target.getBlockPos(), ModSounds.sfx("slap"), SoundCategory.PLAYERS, 1.2f, 0.9f + target.getRandom().nextFloat() * 0.2f);
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("hardest.lore.slipper.1").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("hardest.lore.slipper.2").formatted(Formatting.GRAY, Formatting.ITALIC));
        tooltip.add(Text.translatable("hardest.lore.slipper.3").formatted(Formatting.LIGHT_PURPLE));
    }
}
