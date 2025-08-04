package com.horrormods.spiders;

import com.horrormods.spiders.client.renderer.entity.GroundSpiderRenderer; // Import new renderer
import com.horrormods.spiders.registry.EntityRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Spiders.ModID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientListener {

    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        // Register the renderer for the spider
        event.registerEntityRenderer(EntityRegistry.GROUND_SPIDER.get(), GroundSpiderRenderer::new);
    }
}