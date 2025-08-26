package com.horrormods.spiders.entity.ai;

import com.horrormods.spiders.entity.GroundSpiderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.*;

/**
 * Basic Theta* pathfinder constrained to surfaces. It performs a grid search
 * using {@link ClimberNodeEvaluator} but prunes intermediate nodes whenever a
 * straight line of sight exists between a node and the ancestor of its parent.
 * This allows the spider to travel along true shortest segments while still
 * respecting surface attachments.
 */
public class ThetaStar {

    private ThetaStar() {}

    /**
     * Finds a path between {@code start} and {@code goal} for the given
     * spider. Positions are treated as exact body anchors allowing sub-block
     * routing. Returns {@code null} when no path could be discovered.
     */
    public static Path find(GroundSpiderEntity spider, Level level,
                             Vec3 start, Vec3 goal) {
        BlockPos startPos = BlockPos.containing(start);
        BlockPos goalPos = BlockPos.containing(goal);
        AABB bounds = new AABB(startPos, goalPos).inflate(32);
        BlockPos c1 = new BlockPos(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos c2 = new BlockPos(bounds.maxX, bounds.maxY, bounds.maxZ);
        PathNavigationRegion region = new PathNavigationRegion(level, c1, c2);

        ClimberNodeEvaluator eval = new ClimberNodeEvaluator();
        eval.setCanPathWalls(true);
        eval.setCanPathCeiling(true);
        eval.prepare(region, spider);

        ClimberNodeEvaluator.CustomNode startNode = makeNode(start, eval, spider);
        ClimberNodeEvaluator.CustomNode goalNode  = makeNode(goal,  eval, spider);
        if (startNode == null || goalNode == null) return null;

        PriorityQueue<ClimberNodeEvaluator.CustomNode> open =
                new PriorityQueue<>(Comparator.comparingDouble(n -> n.g + n.h));
        Set<ClimberNodeEvaluator.CustomNode> closed = new HashSet<>();

        startNode.g = 0.0;
        startNode.h = distance(startNode, goalNode);
        startNode.parent = startNode; // anchor for LOS checks
        open.add(startNode);

        int iterations = 0;
        while (!open.isEmpty() && iterations++ < 10000) {
            ClimberNodeEvaluator.CustomNode current = open.poll();
            if (current.equals(goalNode)) {
                goalNode = current;
                break;
            }
            closed.add(current);

            for (ClimberNodeEvaluator.CustomNode neigh : eval.getRawNeighbors(current)) {
                if (closed.contains(neigh)) continue;

                // ensure neighbor has precise anchor
                Vec3 anchor = anchorFor(spider, neigh);
                neigh.px = anchor.x;
                neigh.py = anchor.y;
                neigh.pz = anchor.z;

                ClimberNodeEvaluator.CustomNode parent = current.parent != null ? current.parent : current;
                double tentative;
                if (parent != null && hasLineOfSight(spider, level, eval, parent, neigh)) {
                    tentative = parent.g + distance(parent, neigh);
                    if (tentative < neigh.g) {
                        neigh.parent = parent;
                        neigh.g = tentative;
                        neigh.h = distance(neigh, goalNode);
                        open.remove(neigh);
                        open.add(neigh);
                    }
                } else {
                    tentative = current.g + distance(current, neigh);
                    if (tentative < neigh.g) {
                        neigh.parent = current;
                        neigh.g = tentative;
                        neigh.h = distance(neigh, goalNode);
                        open.remove(neigh);
                        open.add(neigh);
                    }
                }
            }
        }

        if (goalNode.parent == null) return null; // no path found

        LinkedList<Node> nodes = new LinkedList<>();
        ClimberNodeEvaluator.CustomNode n = goalNode;
        while (true) {
            nodes.addFirst(n);
            if (n == n.parent) break;
            n = n.parent;
        }
        return new Path(nodes, goalPos, true);
    }

    /** Convenience overload accepting block positions. */
    public static Path find(GroundSpiderEntity spider, Level level,
                             BlockPos startPos, BlockPos goalPos) {
        Vec3 start = new Vec3(startPos.getX() + 0.5, startPos.getY() + 0.5, startPos.getZ() + 0.5);
        Vec3 goal = new Vec3(goalPos.getX() + 0.5, goalPos.getY() + 0.5, goalPos.getZ() + 0.5);
        return find(spider, level, start, goal);
    }

    private static double distance(ClimberNodeEvaluator.CustomNode a, ClimberNodeEvaluator.CustomNode b) {
        double dx = a.px - b.px;
        double dy = a.py - b.py;
        double dz = a.pz - b.pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static ClimberNodeEvaluator.CustomNode makeNode(Vec3 vec,
                                                            ClimberNodeEvaluator eval,
                                                            GroundSpiderEntity spider) {
        BlockPos pos = BlockPos.containing(vec);
        Direction a = eval.findValidAttachment(pos);
        if (a == null || !eval.isPositionValidWithAttachment(pos, a)) return null;
        ClimberNodeEvaluator.CustomNode n =
                (ClimberNodeEvaluator.CustomNode) eval.getNode(pos.getX(), pos.getY(), pos.getZ());
        n.attachment = a;
        n.g = Double.POSITIVE_INFINITY;
        n.parent = null;
        n.px = vec.x;
        n.py = vec.y;
        n.pz = vec.z;
        return n;
    }

    private static boolean hasLineOfSight(GroundSpiderEntity spider, Level level,
                                          ClimberNodeEvaluator eval,
                                          ClimberNodeEvaluator.CustomNode a,
                                          ClimberNodeEvaluator.CustomNode b) {
        Vec3 start = anchorFor(spider, a);
        Vec3 end = anchorFor(spider, b);
        return hasSurfaceLineOfSight(spider, level, eval, start, end);
    }

    /** Performs collision and surface support checks along the segment. */
    public static boolean hasSurfaceLineOfSight(GroundSpiderEntity spider, Level level,
                                               ClimberNodeEvaluator eval,
                                               Vec3 start, Vec3 end) {
        ClipContext ctx = new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, spider);
        if (level != null && level.clip(ctx).getType() != HitResult.Type.MISS) return false;

        Vec3 diff = end.subtract(start);
        double len = diff.length();
        // Sample density scales with the spider's size and segment length to avoid
        // slipping through narrow gaps or skipping over short obstacles.
        double body = Math.min(spider.getBbWidth(), spider.getBbHeight());
        double stepLen = Math.min(Mth.clamp(body / 3.0, 0.1, 0.5), len / 10.0);
        int steps = Mth.clamp(Mth.ceil(len / stepLen), 1, 2048);
        Vec3 step = diff.scale(1.0 / steps);
        Vec3 pos = start;
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        Direction prev = null;
        for (int i = 0; i <= steps; i++) {
            bp.set(pos.x, pos.y, pos.z);
            Direction at = eval.findValidAttachment(bp);
            if (at == null || !eval.isPositionValidWithAttachment(bp, at)) {
                return false;
            }
            // Abort if the surface abruptly flips (e.g., floor to ceiling
            // without an intermediary wall), which would cause a mid-air step.
            if (prev != null && prev.getNormal().dot(at.getNormal()) < -0.5F) {
                return false;
            }
            prev = at;
            pos = pos.add(step);
        }
        return true;
    }

    private static Vec3 anchorFor(GroundSpiderEntity e, Node node) {
        if (node instanceof ClimberNodeEvaluator.CustomNode cn && !Double.isNaN(cn.px)) {
            return new Vec3(cn.px, cn.py, cn.pz);
        }
        BlockPos pos = node.asBlockPos();
        Direction a = (node instanceof ClimberNodeEvaluator.CustomNode cn2 && cn2.attachment != null)
                ? cn2.attachment : Direction.DOWN;

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double halfW = e.getBbWidth() / 2.0;
        double halfH = e.getBbHeight() / 2.0;
        double eps = 0.03125;

        Vec3 anchor = switch (a) {
            case DOWN  -> new Vec3(cx, pos.getY() + halfH + eps, cz);
            case UP    -> new Vec3(cx, pos.getY() + 1.0 - halfH - eps, cz);
            case NORTH -> new Vec3(cx, cy, pos.getZ() + 1.0 - halfW - eps);
            case SOUTH -> new Vec3(cx, cy, pos.getZ() +       halfW + eps);
            case WEST  -> new Vec3(pos.getX() + 1.0 - halfW - eps, cy, cz);
            case EAST  -> new Vec3(pos.getX() +       halfW + eps, cy, cz);
            default    -> new Vec3(cx, pos.getY() + halfH, cz);
        };
        if (node instanceof ClimberNodeEvaluator.CustomNode cn) {
            cn.px = anchor.x;
            cn.py = anchor.y;
            cn.pz = anchor.z;
        }
        return anchor;
    }
}
