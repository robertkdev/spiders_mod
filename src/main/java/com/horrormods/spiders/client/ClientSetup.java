// ClientSetup.java
package com.horrormods.spiders.client;

import com.horrormods.spiders.Spiders;
import com.horrormods.spiders.client.renderer.GroundSpiderRenderer;
import com.horrormods.spiders.init.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Spiders.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GROUND_SPIDER.get(), GroundSpiderRenderer::new);
    }
}