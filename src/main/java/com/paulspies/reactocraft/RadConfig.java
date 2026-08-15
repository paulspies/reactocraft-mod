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
    public static final ModConfigSpec.DoubleValue NATURAL_RECOVERY;
    public static final ModConfigSpec.BooleanValue MILK_CLEARS_EXPOSURE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MILK_ITEMS;
    public static final ModConfigSpec.IntValue MILK_FULL_CURE_LIMIT;
    public static final ModConfigSpec.IntValue MILK_PARTIAL_RELIEF;
    public static final ModConfigSpec.BooleanValue HEALING_POTION_CURES;
    public static final ModConfigSpec.BooleanValue REGEN_POTION_CURES;
    public static final ModConfigSpec.BooleanValue REGEN_GRANTS_IMMUNITY;
    public static final ModConfigSpec.DoubleValue LETHAL_DOSE;
    public static final ModConfigSpec.IntValue BLOCK_REGEN_FROM_STAGE;
    public static final ModConfigSpec.BooleanValue STAGE_MESSAGES;
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
        // 🚨 THESE ARE RADS PER SECOND NOW, against lethal_dose. Retuned 2026-08-15.
        // ⚠️ TIME TO DEATH IS AN OUTPUT, NOT A SETTING. We set how hot a thing is; the clock falls
        // out of it. Paul's point, and it is the reason the model is worth having: sources ADD, so
        // fallout plus a pocketful of uranium plus the potion kills far faster than any alone, and
        // nobody has to write that case.
        // Nothing grows on its own any more, so a potion is worth exactly rate x duration:
        //     Radiation I   6/s over 3:00  = 1080 -> crosses 1000 at about 2:47, near the very end
        //     Radiation II 12/s over 1:30  = 1080 -> crosses at about 1:23, well inside its duration
        // Both are fatal untreated, which is Paul's rule, and both leave a real treatment window.
        // 🔴 ARITHMETIC, NOT MEASUREMENT. Paul times them in play and we adjust.
        // ⚠️ The alternative worth remembering: drop Radiation I to 4/s and it delivers 720, which
        // never kills on its own and instead strands you sick at tier 3 until you find a cure.
        RATE_RADIATION = b.comment("Radiation I, in rads per second")
                .defineInRange("radiation", 6.0D, 0.0D, 10000.0D);
        RATE_RADIATION_STRONG = b.comment(
                        "Radiation II, the glowstone variant. Three times the rate, so it kills inside its",
                        "own duration and milk stops fully curing almost immediately.")
                .defineInRange("radiation_strong", 12.0D, 0.0D, 10000.0D);
        RATE_WEAK_RADIATION = b.comment(
                        "⚠️ NOT a rate. Weak Radiation deliberately never touches the exposure clock, because",
                        "the clock self-advances and a 'weak' potion that eventually kills you is a bug.",
                        "It applies dizziness directly for as long as it lasts, and this is its strength.")
                .defineInRange("weak_radiation_nausea_level", 0.0D, 0.0D, 9.0D);
        REFERENCE_STRENGTH = b.comment(
                        "The zone strength worth ONE rad per second. Everything else scales off it, so this",
                        "is the single dial that moves every zone at once.",
                        "",
                        "🚨 RESCALED 2026-08-15, and it had to be. At 20, a rad_20 zone gave 1 rad/sec, which",
                        "against a lethal dose of 1000 meant standing in it for sixteen minutes. It was tuned",
                        "when a 'rate' meant the old exposure clock running in real time, and the dose rewrite",
                        "left it behind. At 4:",
                        "    rad_20  ->  5 rads/sec    a bit under a Radiation I potion",
                        "    rad_50  -> 12 rads/sec    a Radiation II potion. Lethal in about 80 seconds",
                        "    rad_100 -> 25 rads/sec    40 seconds. The 'do not walk in here' zone",
                        "⚠️ When any source's meaning changes, check the others in this file. They are all",
                        "expressed against the same lethal_dose and they drift apart silently.")
                .defineInRange("reference_zone_strength", 4, 1, 100);
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
                        "A chunk below this does not dose anyone at all. It is what gives thrown potions a",
                        "lifespan: they decay below it and stop mattering.",
                        "⚠️ This used to be load bearing for a different reason - the old exposure clock",
                        "self-advanced, so any lingering trace would eventually kill. Nothing self-advances",
                        "now, so the floor is only about tidiness and cost, not safety.")
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
                        "How many rads sitting in a chunk are worth ONE rad per second to anyone standing in",
                        "it. At 100, a chunk holding 600 doses you at 6/sec - a Radiation I potion - and a",
                        "fresh reactor crater at 2000 doses you at 20/sec, which is lethal in under a minute.",
                        "That is the intended feel: a blast site is the most dangerous place in the world, and",
                        "it becomes survivable as it decays.")
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
                        "🚨 Dose is per ITEM and SUMMED across every slot, which is HBM's rule. Their items",
                        "give `level / 20F` per tick, so a level of 1 is one rad per second - the same units",
                        "everything else in this file uses.",
                        "",
                        "This caps how bad a hoard can get. At 6.0 a full backpack of uranium is worth about a",
                        "Radiation I potion: it will kill you in a few minutes if you never put it down, which",
                        "is the point, but it will not kill you for picking some up.",
                        "⚠️ RAISED FROM 2.0 on 2026-08-15 with the dose rescale. At 2.0 a hoard capped out at",
                        "2 rads/sec, or over eight minutes to lethal, which made carrying uranium a non-event.",
                        "Set to 0 for no cap, which is genuinely brutal.")
                .defineInRange("max_rate", 6.0D, 0.0D, 1000.0D);
        RADIOACTIVE_ITEMS = b.comment(
                        "Rads per second, per item, multiplied by stack size and summed.",
                        "",
                        "🔑 THE VALUES FOLLOW A RULE RATHER THAN A GUESS: material x form. HBM keeps a",
                        "radioactivity per material (natural uranium 0.35, U-235 1.0, U-238 0.25, uranium fuel",
                        "0.5) and a multiplier per shape (ingot 1, nugget 0.1, billet and rod 0.5, a full RBMK",
                        "fuel rod 4, and POWDER 3x an ingot because dust is worse than solid metal). Ours are",
                        "set the same way, so a new material is one number rather than a new argument.",
                        "",
                        "⚠️ RETUNED 2026-08-15. They were flat guesses between 0.1 and 0.3, which made a fuel",
                        "rod take the better part of an hour to matter. Kolten's whole point was that pulling a",
                        "hot rod out of a reactor should burn you.",
                        "At 2.0, carrying one fuel rod is lethal in about eight minutes of never putting it",
                        "down: fine to move, not fine to pocket.")
                .defineList("radioactive_items",
                List.of("createnuclear:uranium_rod=2.0",          // fuel x rod_rbmk
                        "createnuclear:uranium_bucket=1.75",      // a bucket of the liquid
                        "createnuclear:raw_uranium_block=3.15",   // nine raw, natural uranium
                        "createnuclear:enriched_yellowcake=1.0",  // enriched, so U-235's number
                        "createnuclear:uranium_powder=1.05",      // natural uranium x powder
                        "createnuclear:raw_uranium=0.35"),        // natural uranium, one ingot's worth
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
        // 🚨 REWRITTEN 2026-08-15 — THE STAGES ARE DOSE NOW, NOT SECONDS, AND DEATH IS A THRESHOLD.
        //
        // Read out of both reference mods before changing this: NEITHER kills you with damage.
        // HBM at 1000 rads does attackEntityFrom(radiation, 1000F) then sets health to 0.
        // Radioactive at 10 does hurt(radiation_dt, 1.0E7f). A damage race is a negotiation you can
        // win with food; a deadline is not. Paul measured the old damage race dying at 2:30 with
        // stage 6 never reached at all, because stage 4 poison emptied the bar before stage 5 landed.
        //
        // ⚠️ The keys are RENAMED, so the old stage_*_seconds entries are now dead weight in any
        // deployed toml. Delete them by hand; NeoForge will not.
        LETHAL_DOSE = b.comment(
                        "🚨 THE DEADLINE. Reach this and you die outright, whatever your health, armour or",
                        "food. Everything below is a fraction of it, which is how both reference mods do it.")
                .defineInRange("lethal_dose", 1000.0D, 1.0D, 1000000.0D);
        // 🚨 HBM'S FOUR TIERS, VERBATIM. ModEventHandler, the `eRad >=` chain. Four, not six - the
        // six-stage ladder was mine. The symptoms that fire at each are a table in RadEngine, also
        // copied straight from their source so the two can be diffed.
        STAGE_1_NAUSEA = b.defineInRange("tier_1_dose", 200, 1, 1000000);
        STAGE_2_SLOWNESS = b.defineInRange("tier_2_dose", 400, 1, 1000000);
        STAGE_3_MINING_FATIGUE = b.comment("Poison starts here, at level II, exactly as they have it")
                .defineInRange("tier_3_dose", 600, 1, 1000000);
        STAGE_4_POISON = b.comment("Wither joins. The last tier before the deadline.")
                .defineInRange("tier_4_dose", 800, 1, 1000000);
        BLOCK_REGEN_FROM_STAGE = b.comment(
                        "Cancel natural regeneration from this tier upward. HBM does NOT do this, so it is",
                        "off by default (5 is above the top tier of 4).",
                        "It existed to stop players out-eating chip damage, and there is no chip damage now -",
                        "the deadline kills regardless of health, so food cannot save anyone anyway.")
                .defineInRange("block_regen_from_stage", 5, 1, 7);
        STAGE_MESSAGES = b.comment(
                        "Announce each stage change on the actionbar, above the hotbar. Paul could not tell",
                        "the stages existed, and he was right that nothing on screen said so: every symptom",
                        "effect is added with its icon hidden, and only Contamination's Roman numeral carried",
                        "the stage at all.")
                .define("stage_messages", true);
        NATURAL_RECOVERY = b.comment(
                        "Rads per second your body clears while nothing is dosing you.",
                        "",
                        "⚠️ THE ONE DELIBERATE DEVIATION FROM HBM, and the reasoning is in RadEngine.applyDose.",
                        "Their dose never falls, so a player at 999 rads with no cure is stuck at the worst",
                        "tier forever - never dying, never recovering. Radaway is common in their mod; our",
                        "cures are milk and potions and someone will run out.",
                        "At 1.0 a serious dose takes about fifteen minutes to clear, which is far too slow to",
                        "wait out in a fight, so treatment is still the real answer.",
                        "Set to 0.0 to match HBM exactly.")
                .defineInRange("natural_recovery_rads", 1.0D, 0.0D, 10000.0D);
        // ❌ symptom_ramp_cap is gone. Kolten asked on 2026-08-12 for symptoms to intensify, and this
        // computed an amplifier from how far past a stage you were. HBM's table sets the amplifier
        // explicitly at every tier instead - Weakness I at 400 and II at 600, Poison II at 600 and
        // III at 800 - so the intensification is still there, just stated rather than derived.
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
                        "Drinkable, splash and lingering all work. Splash and lingering go through a separate",
                        "impact hook, because vanilla applies instant effects without ever calling addEffect.")
                .define("healing_potion_cures", true);
        REGEN_POTION_CURES = b.comment(
                        "A Potion of Regeneration cures the same way. Paul, 2026-08-14: both bottles a player",
                        "thinks of as medicine should work. Regeneration also scrubs the LAND when thrown, so",
                        "one bottle now treats the player and the ground it lands on.")
                .define("regen_potion_cures", true);
        REGEN_GRANTS_IMMUNITY = b.comment(
                        "Regeneration also holds radiation off for as long as it lasts, not only at the",
                        "moment you drink it. Paul, 2026-08-14: 'it is a time limit countdown, so you should",
                        "be immune during the time the regen is active.'",
                        "This is what makes Regeneration the answer to Radiation II: the cure is a moment,",
                        "the immunity is a window you can work inside.")
                .define("regen_grants_immunity", true);
        b.pop();

        SPEC = b.build();
    }
}
