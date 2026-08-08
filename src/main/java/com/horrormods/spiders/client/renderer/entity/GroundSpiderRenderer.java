package com.horrormods.spiders.client.renderer.entity;

import com.horrormods.spiders.client.model.entity.GroundSpiderModel;
import com.horrormods.spiders.entity.GroundSpiderEntity;
import com.horrormods.spiders.entity.util.GroundSpiderAttachmentPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib3.renderers.geo.GeoEntityRenderer;

public class GroundSpiderRenderer extends GeoEntityRenderer<GroundSpiderEntity> {
    public GroundSpiderRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new GroundSpiderModel());
        this.shadowRadius = 0.7F;
    }

    @Override
    public RenderType getRenderType(GroundSpiderEntity animatable, float partialTick, PoseStack poseStack,
                                    MultiBufferSource bufferSource, VertexConsumer buffer, int packedLight,
                                    ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(getTextureLocation(animatable));
    }

    /**
     * This is the new, overridden method that handles all the rendering and rotation.
     */
    @Override
    public void render(GroundSpiderEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferIn, int packedLightIn) {

        // Get the current attachment direction from the entity
        Direction attachment = entity.getAttachmentDirection();

        // Save the current matrix state so our rotations don't affect other entities
        poseStack.pushPose();

        // Apply attachment rotations around the entity body center. The model's
        // unrotated belly points down, so these rotations point it into the
        // support surface and leave the back facing open air.
        if (attachment != Direction.DOWN) { // No special rotation needed for walking on the floor
            poseStack.translate(0.0D, entity.getBbHeight() / 2.0, 0.0D);
            applyAttachmentRotation(poseStack, attachment);
            poseStack.translate(0.0D, -entity.getBbHeight() / 2.0, 0.0D);
        }

        // Call the original render method to draw the spider with our modified pose
        super.render(entity, entityYaw, partialTicks, poseStack, bufferIn, packedLightIn);

        // Restore the matrix to its original state
        poseStack.popPose();
    }

    private static void applyAttachmentRotation(PoseStack poseStack, Direction attachment) {
        float degrees = GroundSpiderAttachmentPose.rotationDegrees(attachment);
        switch (GroundSpiderAttachmentPose.rotationAxis(attachment)) {
            case X:
                poseStack.mulPose(Vector3f.XP.rotationDegrees(degrees));
                break;
            case Z:
                poseStack.mulPose(Vector3f.ZP.rotationDegrees(degrees));
                break;
            default:
                break;
        }
    }
}
