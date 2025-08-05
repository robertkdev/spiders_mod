package com.horrormods.spiders.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Target;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class AdvancedWalkNodeEvaluator extends WalkNodeEvaluator {

    private final EnumSet<Direction> allowedDirections = EnumSet.of(Direction.DOWN);
    private static final Direction[] HORIZONTAL_DIRECTIONS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    public void setCanPathWalls(boolean canPathWalls) {
        if (canPathWalls) {
            this.allowedDirections.addAll(EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
        } else {
            this.allowedDirections.removeAll(EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
        }
    }

    public void setCanPathCeiling(boolean canPathCeiling) {
        if (canPathCeiling) {
            this.allowedDirections.add(Direction.UP);
        } else {
            this.allowedDirections.remove(Direction.UP);
        }
    }

    // Override to ensure we create our CustomNode instead of the default Node
    @Override
    protected Node getNode(int x, int y, int z) {
        return this.nodes.computeIfAbsent(Node.createHash(x, y, z), (key) -> new CustomNode(x, y, z));
    }

    @Override
    public Node getStart() {
        return findBestNode(this.mob.blockPosition(), 2);
    }

    @Override
    public Target getGoal(double x, double y, double z) {
        Node node = findBestNode(new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z)), 3);
        return getTargetFromNode(node);
    }

    @Override
    public int getNeighbors(Node[] out, Node current) {
        int i = 0;
        CustomNode customCurrent = (CustomNode) current;

        // As you correctly designed, we get movement directions based on the current attachment surface.
        for (Direction moveDir : getMoveDirections(customCurrent.attachment)) {
            BlockPos neighborPos = new BlockPos(current.x, current.y, current.z).relative(moveDir);

            // Now find the best valid attachment for the neighbor position
            Direction neighborAttachment = findValidAttachment(neighborPos);
            if (neighborAttachment != null) {
                CustomNode neighborNode = (CustomNode) this.getNode(neighborPos.getX(), neighborPos.getY(), neighborPos.getZ());
                if (neighborNode != null && !neighborNode.closed) {
                    neighborNode.attachment = neighborAttachment; // CRITICAL: Set the attachment for the next node
                    out[i++] = neighborNode;
                }
            }
        }
        return i;
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
        if (!this.canOccupy(x, y, z)) {
            return BlockPathTypes.BLOCKED;
        }
        if (findValidAttachment(new BlockPos(x, y, z)) != null) {
            return BlockPathTypes.WALKABLE;
        }
        return BlockPathTypes.BLOCKED;
    }

    // Helper to find the best attachment surface for a given position.
    @Nullable
    private Direction findValidAttachment(BlockPos pos) {
        for (Direction surface : allowedDirections) {
            BlockPos supportPos = pos.relative(surface);
            if (!this.level.getBlockState(supportPos).getCollisionShape(this.level, supportPos).isEmpty()) {
                return surface;
            }
        }
        return null;
    }

    // Helper to find the best valid node in a small area, setting its attachment.
    @Nullable
    private Node findBestNode(BlockPos center, int radius) {
        Node bestNode = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos currentPos = center.offset(dx, dy, dz);
                    if (this.canOccupy(currentPos.getX(), currentPos.getY(), currentPos.getZ())) {
                        Direction attachment = findValidAttachment(currentPos);
                        if (attachment != null) {
                            CustomNode node = (CustomNode) this.getNode(currentPos.getX(), currentPos.getY(), currentPos.getZ());
                            node.attachment = attachment;
                            double distSqr = center.distToCenterSqr(currentPos.getX() + 0.5, currentPos.getY() + 0.5, currentPos.getZ() + 0.5);
                            if (distSqr < bestDistSqr) {
                                bestDistSqr = distSqr;
                                bestNode = node;
                            }
                        }
                    }
                }
            }
        }
        return bestNode;
    }

    // Helper to get movement directions based on the current attachment surface.
    private Direction[] getMoveDirections(Direction attachment) {
        if (attachment == null) return new Direction[0]; // Should not happen
        switch (attachment) {
            case DOWN, UP:
                return HORIZONTAL_DIRECTIONS;
            case NORTH, SOUTH:
                return new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST};
            case EAST, WEST:
                return new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH};
            default:
                return new Direction[0];
        }
    }

    // Size-aware occupancy check
    private boolean canOccupy(int x, int y, int z) {
        double w = this.mob.getBbWidth();
        double h = this.mob.getBbHeight();
        double cx = x + 0.5, cy = y, cz = z + 0.5;
        AABB aabb = new AABB(cx - w / 2, cy, cz - w / 2, cx + w / 2, cy + h, cz + w / 2);
        return this.level.noCollision(this.mob, aabb);
    }
}