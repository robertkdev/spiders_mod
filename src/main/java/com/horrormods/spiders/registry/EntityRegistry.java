package com.horrormods.spiders.registry;

import com.horrormods.spiders.Spiders;
import com.horrormods.spiders.entity.GroundSpiderEntity; // Import your new entity
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES;

    // Change this to your ground spider
    public static final RegistryObject<EntityType<GroundSpiderEntity>> GROUND_SPIDER;

    public EntityRegistry() {
    }

    public static <T extends Entity> RegistryObject<EntityType<T>> buildEntity(EntityType.EntityFactory<T> entity, Class<T> entityClass, float width, float height) {
        String name = entityClass.getSimpleName().toLowerCase().replace("entity", ""); // "groundspiderentity" -> "groundspider"
        return ENTITIES.register(name, () -> {
            return EntityType.Builder.of(entity, MobCategory.MONSTER) // MONSTER is better for a spider
                    .sized(width, height).build(name);
        });
    }

    static {
        ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Spiders.ModID);
        // Register the ground spider with its size. Adjust width/height as needed.
        GROUND_SPIDER = buildEntity(GroundSpiderEntity::new, GroundSpiderEntity.class, 1.4F, 0.9F);
    }
}