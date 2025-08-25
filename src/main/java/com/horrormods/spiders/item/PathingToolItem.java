package com.horrormods.spiders.item;

import com.horrormods.spiders.entity.GroundSpiderEntity;
import com.horrormods.spiders.entity.ai.ClimberNodeEvaluator;
import com.horrormods.spiders.network.DisplayPathPacket;
import com.horrormods.spiders.network.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.PacketDistributor;
import com.horrormods.spiders.entity.ai.ClimberNodeEvaluator.CustomNode;

import javax.annotation.Nullable;
import java.util.*;

public class PathingToolItem extends Item {

    public PathingToolItem(Properties pProperties) {
        super(pProperties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        ItemStack tool = pPlayer.getItemInHand(pUsedHand);

        if (pLevel.isClientSide()) {
            return InteractionResultHolder.pass(tool);
        }

        // --- NEW ROBUST RAY TRACE LOGIC ---
        // 1. First, check for an entity hit.
        double reachDistance = 5.0D; // Or pPlayer.getReachDistance();
        Vec3 eyePos = pPlayer.getEyePosition();
        Vec3 lookVec = pPlayer.getViewVector(1.0F);
        Vec3 endPosVec = eyePos.add(lookVec.x * reachDistance, lookVec.y * reachDistance, lookVec.z * reachDistance);
        AABB searchBox = pPlayer.getBoundingBox().expandTowards(lookVec.scale(reachDistance)).inflate(1.0D);

        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(pPlayer, eyePos, endPosVec, searchBox,
                (entity) -> !entity.isSpectator() && entity.isPickable(), reachDistance * reachDistance);

        if (entityHitResult != null && entityHitResult.getEntity() instanceof GroundSpiderEntity spider) {
            CompoundTag nbt = tool.getOrCreateTag();
            nbt.putUUID("BoundSpiderUUID", spider.getUUID());
            pPlayer.sendSystemMessage(Component.literal("Pathing tool bound to spider: " + spider.getUUID().toString().substring(0, 8)));
            return InteractionResultHolder.success(tool);
        }

        // 2. If no entity was hit, check for a block hit.
        BlockHitResult blockHitResult = getPlayerPOVHitResult(pLevel, pPlayer, ClipContext.Fluid.NONE);
        if (blockHitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos clickedBlock = blockHitResult.getBlockPos();
            Direction clickedFace = blockHitResult.getDirection();
            BlockPos targetAirBlock = clickedBlock.relative(clickedFace);
            CompoundTag nbt = tool.getOrCreateTag();

            if (nbt.contains("StartPoint")) {
                BlockPos startPos = NbtUtils.readBlockPos(nbt.getCompound("StartPoint"));
                pPlayer.sendSystemMessage(Component.literal("End point set at: " + posToString(targetAirBlock)));
                findPath((ServerLevel) pLevel, pPlayer, tool, startPos, targetAirBlock);
                nbt.remove("StartPoint");
            } else {
                nbt.put("StartPoint", NbtUtils.writeBlockPos(targetAirBlock));
                pPlayer.sendSystemMessage(Component.literal("Start point set at: " + posToString(targetAirBlock)));
                ((ServerLevel) pLevel).sendParticles(ParticleTypes.HAPPY_VILLAGER, targetAirBlock.getX() + 0.5, targetAirBlock.getY() + 0.5, targetAirBlock.getZ() + 0.5, 20, 0, 0, 0, 0);
            }
            return InteractionResultHolder.success(tool);
        }

        return InteractionResultHolder.pass(tool);
    }

    @Override
    public boolean isFoil(ItemStack pStack) {
        return pStack.hasTag() && pStack.getTag().contains("BoundSpiderUUID");
    }

    // The old useOn and interactLivingEntity methods are no longer needed.

    private void findPath(ServerLevel level, Player player, ItemStack tool, BlockPos startPos, BlockPos endPos) {
        CompoundTag nbt = tool.getTag();
        if (nbt == null || !nbt.hasUUID("BoundSpiderUUID")) {
            player.sendSystemMessage(Component.literal("No spider bound. Right-click a spider to bind it."));
            return;
        }

        UUID spiderId = nbt.getUUID("BoundSpiderUUID");
        GroundSpiderEntity spider = (GroundSpiderEntity) level.getEntity(spiderId);
        if (spider == null || !spider.isAlive()) {
            player.sendSystemMessage(Component.literal("Bound spider has disappeared. Unbinding tool."));
            nbt.remove("BoundSpiderUUID");
            return;
        }

        AABB bounds = new AABB(startPos, endPos).inflate(32);
        BlockPos c1 = new BlockPos(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos c2 = new BlockPos(bounds.maxX, bounds.maxY, bounds.maxZ);
        PathNavigationRegion region = new PathNavigationRegion(level, c1, c2);

        ClimberNodeEvaluator eval = new ClimberNodeEvaluator();
        eval.setCanPathWalls(true);
        eval.setCanPathCeiling(true);
        eval.prepare(region, spider);

        CustomNode startNode = (CustomNode) makeNode(startPos, eval);
        CustomNode goalNode  = (CustomNode) makeNode(endPos,  eval);

        if (startNode == null || goalNode == null) {
            player.sendSystemMessage(Component.literal("Could not create a valid start or end node."));
            return;
        }

        PriorityQueue<CustomNode> open = new PriorityQueue<>(new Comparator<CustomNode>() {
            @Override
            public int compare(CustomNode o1, CustomNode o2) {
                double f1 = o1.g + o1.h;
                double f2 = o2.g + o2.h;
                int fCompare = Double.compare(f1, f2);
                if (fCompare != 0) return fCompare;
                return Double.compare(o1.h, o2.h);
            }
        });

        HashSet<CustomNode> closed = new HashSet<>();
        CustomNode finalNode = null;

        startNode.g = 0;
        startNode.h = startNode.distanceManhattan(goalNode);
        open.add(startNode);

        int maxIterations = 10000;
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
                double tentativeG = current.g + 1.0;
                if (tentativeG < neighbor.g) {
                    neighbor.parent = current;
                    neighbor.g = tentativeG;
                    neighbor.h = neighbor.distanceManhattan(goalNode);
                    if (!open.contains(neighbor)) {
                        open.add(neighbor);
                    } else {
                        open.remove(neighbor);
                        open.add(neighbor);
                    }
                }
            }
        }

        if (finalNode != null) {
            LinkedList<CustomNode> pathCustomNodes = new LinkedList<>();
            CustomNode current = finalNode;
            while (current != null) {
                pathCustomNodes.addFirst(current);
                current = current.parent;
            }

            if (pathCustomNodes.isEmpty()) return;

            List<Node> vanillaNodes = new ArrayList<>(pathCustomNodes);
            List<BlockPos> pathPositions = vanillaNodes.stream().map(Node::asBlockPos).toList();

            Path path = new Path(vanillaNodes, goalNode.asBlockPos(), false);

            BlockPos firstPos = pathPositions.get(0);
            spider.teleportTo(firstPos.getX() + 0.5, firstPos.getY(), firstPos.getZ() + 0.5);

            PathNavigation navigation = spider.getNavigation();
            navigation.stop();
            navigation.moveTo(path, 1.0D);

            player.sendSystemMessage(Component.literal("Path sent to spider! Length: " + path.getNodeCount()));
            PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player), new DisplayPathPacket(pathPositions));
        } else {
            player.sendSystemMessage(Component.literal("Could not find a path to the end point."));
            level.sendParticles(ParticleTypes.SMOKE, endPos.getX() + 0.5, endPos.getY() + 0.5, endPos.getZ() + 0.5, 20, 0, 0, 0, 0);
        }
    }

    @Nullable
    private Node makeNode(BlockPos pos, ClimberNodeEvaluator eval) {
        Direction a = eval.findValidAttachment(pos);
        if (a == null) return null;
        if (!eval.isPositionValidWithAttachment(pos, a)) return null;
        CustomNode n = (CustomNode) eval.getNode(pos.getX(), pos.getY(), pos.getZ());
        n.attachment = a;
        return n;
    }

    private String posToString(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}