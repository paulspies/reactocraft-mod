package com.paulspies.reactocraft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A cow that has been somewhere it should not have been.
 *
 * 🚨 CREATE NUCLEAR DOES NOT SHIP ONE. It has exactly three irradiated mobs, cat, chicken and wolf,
 * confirmed from its lang file and its entity textures. Paul was sure there was a cow; there is not,
 * so this is ours. The texture is Paul's own art, generated 2026-08-13 and masked to the vanilla cow
 * UV footprint. No Create Nuclear or Mojang art is used.
 *
 * THE THREE THINGS IT DOES, Paul's spec 2026-08-13:
 *
 *   1. EMPTY BUCKET  -> a bucket of Liquid Uranium instead of milk. The whole point of it.
 *      ⚠️ That bucket is on the inventory radiation list, so walking home with it costs you.
 *   2. MILK BUCKET   -> cured, turns back into an ordinary cow. Handled in RadCuring with the other
 *      three, keeping its name, age and health fraction.
 *   3. HIT IT        -> it hits back. Paul: "like a spider in daytime", meaning neutral until
 *      provoked, then it comes for you. It will not start a fight.
 *
 * Breeding two of them produces another irradiated calf. Contamination breeds true.
 */
public class IrradiatedCow extends Cow {

    private static final ResourceLocation URANIUM_BUCKET =
            ResourceLocation.fromNamespaceAndPath("createnuclear", "uranium_bucket");

    public IrradiatedCow(EntityType<? extends Cow> type, Level level) {
        super(type, level);
    }

    /**
     * Neutral until provoked, then it fights. Paul's "like a spider in daytime".
     *
     * HurtByTargetGoal is what makes it retaliate rather than hunt: it only ever picks a target that
     * has already hit it. The cow keeps every normal cow goal, so it still wanders, eats and breeds
     * when nobody is bothering it.
     */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25D, true));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // An empty bucket draws uranium, not milk. Everything else, including the milk bucket that
        // cures it, falls through to the normal cow behaviour and to RadCuring's event hook.
        if (held.is(Items.BUCKET) && !this.isBaby()) {
            player.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
            ItemStack uranium = new ItemStack(BuiltInRegistries.ITEM.get(URANIUM_BUCKET));

            // If Create Nuclear ever goes missing the lookup gives AIR, and handing the player an
            // empty stack would silently eat their bucket. Better to do nothing at all.
            if (uranium.isEmpty()) return InteractionResult.PASS;

            player.setItemInHand(hand, ItemStack.isSameItem(held, uranium)
                    ? held
                    : shrinkAndReplace(player, held, uranium));
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    private ItemStack shrinkAndReplace(Player player, ItemStack held, ItemStack given) {
        held.shrink(1);
        if (held.isEmpty()) return given;
        if (!player.getInventory().add(given)) player.drop(given, false);
        return held;
    }

    @Override
    public Cow getBreedOffspring(net.minecraft.server.level.ServerLevel level,
                                 net.minecraft.world.entity.AgeableMob other) {
        // Contamination breeds true. Two irradiated cows do not produce a healthy calf.
        return ModEntities.IRRADIATED_COW.get().create(level);
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos,
                                 net.minecraft.world.level.block.state.BlockState state) {
        this.playSound(SoundEvents.COW_STEP, 0.15F, 0.9F);
    }

    // Sounds are the vanilla cow's, unaltered. Paul, 2026-08-13: "you also make the same cow sound."
    // An earlier version dropped the ambient pitch to 0.7 to make it sound sick; that is removed.
    // Everything a cow does audibly, this does identically, which is inherited from Cow with no
    // override at all.
}
