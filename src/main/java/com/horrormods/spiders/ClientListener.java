package com.horrormods.spiders;


import com.horrormods.spiders.registry.EntityRegistry;
import com.horrormods.spiders.client.renderer.entity.ExampleGeoRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Spiders.ModID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientListener {

    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {

            event.registerEntityRenderer(EntityRegistry.GEO_EXAMPLE_ENTITY.get(), ExampleGeoRenderer::new);
    }

    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.AddLayers event) {


    }

    @SubscribeEvent
    public static void registerRenderers(final FMLClientSetupEvent event) {

    }
}
