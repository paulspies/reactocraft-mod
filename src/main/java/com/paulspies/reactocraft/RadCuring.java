package com.paulspies.reactocraft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Map;

/**
 * Curing irradiated animals.
 *
 * Create Nuclear adds three irradiated mobs but no way back. Right-clicking one with a milk bucket
 * turns it into its healthy vanilla counterpart, which matches the rest of the pack: milk is what
 * clears radiation off a player, so it should clear it off an animal too.
 *
 * The animal is never killed. It is replaced in place, keeping its name, its age and its health
 * fraction, so a named pet stays the same pet.
 */
public final class RadCuring {
    private RadCuring() {}

    private static final Map<String, EntityType<?>> CURES = Map.of(
            "createnuclear:irradiated_cat", EntityType.CAT,
            "createnuclear:irradiated_chicken", EntityType.CHICKEN,
            "createnuclear:irradiated_wolf", EntityType.WOLF);

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (!held.is(Items.MILK_BUCKET)) return;

        Entity target = event.getTarget();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        EntityType<?> cured = CURES.get(id.toString());
        if (cured == null) return;

        // Cancel either way, so a milk bucket is never wasted drinking on a cure that then runs
        // server-side and looks like a double action to the client.
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);

        if (!(target.level() instanceof ServerLevel level)) return;

        Entity replacement = cured.create(level);
        if (replacement == null) return;

        replacement.moveTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
        if (target.hasCustomName()) {
            replacement.setCustomName(target.getCustomName());
            replacement.setCustomNameVisible(target.isCustomNameVisible());
        }
        if (target instanceof LivingEntity oldMob && replacement instanceof LivingEntity newMob) {
            float fraction = oldMob.getHealth() / oldMob.getMaxHealth();
            newMob.setHealth(Math.max(1.0F, newMob.getMaxHealth() * fraction));
        }
        if (replacement instanceof Mob mob) {
            mob.setPersistenceRequired();
        }

        target.discard();
        level.addFreshEntity(replacement);

        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                replacement.getX(), replacement.getY() + 0.5D, replacement.getZ(), 12, 0.4D, 0.5D, 0.4D, 0.0D);
        level.playSound(null, replacement.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.NEUTRAL, 1.0F, 1.4F);

        if (!player.getAbilities().instabuild) {
            player.setItemInHand(event.getHand() == InteractionHand.MAIN_HAND
                    ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, new ItemStack(Items.BUCKET));
        }
    }
}
