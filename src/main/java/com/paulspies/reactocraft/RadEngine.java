package com.paulspies.reactocraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    private static final String ZONE_TAG = "rad";

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

        double rate = incomingRate(player);
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

        applyExposure(player, dose);
    }

    /**
     * How fast this player is being contaminated, where 1.0 means the exposure clock runs in real
     * time. Sources do not stack; the strongest one wins, which keeps a weak zone from adding to a
     * strong one and quietly doubling the difficulty.
     */
    private static double incomingRate(ServerPlayer player) {
        double rate = 0.0D;

        if (player.hasEffect(ModEffects.RADIATION)) {
            rate = player.getEffect(ModEffects.RADIATION).getAmplifier() >= 1
                    ? RadConfig.RATE_RADIATION_STRONG.get()
                    : RadConfig.RATE_RADIATION.get();
        }
        // Weak Radiation is handled in onPlayerTick and never reaches the clock. See there for why.

        rate = Math.max(rate, zoneRate(player));
        rate = Math.max(rate, chunkRate(player));
        rate = Math.max(rate, inventoryRate(player));
        return rate;
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

    /** Add to the exposure clock, then apply whichever symptoms it has earned. */
    private static void applyExposure(ServerPlayer player, double dose) {
        CompoundTag data = player.getPersistentData();
        int intervalSeconds = Math.max(1, RadConfig.DOSE_INTERVAL.get() / 20);
        int exposure = data.getInt(EXPOSURE_KEY);

        // 🚨 Once contaminated, it keeps getting worse on its own. A Radiation potion only lasts
        // three minutes but the ladder runs five, so without this a single dose could never reach
        // the damage stage no matter how long you waited. Paul caught that in play on 2026-08-12.
        double advance = dose;
        if (exposure > 0) advance = Math.max(advance, RadConfig.SELF_ADVANCE_RATE.get());

        if (advance > 0.0D) {
            exposure += Math.max(1, (int) Math.round(intervalSeconds * advance));
        } else {
            exposure -= RadConfig.NATURAL_RECOVERY.get();
        }

        int hardMax = RadConfig.STAGE_6_WITHER.get() * 2;
        exposure = Math.max(0, Math.min(hardMax, exposure));
        data.putInt(EXPOSURE_KEY, exposure);

        if (exposure <= 0) {
            player.removeEffect(ModEffects.CONTAMINATION);
            return;
        }

        int stage = stageFor(exposure);
        if (stage == 0) return;

        // Infinite, because it does not wear off. The HUD shows the level as a Roman numeral and an
        // infinity marker instead of a countdown, which says "this needs treating" on its own.
        MobEffectInstance current = player.getEffect(ModEffects.CONTAMINATION);
        if (current == null || current.getAmplifier() != stage - 1) {
            player.addEffect(new MobEffectInstance(
                    ModEffects.CONTAMINATION, MobEffectInstance.INFINITE_DURATION, stage - 1, true, true));
        }

        // Long enough to outlast the gap between doses so the icons hold steady.
        int ticks = RadConfig.DOSE_INTERVAL.get() + 40;

        if (stage >= 1) player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks, ramp(stage, 1), true, false));
        if (stage >= 2) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, ramp(stage, 2), true, false));
        if (stage >= 3) player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, ramp(stage, 3), true, false));
        if (stage >= 4) player.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, ramp(stage, 4), true, false));
        if (stage >= 6) player.addEffect(new MobEffectInstance(MobEffects.WITHER, ticks, ramp(stage, 6), true, false));

        if (stage >= 5) {
            // Ramps past the threshold, so standing in it gets worse rather than sitting at a trickle.
            int at = RadConfig.STAGE_5_DAMAGE.get();
            double ramp = (double) (exposure - at) / Math.max(1, at);
            hurt(player, (float) (RadConfig.DAMAGE_PER_DOSE.get() * (1.0D + ramp * 3.0D)));
        }
    }

    /**
     * A symptom gets stronger the further past its own stage you are, capped by config.
     *
     * ⚠️ Nausea is the exception. Vanilla draws the same screen wobble at every amplifier, so its
     * level rises in the HUD but the picture does not change. Everything else genuinely intensifies.
     */
    private static int ramp(int stage, int stageIntroduced) {
        return Math.min(RadConfig.SYMPTOM_RAMP_CAP.get(), stage - stageIntroduced);
    }

    /** 0 means no symptoms yet. 1 through 6 are the stages, and also the Contamination level. */
    private static int stageFor(int exposure) {
        if (exposure >= RadConfig.STAGE_6_WITHER.get()) return 6;
        if (exposure >= RadConfig.STAGE_5_DAMAGE.get()) return 5;
        if (exposure >= RadConfig.STAGE_4_POISON.get()) return 4;
        if (exposure >= RadConfig.STAGE_3_MINING_FATIGUE.get()) return 3;
        if (exposure >= RadConfig.STAGE_2_SLOWNESS.get()) return 2;
        if (exposure >= RadConfig.STAGE_1_NAUSEA.get()) return 1;
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

        // A Potion of Healing, any level, wipes it completely at any stage. This is the way back
        // once you are past the milk limit. Paul's rule, 2026-08-12.
        if (RadConfig.HEALING_POTION_CURES.get()
                && event.getItem().is(Items.POTION)
                && isHealingPotion(event.getItem())) {
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
            // A vanilla milk BUCKET strips every effect by itself, but Farmer's Delight's bottle and
            // our own drinks do not, so Contamination goes explicitly rather than being assumed gone.
            player.removeEffect(ModEffects.CONTAMINATION);
            return;
        }

        int left = Math.max(0, exposure - relief);
        data.putInt(EXPOSURE_KEY, left);

        // A milk bucket stripped Contamination on the way in, so put it back at the level still
        // deserved. Otherwise the HUD would claim you were clean while the clock kept running.
        int stage = stageFor(left);
        if (stage > 0) {
            player.addEffect(new MobEffectInstance(
                    ModEffects.CONTAMINATION, MobEffectInstance.INFINITE_DURATION, stage - 1, true, true));
        }
    }

    /**
     * True for any potion item carrying Instant Health at any level, drinkable, splash or lingering.
     *
     * Checked by EFFECT rather than by potion id so that Healing, Healing II and any modded or
     * custom bottle granting instant health all count, which is what "any kind" means.
     */
    private static boolean isHealingPotion(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && hasHeal(contents);
    }

    private static boolean hasHeal(PotionContents contents) {
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getEffect() == MobEffects.HEAL) return true;
        }
        return false;
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
     * A thrown healing potion cures everyone it lands on. This covers BOTH splash and lingering,
     * because a lingering potion is also a ThrownPotion at the moment it breaks.
     *
     * A separate hook is needed because instant effects never go through addEffect. Vanilla calls
     * applyInstantenousEffect directly, so the drink hook cannot see a thrown one.
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownPotion potion)) return;
        if (!(potion.level() instanceof ServerLevel level)) return;

        if (RadConfig.HEALING_POTION_CURES.get() && isHealingPotion(potion.getItem())) {
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

    private static void cure(ServerPlayer player) {
        player.getPersistentData().putInt(EXPOSURE_KEY, 0);
        player.removeEffect(ModEffects.CONTAMINATION);
    }

    private static void hurt(ServerPlayer player, float amount) {
        Holder<DamageType> type = player.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(RADIATION_DAMAGE);
        player.hurt(new DamageSource(type), amount);
    }
}
