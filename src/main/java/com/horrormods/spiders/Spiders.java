// SpidersMod.java
package com.horrormods.spiders;

import com.horrormods.spiders.network.PacketHandler;
import com.horrormods.spiders.registry.EntityRegistry;
import com.horrormods.spiders.registry.ItemRegistry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Spiders.ModID)
public class Spiders {
    public static final String ModID = "spiders";

    public Spiders() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        EntityRegistry.ENTITIES.register(bus);
        ItemRegistry.ITEMS.register(bus);
        PacketHandler.register();
    }
}