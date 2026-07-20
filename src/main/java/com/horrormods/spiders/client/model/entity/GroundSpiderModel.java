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
    private static final float HEAD_YAW_LIMIT_DEGREES = 65.0F;
    private static final float HEAD_PITCH_LIMIT_DEGREES = 35.0F;

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
            float headPitch = Mth.clamp(extraData.headPitch, -HEAD_PITCH_LIMIT_DEGREES, HEAD_PITCH_LIMIT_DEGREES);
            float headYaw = Mth.clamp(extraData.netHeadYaw, -HEAD_YAW_LIMIT_DEGREES, HEAD_YAW_LIMIT_DEGREES);
            head.setRotationX(headPitch * Mth.DEG_TO_RAD);
            head.setRotationY(headYaw * Mth.DEG_TO_RAD);
        }
    }
}
