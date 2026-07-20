package com.horrormods.spiders.registry;

import com.horrormods.spiders.Spiders;
import com.horrormods.spiders.item.PathingToolItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ItemRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Spiders.ModID);

    // In ItemRegistry.java

    public static final RegistryObject<Item> PATHING_TOOL = ITEMS.register("pathing_tool",
            () -> new PathingToolItem(new Item.Properties().tab(CreativeModeTab.TAB_TOOLS).stacksTo(1)));
    public static final RegistryObject<Item> SINGLE_THREAD_WEB = ITEMS.register("single_thread_web",
            () -> new BlockItem(BlockRegistry.SINGLE_THREAD_WEB.get(),
                    new Item.Properties().tab(CreativeModeTab.TAB_DECORATIONS)));
    public static final RegistryObject<Item> GROUND_SPIDER_SPAWN_EGG = ITEMS.register("ground_spider_spawn_egg",
            () -> new ForgeSpawnEggItem(EntityRegistry.GROUND_SPIDER, 0x473e35, 0xa80e0e,
                    new Item.Properties().tab(CreativeModeTab.TAB_MISC)));
}
