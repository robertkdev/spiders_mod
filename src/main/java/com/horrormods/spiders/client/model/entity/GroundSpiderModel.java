package com.horrormods.spiders.client.model.entity;

import com.horrormods.spiders.client.EntityResources;
import com.horrormods.spiders.entity.GroundSpiderEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import software.bernie.geckolib3.model.provider.data.EntityModelData;

public class GroundSpiderModel extends AnimatedGeoModel<GroundSpiderEntity> {
    @Override
    public ResourceLocation getAnimationResource(GroundSpiderEntity entity) {
        return EntityResources.SPIDER_ANIMATIONS;
    }

    @Override
    public ResourceLocation getModelResource(GroundSpiderEntity entity) {
        return EntityResources.SPIDER_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(GroundSpiderEntity entity) {
        return EntityResources.SPIDER_TEXTURE;
    }

    /**
     * This method is called every frame to apply custom animations.
     * We use it here to rotate the head bone to look at the player.
     */
    @Override
    public void setCustomAnimations(GroundSpiderEntity animatable, int instanceId, AnimationEvent animationEvent) {
        // This is required to be called at the start of this method
        super.setCustomAnimations(animatable, instanceId, animationEvent);

        // Get the head bone from the model
        IBone head = this.getAnimationProcessor().getBone("head");

        // Get the model data that contains the head rotation values
        EntityModelData extraData = (EntityModelData) animationEvent.getExtraDataOfType(EntityModelData.class).get(0);

        // Check that the head bone is not null
        if (head != null) {
            // Apply the head rotation from the entity's look controller
            // We convert the degrees from the entity data to radians for the model
            head.setRotationX(extraData.headPitch * Mth.DEG_TO_RAD);
            head.setRotationY(extraData.netHeadYaw * Mth.DEG_TO_RAD);
        }
    }
}