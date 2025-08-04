package com.horrormods.spiders.client.renderer.entity;

import com.horrormods.spiders.client.model.entity.GroundSpiderModel;
import com.horrormods.spiders.entity.GroundSpiderEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib3.renderers.geo.GeoEntityRenderer;

// Change classes to reference your new spider
public class GroundSpiderRenderer extends GeoEntityRenderer<GroundSpiderEntity> {
    public GroundSpiderRenderer(EntityRendererProvider.Context renderManager) {
        // Use your new GroundSpiderModel
        super(renderManager, new GroundSpiderModel());
        this.shadowRadius = 0.7F; // Adjust shadow size if needed
    }

    @Override
    public RenderType getRenderType(GroundSpiderEntity animatable, float partialTick, PoseStack poseStack,
                                    MultiBufferSource bufferSource, VertexConsumer buffer, int packedLight,
                                    ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(getTextureLocation(animatable));
    }
}