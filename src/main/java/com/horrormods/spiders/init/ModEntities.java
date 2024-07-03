package com.horrormods.spiders.init;

import com.horrormods.spiders.Spiders;
import com.horrormods.spiders.entity.GroundSpiderEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Spiders.MODID);

    public static final RegistryObject<EntityType<GroundSpiderEntity>> GROUND_SPIDER = ENTITIES.register("ground_spider",
            () -> EntityType.Builder.of(GroundSpiderEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 0.8f)
                    .build("ground_spider"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }

    public static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(GROUND_SPIDER.get(), GroundSpiderEntity.createAttributes().build());
    }
}