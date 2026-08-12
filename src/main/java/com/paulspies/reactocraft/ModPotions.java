package com.paulspies.reactocraft;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The eight potions. Durations follow vanilla's pattern exactly: base 3:00, redstone extends to
 * 8:00, glowstone trades duration for potency and drops to 1:30.
 *
 * The second constructor argument on the long/strong variants is the shared display name, which is
 * how vanilla makes "Potion of Fire Resistance" read the same whether it is extended or not.
 */
public final class ModPotions {
    private ModPotions() {}

    private static final int THREE_MIN = 3600;   // 3:00
    private static final int EIGHT_MIN = 9600;   // 8:00
    private static final int NINETY_SEC = 1800;  // 1:30

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, ReactoCraft.MODID);

    // Rad Resistance — awkward + lead nugget
    public static final Holder<Potion> RAD_RESISTANCE = POTIONS.register("rad_resistance",
            () -> new Potion(new MobEffectInstance(ModEffects.RAD_RESISTANCE, THREE_MIN)));

    public static final Holder<Potion> LONG_RAD_RESISTANCE = POTIONS.register("long_rad_resistance",
            () -> new Potion("rad_resistance", new MobEffectInstance(ModEffects.RAD_RESISTANCE, EIGHT_MIN)));

    public static final Holder<Potion> STRONG_RAD_RESISTANCE = POTIONS.register("strong_rad_resistance",
            () -> new Potion("rad_resistance", new MobEffectInstance(ModEffects.RAD_RESISTANCE, NINETY_SEC, 1)));

    public static final Holder<Potion> RAD_WEAKNESS = POTIONS.register("rad_weakness",
            () -> new Potion(new MobEffectInstance(ModEffects.RAD_WEAKNESS, THREE_MIN)));

    // Radiation — awkward + uranium powder
    public static final Holder<Potion> RADIATION = POTIONS.register("radiation",
            () -> new Potion(new MobEffectInstance(ModEffects.RADIATION, THREE_MIN)));

    public static final Holder<Potion> LONG_RADIATION = POTIONS.register("long_radiation",
            () -> new Potion("radiation", new MobEffectInstance(ModEffects.RADIATION, EIGHT_MIN)));

    public static final Holder<Potion> STRONG_RADIATION = POTIONS.register("strong_radiation",
            () -> new Potion("radiation", new MobEffectInstance(ModEffects.RADIATION, NINETY_SEC, 1)));

    public static final Holder<Potion> WEAK_RADIATION = POTIONS.register("weak_radiation",
            () -> new Potion(new MobEffectInstance(ModEffects.WEAK_RADIATION, NINETY_SEC)));
}
