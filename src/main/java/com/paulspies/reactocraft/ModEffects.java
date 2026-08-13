package com.paulspies.reactocraft;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The five effects, and they split into two jobs.
 *
 * SOURCES say you are being irradiated right now. They tick down normally and stop when they end.
 *   RADIATION       from the potion or a zone. Amplifier 1 is the strong variant.
 *   WEAK_RADIATION  the fermented spider eye branch. Enough to make you dizzy, never worse.
 *
 * STATE says how poisoned you already are. It does not tick down.
 *   CONTAMINATION   infinite, and its LEVEL is the stage you have reached, so the HUD reads
 *                   "Contamination IV" with an infinity marker. Only milk clears it.
 *
 * SHIELDING modifies incoming dose.
 *   RAD_RESISTANCE  I and II, percentages in the config
 *   RAD_WEAKNESS    cancels every protection and doubles what lands
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

    /** The state, not a source. Level = stage reached. Infinite until milk. */
    public static final Holder<MobEffect> CONTAMINATION = EFFECTS.register("contamination",
            () -> new RadEffect(MobEffectCategory.HARMFUL, 0x7FA61E));
}
