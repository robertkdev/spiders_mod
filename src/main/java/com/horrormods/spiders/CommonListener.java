package com.horrormods.spiders;

import com.horrormods.spiders.entity.GroundSpiderEntity; // Import new entity
import com.horrormods.spiders.registry.EntityRegistry;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Spiders.ModID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CommonListener {
    public CommonListener() {
    }

    @SubscribeEvent
    public static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        // Give attributes to the spider
        event.put(EntityRegistry.GROUND_SPIDER.get(), GroundSpiderEntity.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 16.0) // Minecraft spider health
                .add(Attributes.MOVEMENT_SPEED, 0.3) // Minecraft spider speed
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .build());
    }
}