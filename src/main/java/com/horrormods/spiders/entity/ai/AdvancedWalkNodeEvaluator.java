package com.horrormods.spiders.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AdvancedWalkNodeEvaluator extends WalkNodeEvaluator {

    private final EnumSet<Direction> allowedDirections = EnumSet.of(Direction.DOWN);

    public static class CustomNode extends Node {
        public Direction attachment;
        public double g = Double.MAX_VALUE;
        public double h;
        public CustomNode parent;

        public CustomNode(int x, int y, int z) {
            super(x, y, z);
        }

        public double distanceTo(CustomNode other) {
            double dx = this.x - other.x;
            double dy = this.y - other.y;
            double dz = this.z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        public float distanceManhattan(CustomNode other) {
            float dx = Math.abs(this.x - other.x);
            float dy = Math.abs(this.y - other.y);
            float dz = Math.abs(this.z - other.z);
            return dx + dy + dz;
        }
    }

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

    @Override
    public void prepare(PathNavigationRegion level, Mob mob) {
        super.prepare(level, mob);
        this.nodes.clear();
    }

    @Override
    public Node getNode(int x, int y, int z) {
        return this.nodes.computeIfAbsent(Node.createHash(x, y, z), key -> new CustomNode(x, y, z));
    }

    /**
     * REWRITTEN: Generates neighbors with symmetrical "step-up" and "step-down" logic.
     */
    public List<CustomNode> getRawNeighbors(CustomNode current) {
        Set<CustomNode> neighbors = new HashSet<>();
        BlockPos currentPos = current.asBlockPos();

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = currentPos.relative(dir);

            if (dir.getAxis().isVertical()) {
                // For simple UP/DOWN movement, just check the single block.
                tryAddNode(neighborPos, neighbors);
            } else {
                // For HORIZONTAL movement, check straight, up, and down.
                // 1. Check at the same level.
                tryAddNode(neighborPos, neighbors);

                // 2. Check for a "step up".
                if (this.mob != null && this.mob.maxUpStep > 0.0F) {
                    tryAddNode(neighborPos.above(), neighbors);
                }

                // 3. Check for a "step down".
                tryAddNode(neighborPos.below(), neighbors);
            }
        }
        return new ArrayList<>(neighbors);
    }

    private void tryAddNode(BlockPos pos, Set<CustomNode> set) {
        Direction attachment = findValidAttachment(pos);
        if (attachment != null && isPositionValidWithAttachment(pos, attachment)) {
            CustomNode neighbor = (CustomNode) getNode(pos.getX(), pos.getY(), pos.getZ());
            neighbor.attachment = attachment;
            set.add(neighbor);
        }
    }

    public boolean isPositionValidWithAttachment(BlockPos pos, Direction attachment) {
        if (this.mob == null) return false;
        double halfWidth = this.mob.getBbWidth() / 2.0;
        double height = this.mob.getBbHeight();
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        AABB aabb;
        if (attachment.getAxis().isHorizontal()) {
            cx -= attachment.getStepX() * halfWidth;
            cz -= attachment.getStepZ() * halfWidth;
            aabb = new AABB(cx - halfWidth, cy - height / 2.0, cz - halfWidth,
                    cx + halfWidth, cy + height / 2.0, cz + halfWidth);
        } else {
            aabb = new AABB(cx - halfWidth, pos.getY(), cz - halfWidth,
                    cx + halfWidth, pos.getY() + height, cz + halfWidth);
        }
        return this.level.noCollision(this.mob, aabb);
    }

    @Nullable
    public Direction findValidAttachment(BlockPos pos) {
        for (Direction surface : allowedDirections) {
            BlockPos supportPos = pos.relative(surface);
            if (this.level != null && !this.level.getBlockState(supportPos).getCollisionShape(this.level, supportPos).isEmpty()) {
                return surface;
            }
        }
        return null;
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
        if (this.canOccupy(new BlockPos(x,y,z))) {
            return BlockPathTypes.OPEN;
        }
        return BlockPathTypes.BLOCKED;
    }

    public boolean canOccupy(BlockPos pos) {
        return canOccupy(pos.getX(), pos.getY(), pos.getZ());
    }

    private boolean canOccupy(int x, int y, int z) {
        if (this.mob == null) return false;
        double w = this.mob.getBbWidth();
        double h = this.mob.getBbHeight();
        double cx = x + 0.5;
        double cy = y + 0.5;
        double cz = z + 0.5;
        AABB aabb = new AABB(
                cx - w / 2.0, cy - h / 2.0, cz - w / 2.0,
                cx + w / 2.0, cy + h / 2.0, cz + w / 2.0
        );
        return this.level.noCollision(this.mob, aabb);
    }
}