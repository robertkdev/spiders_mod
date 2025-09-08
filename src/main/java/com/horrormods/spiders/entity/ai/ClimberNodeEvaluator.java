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
import java.util.*;

/**
 * Evaluator that adds nodes on walls and ceilings.
 */
public class ClimberNodeEvaluator extends WalkNodeEvaluator {

    private final EnumSet<Direction> allowed = EnumSet.of(Direction.DOWN);
    private boolean startPathOnGround = true;
    private final Map<Long, EnumMap<Direction, CustomNode>> nodeMap = new HashMap<>();

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
        this.nodeMap.clear();
    }

    @Override
    public Node getNode(int x, int y, int z) {
        return getNode(new BlockPos(x, y, z), Direction.DOWN);
    }

    public Node getNode(BlockPos pos, Direction dir) {
        long key = Node.createHash(pos.getX(), pos.getY(), pos.getZ());
        EnumMap<Direction, CustomNode> dirMap = this.nodeMap.computeIfAbsent(key, k -> new EnumMap<>(Direction.class));
        return dirMap.computeIfAbsent(dir, d -> new CustomNode(pos.getX(), pos.getY(), pos.getZ(), d));
    }

    public static class CustomNode extends Node {
        public final Direction attachment;
        public double g = Double.POSITIVE_INFINITY;
        public double h;
        public CustomNode parent;
        // Precise anchor position for sub-block routing
        public double px = Double.NaN;
        public double py = Double.NaN;
        public double pz = Double.NaN;

        public CustomNode(int x, int y, int z, Direction attachment) {
            super(x, y, z);
            this.attachment = attachment;
        }
        public BlockPos asBlockPos() {
            return new BlockPos(this.x, this.y, this.z);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CustomNode cn)) return false;
            return this.x == cn.x && this.y == cn.y && this.z == cn.z && this.attachment == cn.attachment;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.x, this.y, this.z, this.attachment);
        }
    }

    @Override
    public Node getStart() {
        if (this.mob == null) return null;
        BlockPos here = this.mob.blockPosition();
        if (!startPathOnGround) {
            EnumSet<Direction> dirs = findAttachments(here);
            for (Direction a : dirs) {
                if (isPositionValidWithAttachment(here, a)) {
                    return getNode(here, a);
                }
            }
        }
        return super.getStart();
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
        BlockPathTypes type = super.getBlockPathType(level, x, y, z);
        if (type == BlockPathTypes.OPEN && !findAttachments(new BlockPos(x, y, z)).isEmpty()) {
            return BlockPathTypes.WALKABLE;
        }
        return type;
    }

    public List<CustomNode> getRawNeighbors(CustomNode current) {
        Set<CustomNode> out = new HashSet<>();
        BlockPos pos = current.asBlockPos();

        // Determine the pair of axes that form the tangent plane for the current attachment.
        Direction attach = current.attachment != null ? current.attachment : Direction.DOWN;
        Direction axis1, axis2;
        switch (attach) {
            case DOWN, UP -> {
                axis1 = Direction.EAST; // X axis
                axis2 = Direction.SOUTH; // Z axis
            }
            case NORTH, SOUTH -> {
                axis1 = Direction.EAST; // X axis
                axis2 = Direction.UP;   // Y axis
            }
            case EAST, WEST -> {
                axis1 = Direction.NORTH; // Z axis
                axis2 = Direction.UP;    // Y axis
            }
            default -> {
                axis1 = Direction.EAST;
                axis2 = Direction.SOUTH;
            }
        }

        Direction[] tangential = new Direction[]{axis1, axis1.getOpposite(), axis2, axis2.getOpposite()};

        // Cardinal neighbors in the tangent plane
        for (Direction dir : tangential) {
            BlockPos neighbor = pos.relative(dir);
            tryAddNode(neighbor, true, out);
            // Allow stepping up/down when walking on horizontal surfaces
            if (attach == Direction.DOWN || attach == Direction.UP) {
                if (this.mob != null && this.mob.maxUpStep > 0.0F) {
                    tryAddNode(neighbor.above(), true, out);
                }
                tryAddNode(neighbor.below(), true, out);
            }
        }

        // Diagonal neighbors within the tangent plane. Avoid cutting corners by
        // ensuring both adjacent cardinal positions are valid attachments.
        for (int i = 0; i < tangential.length; i++) {
            Direction d1 = tangential[i];
            if (d1 == null) continue;
            for (int j = i + 1; j < tangential.length; j++) {
                Direction d2 = tangential[j];
                if (d2 == null || d1.getAxis() == d2.getAxis()) continue;

                BlockPos adj1 = pos.relative(d1);
                BlockPos adj2 = pos.relative(d2);
                if (!isWalkable(adj1) || !isWalkable(adj2)) continue;

                BlockPos diag = adj1.relative(d2);
                tryAddNode(diag, true, out);
                if (attach == Direction.DOWN || attach == Direction.UP) {
                    if (this.mob != null && this.mob.maxUpStep > 0.0F) {
                        tryAddNode(diag.above(), true, out);
                    }
                    tryAddNode(diag.below(), true, out);
                }
            }
        }

        // Corner-wrap moves: step tangentially then around the corner to an
        // orthogonal face. These allow transitioning from one surface to an
        // adjacent perpendicular surface without passing through solid blocks.
        Direction outward = attach.getOpposite();
        for (Direction t : tangential) {
            if (t == null) continue;
            BlockPos corner = pos.relative(t).relative(outward);
            // The new supporting face is the direction of the tangential step.
            Direction newAttach = t;
            EnumSet<Direction> supports = findAttachments(corner);
            if (supports.contains(newAttach) && isPositionValidWithAttachment(corner, newAttach)) {
                out.add((CustomNode) getNode(corner, newAttach));
            }
        }
        EnumSet<Direction> here = findAttachments(pos);
        for (Direction dir : here) {
            if (dir != attach && isPositionValidWithAttachment(pos, dir)) {
                out.add((CustomNode) getNode(pos, dir));
            }
        }

        return new ArrayList<>(out);
    }

    private boolean isWalkable(BlockPos pos) {
        EnumSet<Direction> dirs = findAttachments(pos);
        for (Direction a : dirs) {
            if (isPositionValidWithAttachment(pos, a)) return true;
        }
        return false;
    }

    @Override
    public int getNeighbors(Node[] nodes, Node node) {
        List<CustomNode> list = getRawNeighbors((CustomNode) node);
        int i = 0;
        for (CustomNode n : list) {
            nodes[i++] = n;
        }
        return i;
    }

    private void tryAddNode(BlockPos pos, boolean allowDownwardScan, Set<CustomNode> out) {
        EnumSet<Direction> dirs = findAttachments(pos);
        for (Direction a : dirs) {
            if (isPositionValidWithAttachment(pos, a)) {
                out.add((CustomNode) getNode(pos, a));
            }
        }
        if (!dirs.isEmpty()) return;
        if (allowDownwardScan && this.level.getBlockState(pos).isAir()) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
            int maxFall = this.mob != null ? this.mob.getMaxFallDistance() : 4;
            for (int i = 0; i < maxFall; i++) {
                m.move(Direction.DOWN);
                if (!this.level.getBlockState(m).isAir()) {
                    EnumSet<Direction> landDirs = findAttachments(m);
                    for (Direction land : landDirs) {
                        if (isPositionValidWithAttachment(m, land)) {
                            out.add((CustomNode) getNode(new BlockPos(m), land));
                        }
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

    public EnumSet<Direction> findAttachments(BlockPos pos) {
        EnumSet<Direction> dirs = EnumSet.noneOf(Direction.class);
        for (Direction d : allowed) {
            BlockPos support = pos.relative(d);
            if (this.level != null && !this.level.getBlockState(support).getCollisionShape(this.level, support).isEmpty()) {
                dirs.add(d);
            }
        }
        return dirs;
    }
}
