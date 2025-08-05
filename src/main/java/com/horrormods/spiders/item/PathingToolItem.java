package com.horrormods.spiders.item;

import com.horrormods.spiders.entity.GroundSpiderEntity;
import com.horrormods.spiders.entity.ai.AdvancedWalkNodeEvaluator;
import com.horrormods.spiders.entity.ai.AdvancedWalkNodeEvaluator.CustomNode;
import com.horrormods.spiders.network.DisplayPathPacket;
import com.horrormods.spiders.network.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

public class PathingToolItem extends Item {

    private static BlockPos startPos;
    private static BlockPos endPos;

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
        BlockPos clickedBlock = pContext.getClickedPos();
        Direction clickedFace = pContext.getClickedFace();

        BlockPos targetAirBlock = clickedBlock.relative(clickedFace);

        if (player.isShiftKeyDown()) {
            endPos = targetAirBlock;
            player.sendSystemMessage(Component.literal("End point set at: " + posToString(endPos)));
            if (startPos != null) {
                findPath((ServerLevel) level, player);
            } else {
                player.sendSystemMessage(Component.literal("Set a start point first!"));
            }
        } else {
            startPos = targetAirBlock;
            player.sendSystemMessage(Component.literal("Start point set at: " + posToString(startPos)));
            ((ServerLevel) level).sendParticles(ParticleTypes.HAPPY_VILLAGER, startPos.getX() + 0.5, startPos.getY() + 0.5, startPos.getZ() + 0.5, 20, 0, 0, 0, 0);
        }
        return InteractionResult.SUCCESS;
    }

    private @Nullable CustomNode makeNode(BlockPos pos, AdvancedWalkNodeEvaluator eval) {
        System.out.println("--- Making node for: " + posToString(pos) + " ---");
        // 1. Find an attachment surface first.
        Direction attach = eval.findValidAttachment(pos);
        if (attach == null) {
            System.out.println("makeNode failed for " + pos + ": No valid attachment found.");
            return null;
        }

        // 2. Now check for collision using the evaluator's public validation method.
        if (!eval.isPositionValidWithAttachment(pos, attach)) {
            System.out.println("makeNode failed for " + pos + ": Position is not valid (collision).");
            return null;
        }

        CustomNode node = (CustomNode) eval.getNode(pos.getX(), pos.getY(), pos.getZ());
        node.attachment = attach;
        return node;
    }

    private void findPath(ServerLevel level, Player player) {
        TargetingConditions targetConditions = TargetingConditions.forNonCombat().ignoreLineOfSight();
        GroundSpiderEntity spider = level.getNearestEntity(
                GroundSpiderEntity.class, targetConditions, null,
                player.getX(), player.getY(), player.getZ(),
                player.getBoundingBox().inflate(64.0D)
        );

        if (spider == null) {
            player.sendSystemMessage(Component.literal("No ground spider found nearby to provide context."));
            return;
        }

        AABB bounds = new AABB(startPos, endPos).inflate(32);
        BlockPos regionCorner1 = new BlockPos(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos regionCorner2 = new BlockPos(bounds.maxX, bounds.maxY, bounds.maxZ);
        PathNavigationRegion region = new PathNavigationRegion(level, regionCorner1, regionCorner2);

        AdvancedWalkNodeEvaluator eval = new AdvancedWalkNodeEvaluator();
        eval.setCanPathWalls(true);
        eval.setCanPathCeiling(true);
        eval.prepare(region, spider);

        System.out.println("Attempting to create nodes with evaluator configured.");
        CustomNode startNode = makeNode(startPos, eval);
        CustomNode goalNode = makeNode(endPos, eval);

        if (startNode == null) {
            player.sendSystemMessage(Component.literal("Could not create a valid start node at " + posToString(startPos) + ". Is it a valid air block with an adjacent surface?"));
            return;
        }
        if (goalNode == null) {
            player.sendSystemMessage(Component.literal("Could not create a valid end node at " + posToString(endPos) + ". Is it a valid air block with an adjacent surface?"));
            return;
        }

        PriorityQueue<CustomNode> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.g + n.h));
        HashSet<CustomNode> closed = new HashSet<>();
        CustomNode finalNode = null;

        startNode.g = 0;
        startNode.h = startNode.distanceTo(goalNode);
        open.add(startNode);

        int maxIterations = 5000;
        int currentIterations = 0;

        while (!open.isEmpty() && currentIterations < maxIterations) {
            currentIterations++;
            CustomNode current = open.poll();
            if (current.equals(goalNode)) {
                finalNode = current;
                break;
            }
            if (!closed.add(current)) continue;

            for (CustomNode neighbor : eval.getRawNeighbors(current)) {
                if (closed.contains(neighbor)) continue;
                double tentativeG = current.g + current.distanceTo(neighbor);
                if (tentativeG < neighbor.g) {
                    neighbor.parent = current;
                    neighbor.g = tentativeG;
                    neighbor.h = neighbor.distanceTo(goalNode);
                    if (!open.contains(neighbor)) {
                        open.add(neighbor);
                    } else {
                        open.remove(neighbor);
                        open.add(neighbor);
                    }
                }
            }
        }

        if(currentIterations >= maxIterations) {
            System.out.println("Pathfinding stopped: reached max iterations.");
        }

        if (finalNode != null) {
            LinkedList<BlockPos> pathNodes = new LinkedList<>();
            CustomNode current = finalNode;
            while (current != null) {
                pathNodes.addFirst(current.asBlockPos());
                current = current.parent;
            }
            player.sendSystemMessage(Component.literal("Path found! Length: " + pathNodes.size()));
            PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player), new DisplayPathPacket(new ArrayList<>(pathNodes)));
        } else {
            player.sendSystemMessage(Component.literal("Could not find a path to the end point."));
            level.sendParticles(ParticleTypes.SMOKE, endPos.getX() + 0.5, endPos.getY() + 0.5, endPos.getZ() + 0.5, 20, 0, 0, 0, 0);
        }
    }

    private String posToString(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}