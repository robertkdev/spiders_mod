package com.horrormods.spiders.item;

import com.horrormods.spiders.entity.GroundSpiderEntity;
import com.horrormods.spiders.network.DisplayPathPacket;
import com.horrormods.spiders.network.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class PathingToolItem extends Item {
    // We use static fields to store the positions between uses
    private static BlockPos startPos;
    private static BlockPos endPos;

    public PathingToolItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        Level level = pContext.getLevel();
        // Logic should only run on the server
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        Player player = pContext.getPlayer();
        BlockPos clickedPos = pContext.getClickedPos();

        if (player.isShiftKeyDown()) {
            // SHIFT + RIGHT-CLICK: Set the end point and trigger pathfinding
            endPos = clickedPos.above(); // Use .above() so the spider paths to the block's surface
            player.sendSystemMessage(Component.literal("End point set at: " + posToString(endPos)));

            if (startPos != null) {
                findPath((ServerLevel) level, player);
            } else {
                player.sendSystemMessage(Component.literal("Set a start point first!"));
            }
        } else {
            // RIGHT-CLICK: Set the start point
            startPos = clickedPos.above();
            player.sendSystemMessage(Component.literal("Start point set at: " + posToString(startPos)));
            // Optional: Draw a particle at the start point
            ((ServerLevel) level).sendParticles(ParticleTypes.HAPPY_VILLAGER, startPos.getX() + 0.5, startPos.getY() + 0.5, startPos.getZ() + 0.5, 20, 0, 0, 0, 0);
        }

        return InteractionResult.SUCCESS;
    }

    private void findPath(ServerLevel level, Player player) {
        // These are the conditions for finding our spider. forNonCombat() is a good default.
        TargetingConditions targetConditions = TargetingConditions.forNonCombat().ignoreLineOfSight();

        // Find the closest ground spider to the start position using the correct arguments
        GroundSpiderEntity spider = level.getNearestEntity(
                GroundSpiderEntity.class,
                targetConditions, // Use the TargetingConditions object here
                null, // No specific living entity target
                startPos.getX(),
                startPos.getY(),
                startPos.getZ(),
                player.getBoundingBox().inflate(64.0D) // Search in a 64-block radius
        );

        if (spider == null) {
            player.sendSystemMessage(Component.literal("No ground spider found nearby."));
            return;
        }

        // --- The rest of the method is the same ---
        spider.teleportTo(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);
        Path path = spider.getNavigation().createPath(endPos, 0);

        if (path != null && !path.isDone()) {
            player.sendSystemMessage(Component.literal("Path found! Length: " + path.getNodeCount()));

            List<BlockPos> pathNodes = new ArrayList<>();
            for (int i = 0; i < path.getNodeCount(); i++) {
                pathNodes.add(path.getNodePos(i));
            }

            PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player), new DisplayPathPacket(pathNodes));

        } else {
            player.sendSystemMessage(Component.literal("Could not find a path to the end point."));
            level.sendParticles(ParticleTypes.SMOKE, endPos.getX() + 0.5, endPos.getY() + 0.5, endPos.getZ() + 0.5, 20, 0, 0, 0, 0);
        }
    }

    private String posToString(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}