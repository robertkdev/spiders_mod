// GroundSpiderRenderer.java
package com.horrormods.spiders.client.renderer;

import com.horrormods.spiders.Spiders;
import com.horrormods.spiders.client.model.GroundSpiderModel;
import com.horrormods.spiders.entity.GroundSpiderEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib3.renderers.geo.GeoEntityRenderer;

public class GroundSpiderRenderer extends GeoEntityRenderer<GroundSpiderEntity> {
    public GroundSpiderRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new GroundSpiderModel());
    }

    @Override
    public ResourceLocation getTextureLocation(GroundSpiderEntity instance) {
        return new ResourceLocation(Spiders.MODID, "textures/entity/ground_spider_texture.png");
    }
}