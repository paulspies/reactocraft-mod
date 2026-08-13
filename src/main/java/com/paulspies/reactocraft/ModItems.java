package com.paulspies.reactocraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Chocolate milk, in a bucket and in a bottle.
 *
 * Paul's idea, 2026-08-13. It fills a real gap in the cure ladder, which previously jumped straight
 * from milk (useless past 3:00) to a Potion of Healing (wipes everything at any stage):
 *
 *     milk              cures to 3:00, then 1:00 a drink
 *     chocolate milk    cures to 5:00, then 3:00 a drink
 *     potion of healing wipes it outright
 *
 * ⚠️ Both stack to 1, like every other filled container in the game. A stack of 16 buckets of milk
 * has never been a thing and these should not be the exception.
 */
public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ReactoCraft.MODID);

    public static final DeferredHolder<Item, Item> CHOCOLATE_MILK_BUCKET = ITEMS.register(
            "chocolate_milk_bucket",
            () -> new DrinkItem(new Item.Properties().stacksTo(1), () -> new ItemStack(Items.BUCKET)));

    public static final DeferredHolder<Item, Item> CHOCOLATE_MILK_BOTTLE = ITEMS.register(
            "chocolate_milk_bottle",
            () -> new DrinkItem(new Item.Properties().stacksTo(1), () -> new ItemStack(Items.GLASS_BOTTLE)));
}
