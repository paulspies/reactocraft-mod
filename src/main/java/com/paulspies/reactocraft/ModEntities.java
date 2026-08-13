package com.paulspies.reactocraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Our own mobs. Currently one: the irradiated cow Create Nuclear never shipped.
 *
 * 🔑 THE SPAWN EGG NEEDS NO TEXTURE. Vanilla draws every spawn egg from one shared model and tints
 * it with two colours, so a new egg costs two hex values and nothing else. Base is the average of
 * Paul's cow art, spots are toxic green.
 */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ReactoCraft.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<IrradiatedCow>> IRRADIATED_COW =
            ENTITIES.register("irradiated_cow", name -> EntityType.Builder
                    .<IrradiatedCow>of(IrradiatedCow::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.4F)
                    .eyeHeight(1.3F)
                    .clientTrackingRange(10)
                    .build(ReactoCraft.MODID + ":irradiated_cow"));

    public static final DeferredHolder<Item, Item> IRRADIATED_COW_SPAWN_EGG = ModItems.ITEMS.register(
            "irradiated_cow_spawn_egg",
            () -> new DeferredSpawnEggItem(IRRADIATED_COW, 0x666334, 0x8FE528, new Item.Properties()));

    /**
     * A vanilla cow's numbers, plus an attack.
     *
     * ⚠️ ATTACK_DAMAGE is not on the vanilla cow, because a cow has never needed to hit anything.
     * Without adding it here the retaliation goal runs and deals nothing at all.
     */
    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder cowAttributes() {
        return Cow.createAttributes()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 3.0D);
    }
}
