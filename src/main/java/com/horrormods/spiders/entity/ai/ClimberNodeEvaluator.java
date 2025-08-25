package com.horrormods.spiders.entity.ai;

import com.horrormods.spiders.entity.util.AttachmentHelper;
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
import java.util.*;

/**
 * Evaluator that adds nodes on walls and ceilings.
 */
public class ClimberNodeEvaluator extends WalkNodeEvaluator {

    private final EnumSet<Direction> allowed = EnumSet.of(Direction.DOWN);
    private boolean startPathOnGround = true;

    public void setStartPathOnGround(boolean start) {
        this.startPathOnGround = start;
    }

    public void setCanPathWalls(boolean walls) {
        if (walls) {
            this.allowed.addAll(EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
        } else {
            this.allowed.removeAll(EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
        }
    }

    public void setCanPathCeiling(boolean ceiling) {
        if (ceiling) {
            this.allowed.add(Direction.UP);
        } else {
            this.allowed.remove(Direction.UP);
        }
    }

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        this.nodes.clear();
    }

    @Override
    public Node getNode(int x, int y, int z) {
        return this.nodes.computeIfAbsent(Node.createHash(x, y, z), key -> new CustomNode(x, y, z));
    }

    public static class CustomNode extends Node {
        public Direction attachment;
        public double g = Double.POSITIVE_INFINITY;
        public double h;
        public CustomNode parent;

        public CustomNode(int x, int y, int z) {
            super(x, y, z);
        }
        public BlockPos asBlockPos() {
            return new BlockPos(this.x, this.y, this.z);
        }
    }

    @Override
    public Node getStart() {
        if (this.mob == null) return null;
        BlockPos here = this.mob.blockPosition();
        if (!startPathOnGround) {
            Direction a = findValidAttachment(here);
            if (a != null && isPositionValidWithAttachment(here, a)) {
                CustomNode start = (CustomNode) getNode(here);
                start.attachment = a;
                return start;
            }
        }
        return super.getStart();
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
        BlockPathTypes type = super.getBlockPathType(level, x, y, z);
        if (type == BlockPathTypes.OPEN && findValidAttachment(new BlockPos(x,y,z)) != null) {
            return BlockPathTypes.WALKABLE;
        }
        return type;
    }

    public List<CustomNode> getRawNeighbors(CustomNode current) {
        Set<CustomNode> out = new HashSet<>();
        BlockPos pos = current.asBlockPos();
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (dir.getAxis().isVertical()) {
                tryAddNode(neighbor, false, out);
            } else {
                tryAddNode(neighbor, true, out);
                if (this.mob != null && this.mob.maxUpStep > 0.0F) {
                    tryAddNode(neighbor.above(), true, out);
                }
                tryAddNode(neighbor.below(), true, out);
            }
        }
        return new ArrayList<>(out);
    }

    private void tryAddNode(BlockPos pos, boolean allowDownwardScan, Set<CustomNode> out) {
        Direction a = findValidAttachment(pos);
        if (a != null && isPositionValidWithAttachment(pos, a)) {
            CustomNode n = (CustomNode) getNode(pos);
            n.attachment = a;
            out.add(n);
            return;
        }
        if (allowDownwardScan && this.level.getBlockState(pos).isAir()) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
            int maxFall = this.mob != null ? this.mob.getMaxFallDistance() : 4;
            for (int i = 0; i < maxFall; i++) {
                m.move(Direction.DOWN);
                if (!this.level.getBlockState(m).isAir()) {
                    Direction land = findValidAttachment(m);
                    if (land != null && isPositionValidWithAttachment(m, land)) {
                        CustomNode n = (CustomNode) getNode(m);
                        n.attachment = land;
                        out.add(n);
                    }
                    return;
                }
            }
        }
    }

    public boolean isPositionValidWithAttachment(BlockPos pos, Direction a) {
        if (this.mob == null) return false;
        double halfW = this.mob.getBbWidth() / 2.0;
        double h = this.mob.getBbHeight();
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        AABB box;
        if (a.getAxis().isHorizontal()) {
            cx -= a.getStepX() * halfW;
            cz -= a.getStepZ() * halfW;
            box = new AABB(cx - halfW, cy - h * 0.5, cz - halfW,
                    cx + halfW, cy + h * 0.5, cz + halfW);
        } else {
            box = new AABB(cx - halfW, pos.getY(), cz - halfW,
                    cx + halfW, pos.getY() + h, cz + halfW);
        }
        return this.level.noCollision(this.mob, box);
    }

    @Nullable
    public Direction findValidAttachment(BlockPos pos) {
        for (Direction d : allowed) {
            BlockPos support = pos.relative(d);
            if (this.level != null && !this.level.getBlockState(support).getCollisionShape(this.level, support).isEmpty()) {
                return d;
            }
        }
        return null;
    }
}
