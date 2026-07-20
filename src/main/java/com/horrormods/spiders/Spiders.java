// SpidersMod.java
package com.horrormods.spiders;

import com.mojang.logging.LogUtils;
import com.horrormods.spiders.network.PacketHandler;
import com.horrormods.spiders.registry.BlockEntityRegistry;
import com.horrormods.spiders.registry.BlockRegistry;
import com.horrormods.spiders.registry.EntityRegistry;
import com.horrormods.spiders.registry.ItemRegistry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Spiders.ModID)
public class Spiders {
    public static final String ModID = "spiders";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Spiders() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BlockRegistry.BLOCKS.register(bus);
        BlockEntityRegistry.BLOCK_ENTITIES.register(bus);
        EntityRegistry.ENTITIES.register(bus);
        ItemRegistry.ITEMS.register(bus);
        PacketHandler.register();
    }
}
