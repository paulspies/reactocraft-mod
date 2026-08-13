package com.paulspies.reactocraft;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Hides Create Nuclear's own radiation potions.
 *
 * WHY. Create Nuclear registers three potions of its own - potion_of_radiation_1, _2 and
 * _augment_1 - and gives them NO brewing recipe, so they are unobtainable in survival and exist
 * only as creative and JEI clutter. They are yellow, they are called "Potion of Radiation", and
 * they sit right next to ours in the search results. Paul, 2026-08-12: "can we get rid of their
 * potion?"
 *
 * 🚨 HIDDEN, NOT DELETED. Registry entries belong to the mod that registered them. Stripping
 * another mod's entries needs a mixin reaching into Create Nuclear, breaks silently on their next
 * update, and their own code may reference the potions internally. /give still works, which is the
 * correct amount of damage to do to someone else's mod.
 *
 * 🔑 WHY THIS ALSO CLEARS JEI. JEI builds its ingredient list from the creative mode tabs, so
 * removing an entry here removes it from JEI as well. That is what makes this better than JEI's own
 * hide feature, which writes to a per-client blacklist that Paul, Kolten and SoundCar would each
 * have to set up by hand. This ships in the jar they all already run.
 *
 * ⚠️ Deliberately NOT config-gated. The engine's config is SERVER type, and creative tabs are built
 * on the client before it ever connects to a server, so a server config cannot reach this code.
 * Reversing it means deleting this class and its registration in ReactoCraft.
 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    private static final String HIDE_NAMESPACE = "createnuclear";

    @SubscribeEvent
    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        List<ItemStack> doomed = new ArrayList<>();

        // Two separate lists: what the tab shows, and what the search tab shows. JEI reads the
        // search list, so missing it would hide them from the creative tab and leave them in JEI.
        collect(event.getParentEntries(), doomed);
        collect(event.getSearchEntries(), doomed);

        // Collected first, removed after. Removing while iterating the sets is asking for it.
        for (ItemStack stack : doomed) {
            event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    private static void collect(Collection<ItemStack> from, List<ItemStack> into) {
        for (ItemStack stack : from) {
            if (!isPotionItem(stack)) continue;
            if (!isFromHiddenNamespace(stack)) continue;
            into.add(stack);
        }
    }

    private static boolean isPotionItem(ItemStack stack) {
        return stack.is(Items.POTION)
                || stack.is(Items.SPLASH_POTION)
                || stack.is(Items.LINGERING_POTION)
                || stack.is(Items.TIPPED_ARROW);
    }

    /** True when the bottle's potion type was registered by the namespace we are hiding. */
    private static boolean isFromHiddenNamespace(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty()) return false;

        var key = BuiltInRegistries.POTION.getKey(contents.potion().get().value());
        return key != null && key.getNamespace().equals(HIDE_NAMESPACE);
    }
}
