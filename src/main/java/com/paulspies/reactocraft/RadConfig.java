package com.paulspies.reactocraft;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Every tunable number lives here, in a SERVER config.
 *
 * That is the whole point of this class. The logic is in Java because raycasts and loops belong in
 * Java, but the balance numbers stay editable on the box. Change config\reactocraft-server.toml,
 * restart the server, done. No rebuild, and nobody has to reinstall a client jar to get a tweak.
 *
 * As of 2026-08-12 this file also carries what the Radioactive mod used to own. That mod has been
 * retired to mods-disabled because two systems both owning radiation is what caused the respawn
 * death loop: our engine listened to these numbers and Radioactive's inventory radiation did not.
 */
public final class RadConfig {
    private RadConfig() {}

    public static final ModConfigSpec SPEC;

    // --- timing ---
    public static final ModConfigSpec.IntValue DOSE_INTERVAL;

    // --- rates: how fast each source fills the exposure clock ---
    public static final ModConfigSpec.DoubleValue RATE_RADIATION;
    public static final ModConfigSpec.DoubleValue RATE_RADIATION_STRONG;
    public static final ModConfigSpec.DoubleValue RATE_WEAK_RADIATION;
    public static final ModConfigSpec.IntValue REFERENCE_STRENGTH;

    // --- shielding ---
    public static final ModConfigSpec.IntValue RESISTANCE_I;
    public static final ModConfigSpec.IntValue RESISTANCE_II;
    public static final ModConfigSpec.IntValue SUIT_HELMET;
    public static final ModConfigSpec.IntValue SUIT_CHESTPLATE;
    public static final ModConfigSpec.IntValue SUIT_LEGGINGS;
    public static final ModConfigSpec.IntValue SUIT_BOOTS;
    public static final ModConfigSpec.IntValue BLOCK_SHIELD_PER_SIDE;
    public static final ModConfigSpec.IntValue BLOCK_SHIELD_RANGE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SHIELDING_BLOCKS;

    // --- zones ---
    public static final ModConfigSpec.IntValue ZONE_RADIUS;

    // --- chunk radiation ---
    public static final ModConfigSpec.BooleanValue CHUNK_RADIATION;
    public static final ModConfigSpec.IntValue CHUNK_UPDATE_TICKS;
    public static final ModConfigSpec.DoubleValue DIFFUSE_KEEP;
    public static final ModConfigSpec.DoubleValue DIFFUSE_SIDE;
    public static final ModConfigSpec.DoubleValue DIFFUSE_DIAGONAL;
    public static final ModConfigSpec.DoubleValue CHUNK_DECAY;
    public static final ModConfigSpec.DoubleValue CHUNK_RADS_PER_RATE;
    public static final ModConfigSpec.DoubleValue MIN_DOSE_RADS;
    public static final ModConfigSpec.DoubleValue POTION_RADS;
    public static final ModConfigSpec.DoubleValue POTION_RADS_STRONG;
    public static final ModConfigSpec.BooleanValue REGEN_CLEANS_LAND;
    public static final ModConfigSpec.DoubleValue REGEN_CLEANUP_RADS;
    public static final ModConfigSpec.DoubleValue REGEN_LINGERING_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue FALLOUT_ON_EXPLOSION;
    public static final ModConfigSpec.DoubleValue FALLOUT_MIN_RADIUS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FALLOUT_TRIGGER_BLOCKS;
    public static final ModConfigSpec.DoubleValue FALLOUT_RADS;
    public static final ModConfigSpec.IntValue FALLOUT_CHUNK_RADIUS;
    public static final ModConfigSpec.DoubleValue FALLOUT_FIRE_CHANCE;
    public static final ModConfigSpec.IntValue FALLOUT_FIRE_CHUNK_RADIUS;

    // --- land decay ---
    public static final ModConfigSpec.BooleanValue LAND_DECAY;
    public static final ModConfigSpec.DoubleValue DECAY_MID_RADS;
    public static final ModConfigSpec.DoubleValue DECAY_HEAVY_RADS;
    public static final ModConfigSpec.IntValue DECAY_BLOCKS_PER_PASS;
    public static final ModConfigSpec.DoubleValue DECAY_CHANCE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DECAY_LIGHT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DECAY_MID;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DECAY_HEAVY;

    // --- inventory radiation, inherited from the retired Radioactive mod ---
    public static final ModConfigSpec.BooleanValue INVENTORY_RADIATION;
    public static final ModConfigSpec.DoubleValue INVENTORY_MAX_RATE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RADIOACTIVE_ITEMS;

    // --- the six stages ---
    public static final ModConfigSpec.IntValue STAGE_1_NAUSEA;
    public static final ModConfigSpec.IntValue STAGE_2_SLOWNESS;
    public static final ModConfigSpec.IntValue STAGE_3_MINING_FATIGUE;
    public static final ModConfigSpec.IntValue STAGE_4_POISON;
    public static final ModConfigSpec.IntValue STAGE_5_DAMAGE;
    public static final ModConfigSpec.IntValue STAGE_6_WITHER;
    public static final ModConfigSpec.DoubleValue DAMAGE_PER_DOSE;
    public static final ModConfigSpec.IntValue NATURAL_RECOVERY;
    public static final ModConfigSpec.BooleanValue MILK_CLEARS_EXPOSURE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MILK_ITEMS;
    public static final ModConfigSpec.DoubleValue SELF_ADVANCE_RATE;
    public static final ModConfigSpec.IntValue SYMPTOM_RAMP_CAP;
    public static final ModConfigSpec.IntValue MILK_FULL_CURE_LIMIT;
    public static final ModConfigSpec.IntValue MILK_PARTIAL_RELIEF;
    public static final ModConfigSpec.BooleanValue HEALING_POTION_CURES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CHOCOLATE_ITEMS;
    public static final ModConfigSpec.IntValue CHOCOLATE_FULL_CURE_LIMIT;
    public static final ModConfigSpec.IntValue CHOCOLATE_PARTIAL_RELIEF;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("How often a dose is applied, in ticks. 20 ticks = 1 second.",
                  "Raising this is the cheapest way to reduce server cost.").push("timing");
        DOSE_INTERVAL = b.defineInRange("dose_interval_ticks", 60, 20, 1200);
        b.pop();

        b.comment(
                "Exposure is counted in SECONDS of contamination, so every stage number below reads as",
                "a clock. A source at rate 1.0 fills it in real time.",
                "",
                "Strength is rate times duration. Remember self_advance_rate keeps the clock running at",
                "1.0 after the potion ends, so the totals below are potion time plus the run-on:",
                "    Radiation      3:00 at 1.0, then 1.0  -> Wither at about 6:00",
                "    Radiation II   1:30 at 3.0, then 1.0  -> Wither at exactly 3:00, Paul's number",
                "    Radiation ext  8:00 at 1.0            -> Wither at 6:00, the extra time is wasted",
                "    Weak Radiation capped, see the cap below, Nausea only").push("rates");
        RATE_RADIATION = b.comment("Radiation, level I")
                .defineInRange("radiation", 1.0D, 0.0D, 100.0D);
        RATE_RADIATION_STRONG = b.comment(
                        "Radiation II, the glowstone variant. 3.0 puts Wither at 3:00: 90 seconds of potion",
                        "at 3x is 270, then the self-advance covers the last 90.")
                .defineInRange("radiation_strong", 3.0D, 0.0D, 100.0D);
        RATE_WEAK_RADIATION = b.comment(
                        "⚠️ NOT a rate. Weak Radiation deliberately never touches the exposure clock, because",
                        "the clock self-advances and a 'weak' potion that eventually kills you is a bug.",
                        "It applies dizziness directly for as long as it lasts, and this is its strength.")
                .defineInRange("weak_radiation_nausea_level", 0.0D, 0.0D, 9.0D);
        REFERENCE_STRENGTH = b.comment(
                        "The zone strength that fills at rate 1.0. At 20, a rad_20 zone runs in real time,",
                        "rad_50 is 2.5x and rad_100 is 5x. Leave it alone unless you want every zone to",
                        "shift at once.")
                .defineInRange("reference_zone_strength", 20, 1, 100);
        b.pop();

        b.comment("Shielding percentages. They add together and cap at 100.").push("shielding");
        RESISTANCE_I = b.comment("Potion of Rad Resistance, level I")
                .defineInRange("resistance_i", 50, 0, 100);
        RESISTANCE_II = b.comment("Potion of Rad Resistance, level II")
                .defineInRange("resistance_ii", 75, 0, 100);
        b.comment("Create Nuclear's anti-radiation armour, per piece. The four add to 100 for a full set,",
                  "which matches what the retired Radioactive mod used.").push("suit");
        SUIT_HELMET = b.defineInRange("helmet", 25, 0, 100);
        SUIT_CHESTPLATE = b.defineInRange("chestplate", 35, 0, 100);
        SUIT_LEGGINGS = b.defineInRange("leggings", 25, 0, 100);
        SUIT_BOOTS = b.defineInRange("boots", 15, 0, 100);
        b.pop();
        BLOCK_SHIELD_PER_SIDE = b.comment(
                        "Shield percent per shielded side. Six sides are checked: up, down and the four",
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

        b.comment(
                "Contamination held per CHUNK, which is how a blast site stays dirty after the blast.",
                "Design taken from HBM's Nuclear Tech Mod, no code from it. See ChunkRadiation.java.",
                "",
                "🚨 This is the cheap way. Nothing scans blocks. One float per contaminated chunk, one",
                "pass a second. Radioactive's block scan cost 136 ms of a 50 ms tick and is why it is",
                "retired.",
                "",
                "Spreading uses a 3x3 kernel and the three shares should total 1.0, or contamination",
                "will grow or vanish on its own regardless of the decay setting.").push("chunk_radiation");
        CHUNK_RADIATION = b.define("enabled", true);
        CHUNK_UPDATE_TICKS = b.comment("How often the spread and decay pass runs")
                .defineInRange("update_interval_ticks", 20, 20, 1200);
        DIFFUSE_KEEP = b.comment("Share that stays in the chunk")
                .defineInRange("diffuse_keep", 0.60D, 0.0D, 1.0D);
        DIFFUSE_SIDE = b.comment("Share to each of the 4 orthogonal neighbours")
                .defineInRange("diffuse_side", 0.075D, 0.0D, 1.0D);
        DIFFUSE_DIAGONAL = b.comment("Share to each of the 4 diagonal neighbours")
                .defineInRange("diffuse_diagonal", 0.025D, 0.0D, 1.0D);
        CHUNK_DECAY = b.comment(
                        "Kept per pass. 0.999 at one pass a second leaves about 3% after three Minecraft",
                        "days, which is Kolten's spec: a blast site cleans itself up eventually.",
                        "Lower it to make fallout fade faster, raise it toward 1.0 for near-permanent.")
                .defineInRange("decay_per_pass", 0.999D, 0.9D, 1.0D);
        MIN_DOSE_RADS = b.comment(
                        "🚨 A chunk below this does not dose anyone at all, and this number is load bearing.",
                        "The exposure clock SELF-ADVANCES once started, so without a floor a single lingering",
                        "rad would eventually kill someone hours later. It is also what gives thrown potions a",
                        "lifespan: they fade below this and stop mattering.")
                .defineInRange("min_dose_rads", 10.0D, 0.0D, 10000.0D);
        POTION_RADS = b.comment(
                        "Rads a thrown Radiation potion leaves in the ground. Paul's spec, 2026-08-13: a splash",
                        "or lingering potion dirties a SMALL patch for about ONE Minecraft day, not three, and",
                        "one healing or regeneration potion cleans it up.",
                        "35 rads decays past min_dose in roughly a Minecraft day at the default decay.")
                .defineInRange("thrown_radiation_rads", 35.0D, 0.0D, 100000.0D);
        POTION_RADS_STRONG = b.comment(
                        "Radiation II, about two Minecraft days. Still under cleanup_rads so a single healing",
                        "potion clears it, which is Paul's rule.")
                .defineInRange("thrown_radiation_rads_strong", 100.0D, 0.0D, 100000.0D);
        REGEN_CLEANS_LAND = b.comment(
                        "A thrown Healing or Regeneration potion scrubs contamination out of the ground.",
                        "Paul's idea, 2026-08-13. This is the cleanup crew's actual tool: breaking irradiated",
                        "blocks removes the source, but the contamination outlives the block, and without this",
                        "there is no way to clean it up at all.")
                .define("healing_potions_clean_land", true);
        REGEN_CLEANUP_RADS = b.comment(
                        "Rads removed from the chunk it lands in. Neighbours get a quarter of this, so a throw",
                        "near a border still helps both sides.")
                .defineInRange("cleanup_rads", 150.0D, 0.0D, 100000.0D);
        REGEN_LINGERING_MULTIPLIER = b.comment("Lingering versions are worth this much more")
                .defineInRange("lingering_multiplier", 3.0D, 0.0D, 100.0D);
        FALLOUT_ON_EXPLOSION = b.comment(
                        "A big enough explosion, or one that destroys reactor or uranium blocks, leaves",
                        "fallout behind. Kolten's ask, 2026-08-13.")
                .define("fallout_on_explosion", true);
        FALLOUT_MIN_RADIUS = b.comment(
                        "Explosions at or above this radius count as nuclear. TNT is 4.0 and a creeper is 3.0,",
                        "so 6.0 keeps ordinary accidents clean.")
                .defineInRange("fallout_min_radius", 6.0D, 1.0D, 1000.0D);
        FALLOUT_TRIGGER_BLOCKS = b.comment(
                        "Any explosion that destroys one of these is nuclear regardless of its size, so a small",
                        "reactor failure still contaminates.")
                .defineList("fallout_trigger_blocks",
                        List.of("createnuclear:reactor_core", "createnuclear:reactor_casing",
                                "createnuclear:reactor_controller", "createnuclear:raw_uranium_block",
                                "createnuclear:uranium_ore", "createnuclear:deepslate_uranium_ore"),
                        o -> o instanceof String);
        FALLOUT_RADS = b.comment("Rads dropped on the epicentre chunk. Neighbours get less with distance.")
                .defineInRange("fallout_rads", 2000.0D, 0.0D, 100000.0D);
        FALLOUT_CHUNK_RADIUS = b.comment("How many chunks out the fallout reaches")
                .defineInRange("fallout_chunk_radius", 4, 0, 32);
        FALLOUT_FIRE_CHANCE = b.comment(
                        "Chance per surface column of catching fire. Kolten likes HBM's burning blast sites.",
                        "🔑 Fire only ever lands on a block that is genuinely FLAMMABLE with air above it, which",
                        "is HBM's own rule. A forest goes up, a stone crater stays quiet.",
                        "HBM uses a flat one in five inside 65 blocks; 0.2 here matches that.",
                        "⚠️ Fire spreads on its own. That is the point and also how you lose a forest.")
                .defineInRange("fallout_fire_chance", 0.2D, 0.0D, 1.0D);
        FALLOUT_FIRE_CHUNK_RADIUS = b.comment(
                        "How many chunks out can catch. HBM burns within 65 blocks, so 4 chunks is the match.",
                        "Set to 0 for the epicentre chunk only, or -1 to disable fire entirely.")
                .defineInRange("fallout_fire_chunk_radius", 4, -1, 32);
        CHUNK_RADS_PER_RATE = b.comment(
                        "How many chunk rads equal a rate of 1.0, which is the exposure clock running in",
                        "real time. At 100, a chunk sitting at 500 doses you five times as fast as a rad_20",
                        "zone does.")
                .defineInRange("rads_per_rate", 100.0D, 1.0D, 100000.0D);
        b.pop();

        b.comment(
                "Carrying raw nuclear material irradiates you. This replaces the retired Radioactive",
                "mod's inventory radiation, which sat at 20 RADs a second and killed anyone who picked",
                "up uranium powder to brew with. Format is item=rate, using the same rate scale as above,",
                "so 0.1 means a tenth of real time and takes about half an hour to reach stage 1.",
                "",
                "The intended answer is to handle uranium in a suit, which is why the suit shields it.")
                .push("inventory");
        INVENTORY_RADIATION = b.define("enabled", true);
        INVENTORY_MAX_RATE = b.comment(
                        "🚨 Dose is per ITEM and SUMMED across every slot, which is HBM's rule. A stack of 64",
                        "uranium powder at 0.1 is a rate of 6.4, reaching the first symptom in about nine",
                        "seconds, so this caps how bad a hoard can get.",
                        "Set to 0 for no cap, which is genuinely brutal.",
                        "2.0 means a big hoard runs the exposure clock at twice real time.")
                .defineInRange("max_rate", 2.0D, 0.0D, 1000.0D);
        RADIOACTIVE_ITEMS = b.defineList("radioactive_items",
                List.of("createnuclear:uranium_rod=0.3",
                        "createnuclear:uranium_bucket=0.3",
                        "createnuclear:raw_uranium_block=0.3",
                        "createnuclear:enriched_yellowcake=0.2",
                        "createnuclear:uranium_powder=0.1",
                        "createnuclear:raw_uranium=0.1"),
                o -> o instanceof String);
        b.pop();

        b.comment(
                "The ground rots where the radiation is. Table-driven, the way HBM's FalloutConfigJSON",
                "is, so you and Kolten can retune what turns into what without a rebuild.",
                "",
                "🔑 THE TIER IS THE CHUNK'S RADIATION, not the distance from a blast. A thrown potion only",
                "ever reaches LIGHT, so it kills grass and leaves shrubs. A reactor reaches HEAVY and gives",
                "ash, mud and burnt trunks. One rule, severity falls out of the number already tracked.",
                "",
                "⚠️ Block edits need a LOADED chunk, so the visuals only advance where somebody is. The",
                "radiation itself is level data and never has this problem.",
                "",
                "Format is from=to. A line naming a block that is not installed is skipped quietly.")
                .push("land_decay");
        LAND_DECAY = b.define("enabled", true);
        DECAY_MID_RADS = b.comment("At or above this, use the MID table")
                .defineInRange("mid_tier_rads", 150.0D, 0.0D, 100000.0D);
        DECAY_HEAVY_RADS = b.comment("At or above this, use the HEAVY table")
                .defineInRange("heavy_tier_rads", 400.0D, 0.0D, 100000.0D);
        DECAY_BLOCKS_PER_PASS = b.comment("Conversion attempts per contaminated loaded chunk, per pass")
                .defineInRange("blocks_per_pass", 2, 0, 64);
        DECAY_CHANCE = b.comment(
                        "Chance each attempt actually converts. Lower means the land dies more slowly.",
                        "0.25 with 2 attempts a second lands roughly in Paul's one-to-three Minecraft days.")
                .defineInRange("chance_per_attempt", 0.25D, 0.0D, 1.0D);
        DECAY_LIGHT = b.comment("LIGHT: the plants die and nothing else. Thrown potions stop here.")
                .defineList("light", List.of(
                        "minecraft:short_grass=minecraft:dead_bush",
                        "minecraft:tall_grass=minecraft:dead_bush",
                        "minecraft:fern=minecraft:dead_bush",
                        "minecraft:large_fern=minecraft:dead_bush",
                        "minecraft:dandelion=minecraft:dead_bush",
                        "minecraft:poppy=minecraft:dead_bush",
                        "minecraft:blue_orchid=minecraft:dead_bush",
                        "minecraft:allium=minecraft:dead_bush",
                        "minecraft:azure_bluet=minecraft:dead_bush",
                        "minecraft:oxeye_daisy=minecraft:dead_bush",
                        "minecraft:cornflower=minecraft:dead_bush",
                        "minecraft:sweet_berry_bush=minecraft:dead_bush",
                        "minecraft:oak_sapling=minecraft:air",
                        "minecraft:birch_sapling=minecraft:air",
                        "minecraft:spruce_sapling=minecraft:air",
                        "minecraft:wheat=minecraft:air",
                        "minecraft:carrots=minecraft:air",
                        "minecraft:potatoes=minecraft:air"),
                        o -> o instanceof String);
        DECAY_MID = b.comment("MID: the ground turns and the trees start to go")
                .defineList("mid", List.of(
                        "minecraft:grass_block=minecraft:rooted_dirt",
                        "minecraft:podzol=minecraft:rooted_dirt",
                        "minecraft:mycelium=minecraft:rooted_dirt",
                        "minecraft:dirt=minecraft:coarse_dirt",
                        "minecraft:sand=chipped:ash_sand",
                        "minecraft:oak_leaves=chipped:dead_oak_leaves",
                        "minecraft:birch_leaves=chipped:dead_birch_leaves",
                        "minecraft:spruce_leaves=chipped:dead_spruce_leaves",
                        "minecraft:jungle_leaves=chipped:dead_jungle_leaves",
                        "minecraft:acacia_leaves=chipped:dead_acacia_leaves",
                        "minecraft:dark_oak_leaves=chipped:dead_dark_oak_leaves",
                        "minecraft:short_grass=minecraft:dead_bush",
                        "minecraft:tall_grass=minecraft:dead_bush"),
                        o -> o instanceof String);
        DECAY_HEAVY = b.comment("HEAVY: ash, mud and burnt trunks. Only a reactor gets you here.")
                .defineList("heavy", List.of(
                        "minecraft:grass_block=supplementaries:ash",
                        "minecraft:rooted_dirt=minecraft:mud",
                        "minecraft:coarse_dirt=minecraft:mud",
                        "minecraft:dirt=minecraft:mud",
                        "minecraft:podzol=minecraft:mud",
                        "minecraft:mycelium=minecraft:mud",
                        "minecraft:sand=chipped:ash_sand",
                        "minecraft:oak_log=minecraft:polished_basalt",
                        "minecraft:birch_log=minecraft:polished_basalt",
                        "minecraft:spruce_log=minecraft:polished_basalt",
                        "minecraft:jungle_log=minecraft:polished_basalt",
                        "minecraft:acacia_log=minecraft:polished_basalt",
                        "minecraft:dark_oak_log=minecraft:polished_basalt",
                        "minecraft:oak_leaves=minecraft:air",
                        "minecraft:birch_leaves=minecraft:air",
                        "minecraft:spruce_leaves=minecraft:air",
                        "minecraft:jungle_leaves=minecraft:air",
                        "minecraft:acacia_leaves=minecraft:air",
                        "minecraft:dark_oak_leaves=minecraft:air",
                        "minecraft:short_grass=minecraft:air",
                        "minecraft:tall_grass=minecraft:air",
                        "minecraft:dead_bush=minecraft:air"),
                        o -> o instanceof String);
        b.pop();

        b.comment(
                "The six stages, in seconds of accumulated exposure. One symptom a minute by default.",
                "",
                "🚨 EXPOSURE DOES NOT WEAR OFF. Paul's spec, 2026-08-12: the effects stay until you get",
                "help. Milk is the cure. Set natural_recovery_seconds above 0 if you want it to fade.",
                "",
                "The Contamination effect's LEVEL is the stage reached, so the HUD reads Contamination IV",
                "and you can see how far gone you are without any counter item.").push("staging");
        STAGE_1_NAUSEA = b.defineInRange("stage_1_nausea_seconds", 60, 1, 100000);
        STAGE_2_SLOWNESS = b.defineInRange("stage_2_slowness_seconds", 120, 1, 100000);
        STAGE_3_MINING_FATIGUE = b.defineInRange("stage_3_mining_fatigue_seconds", 180, 1, 100000);
        STAGE_4_POISON = b.comment("Poison cannot kill on its own, vanilla stops it at half a heart")
                .defineInRange("stage_4_poison_seconds", 240, 1, 100000);
        STAGE_5_DAMAGE = b.comment("Radiation damage. The first stage that can kill you.")
                .defineInRange("stage_5_damage_seconds", 300, 1, 100000);
        STAGE_6_WITHER = b.comment("Wither on top of the damage. The end of the road.")
                .defineInRange("stage_6_wither_seconds", 360, 1, 100000);
        DAMAGE_PER_DOSE = b.comment("Half a heart is 1.0. Stage 5 and up only, and it ramps from there.")
                .defineInRange("damage_per_dose", 1.0D, 0.0D, 100.0D);
        NATURAL_RECOVERY = b.comment(
                        "Seconds of exposure shed per interval while clean AND self_advance_rate is 0.",
                        "Ignored while contamination is self-advancing, which is the intended design.")
                .defineInRange("natural_recovery_seconds", 0, 0, 1000);
        SELF_ADVANCE_RATE = b.comment(
                        "🚨 Once you are contaminated at all, it keeps getting worse on its own at this rate,",
                        "whether or not you are still standing in the source. Paul's spec: five minutes, one",
                        "symptom a minute, IF YOU DO NOT GET HELP. One sip of a Radiation potion therefore",
                        "runs the whole ladder unless you treat it.",
                        "Set to 0.0 to go back to only advancing while a source is present.")
                .defineInRange("self_advance_rate", 1.0D, 0.0D, 100.0D);
        SYMPTOM_RAMP_CAP = b.comment(
                        "Symptoms get stronger the further past their stage you are, up to this many extra",
                        "levels. Kolten's ask, 2026-08-12.",
                        "⚠️ Nausea is the exception: vanilla renders the same screen wobble at every level, so",
                        "its number rises but the picture does not. Slowness, mining fatigue, poison and",
                        "wither all genuinely intensify.")
                .defineInRange("symptom_ramp_cap", 3, 0, 9);
        b.pop();

        b.comment(
                "Milk is the cure, but not forever. Past the limit the poisoning is too far gone for a",
                "bucket of milk to undo and it only buys you time. Kolten's ask, 2026-08-12.",
                "",
                "⚠️ There is currently NO stronger cure, so past the limit a player has to keep drinking",
                "milk to stay ahead of it. Raise milk_partial_relief_seconds if that proves unwinnable.")
                .push("cure");
        MILK_CLEARS_EXPOSURE = b.comment("Milk wipes the accumulated clock, not just the symptoms")
                .define("milk_clears_exposure", true);
        MILK_ITEMS = b.comment(
                        "Everything that counts as milk. Farmer's Delight's bottle is here because Paul asked",
                        "for it: it is the same idea in a smaller container, so it should cure the same way.",
                        "⚠️ Only a real milk BUCKET strips mob effects by itself, that is vanilla. For anything",
                        "else here we clear the exposure clock AND the Contamination effect ourselves, or the",
                        "symptoms would linger with no clock behind them.")
                .defineList("milk_items",
                        List.of("minecraft:milk_bucket", "farmersdelight:milk_bottle"),
                        o -> o instanceof String);
        MILK_FULL_CURE_LIMIT = b.comment(
                        "Below this many seconds of exposure, milk cures you completely. Paul's number is 180:",
                        "milk works right up to poison, and once poison starts there is no going back on milk",
                        "alone.")
                .defineInRange("milk_full_cure_limit_seconds", 180, 0, 100000);
        MILK_PARTIAL_RELIEF = b.comment("Past the limit, each milk only takes this many seconds off")
                .defineInRange("milk_partial_relief_seconds", 60, 0, 100000);
        CHOCOLATE_ITEMS = b.comment(
                        "Chocolate milk, the middle rung of the cure ladder. Crafted from milk plus cocoa",
                        "beans, so it costs something without needing a brewing stand.")
                .defineList("chocolate_milk_items",
                        List.of("reactocraft:chocolate_milk_bucket", "reactocraft:chocolate_milk_bottle"),
                        o -> o instanceof String);
        CHOCOLATE_FULL_CURE_LIMIT = b.comment("Below this, chocolate milk cures completely")
                .defineInRange("chocolate_full_cure_limit_seconds", 300, 0, 100000);
        CHOCOLATE_PARTIAL_RELIEF = b.comment("Past the limit, each drink takes this many seconds off")
                .defineInRange("chocolate_partial_relief_seconds", 180, 0, 100000);
        HEALING_POTION_CURES = b.comment(
                        "A Potion of Healing, any level, wipes contamination completely at any stage. This is",
                        "the answer to being past the milk limit. Paul's rule, 2026-08-12.",
                        "",
                        "⚠️ Drinkable potions only. A splash or lingering healing potion applies its effect by a",
                        "different path that this hook does not see, so it heals hearts without curing.")
                .define("healing_potion_cures", true);
        b.pop();

        SPEC = b.build();
    }
}
