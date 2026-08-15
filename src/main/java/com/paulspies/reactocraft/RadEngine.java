package com.paulspies.reactocraft;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * The radiation engine.
 *
 * Runs once every RadConfig.DOSE_INTERVAL ticks per player: work out what is irradiating them, work
 * out how much of it gets through, add that to their exposure clock, then apply whichever symptoms
 * that many seconds have earned.
 *
 * 🚨 EXPOSURE DOES NOT WEAR OFF. That is deliberate, Paul's spec on 2026-08-12. Radiation is
 * something you treat, not something you wait out. Milk is the cure.
 *
 * 🚨 On cost. The mod this replaces swept a cube around every player every single tick, per emitter
 * definition, with no index. Radius 8 is 4,913 block reads, and it measured 136 ms of a 50 ms
 * budget. Nothing here does that. Zones are entities, so the game's own index finds them. Block
 * shielding is six short rays and only runs when a dose is actually landing, so a player standing
 * in a clean world pays for one entity query and one inventory walk every three seconds.
 */
public final class RadEngine {
    private RadEngine() {}

    private static final String EXPOSURE_KEY = "reactocraft_exposure";
    /** The last stage announced, so the actionbar fires on the change and not every three seconds. */
    private static final String STAGE_KEY = "reactocraft_stage";
    private static final String ZONE_TAG = "rad";

    /** Indexed by tier, so 0 is unused. Kept short: this is an actionbar line, not a paragraph. */
    private static final String[] STAGE_NAMES = {
            "", "you feel sick", "it is getting worse", "poisoned", "dying"
    };

    /**
     * One symptom roll, in HBM's own terms.
     *
     * @param chancePerTick their raw `rand.nextInt(N)` denominator, kept EXACTLY as written in
     *                      ModEventHandler so this table can be diffed against their source. It is
     *                      converted to our slower dose tick at roll time.
     */
    private record Symptom(Holder<net.minecraft.world.effect.MobEffect> effect,
                           int durationTicks, int amplifier, int chancePerTick) {

        void maybeApply(ServerPlayer player) {
            // They roll every tick; we roll once per dose interval, so the denominator shrinks by
            // exactly that factor and the symptom arrives just as often in wall-clock terms.
            // ⚠️ ROUNDED, not truncated. 500/60 is 8.33, and integer division to 8 made every
            // symptom noticeably more frequent than theirs.
            int perDose = Math.max(1, Math.round(
                    (float) chancePerTick / Math.max(1, RadConfig.DOSE_INTERVAL.get())));
            if (player.getRandom().nextInt(perDose) != 0) return;
            player.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, true, false));
        }
    }

    /**
     * The symptom ladder, reimplemented from HBM's observed behaviour. Credit to HBM's Nuclear Tech
     * Mod for the design; none of their code is here.
     *
     * ⚠️ ON WORDING, because this matters for our licence: HBM Reloaded is GPL-3.0 and we ship MIT.
     * Copyright protects expression, not systems or the parameters that make a system behave a
     * certain way, so a ladder of thresholds with effect durations is ours to write. What would not
     * be ours is their source, their art or their text. This table is our structure, our record
     * type, our roll - the numbers are functional values chosen because they are known to play well.
     *
     * Tiers: 1 = 200 rads, 2 = 400, 3 = 600, 4 = 800. Death is at 1000 and lives in applyDose.
     * Durations are in ticks, written as `seconds * 20`; amplifier 0 is level I.
     *
     * 🔑 Every symptom rolls INDEPENDENTLY, which is why HBM's sickness arrives in waves instead of
     * sitting on you permanently. That wave pattern is the "feel" Kolten recognised and could not
     * name, and it is the only lever anyone has on nausea - vanilla draws the identical wobble at
     * every amplifier, which is why they pass level 0 at every tier and vary timing instead.
     *
     * ⚠️ No damage of ours appears anywhere in here, deliberately. Poison is the slow burn, Wither is
     * the rapid phase, and the deadline is the only thing that kills.
     */
    private static final Symptom[][] SYMPTOMS = {
            {}, // tier 0, unused
            { // >= 200
                    new Symptom(MobEffects.CONFUSION, 5 * 20, 0, 300),
                    new Symptom(MobEffects.WEAKNESS, 5 * 20, 0, 500),
                    new Symptom(MobEffects.HUNGER, 3 * 20, 2, 700),
            },
            { // >= 400
                    new Symptom(MobEffects.CONFUSION, 5 * 30, 0, 300),
                    new Symptom(MobEffects.MOVEMENT_SLOWDOWN, 5 * 20, 0, 500),
                    new Symptom(MobEffects.WEAKNESS, 5 * 20, 1, 300),
                    new Symptom(MobEffects.HUNGER, 3 * 20, 2, 500),
            },
            { // >= 600
                    new Symptom(MobEffects.CONFUSION, 5 * 30, 0, 300),
                    new Symptom(MobEffects.MOVEMENT_SLOWDOWN, 10 * 20, 2, 300),
                    new Symptom(MobEffects.WEAKNESS, 10 * 20, 2, 300),
                    new Symptom(MobEffects.POISON, 3 * 20, 1, 500),
                    new Symptom(MobEffects.HUNGER, 3 * 20, 3, 300),
            },
            { // >= 800
                    new Symptom(MobEffects.CONFUSION, 5 * 30, 0, 300),
                    new Symptom(MobEffects.MOVEMENT_SLOWDOWN, 10 * 20, 2, 300),
                    new Symptom(MobEffects.WEAKNESS, 10 * 20, 2, 300),
                    new Symptom(MobEffects.POISON, 3 * 20, 2, 500),
                    new Symptom(MobEffects.WITHER, 3 * 20, 1, 700),
                    new Symptom(MobEffects.HUNGER, 5 * 20, 3, 300),
            },
    };

    public static final ResourceKey<DamageType> RADIATION_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ReactoCraft.MODID, "radiation"));

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator() || player.isCreative()) return;
        if (player.tickCount % RadConfig.DOSE_INTERVAL.get() != 0) return;

        // Weak Radiation is dizziness and nothing else, on purpose. It never enters the exposure
        // clock, because the clock self-advances once started, so any contribution at all would
        // eventually kill you and a "weak" potion that kills you is a bug, not a difficulty setting.
        if (player.hasEffect(ModEffects.WEAK_RADIATION)) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
                    RadConfig.DOSE_INTERVAL.get() + 40,
                    (int) Math.round(RadConfig.RATE_WEAK_RADIATION.get()), true, false));
        }

        double rate = incomingRads(player);
        double dose = 0.0D;

        if (rate > 0.0D) {
            int shield = shieldPercent(player);
            if (player.hasEffect(ModEffects.RAD_WEAKNESS)) {
                // Rad Weakness cancels every protection and doubles what lands.
                shield = 0;
                rate *= 2.0D;
            }
            dose = rate * (100 - shield) / 100.0D;
        }

        applyDose(player, dose);
    }

    /**
     * How many rads per second are landing on this player, from everything at once.
     *
     * 🚨 SOURCES ADD. They used to compete, with the strongest winning, which meant standing in a
     * hot chunk while carrying uranium was no worse than either one alone. HBM has no such rule:
     * every source calls the same ContaminationUtil.contaminate and it all lands in one number.
     *
     * 🔑 Kolten worked this out from playing HBM before I read any of it - he pointed out that a rod
     * pulled from a reactor burns you, so the item and the world must be the same system.
     */
    private static double incomingRads(ServerPlayer player) {
        double rads = 0.0D;

        if (player.hasEffect(ModEffects.RADIATION)) {
            rads += player.getEffect(ModEffects.RADIATION).getAmplifier() >= 1
                    ? RadConfig.RATE_RADIATION_STRONG.get()
                    : RadConfig.RATE_RADIATION.get();
        }
        // Weak Radiation is handled in onPlayerTick and never reaches the dose. See there for why.

        rads += zoneRate(player);
        rads += chunkRate(player);
        rads += inventoryRate(player);
        return rads;
    }

    /**
     * Contamination held in the chunk you are standing in. This is the fallout system: one float per
     * chunk, no block scanning, and it exists whether or not the chunk is loaded.
     */
    private static double chunkRate(ServerPlayer player) {
        if (!RadConfig.CHUNK_RADIATION.get()) return 0.0D;
        if (!(player.level() instanceof ServerLevel level)) return 0.0D;

        float rads = ChunkRadiation.get(level).get(player.chunkPosition());

        // 🚨 Below the floor a chunk does not dose at all. This is load bearing: the exposure clock
        // self-advances once started, so a single lingering rad would eventually kill someone hours
        // later. It is also what gives thrown potions a lifespan rather than a permanent scar.
        if (rads < RadConfig.MIN_DOSE_RADS.get()) return 0.0D;

        return rads / RadConfig.CHUNK_RADS_PER_RATE.get();
    }

    /** Zones placed by /function rad:zone_* are entities tagged "rad" plus "rad_<strength>". */
    private static double zoneRate(ServerPlayer player) {
        int strongest = 0;
        AABB box = player.getBoundingBox().inflate(RadConfig.ZONE_RADIUS.get());
        List<Entity> zones = player.level().getEntities(player, box, e -> e.getTags().contains(ZONE_TAG));

        for (Entity zone : zones) {
            for (String tag : zone.getTags()) {
                if (!tag.startsWith("rad_")) continue;
                try {
                    strongest = Math.max(strongest, Integer.parseInt(tag.substring(4)));
                } catch (NumberFormatException ignored) {
                    // Tags like "rad_zone" are not strengths. Skipping them is correct.
                }
            }
        }
        return (double) strongest / RadConfig.REFERENCE_STRENGTH.get();
    }

    /**
     * Carrying raw nuclear material irradiates you. This is what the retired Radioactive mod used to
     * do, at a rate that is survivable long enough to actually brew with the stuff.
     */
    private static double inventoryRate(ServerPlayer player) {
        if (!RadConfig.INVENTORY_RADIATION.get()) return 0.0D;

        double total = 0.0D;

        // Main inventory and the offhand. Worn armour is deliberately excluded: the anti-radiation
        // suit is armour, and having a suit dose you for wearing it would be absurd.
        total += scan(player.getInventory().items);
        total += scan(player.getInventory().offhand);

        double cap = RadConfig.INVENTORY_MAX_RATE.get();
        return cap > 0.0D ? Math.min(cap, total) : total;
    }

    /**
     * 🚨 SCALED BY STACK SIZE AND SUMMED, which is HBM's rule and is the whole point.
     *
     * The first version took the single worst item and ignored the rest, so one uranium powder and
     * eight stacks of it dosed you identically. That made hoarding free, which is the opposite of
     * what carrying nuclear material should feel like.
     *
     * ⚠️ It is capped, because unbounded it is brutal: at 0.1 a full stack of 64 powder is a rate of
     * 6.4, which reaches the first symptom in about nine seconds. The cap keeps a hoard genuinely
     * dangerous without being instantly fatal, and it is the number to tune if this feels wrong.
     */
    private static double scan(Iterable<ItemStack> slots) {
        double total = 0.0D;
        for (ItemStack stack : slots) {
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            for (String entry : RadConfig.RADIOACTIVE_ITEMS.get()) {
                int eq = entry.indexOf('=');
                if (eq < 0 || !entry.substring(0, eq).equals(id)) continue;
                try {
                    total += Double.parseDouble(entry.substring(eq + 1)) * stack.getCount();
                } catch (NumberFormatException ignored) {
                    // A malformed config line should not take the engine down with it.
                }
            }
        }
        return total;
    }

    /** Total shielding, 0-100. Potion, then each armour piece, then the six sides of the room. */
    private static int shieldPercent(ServerPlayer player) {
        int shield = 0;

        // Regeneration holds radiation off for as long as it runs, not just at the sip. Paul's rule,
        // 2026-08-14: it is a countdown, so it should buy a window you can act inside. That window
        // is the only sane answer to Radiation II, which outruns milk in sixty seconds.
        if (RadConfig.REGEN_GRANTS_IMMUNITY.get() && player.hasEffect(MobEffects.REGENERATION)) {
            return 100;
        }

        if (player.hasEffect(ModEffects.RAD_RESISTANCE)) {
            shield += player.getEffect(ModEffects.RAD_RESISTANCE).getAmplifier() >= 1
                    ? RadConfig.RESISTANCE_II.get()
                    : RadConfig.RESISTANCE_I.get();
        }
        shield += suitShield(player);
        shield += blockShield(player);

        return Math.min(100, shield);
    }

    /** Per piece, so a partial suit is partial protection rather than nothing. */
    private static int suitShield(ServerPlayer player) {
        int shield = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("anti_radiation")) continue;
            shield += switch (slot) {
                case HEAD -> RadConfig.SUIT_HELMET.get();
                case CHEST -> RadConfig.SUIT_CHESTPLATE.get();
                case LEGS -> RadConfig.SUIT_LEGGINGS.get();
                default -> RadConfig.SUIT_BOOTS.get();
            };
        }
        return shield;
    }

    /**
     * Kolten's sealed-room test. Six directions, floor, ceiling and the four walls. A side counts as
     * shielded if the first solid block along that ray is a shielding block.
     */
    private static int blockShield(ServerPlayer player) {
        int perSide = RadConfig.BLOCK_SHIELD_PER_SIDE.get();
        if (perSide <= 0) return 0;

        int range = RadConfig.BLOCK_SHIELD_RANGE.get();
        List<? extends String> shielding = RadConfig.SHIELDING_BLOCKS.get();
        Level level = player.level();
        BlockPos origin = player.blockPosition().above();
        int sides = 0;

        for (Direction dir : Direction.values()) {
            BlockPos.MutableBlockPos cursor = origin.mutable();
            for (int i = 0; i < range; i++) {
                cursor.move(dir);
                if (!level.isLoaded(cursor)) break;
                BlockState state = level.getBlockState(cursor);
                if (state.isAir()) continue;
                if (shielding.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) sides++;
                // The first solid block on this ray settles the side either way. A concrete wall does
                // not shield you, and it also stops you seeing the lead behind it.
                break;
            }
        }
        return sides * perSide;
    }

    /**
     * Add to the accumulated dose, then apply whatever it has earned - up to and including death.
     *
     * 🚨 THE DOSE IS A DEADLINE, NOT A HEALTH BAR. Reaching lethal_dose kills outright, whatever
     * your health, food or armour. Everything below it hurts but cannot finish you, because
     * damage_can_kill is false. That is what guarantees all six stages happen in order, every time.
     *
     * The old version was a damage race and Paul measured what that produces: dead at 2:30 against a
     * 4:00 target, and stage 6 never reached at all, because stage 4 poison emptied the bar and the
     * first chip of stage 5 finished it. Both reference mods avoid this the same way - see BEHAVIOR.
     */
    private static void applyDose(ServerPlayer player, double dose) {
        CompoundTag data = player.getPersistentData();
        double intervalSeconds = Math.max(1, RadConfig.DOSE_INTERVAL.get() / 20);
        int rads = data.getInt(EXPOSURE_KEY);

        // What actually landed on you this tick.
        if (dose > 0.0D) {
            rads += Math.max(1, (int) Math.round(intervalSeconds * dose));
        }

        // 🚨 THE DOSE ONLY EVER GOES UP, AND NOTHING MAKES IT GROW BY ITSELF.
        //
        // Read out of HBM before writing this: HbmLivingProps, ModEventHandler and ContaminationUtil
        // between them never reduce an entity's rads and never inflate them. incrementRadiation is
        // the only path in, it caps at 2,500,000, and the figure resets to 0 only when radiation
        // kills you. A cure item is the sole way down.
        //
        // 🔴 The self-advance this replaces was mine, added 2026-08-12 to make a 3 minute potion
        // reach a 5 minute ladder. With a dose model that problem does not exist, and the flat rate
        // it used made every source equally lethal - one rad picked up in passing killed you as
        // surely as the potion. Paul's original rule ("the effects stay until you get help") is
        // satisfied better by HBM's version: they stay, they simply do not grow.
        //
        // ⚠️ ONE DELIBERATE DEVIATION FROM HBM: a slow decay while nothing is dosing you.
        // Theirs never falls, which means a player who takes 999 rads and has no cure is stuck at
        // tier 4 permanently - poisoned and withering forever, never dying, never recovering. That
        // works for HBM because Radaway is craftable and common; our cures are milk and potions, and
        // running out would strand someone in a state with no way back. At 1 rad/sec a serious dose
        // takes about fifteen minutes to clear, which is far too slow to wait out in a fight, so
        // treatment is still the real answer. It is also true to life - the body does repair.
        if (dose <= 0.0D) {
            rads -= (int) Math.round(RadConfig.NATURAL_RECOVERY.get() * intervalSeconds);
            rads = Math.max(0, rads);
        }

        data.putInt(EXPOSURE_KEY, rads);

        if (rads <= 0) {
            player.removeEffect(ModEffects.CONTAMINATION);
            data.putInt(STAGE_KEY, 0);
            return;
        }

        // 🚨 THE DEADLINE. Checked before the symptoms, because nothing below matters once it lands.
        if (rads >= RadConfig.LETHAL_DOSE.get()) {
            kill(player, data);
            return;
        }

        int tier = tierFor(rads);
        announceStage(player, data, tier);
        if (tier == 0) return;

        // Infinite, because it does not wear off. The HUD shows the level as a Roman numeral and an
        // infinity marker instead of a countdown, which says "this needs treating" on its own.
        MobEffectInstance current = player.getEffect(ModEffects.CONTAMINATION);
        if (current == null || current.getAmplifier() != tier - 1) {
            player.addEffect(new MobEffectInstance(
                    ModEffects.CONTAMINATION, MobEffectInstance.INFINITE_DURATION, tier - 1, true, true));
        }

        for (Symptom s : SYMPTOMS[tier]) {
            s.maybeApply(player);
        }

        // ❌ Nothing else. No layered Hunger/Weakness/Darkness of mine, no chip damage, no blindness.
        // HUNGER and WEAKNESS are in their table above at the tiers they chose; Darkness and
        // Blindness are not in HBM at all, and Paul and Kolten both said no to taking sight away.
        // Poison is the slow burn, Wither is the rapid phase, and the deadline is the only killer.
    }

    /**
     * 🚨 THE DEADLINE LANDING. Both reference mods do exactly this and nothing subtler.
     *
     * HBM: attackEntityFrom(radiation, 1000F), then setHealth(0), then onDeath.
     * Radioactive: hurt(radiation_dt, 1.0E7f).
     *
     * The damage call is what produces the death message and the statistics; the setHealth is the
     * guarantee, because absorption hearts, resistance or another mod's shield could otherwise
     * absorb even a huge hit and leave the player standing at lethal dose forever.
     *
     * The dose resets, so respawning does not put you straight back into a corpse.
     */
    private static void kill(ServerPlayer player, CompoundTag data) {
        data.putInt(EXPOSURE_KEY, 0);
        data.putInt(STAGE_KEY, 0);
        player.removeEffect(ModEffects.CONTAMINATION);
        clearSources(player);

        Holder<DamageType> type = player.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(RADIATION_DAMAGE);
        player.hurt(new DamageSource(type), Float.MAX_VALUE);

        if (player.isAlive()) {
            player.setHealth(0.0F);
        }
    }

    /**
     * Say out loud which stage you just reached. Paul, 2026-08-14: "you said we would have stages
     * and I told you I don't see them."
     *
     * 🔑 He was right, and the reason is in this file: every symptom effect above is added with its
     * icon HIDDEN, so the only thing on screen carrying the stage was a Roman numeral on the
     * Contamination icon, in a menu you have to open. The stages were real and invisible.
     *
     * Only ever announces going UP. Coming down happens through a cure, which has its own feedback.
     */
    private static void announceStage(ServerPlayer player, CompoundTag data, int stage) {
        int previous = data.getInt(STAGE_KEY);
        if (stage == previous) return;
        data.putInt(STAGE_KEY, stage);

        if (!RadConfig.STAGE_MESSAGES.get() || stage <= previous || stage < 1) return;

        player.displayClientMessage(
                Component.literal("Radiation sickness - stage " + stage + " of 6: " + STAGE_NAMES[stage])
                        .withStyle(stage >= 5 ? ChatFormatting.RED
                                : stage >= 3 ? ChatFormatting.GOLD
                                : ChatFormatting.YELLOW),
                true);
    }

    /**
     * 🚨 THE ANSWER TO "YOU CAN OUTLAST IT", Paul 2026-08-14.
     *
     * At stage 5 and up, natural regeneration is cancelled. Before this, our damage started at half
     * a heart every three seconds while a fed player healed back about as fast, so radiation looked
     * like it floored you at half a heart instead of killing you. It never floored anything; food
     * was simply winning.
     *
     * ⚠️ Healing and Regeneration potions are unaffected, because both CURE on the way in, which
     * puts the stage back to 0 before any healing is applied. Medicine works, bread does not.
     */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        int from = RadConfig.BLOCK_REGEN_FROM_STAGE.get();
        if (from > 6) return;
        if (player.getPersistentData().getInt(STAGE_KEY) >= from) {
            event.setCanceled(true);
        }
    }


    /**
     * 0 means no symptoms yet. 1 through 4 are HBM's tiers, and also the Contamination level.
     *
     * Four, not six. The six-stage ladder was mine; theirs is 200/400/600/800 with death at 1000.
     */
    private static int tierFor(int rads) {
        if (rads >= RadConfig.STAGE_4_POISON.get()) return 4;
        if (rads >= RadConfig.STAGE_3_MINING_FATIGUE.get()) return 3;
        if (rads >= RadConfig.STAGE_2_SLOWNESS.get()) return 2;
        if (rads >= RadConfig.STAGE_1_NAUSEA.get()) return 1;
        return 0;
    }

    /**
     * Milk is the cure, but only up to a point.
     *
     * Below the limit it wipes the clock completely. Past it the poisoning is too far gone to undo
     * with a bucket of milk, and each one only buys back a fixed number of seconds. Kolten's ask,
     * 2026-08-12: after long enough, milk should stop saving you.
     */
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // A Potion of Healing or Regeneration, any level, wipes it completely at any stage. This is
        // the way back once you are past the milk limit. Paul's rule, 2026-08-12; Regeneration added
        // 2026-08-14 at his instruction, so both bottles a player thinks of as medicine work.
        if (event.getItem().is(Items.POTION) && isCurePotion(event.getItem())) {
            cure(player);
            return;
        }

        if (!RadConfig.MILK_CLEARS_EXPOSURE.get()) return;

        String id = BuiltInRegistries.ITEM.getKey(event.getItem().getItem()).toString();
        int fullCure;
        int relief;

        if (RadConfig.CHOCOLATE_ITEMS.get().contains(id)) {
            fullCure = RadConfig.CHOCOLATE_FULL_CURE_LIMIT.get();
            relief = RadConfig.CHOCOLATE_PARTIAL_RELIEF.get();
        } else if (RadConfig.MILK_ITEMS.get().contains(id)) {
            fullCure = RadConfig.MILK_FULL_CURE_LIMIT.get();
            relief = RadConfig.MILK_PARTIAL_RELIEF.get();
        } else {
            return;
        }

        CompoundTag data = player.getPersistentData();
        int exposure = data.getInt(EXPOSURE_KEY);

        if (exposure <= fullCure) {
            data.putInt(EXPOSURE_KEY, 0);
            data.putInt(STAGE_KEY, 0);
            // A vanilla milk BUCKET strips every effect by itself, but Farmer's Delight's bottle and
            // our own drinks do not, so Contamination goes explicitly rather than being assumed gone.
            player.removeEffect(ModEffects.CONTAMINATION);
            clearSources(player);
            return;
        }

        int left = Math.max(0, exposure - relief);
        data.putInt(EXPOSURE_KEY, left);
        data.putInt(STAGE_KEY, tierFor(left));

        // Same reasoning as above, and it applies even to a partial cure: a real milk bucket removes
        // the Radiation effect whether or not it clears the clock, so the bottle and the chocolate
        // versions match it rather than quietly leaving the source running.
        clearSources(player);

        // A milk bucket stripped Contamination on the way in, so put it back at the level still
        // deserved. Otherwise the HUD would claim you were clean while the clock kept running.
        int tier = tierFor(left);
        if (tier > 0) {
            player.addEffect(new MobEffectInstance(
                    ModEffects.CONTAMINATION, MobEffectInstance.INFINITE_DURATION, tier - 1, true, true));
        }
    }

    /**
     * True for any potion the cure ladder accepts: Instant Health, or Regeneration since 2026-08-14.
     * Drinkable, splash or lingering.
     *
     * Checked by EFFECT rather than by potion id so that Healing II, Regeneration II and any modded
     * or custom bottle granting either one all count, which is what "any kind" means.
     */
    private static boolean isCurePotion(ItemStack stack) {
        if (RadConfig.HEALING_POTION_CURES.get() && hasEffect(stack, MobEffects.HEAL)) return true;
        return RadConfig.REGEN_POTION_CURES.get() && hasEffect(stack, MobEffects.REGENERATION);
    }

    /**
     * The spread and decay pass, once a second by default.
     *
     * This is the ENTIRE per-tick cost of the fallout system: one pass over a map that only holds
     * contaminated chunks. An empty world does nothing at all.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!RadConfig.CHUNK_RADIATION.get()) return;
        if (event.getServer().getTickCount() % RadConfig.CHUNK_UPDATE_TICKS.get() != 0) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            ChunkRadiation rads = ChunkRadiation.get(level);
            rads.diffuse(
                    RadConfig.DIFFUSE_KEEP.get().floatValue(),
                    RadConfig.DIFFUSE_SIDE.get().floatValue(),
                    RadConfig.DIFFUSE_DIAGONAL.get().floatValue(),
                    RadConfig.CHUNK_DECAY.get().floatValue());

            // The ground rots on the same pass, so the visuals stay in step with the numbers.
            LandDecay.tick(level, rads);
        }
    }

    /**
     * A thrown Healing or Regeneration potion cures everyone it lands on. This covers BOTH splash
     * and lingering, because a lingering potion is also a ThrownPotion at the moment it breaks.
     *
     * A separate hook is needed because instant effects never go through addEffect. Vanilla calls
     * applyInstantenousEffect directly, so the drink hook cannot see a thrown one.
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownPotion potion)) return;
        if (!(potion.level() instanceof ServerLevel level)) return;

        if (isCurePotion(potion.getItem())) {
            // Matches vanilla's splash radius: 4 wide, 2 tall.
            AABB box = potion.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
            for (ServerPlayer hit : level.getEntitiesOfClass(ServerPlayer.class, box)) {
                cure(hit);
            }
        }

        // Paul, 2026-08-13: healing AND regeneration both heal the land, not only the player.
        boolean heals = hasEffect(potion.getItem(), MobEffects.HEAL)
                || hasEffect(potion.getItem(), MobEffects.REGENERATION);
        if (RadConfig.REGEN_CLEANS_LAND.get() && heals) {
            decontaminate(level, potion, potion.getItem().is(Items.LINGERING_POTION));
            return;
        }

        contaminateFromPotion(level, potion);
    }

    /**
     * A thrown Radiation potion dirties the ground where it lands. Paul's spec, 2026-08-13.
     *
     * Deliberately small and short: one chunk, and few enough rads that it decays back under
     * min_dose_rads in about a Minecraft day (two for the strong version), and few enough that a
     * SINGLE healing or regeneration potion cleans it up. A thrown potion is a nuisance, not a
     * disaster. Reactor blasts are the disaster.
     */
    private static void contaminateFromPotion(ServerLevel level, ThrownPotion potion) {
        if (!RadConfig.CHUNK_RADIATION.get()) return;

        PotionContents contents = potion.getItem().get(DataComponents.POTION_CONTENTS);
        if (contents == null) return;

        double rads = 0.0D;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getEffect() != ModEffects.RADIATION) continue;
            rads = effect.getAmplifier() >= 1
                    ? RadConfig.POTION_RADS_STRONG.get()
                    : RadConfig.POTION_RADS.get();
        }
        if (rads <= 0.0D) return;

        ChunkRadiation.get(level).add(new ChunkPos(potion.blockPosition()), (float) rads);
    }

    /** Shared with RadCuring, which uses it to decide whether a thrown potion infects animals. */
    public static boolean isRadiationPotion(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getEffect() == ModEffects.RADIATION) return true;
        }
        return false;
    }

    /**
     * Healing and Regeneration heal the land, not just the player. Paul's idea, 2026-08-13.
     *
     * This is the cleanup crew's actual tool. Breaking irradiated blocks removes the source, but the
     * contamination in the chunk map outlives it, and until now there was no way to scrub that. A
     * thrown Regeneration potion now takes a bite out of the chunk it lands in.
     *
     * Cheap for the same reason everything else here is cheap: it edits one float.
     */
    private static void decontaminate(ServerLevel level, ThrownPotion potion, boolean lingering) {
        double amount = RadConfig.REGEN_CLEANUP_RADS.get();
        if (lingering) amount *= RadConfig.REGEN_LINGERING_MULTIPLIER.get();

        ChunkRadiation rads = ChunkRadiation.get(level);
        ChunkPos centre = new ChunkPos(potion.blockPosition());

        // The centre chunk takes the full dose of cleanup, the eight around it take a quarter, so a
        // thrown potion near a chunk border still does something useful on both sides.
        rads.add(centre, (float) -amount);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                rads.add(new ChunkPos(centre.x + dx, centre.z + dz), (float) (-amount * 0.25D));
            }
        }
    }

    private static boolean hasEffect(ItemStack stack, Holder<net.minecraft.world.effect.MobEffect> wanted) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getEffect() == wanted) return true;
        }
        return false;
    }

    /**
     * Zero the clock, drop the state effect, and REMOVE WHATEVER IS STILL IRRADIATING THEM.
     *
     * 🚨 That last part is the fix for 2026-08-14, and it is the whole bug. A cure taken while the
     * Radiation effect was still running zeroed the clock, then the very next dose tick three
     * seconds later read the effect that was never removed and started the clock again. In game
     * that reads as "the healing potion did nothing".
     *
     * 🔑 The tell was that milk worked and healing did not: a vanilla milk BUCKET strips every
     * effect on the way in, so it took the source with it by accident. Nothing else did.
     *
     * ⚠️ This only clears effect sources. A player standing in a zone, in a hot chunk, or holding
     * uranium is re-dosed immediately and SHOULD be. Curing does not make you immune, it makes you
     * clean, which is the whole point of the cleanup loop.
     */
    private static void cure(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        boolean wasSick = data.getInt(EXPOSURE_KEY) > 0;
        data.putInt(EXPOSURE_KEY, 0);
        data.putInt(STAGE_KEY, 0);
        player.removeEffect(ModEffects.CONTAMINATION);
        clearSources(player);

        if (wasSick && RadConfig.STAGE_MESSAGES.get()) {
            player.displayClientMessage(
                    Component.literal("The radiation is out of you.").withStyle(ChatFormatting.GREEN), true);
        }
    }

    /** The effect-borne sources. Zones, chunks and inventory are places, not effects, and stay. */
    private static void clearSources(ServerPlayer player) {
        player.removeEffect(ModEffects.RADIATION);
        player.removeEffect(ModEffects.WEAK_RADIATION);
    }

    /**
     * Radiation damage, used only by the deadline now.
     *
     * ❌ Nothing in the tier ladder calls this any more. HBM inflicts no radiation damage of its own
     * below 1000 rads - Poison and Wither do that work - and neither do we. Kept because the damage
     * type carries the death message and the statistics.
     */
    private static void hurt(ServerPlayer player, float amount) {
        Holder<DamageType> type = player.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(RADIATION_DAMAGE);
        player.hurt(new DamageSource(type), amount);
    }
}
