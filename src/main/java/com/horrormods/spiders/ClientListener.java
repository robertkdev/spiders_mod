package com.horrormods.spiders;

import com.horrormods.spiders.client.ClientPathManager;
import com.horrormods.spiders.client.renderer.entity.GroundSpiderRenderer;
import com.horrormods.spiders.registry.EntityRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = Spiders.ModID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientListener {

    @SubscribeEvent
    public static void onRenderLevelLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        List<BlockPos> path = ClientPathManager.getPath();
        // We need at least 2 points to draw a line
        if (path.size() < 2) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Get rendering objects
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());

        // We must render relative to the camera's position to prevent floating point issues
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Loop through the path nodes and draw a line segment between each one
        for (int i = 0; i < path.size() - 1; i++) {
            BlockPos pos1 = path.get(i);
            BlockPos pos2 = path.get(i + 1);

            // Add 0.5 to center the line in the middle of the block
            float x1 = (float) (pos1.getX() + 0.5);
            float y1 = (float) (pos1.getY() + 0.5);
            float z1 = (float) (pos1.getZ() + 0.5);
            float x2 = (float) (pos2.getX() + 0.5);
            float y2 = (float) (pos2.getY() + 0.5);
            float z2 = (float) (pos2.getZ() + 0.5);

            // Draw the line vertex by vertex
            vertexConsumer.vertex(poseStack.last().pose(), x1, y1, z1).color(255, 0, 0, 255).normal(1, 0, 0).endVertex();
            vertexConsumer.vertex(poseStack.last().pose(), x2, y2, z2).color(255, 0, 0, 255).normal(1, 0, 0).endVertex();
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }

    // This nested class for MOD bus events remains the same
    @Mod.EventBusSubscriber(modid = Spiders.ModID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(EntityRegistry.GROUND_SPIDER.get(), GroundSpiderRenderer::new);
        }
    }
}