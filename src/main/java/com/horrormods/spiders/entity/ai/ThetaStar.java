package com.horrormods.spiders.entity.ai;

import com.horrormods.spiders.entity.GroundSpiderEntity;
import com.horrormods.spiders.entity.util.AttachmentHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
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
        return find(spider, level, start, goal, 10000);
    }

    public static Path find(GroundSpiderEntity spider, Level level,
                            Vec3 start, Vec3 goal, int maxIterations) {
        BlockPos startPos = new BlockPos(Mth.floor(start.x), Mth.floor(start.y), Mth.floor(start.z));
        BlockPos goalPos  = new BlockPos(Mth.floor(goal.x),  Mth.floor(goal.y),  Mth.floor(goal.z));
        AABB bounds = new AABB(startPos, goalPos).inflate(32);
        BlockPos c1 = new BlockPos(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos c2 = new BlockPos(bounds.maxX, bounds.maxY, bounds.maxZ);
        PathNavigationRegion region = new PathNavigationRegion(level, c1, c2);

        ClimberNodeEvaluator eval = new ClimberNodeEvaluator();
        eval.setCanPathWalls(true);
        eval.setCanPathCeiling(true);
        eval.prepare(region, spider);

        ClimberNodeEvaluator.CustomNode startNode = makeNode(spider, start, eval, spider.getAttachmentDirection());
        ClimberNodeEvaluator.CustomNode goalNode  = makeNode(spider, goal, eval, null);
        if (startNode == null || goalNode == null) return null;

        PriorityQueue<ClimberNodeEvaluator.CustomNode> open =
                new PriorityQueue<>(Comparator.comparingDouble(n -> n.g + n.h));
        Set<ClimberNodeEvaluator.CustomNode> closed = new HashSet<>();

        startNode.g = 0.0;
        startNode.h = distance(startNode, goalNode);
        startNode.parent = startNode; // anchor for LOS checks
        open.add(startNode);

        int iterations = 0;
        while (!open.isEmpty() && iterations++ < maxIterations) {
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
        Vec3 goal  = new Vec3(goalPos.getX()  + 0.5, goalPos.getY()  + 0.5, goalPos.getZ()  + 0.5);
        return find(spider, level, start, goal);
    }

    private static double distance(ClimberNodeEvaluator.CustomNode a, ClimberNodeEvaluator.CustomNode b) {
        double dx = a.px - b.px;
        double dy = a.py - b.py;
        double dz = a.pz - b.pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static ClimberNodeEvaluator.CustomNode makeNode(GroundSpiderEntity spider,
                                                            Vec3 vec,
                                                            ClimberNodeEvaluator eval,
                                                            Direction preferred) {
        BlockPos pos = new BlockPos(Mth.floor(vec.x), Mth.floor(vec.y), Mth.floor(vec.z));
        EnumSet<Direction> dirs = eval.findAttachments(pos);
        if (dirs.isEmpty()) return null;
        Direction guess = Direction.getNearest(
                vec.x - (pos.getX() + 0.5),
                vec.y - (pos.getY() + 0.5),
                vec.z - (pos.getZ() + 0.5));
        Direction a = null;
        if (preferred != null && dirs.contains(preferred) && eval.isPositionValidWithAttachment(pos, preferred)) {
            a = preferred;
        } else if (dirs.contains(guess) && eval.isPositionValidWithAttachment(pos, guess)) {
            a = guess;
        } else {
            for (Direction d : dirs) {
                if (eval.isPositionValidWithAttachment(pos, d)) {
                    a = d;
                    break;
                }
            }
        }
        if (a == null) return null;
        ClimberNodeEvaluator.CustomNode n = (ClimberNodeEvaluator.CustomNode) eval.getNode(pos, a);
        n.g = Double.POSITIVE_INFINITY;
        n.parent = null;
        Vec3 anchor = AttachmentHelper.anchorFor(spider, pos, a);
        n.px = anchor.x;
        n.py = anchor.y;
        n.pz = anchor.z;
        return n;
    }

    private static boolean hasLineOfSight(GroundSpiderEntity spider, Level level,
                                          ClimberNodeEvaluator eval,
                                          ClimberNodeEvaluator.CustomNode a,
                                          ClimberNodeEvaluator.CustomNode b) {
        if (a.attachment != b.attachment && !a.asBlockPos().equals(b.asBlockPos())) {
            return false;
        }
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
        double body = Math.min(spider.getBbWidth(), spider.getBbHeight());
        double stepLen = Math.min(Mth.clamp(body / 3.0, 0.1, 0.5), len / 10.0);
        int steps = Mth.clamp(Mth.ceil(len / stepLen), 1, 2048);
        Vec3 step = diff.scale(1.0 / steps);
        Vec3 pos = start;
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        Direction prev = null;
        for (int i = 0; i <= steps; i++) {
            bp.set(Mth.floor(pos.x), Mth.floor(pos.y), Mth.floor(pos.z));
            EnumSet<Direction> set = eval.findAttachments(bp);
            if (set.isEmpty()) {
                return false;
            }
            Direction at = null;
            if (prev != null && set.contains(prev) && eval.isPositionValidWithAttachment(bp, prev)) {
                at = prev;
            } else {
                for (Direction cand : set) {
                    if (eval.isPositionValidWithAttachment(bp, cand)) {
                        at = cand;
                        break;
                    }
                }
            }
            if (at == null) {
                return false;
            }
            if (prev != null) {
                Vec3i p = prev.getNormal();
                Vec3i q = at.getNormal();
                int dot = p.getX() * q.getX() + p.getY() * q.getY() + p.getZ() * q.getZ();
                if (dot < -1) { // -1 means vectors point opposite on unit axis
                    return false;
                }
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

        Vec3 anchor = AttachmentHelper.anchorFor(e, pos, a);
        if (node instanceof ClimberNodeEvaluator.CustomNode cn) {
            cn.px = anchor.x;
            cn.py = anchor.y;
            cn.pz = anchor.z;
        }
        return anchor;
    }
}
