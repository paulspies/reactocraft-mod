package com.paulspies.reactocraft;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Every tunable number lives here, in a SERVER config.
 *
 * That is the whole point of this class. The logic is in Java because raycasts and loops belong in
 * Java, but the balance numbers stay editable on the box. Change config\reactocraft-server.toml,
 * restart the server, done. No rebuild, and nobody has to reinstall a client jar to get a tweak.
 */
public final class RadConfig {
    private RadConfig() {}

    public static final ModConfigSpec SPEC;

    // --- timing ---
    public static final ModConfigSpec.IntValue DOSE_INTERVAL;

    // --- shielding ---
    public static final ModConfigSpec.IntValue RESISTANCE_I;
    public static final ModConfigSpec.IntValue RESISTANCE_II;
    public static final ModConfigSpec.IntValue SUIT_SHIELD;
    public static final ModConfigSpec.IntValue BLOCK_SHIELD_PER_SIDE;
    public static final ModConfigSpec.IntValue BLOCK_SHIELD_RANGE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SHIELDING_BLOCKS;

    // --- zones ---
    public static final ModConfigSpec.IntValue ZONE_RADIUS;

    // --- staging ---
    public static final ModConfigSpec.IntValue EXPOSURE_MAX;
    public static final ModConfigSpec.IntValue STAGE_NAUSEA_AT;
    public static final ModConfigSpec.IntValue STAGE_DAMAGE_AT;
    public static final ModConfigSpec.IntValue STAGE_BLIND_AT;
    public static final ModConfigSpec.DoubleValue DAMAGE_PER_DOSE;
    public static final ModConfigSpec.IntValue EXPOSURE_DECAY;
    public static final ModConfigSpec.IntValue WEAK_CAP;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("How often a dose is applied, in ticks. 20 ticks = 1 second.",
                  "Raising this is the cheapest way to reduce server cost.").push("timing");
        DOSE_INTERVAL = b.defineInRange("dose_interval_ticks", 60, 20, 1200);
        b.pop();

        b.comment("Shielding percentages. These add together and are capped at 100.").push("shielding");
        RESISTANCE_I = b.comment("Potion of Rad Resistance, level I")
                .defineInRange("resistance_i", 50, 0, 100);
        RESISTANCE_II = b.comment("Potion of Rad Resistance, level II")
                .defineInRange("resistance_ii", 75, 0, 100);
        SUIT_SHIELD = b.comment("A full four-piece anti-radiation armour set")
                .defineInRange("suit_shield", 100, 0, 100);
        BLOCK_SHIELD_PER_SIDE = b.comment(
                        "Shield percent per shielded side. Six sides are checked: up, down, and the four",
                        "horizontals. At 20, a fully sealed lead room is 120, which caps at 100.")
                .defineInRange("block_shield_per_side", 20, 0, 100);
        BLOCK_SHIELD_RANGE = b.comment("How many blocks out to look on each of the six sides")
                .defineInRange("block_shield_range", 8, 1, 32);
        SHIELDING_BLOCKS = b.comment("Blocks that count as shielding")
                .defineList("shielding_blocks",
                        List.of("createnuclear:lead_block", "createnuclear:reactor_casing"),
                        o -> o instanceof String);
        b.pop();

        b.comment("Radiation zones placed by /function rad:zone_*").push("zones");
        ZONE_RADIUS = b.comment("How close a zone entity has to be to dose you")
                .defineInRange("zone_radius", 8, 1, 64);
        b.pop();

        b.comment("Exposure builds while you take doses and decays when you don't.",
                  "The stages below are percentages of exposure_max.").push("staging");
        EXPOSURE_MAX = b.defineInRange("exposure_max", 100, 10, 10000);
        STAGE_NAUSEA_AT = b.comment("Dizziness and screen movement start here")
                .defineInRange("stage_nausea_at", 20, 0, 100);
        STAGE_DAMAGE_AT = b.comment("Health damage starts here")
                .defineInRange("stage_damage_at", 40, 0, 100);
        STAGE_BLIND_AT = b.comment("Blindness starts here")
                .defineInRange("stage_blind_at", 70, 0, 100);
        DAMAGE_PER_DOSE = b.comment("Half a heart is 1.0. Scales up with exposure past stage_damage_at.")
                .defineInRange("damage_per_dose", 1.0D, 0.0D, 100.0D);
        EXPOSURE_DECAY = b.comment("Exposure lost per interval when you are not being dosed")
                .defineInRange("exposure_decay", 2, 0, 1000);
        WEAK_CAP = b.comment(
                        "Ceiling on exposure, as a percent, when Weak Radiation is the only thing dosing you.",
                        "Keep this below stage_damage_at or a 'weak' potion will eventually kill someone.",
                        "Paul's spec was sick and dizzy, nothing worse.")
                .defineInRange("weak_radiation_cap", 35, 0, 100);
        b.pop();

        SPEC = b.build();
    }
}
