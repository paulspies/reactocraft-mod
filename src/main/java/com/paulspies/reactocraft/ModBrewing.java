package com.paulspies.reactocraft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * The brewing stand recipes.
 *
 * addMix registers the drinkable form AND the splash and lingering forms in one call, exactly as
 * vanilla does, so gunpowder and dragon's breath work on these with no extra registration.
 */
public final class ModBrewing {
    private ModBrewing() {}

    private static final ResourceLocation URANIUM_POWDER =
            ResourceLocation.fromNamespaceAndPath("createnuclear", "uranium_powder");
    private static final ResourceLocation LEAD_NUGGET =
            ResourceLocation.fromNamespaceAndPath("createnuclear", "lead_nugget");

    @SubscribeEvent
    public static void registerBrewingRecipes(RegisterBrewingRecipesEvent event) {
        PotionBrewing.Builder builder = event.getBuilder();

        // Both ingredients come from Create Nuclear, which is a required dependency, so these
        // lookups cannot fall back to AIR without the mod having failed to load first.
        Item uraniumPowder = BuiltInRegistries.ITEM.get(URANIUM_POWDER);
        Item leadNugget = BuiltInRegistries.ITEM.get(LEAD_NUGGET);

        // Rad Resistance
        builder.addMix(Potions.AWKWARD, leadNugget, ModPotions.RAD_RESISTANCE);
        builder.addMix(ModPotions.RAD_RESISTANCE, Items.REDSTONE, ModPotions.LONG_RAD_RESISTANCE);
        builder.addMix(ModPotions.RAD_RESISTANCE, Items.GLOWSTONE_DUST, ModPotions.STRONG_RAD_RESISTANCE);
        builder.addMix(ModPotions.RAD_RESISTANCE, Items.FERMENTED_SPIDER_EYE, ModPotions.RAD_WEAKNESS);

        // Radiation
        builder.addMix(Potions.AWKWARD, uraniumPowder, ModPotions.RADIATION);
        builder.addMix(ModPotions.RADIATION, Items.REDSTONE, ModPotions.LONG_RADIATION);
        builder.addMix(ModPotions.RADIATION, Items.GLOWSTONE_DUST, ModPotions.STRONG_RADIATION);
        builder.addMix(ModPotions.RADIATION, Items.FERMENTED_SPIDER_EYE, ModPotions.WEAK_RADIATION);
    }
}
