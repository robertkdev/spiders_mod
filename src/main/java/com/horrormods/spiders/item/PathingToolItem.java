package com.horrormods.spiders.item;

import com.horrormods.spiders.entity.GroundSpiderEntity;
import com.horrormods.spiders.network.DisplayPathPacket;
import com.horrormods.spiders.network.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction; // NEW Import
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
    private static BlockPos startPos;
    private static BlockPos endPos;
    private static Direction startFace; // NEW: Store the face of the starting block

    public PathingToolItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        Level level = pContext.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        Player player = pContext.getPlayer();
        BlockPos clickedPos = pContext.getClickedPos();

        if (player.isShiftKeyDown()) {
            endPos = clickedPos;
            player.sendSystemMessage(Component.literal("End point set at: " + posToString(endPos)));

            if (startPos != null) {
                findPath((ServerLevel) level, player);
            } else {
                player.sendSystemMessage(Component.literal("Set a start point first!"));
            }
        } else {
            startPos = clickedPos;
            startFace = pContext.getClickedFace(); // NEW: Store the direction of the clicked face
            player.sendSystemMessage(Component.literal("Start point set at: " + posToString(startPos) + " on face " + startFace));
            ((ServerLevel) level).sendParticles(ParticleTypes.HAPPY_VILLAGER, startPos.getX() + 0.5, startPos.getY() + 0.5, startPos.getZ() + 0.5, 20, 0, 0, 0, 0);
        }

        return InteractionResult.SUCCESS;
    }

    private void findPath(ServerLevel level, Player player) {
        TargetingConditions targetConditions = TargetingConditions.forNonCombat().ignoreLineOfSight();
        GroundSpiderEntity spider = level.getNearestEntity(
                GroundSpiderEntity.class, targetConditions, null,
                startPos.getX(), startPos.getY(), startPos.getZ(),
                player.getBoundingBox().inflate(64.0D)
        );

        if (spider == null) {
            player.sendSystemMessage(Component.literal("No ground spider found nearby."));
            return;
        }

        // --- FIX: Teleport the spider to the air block adjacent to the clicked face ---
        BlockPos teleportPos = startPos.relative(startFace);
        spider.teleportTo(teleportPos.getX() + 0.5, teleportPos.getY() + 0.5, teleportPos.getZ() + 0.5);

        Path path = spider.getNavigation().createPath(endPos, 128);

        if (path != null && path.getNodeCount() > 1) {
            player.sendSystemMessage(Component.literal("Path found! Length: " + path.getNodeCount()));

            List<BlockPos> pathNodes = new ArrayList<>();
            for (int i = 0; i < path.getNodeCount(); i++) {
                pathNodes.add(path.getNodePos(i));
            }

            PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player), new DisplayPathPacket(pathNodes));
        } else {
            // This already prints a message in-game if a path isn't found
            player.sendSystemMessage(Component.literal("Could not find a path to the end point."));
            level.sendParticles(ParticleTypes.SMOKE, endPos.getX() + 0.5, endPos.getY() + 0.5, endPos.getZ() + 0.5, 20, 0, 0, 0, 0);
        }
    }

    private String posToString(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}