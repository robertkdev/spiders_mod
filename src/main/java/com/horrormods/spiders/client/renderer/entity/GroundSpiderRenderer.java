package com.horrormods.spiders.client.renderer.entity;

import com.horrormods.spiders.client.model.entity.GroundSpiderModel;
import com.horrormods.spiders.entity.GroundSpiderEntity;
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

        // Apply translations and rotations based on the attachment direction
        // We translate to center the rotation on the entity's body
        if (attachment != Direction.DOWN) { // No special rotation needed for walking on the floor
            poseStack.translate(0.0D, entity.getBbHeight() / 2.0, 0.0D);

            switch (attachment) {
                case UP: // On a ceiling
                    poseStack.mulPose(Vector3f.XP.rotationDegrees(180.0F));
                    // May need an additional Y rotation if it looks backwards on the ceiling
                    // poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F));
                    poseStack.translate(0.0D, -entity.getBbHeight() / 2.0, 0.0D); // Adjust position after rotation
                    break;
                case NORTH: // On a north-facing wall (entity is facing south)
                    poseStack.mulPose(Vector3f.XP.rotationDegrees(90.0F));
                    break;
                case SOUTH: // On a south-facing wall (entity is facing north)
                    poseStack.mulPose(Vector3f.XP.rotationDegrees(-90.0F));
                    break;
                case WEST: // On a west-facing wall (entity is facing east)
                    poseStack.mulPose(Vector3f.ZP.rotationDegrees(90.0F));
                    break;
                case EAST: // On an east-facing wall (entity is facing west)
                    poseStack.mulPose(Vector3f.ZP.rotationDegrees(-90.0F));
                    break;
                default:
                    break;
            }
            poseStack.translate(0.0D, -entity.getBbHeight() / 2.0, 0.0D);
        }

        // Call the original render method to draw the spider with our modified pose
        super.render(entity, entityYaw, partialTicks, poseStack, bufferIn, packedLightIn);

        // Restore the matrix to its original state
        poseStack.popPose();
    }
}