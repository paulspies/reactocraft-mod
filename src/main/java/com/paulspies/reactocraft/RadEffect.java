package com.paulspies.reactocraft;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * A mob effect with no behaviour of its own. It exists only to be a registry entry the `rad`
 * datapack can look for. MobEffect's constructor is protected, so a subclass is required even
 * though there is nothing to override.
 */
public class RadEffect extends MobEffect {
    public RadEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
