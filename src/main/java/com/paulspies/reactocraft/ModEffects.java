package com.paulspies.reactocraft;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The four effects. Amplifier 0 is level I, amplifier 1 is level II.
 *
 * Shielding percentages, read by the `rad` datapack, not enforced here:
 *   RAD_RESISTANCE  I = 50%   II = 75%   (a full anti-radiation armour set is already 100%)
 *   RAD_WEAKNESS    cancels resistance and makes an unprotected dose hit harder
 */
public final class ModEffects {
    private ModEffects() {}

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, ReactoCraft.MODID);

    public static final Holder<MobEffect> RAD_RESISTANCE = EFFECTS.register("rad_resistance",
            () -> new RadEffect(MobEffectCategory.BENEFICIAL, 0x3F76E4));

    public static final Holder<MobEffect> RAD_WEAKNESS = EFFECTS.register("rad_weakness",
            () -> new RadEffect(MobEffectCategory.HARMFUL, 0x6E7A8A));

    public static final Holder<MobEffect> RADIATION = EFFECTS.register("radiation",
            () -> new RadEffect(MobEffectCategory.HARMFUL, 0x00FF00));

    public static final Holder<MobEffect> WEAK_RADIATION = EFFECTS.register("weak_radiation",
            () -> new RadEffect(MobEffectCategory.HARMFUL, 0x9ACD32));
}
