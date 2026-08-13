package com.paulspies.reactocraft;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * ReactoCraft — radiation potions for Paul's Minecraft server.
 *
 * The mod deliberately does almost nothing on its own. It registers four mob effects and eight
 * potions so they exist in the synced registries, and it registers the brewing recipes that make
 * them. Everything the effects actually DO is handled by the `rad` datapack on the server, which
 * reads the effect off the player and sets `rad.shield` or applies a dose. That split is on
 * purpose: balance numbers can be changed with /reload instead of rebuilding this jar and
 * reinstalling it on every client.
 */
@Mod(ReactoCraft.MODID)
public class ReactoCraft {
    public static final String MODID = "reactocraft";

    public ReactoCraft(IEventBus modBus, ModContainer container) {
        ModEffects.EFFECTS.register(modBus);
        ModPotions.POTIONS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        modBus.addListener(ReactoCraft::registerAttributes);

        // Creative tab contents are a MOD bus event, not the game bus.
        modBus.register(ModCreativeTabs.class);

        // SERVER type, so the file lands in the server's config folder and never ships to clients.
        container.registerConfig(ModConfig.Type.SERVER, RadConfig.SPEC, "reactocraft-server.toml");

        // Both fire on the game bus, not the mod bus.
        NeoForge.EVENT_BUS.register(ModBrewing.class);
        NeoForge.EVENT_BUS.register(RadEngine.class);
        NeoForge.EVENT_BUS.register(RadCuring.class);
    }

    /** Without this the cow has no health or speed and the game refuses to spawn it. */
    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.IRRADIATED_COW.get(), ModEntities.cowAttributes().build());
    }
}
