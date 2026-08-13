package com.paulspies.reactocraft;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/**
 * A drinkable that hands back its container, the way a milk bucket hands back a bucket.
 *
 * The curing itself is NOT here. It lives in RadEngine's LivingEntityUseItemEvent.Finish hook
 * alongside milk and healing potions, so every cure in the mod is decided in one place against one
 * config rather than scattered across item classes.
 */
public class DrinkItem extends Item {
    private final Supplier<ItemStack> container;

    public DrinkItem(Properties properties, Supplier<ItemStack> container) {
        super(properties);
        this.container = container;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player) || player.getAbilities().instabuild) {
            return stack;
        }

        stack.shrink(1);
        ItemStack empty = container.get();

        // Only hand the container back into the slot if the drink is gone, otherwise it would
        // replace a partial stack. Same shape as vanilla's own potion and bucket handling.
        if (stack.isEmpty()) {
            return empty;
        }
        if (!player.getInventory().add(empty)) {
            player.drop(empty, false);
        }
        return stack;
    }
}
