package com.paulspies.reactocraft;

import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Everything that only exists on a client.
 *
 * 🚨 THIS CLASS MUST NEVER BE TOUCHED BY THE SERVER. It imports client-only rendering classes, and
 * loading it on a dedicated server would throw exactly the error that killed MobsPlus on 2026-08-09:
 * "Attempted to load class net/minecraft/client/... for invalid dist DEDICATED_SERVER".
 *
 * The Dist.CLIENT on the annotation is what prevents that. It is not decoration.
 */
@EventBusSubscriber(modid = ReactoCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {}

    private static final ResourceLocation IRRADIATED_COW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ReactoCraft.MODID, "textures/entity/irradiated_cow.png");

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.IRRADIATED_COW.get(), IrradiatedCowRenderer::new);
    }

    /** Reuses the vanilla cow model entirely. Only the texture differs. */
    public static class IrradiatedCowRenderer extends CowRenderer {
        public IrradiatedCowRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(Cow entity) {
            return IRRADIATED_COW_TEXTURE;
        }
    }
}
