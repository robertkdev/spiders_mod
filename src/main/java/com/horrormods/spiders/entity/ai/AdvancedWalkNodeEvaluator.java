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

    // NEW: Debug log to confirm settings when a path calculation begins.
    @Override
    public void prepare(PathNavigationRegion level, Mob mob) {
        super.prepare(level, mob);
        System.out.println("Preparing pathfinder: Walls=" + this.allowedDirections.contains(Direction.NORTH) + ", Ceiling=" + this.allowedDirections.contains(Direction.UP));
    }

    @Override
    public Node getStart() {
        Node node = this.getNode(this.mob.getBlockX(), this.mob.getBlockY(), this.mob.getBlockZ());
        if (node != null && node.costMalus >= 0.0F) {
            return node;
        }
        return findNearestAttachableNode(this.mob.blockPosition(), 2);
    }

    // UPDATED: Guard against nulls by falling back to the super method.
    @Override
    public Target getGoal(double x, double y, double z) {
        BlockPos goalPos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        Node node = this.getNode(goalPos.getX(), goalPos.getY(), goalPos.getZ());
        if (node == null || node.costMalus < 0.0F) {
            node = findNearestAttachableNode(goalPos, 3);
        }
        return (node != null) ? getTargetFromNode(node) : super.getGoal(x, y, z);
    }

    @Override
    public int getNeighbors(Node[] out, Node current) {
        int i = 0;
        for (Direction direction : this.allowedDirections) {
            Node neighbor = this.getNode(current.x + direction.getStepX(), current.y + direction.getStepY(), current.z + direction.getStepZ());
            if (neighbor != null && !neighbor.closed && neighbor.costMalus >= 0.0F) {
                out[i++] = neighbor;
            }
        }
        return i;
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
        if (!this.canOccupy(x, y, z)) {
            return BlockPathTypes.BLOCKED;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        for (Direction surface : allowedDirections) {
            BlockPos support = pos.relative(surface);
            if (!level.getBlockState(support).isAir()) {
                return BlockPathTypes.WALKABLE;
            }
        }
        return BlockPathTypes.BLOCKED;
    }

    private boolean canOccupy(int x, int y, int z) {
        double w = this.mob.getBbWidth();
        double h = this.mob.getBbHeight();
        double cx = x + 0.5;
        double cy = y;
        double cz = z + 0.5;
        AABB aabb = new AABB(cx - w / 2, cy, cz - w / 2, cx + w / 2, cy + h, cz + w / 2);
        return this.level.noCollision(this.mob, aabb);
    }

    @Nullable
    private Node findNearestAttachableNode(BlockPos center, int radius) {
        Node bestNode = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos currentPos = center.offset(dx, dy, dz);
                    Node node = this.getNode(currentPos.getX(), currentPos.getY(), currentPos.getZ());
                    if (node != null && node.costMalus >= 0.0F) {
                        // UPDATED: Use distToCenterSqr for more accurate tie-breaking.
                        double distSqr = center.distToCenterSqr(currentPos.getX() + 0.5, currentPos.getY() + 0.5, currentPos.getZ() + 0.5);
                        if (distSqr < bestDistSqr) {
                            bestDistSqr = distSqr;
                            bestNode = node;
                        }
                    }
                }
            }
        }
        return bestNode;
    }
}