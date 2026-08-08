package com.horrormods.spiders.client.renderer.blockentity;

import com.horrormods.spiders.block.entity.SingleThreadWebBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;

public class SingleThreadWebRenderer implements BlockEntityRenderer<SingleThreadWebBlockEntity> {
    public SingleThreadWebRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SingleThreadWebBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!blockEntity.hasStrand() || !blockEntity.isRenderHost()) {
            return;
        }

        Vec3 origin = Vec3.atLowerCornerOf(blockEntity.getBlockPos());
        Vec3 start = Vec3.atCenterOf(blockEntity.getFirstAnchor()).subtract(origin);
        Vec3 end = Vec3.atCenterOf(blockEntity.getSecondAnchor()).subtract(origin);
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() <= 1.0E-6D) {
            return;
        }

        Vec3 normal = direction.normalize();
        Vec3 side = normal.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() <= 1.0E-6D) {
            side = normal.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize().scale(0.045D);
        Vec3 up = normal.cross(side);
        if (up.lengthSqr() <= 1.0E-6D) {
            up = new Vec3(0.0D, 0.045D, 0.0D);
        } else {
            up = up.normalize().scale(0.035D);
        }

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();
        drawLine(consumer, pose, start, end, normal, 255, 255, 245, 255);
        drawLine(consumer, pose, start.add(side), end.add(side), normal, 220, 220, 210, 245);
        drawLine(consumer, pose, start.subtract(side), end.subtract(side), normal, 220, 220, 210, 245);
        drawLine(consumer, pose, start.add(up), end.add(up), normal, 210, 210, 205, 235);
        drawLine(consumer, pose, start.subtract(up), end.subtract(up), normal, 170, 170, 170, 220);
        drawLine(consumer, pose, start.add(side).add(up), end.add(side).add(up), normal, 200, 200, 195, 225);
        drawLine(consumer, pose, start.subtract(side).subtract(up), end.subtract(side).subtract(up), normal, 145, 145, 150, 210);
    }

    @Override
    public boolean shouldRenderOffScreen(SingleThreadWebBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    private static void drawLine(VertexConsumer consumer, PoseStack.Pose pose, Vec3 start, Vec3 end, Vec3 normal,
            int red, int green, int blue, int alpha) {
        consumer.vertex(pose.pose(), (float) start.x, (float) start.y, (float) start.z)
                .color(red, green, blue, alpha)
                .normal((float) normal.x, (float) normal.y, (float) normal.z)
                .endVertex();
        consumer.vertex(pose.pose(), (float) end.x, (float) end.y, (float) end.z)
                .color(red, green, blue, alpha)
                .normal((float) normal.x, (float) normal.y, (float) normal.z)
                .endVertex();
    }
}
