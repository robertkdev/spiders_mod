// SpidersMod.java
package com.horrormods.spiders;

import com.horrormods.spiders.init.ModEntities;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Spiders.MODID)
public class Spiders {
    public static final String MODID = "spiders";

    public Spiders() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.register(modEventBus);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(ModEntities::registerEntityAttributes);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {

        });
    }

}