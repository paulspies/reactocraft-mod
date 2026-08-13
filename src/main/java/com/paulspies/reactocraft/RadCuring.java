package com.paulspies.reactocraft;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Map;

/**
 * Turning animals irradiated, and turning them back.
 *
 * Two directions, and they are deliberate mirrors of each other:
 *
 *   MILK BUCKET on an irradiated animal   -> the healthy vanilla animal
 *   SPLASH or LINGERING Radiation potion  -> the irradiated version
 *
 * Paul, 2026-08-13: "splashing any one of these mobs with one of our potions of radiation will turn
 * them into an irradiated mob version." So a thrown potion is not just a weapon, it is how you make
 * a uranium farm, and milk is how you undo it.
 *
 * 🚨 Animals are REPLACED, never killed. Name, age and health fraction all carry across, so a named
 * pet stays the same pet through both transformations.
 *
 * ⚠️ Three of the four irradiated types belong to Create Nuclear, so they are looked up by id rather
 * than by class. The cow is ours, because Create Nuclear never shipped one.
 */
public final class RadCuring {
    private RadCuring() {}

    /** Irradiated -> healthy. What a milk bucket does. */
    private static final Map<String, EntityType<?>> CURES = Map.of(
            "createnuclear:irradiated_cat", EntityType.CAT,
            "createnuclear:irradiated_chicken", EntityType.CHICKEN,
            "createnuclear:irradiated_wolf", EntityType.WOLF,
            "reactocraft:irradiated_cow", EntityType.COW);

    /** Healthy -> irradiated. What a thrown Radiation potion does. */
    private static final Map<String, String> INFECTS = Map.of(
            "minecraft:cat", "createnuclear:irradiated_cat",
            "minecraft:chicken", "createnuclear:irradiated_chicken",
            "minecraft:wolf", "createnuclear:irradiated_wolf",
            "minecraft:cow", "reactocraft:irradiated_cow",
            // A mooshroom is still a cow underneath, and it would be strange if it were immune.
            "minecraft:mooshroom", "reactocraft:irradiated_cow");

    // --- milk cures ------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!event.getItemStack().is(Items.MILK_BUCKET)) return;

        Entity target = event.getTarget();
        EntityType<?> cured = CURES.get(idOf(target));
        if (cured == null) return;

        // Cancel either way, so the bucket is never drunk as well as used.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!(target.level() instanceof ServerLevel level)) return;
        Entity made = transform(level, target, cured, ParticleTypes.HAPPY_VILLAGER);
        if (made == null) return;

        level.playSound(null, made.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.NEUTRAL, 1.0F, 1.4F);

        if (!player.getAbilities().instabuild) {
            player.setItemInHand(event.getHand(), new ItemStack(Items.BUCKET));
        }
    }

    // --- radiation potions infect -----------------------------------------------------------------

    @SubscribeEvent
    public static void onPotionImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownPotion potion)) return;
        if (!(potion.level() instanceof ServerLevel level)) return;
        if (!RadEngine.isRadiationPotion(potion.getItem())) return;

        // Vanilla's splash radius: 4 wide, 2 tall.
        AABB box = potion.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
        for (Entity target : level.getEntities(potion, box)) {
            String infected = INFECTS.get(idOf(target));
            if (infected == null) continue;

            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                    .get(ResourceLocation.parse(infected));
            if (type == null) continue;

            transform(level, target, type, ParticleTypes.SNEEZE);
            level.playSound(null, target.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED,
                    SoundSource.NEUTRAL, 0.7F, 1.6F);
        }
    }

    // --- the shared machinery ---------------------------------------------------------------------

    private static String idOf(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    /**
     * Replace one animal with another in place, carrying over everything that makes it *that*
     * animal. Never kills, so a named pet survives being irradiated and cured again.
     */
    private static Entity transform(ServerLevel level, Entity from, EntityType<?> to,
                                    net.minecraft.core.particles.SimpleParticleType particle) {
        Entity made = to.create(level);
        if (made == null) return null;

        made.moveTo(from.getX(), from.getY(), from.getZ(), from.getYRot(), from.getXRot());
        if (from.hasCustomName()) {
            made.setCustomName(from.getCustomName());
            made.setCustomNameVisible(from.isCustomNameVisible());
        }
        if (from instanceof LivingEntity oldMob && made instanceof LivingEntity newMob) {
            float fraction = oldMob.getHealth() / oldMob.getMaxHealth();
            newMob.setHealth(Math.max(1.0F, newMob.getMaxHealth() * fraction));
        }
        if (from instanceof net.minecraft.world.entity.AgeableMob oldAge
                && made instanceof net.minecraft.world.entity.AgeableMob newAge) {
            newAge.setAge(oldAge.getAge());
        }
        if (made instanceof Mob mob) {
            mob.setPersistenceRequired();
        }

        from.discard();
        level.addFreshEntity(made);
        level.sendParticles(particle, made.getX(), made.getY() + 0.5D, made.getZ(), 12, 0.4D, 0.5D, 0.4D, 0.0D);
        return made;
    }
}
