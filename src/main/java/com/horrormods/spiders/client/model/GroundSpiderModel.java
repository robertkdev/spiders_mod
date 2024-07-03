// GroundSpiderModel.java
package com.horrormods.spiders.client.model;

import com.horrormods.spiders.Spiders;
import com.horrormods.spiders.entity.GroundSpiderEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.processor.IBone;
import software.bernie.geckolib3.model.AnimatedGeoModel;
import net.minecraft.client.model.geom.ModelPart;

public class GroundSpiderModel extends AnimatedGeoModel<GroundSpiderEntity> {

    private final ModelPart head;

    public GroundSpiderModel() {
        // Initialize the head ModelPart here
        this.head = new ModelPart(/* parameters to initialize the head model part */);
    }

    @Override
    public ResourceLocation getModelResource(GroundSpiderEntity object) {
        return new ResourceLocation(Spiders.MODID, "geo/ground_spider.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(GroundSpiderEntity object) {
        return new ResourceLocation(Spiders.MODID, "textures/entity/ground_spider_texture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(GroundSpiderEntity animatable) {
        return new ResourceLocation(Spiders.MODID, "animations/ground_spider.animation.json");
    }

    @Override
    public void setLivingAnimations(GroundSpiderEntity entity, Integer uniqueID, @SuppressWarnings("rawtypes") AnimationEvent customPredicate) {
        super.setLivingAnimations(entity, uniqueID, customPredicate);

        // Ensure the head part is correctly referenced and can be manipulated
        IBone headBone = this.getAnimationProcessor().getBone("head2");
        if (headBone != null) {
            headBone.setHidden(false);
        }
    }

    public ModelPart getHead() {
        return this.head;
    }
}
