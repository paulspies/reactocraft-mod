package com.paulspies.reactocraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * The radiation engine.
 *
 * Runs once every RadConfig.DOSE_INTERVAL ticks per player and does four things: work out how much
 * radiation is on the player, work out how much of it is shielded, apply the dose, then stage the
 * symptoms off accumulated exposure.
 *
 * 🚨 On cost. The mod this replaces swept a cube around every player every single tick, per emitter
 * definition, with no index — radius 8 is 4,913 block reads, and it measured 136 ms of a 50 ms
 * budget. Nothing here does that. Zones are entities, so the game's own index finds them. Block
 * shielding is six short rays and only runs when a dose is actually landing, so a player standing
 * in a clean world pays for one entity query every three seconds and nothing else.
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

        int interval = RadConfig.DOSE_INTERVAL.get();
        if (player.tickCount % interval != 0) return;

        Incoming incoming = incomingStrength(player);
        int strength = incoming.strength();
        int dose = 0;

        if (strength > 0) {
            int shield = shieldPercent(player);
            if (player.hasEffect(ModEffects.RAD_WEAKNESS)) {
                // Rad Weakness cancels every protection and doubles what lands.
                shield = 0;
                strength *= 2;
            }
            dose = Math.max(0, strength * (100 - shield) / 100);
        }

        // A "weak" dose must stay weak. Without this ceiling, Weak Radiation at strength 5 reaches
        // the blindness stage in about 40 seconds, which is the opposite of what it is for.
        int ceiling = incoming.mildOnly() ? RadConfig.WEAK_CAP.get() : 100;
        applyExposure(player, dose, ceiling);
    }

    /** What is dosing this player, and whether the only source is a mild one. */
    private record Incoming(int strength, boolean mildOnly) {}

    private static Incoming incomingStrength(ServerPlayer player) {
        int strength = 0;
        int mild = 0;

        if (player.hasEffect(ModEffects.RADIATION)) {
            strength = player.getEffect(ModEffects.RADIATION).getAmplifier() >= 1 ? 50 : 20;
        }
        if (player.hasEffect(ModEffects.WEAK_RADIATION)) {
            mild = 5;
        }

        // Zones placed by /function rad:zone_* are entities tagged "rad" plus "rad_<strength>".
        double r = RadConfig.ZONE_RADIUS.get();
        AABB box = player.getBoundingBox().inflate(r);
        List<Entity> zones = player.level().getEntities(player, box, e -> e.getTags().contains(ZONE_TAG));
        for (Entity zone : zones) {
            for (String tag : zone.getTags()) {
                if (!tag.startsWith("rad_")) continue;
                try {
                    strength = Math.max(strength, Integer.parseInt(tag.substring(4)));
                } catch (NumberFormatException ignored) {
                    // Tags like "rad_zone" are not strengths. Skipping them is correct.
                }
            }
        }

        boolean mildOnly = strength == 0 && mild > 0;
        return new Incoming(Math.max(strength, mild), mildOnly);
    }

    /** Total shielding, 0-100. Potion, then suit, then the six sides of the room. */
    private static int shieldPercent(ServerPlayer player) {
        int shield = 0;

        if (player.hasEffect(ModEffects.RAD_RESISTANCE)) {
            shield += player.getEffect(ModEffects.RAD_RESISTANCE).getAmplifier() >= 1
                    ? RadConfig.RESISTANCE_II.get()
                    : RadConfig.RESISTANCE_I.get();
        }
        if (wearingFullSuit(player)) {
            shield += RadConfig.SUIT_SHIELD.get();
        }
        shield += blockShield(player);

        return Math.min(100, shield);
    }

    private static boolean wearingFullSuit(ServerPlayer player) {
        int pieces = 0;
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id.getPath().contains("anti_radiation")) pieces++;
        }
        return pieces >= 4;
    }

    /**
     * Kolten's sealed-room test. Six directions, floor, ceiling and the four walls. A side counts as
     * shielded if a shielding block is found within block_shield_range along that ray.
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
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                if (shielding.contains(id.toString())) sides++;
                // First solid block on this ray settles the side either way. A concrete wall does
                // not shield you, and it also stops you seeing the lead behind it.
                break;
            }
        }
        return sides * perSide;
    }

    /**
     * Build or decay exposure, then stage the symptoms off it.
     *
     * @param ceilingPct the highest percentage this source is allowed to push exposure to. 100 for
     *                   real radiation; lower for mild sources that must never become lethal.
     */
    private static void applyExposure(ServerPlayer player, int dose, int ceilingPct) {
        CompoundTag data = player.getPersistentData();
        int max = RadConfig.EXPOSURE_MAX.get();
        int exposure = data.getInt(EXPOSURE_KEY);

        int ceiling = max * ceilingPct / 100;
        int decay = RadConfig.EXPOSURE_DECAY.get();

        if (dose <= 0) {
            exposure -= decay;
        } else if (exposure < ceiling) {
            exposure = Math.min(ceiling, exposure + dose);
        } else {
            // Already above what this source can cause, so it can only fade. This is what stops a
            // mild source from pinning someone at a lethal level after a real dose.
            exposure -= decay;
        }

        exposure = Math.max(0, Math.min(max, exposure));
        data.putInt(EXPOSURE_KEY, exposure);

        if (exposure <= 0) return;

        int pct = exposure * 100 / max;
        int interval = RadConfig.DOSE_INTERVAL.get();
        int effectTicks = interval + 40; // outlast the gap so the icon does not flicker

        if (pct >= RadConfig.STAGE_NAUSEA_AT.get()) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, effectTicks, 0, true, false));
        }
        if (pct >= RadConfig.STAGE_BLIND_AT.get()) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, effectTicks, 0, true, false));
        }
        if (pct >= RadConfig.STAGE_DAMAGE_AT.get()) {
            // Damage ramps with how far past the damage stage you are, so it creeps rather than
            // arriving all at once. This is the "slower wither" feel.
            int span = Math.max(1, 100 - RadConfig.STAGE_DAMAGE_AT.get());
            double ramp = (double) (pct - RadConfig.STAGE_DAMAGE_AT.get()) / span;
            float amount = (float) (RadConfig.DAMAGE_PER_DOSE.get() * (1.0D + ramp * 3.0D));
            hurt(player, amount);
        }
    }

    private static void hurt(ServerPlayer player, float amount) {
        Holder<DamageType> type = player.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(RADIATION_DAMAGE);
        player.hurt(new DamageSource(type), amount);
    }
}
