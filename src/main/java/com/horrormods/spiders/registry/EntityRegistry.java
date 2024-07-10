package com.horrormods.spiders.registry;


import com.horrormods.spiders.entity.GeoExampleEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES;
    public static final RegistryObject<EntityType<GeoExampleEntity>> GEO_EXAMPLE_ENTITY;
    public EntityRegistry() {
    }

    public static <T extends Entity> RegistryObject<EntityType<T>> buildEntity(EntityType.EntityFactory<T> entity, Class<T> entityClass, float width, float height) {
        String name = entityClass.getSimpleName().toLowerCase();
        return ENTITIES.register(name, () -> {
            return EntityType.Builder.of(entity, MobCategory.CREATURE).sized(width, height).build(name);
        });
    }

    static {
        ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "spiders");
        GEO_EXAMPLE_ENTITY = buildEntity(GeoExampleEntity::new, GeoExampleEntity.class, 0.7F, 1.3F);
    }
}
