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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

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
        rate = Math.max(rate, inventoryRate(player));
        return rate;
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

        double worst = 0.0D;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            for (String entry : RadConfig.RADIOACTIVE_ITEMS.get()) {
                int eq = entry.indexOf('=');
                if (eq < 0 || !entry.substring(0, eq).equals(id)) continue;
                try {
                    worst = Math.max(worst, Double.parseDouble(entry.substring(eq + 1)));
                } catch (NumberFormatException ignored) {
                    // A malformed config line should not take the engine down with it.
                }
            }
        }
        return worst;
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
        if (!isMilk(event.getItem())) return;

        CompoundTag data = player.getPersistentData();
        int exposure = data.getInt(EXPOSURE_KEY);

        if (exposure <= RadConfig.MILK_FULL_CURE_LIMIT.get()) {
            data.putInt(EXPOSURE_KEY, 0);
            // A vanilla milk BUCKET strips every effect by itself, but Farmer's Delight's bottle may
            // not, so Contamination is removed explicitly rather than assumed gone.
            player.removeEffect(ModEffects.CONTAMINATION);
            return;
        }

        int left = Math.max(0, exposure - RadConfig.MILK_PARTIAL_RELIEF.get());
        data.putInt(EXPOSURE_KEY, left);

        // Vanilla milk stripped Contamination on the way in, so put it back at the level that is
        // still deserved. Otherwise the HUD would claim you were clean while the clock kept running.
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
     * A thrown healing potion cures everyone it lands on. This covers BOTH splash and lingering,
     * because a lingering potion is also a ThrownPotion at the moment it breaks.
     *
     * A separate hook is needed because instant effects never go through addEffect. Vanilla calls
     * applyInstantenousEffect directly, so the drink hook cannot see a thrown one.
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!RadConfig.HEALING_POTION_CURES.get()) return;
        if (!(event.getProjectile() instanceof ThrownPotion potion)) return;
        if (!isHealingPotion(potion.getItem())) return;
        if (!(potion.level() instanceof ServerLevel level)) return;

        // Matches vanilla's splash radius: 4 wide, 2 tall.
        AABB box = potion.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
        for (ServerPlayer hit : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            cure(hit);
        }
    }

    /** Milk in any container the config lists. Vanilla bucket plus Farmer's Delight's bottle. */
    private static boolean isMilk(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return RadConfig.MILK_ITEMS.get().contains(id);
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
