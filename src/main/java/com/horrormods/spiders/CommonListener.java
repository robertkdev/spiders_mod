package com.horrormods.spiders;


import com.horrormods.spiders.registry.EntityRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(
        modid = "spiders",
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public class CommonListener {
    public CommonListener() {
    }

    @SubscribeEvent
    public static void registerEntityAttributes(EntityAttributeCreationEvent event) {
            event.put((EntityType) EntityRegistry.GEO_EXAMPLE_ENTITY.get(), PathfinderMob.createMobAttributes().add(Attributes.FOLLOW_RANGE, 16.0).add(Attributes.MAX_HEALTH, 1.0).build());
    }
}
