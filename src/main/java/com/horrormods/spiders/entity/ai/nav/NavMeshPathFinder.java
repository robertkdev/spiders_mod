package com.horrormods.spiders.entity.ai.nav;

import com.horrormods.spiders.entity.GroundSpiderEntity;
import com.horrormods.spiders.entity.ai.ClimberNodeEvaluator;
import com.horrormods.spiders.entity.ai.ThetaStar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Demonstrates pathfinding on top of a navigation mesh. The current
 * implementation first tests for a direct line of sight between start and
 * goal, then falls back to a basic Theta* search over nearby polygons.
 * <p>
 * It prioritises clarity over efficiency; production deployments should add
 * optimisation and comprehensive surface validation.
 */
public class NavMeshPathFinder {

    private final NavMeshManager manager = new NavMeshManager();
    private final Level level;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public NavMeshPathFinder(Level level) {
        this.level = level;
    }

    public void onBlockChanged(BlockPos pos) {
        manager.onBlockChanged(level, pos);
    }

    /** Exposes manager eviction for external callers. */
    public void tick(Vec3 centre) {
        manager.tick(centre, 2);
    }

    /** Asynchronous variant used by the navigator to avoid blocking the game thread. */
    public CompletableFuture<Path> findPathAsync(GroundSpiderEntity spider, Vec3 start, BlockPos goal) {
        return CompletableFuture.supplyAsync(() -> findPath(spider, start, goal), executor);
    }

    /**
     * Attempts to find a path from {@code start} to {@code goal}. If a direct
     * line of sight exists the path will contain a single segment. Otherwise
     * {@code null} is returned and the caller may fall back to another method.
     */
    public Path findPath(GroundSpiderEntity spider, Vec3 start, BlockPos goal) {
        Vec3 goalCenter = new Vec3(goal.getX() + 0.5, goal.getY() + 0.5, goal.getZ() + 0.5);

        // Prepare evaluator for line-of-sight checks
        AABB bounds = new AABB(start, goalCenter).inflate(32);
        BlockPos c1 = new BlockPos(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos c2 = new BlockPos(bounds.maxX, bounds.maxY, bounds.maxZ);
        ClimberNodeEvaluator eval = new ClimberNodeEvaluator();
        eval.setCanPathWalls(true);
        eval.setCanPathCeiling(true);
        eval.prepare(new PathNavigationRegion(level, c1, c2), spider);

        if (ThetaStar.hasSurfaceLineOfSight(spider, level, eval, start, goalCenter)) {
            List<Node> nodes = new ArrayList<>();
            ClimberNodeEvaluator.CustomNode startNode = makeNode(eval, start);
            ClimberNodeEvaluator.CustomNode goalNode = makeNode(eval, goalCenter);
            if (startNode == null || goalNode == null) return null;
            nodes.add(startNode);
            nodes.add(goalNode);
            return new Path(nodes, goal, true);
        }

// Gather polygons from a coarse chunk path between start and goal
        BlockPos startBlockPos = new BlockPos(start.x, start.y, start.z);
        ChunkPos startChunk = new ChunkPos(startBlockPos);
        ChunkPos goalChunk = new ChunkPos(goal);

        List<ChunkPos> chunkRoute = findChunkRoute(startChunk, goalChunk, 4);
        List<SurfaceNavMesh.Polygon> polys = new ArrayList<>();
        for (ChunkPos cp : chunkRoute) {
            polys.addAll(manager.get(level, cp).getPolygons());
        }

        SurfaceNavMesh.Polygon startPoly = null, goalPoly = null;
        double bestStart = Double.MAX_VALUE, bestGoal = Double.MAX_VALUE;
        for (SurfaceNavMesh.Polygon p : polys) {
            double ds = p.centre.distanceTo(start);
            if (ds < bestStart) { bestStart = ds; startPoly = p; }
            double dg = p.centre.distanceTo(goalCenter);
            if (dg < bestGoal) { bestGoal = dg; goalPoly = p; }
        }
        if (startPoly == null || goalPoly == null) return null;

        class Record {
            final SurfaceNavMesh.Polygon poly;
            Record parent;
            double g = Double.POSITIVE_INFINITY;
            double h;
            Record(SurfaceNavMesh.Polygon p) { this.poly = p; }
        }

        Map<SurfaceNavMesh.Polygon, Record> records = new HashMap<>();
        PriorityQueue<Record> open = new PriorityQueue<>(Comparator.comparingDouble(r -> r.g + r.h));
        Set<SurfaceNavMesh.Polygon> closed = new HashSet<>();

        Record startRec = new Record(startPoly);
        startRec.g = 0;
        startRec.h = startPoly.centre.distanceTo(goalCenter);
        startRec.parent = startRec;
        records.put(startPoly, startRec);
        open.add(startRec);

        Record goalRec = null;
        while (!open.isEmpty()) {
            Record current = open.poll();
            if (current.poly == goalPoly) { goalRec = current; break; }
            closed.add(current.poly);

            for (SurfaceNavMesh.Polygon neigh : current.poly.neighbours) {
                if (closed.contains(neigh)) continue;
                Record nr = records.computeIfAbsent(neigh, Record::new);
                Record parent = current.parent != null ? current.parent : current;
                double tentative;
                if (ThetaStar.hasSurfaceLineOfSight(spider, level, eval, parent.poly.centre, neigh.centre)) {
                    tentative = parent.g + parent.poly.centre.distanceTo(neigh.centre);
                    if (tentative < nr.g) {
                        nr.parent = parent;
                        nr.g = tentative;
                        nr.h = neigh.centre.distanceTo(goalCenter);
                        open.remove(nr);
                        open.add(nr);
                    }
                } else {
                    tentative = current.g + current.poly.centre.distanceTo(neigh.centre);
                    if (tentative < nr.g) {
                        nr.parent = current;
                        nr.g = tentative;
                        nr.h = neigh.centre.distanceTo(goalCenter);
                        open.remove(nr);
                        open.add(nr);
                    }
                }
            }
        }

        if (goalRec == null) return null;

        List<Node> nodes = new ArrayList<>();
        ClimberNodeEvaluator.CustomNode startNode = makeNode(eval, start);
        if (startNode == null) return null;
        nodes.add(startNode);

        List<SurfaceNavMesh.Polygon> chain = new ArrayList<>();
        Record r = goalRec;
        while (r != r.parent) {
            chain.add(0, r.poly);
            r = r.parent;
        }
        for (SurfaceNavMesh.Polygon p : chain) {
            ClimberNodeEvaluator.CustomNode pn = (ClimberNodeEvaluator.CustomNode) eval.getNode(new BlockPos(Mth.floor(p.centre.x), Mth.floor(p.centre.y), Mth.floor(p.centre.z)), p.normal.getOpposite());
            pn.px = p.centre.x;
            pn.py = p.centre.y;
            pn.pz = p.centre.z;
            nodes.add(pn);
        }
        ClimberNodeEvaluator.CustomNode goalNode = makeNode(eval, goalCenter);
        if (goalNode == null) return null;
        nodes.add(goalNode);

        return new Path(nodes, goal, true);
    }

    private static ClimberNodeEvaluator.CustomNode makeNode(ClimberNodeEvaluator eval, Vec3 vec) {
        BlockPos pos = new BlockPos(Mth.floor(vec.x), Mth.floor(vec.y), Mth.floor(vec.z));
        EnumSet<Direction> dirs = eval.findAttachments(pos);
        if (dirs.isEmpty()) return null;
        Direction guess = Direction.getNearest(
                vec.x - (pos.getX() + 0.5),
                vec.y - (pos.getY() + 0.5),
                vec.z - (pos.getZ() + 0.5));
        Direction a = null;
        if (dirs.contains(guess) && eval.isPositionValidWithAttachment(pos, guess)) {
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
        ClimberNodeEvaluator.CustomNode node = (ClimberNodeEvaluator.CustomNode) eval.getNode(pos, a);
        node.px = vec.x;
        node.py = vec.y;
        node.pz = vec.z;
        return node;
    }

    /** Breadth-first search across chunks to obtain a high level route. */
    private List<ChunkPos> findChunkRoute(ChunkPos start, ChunkPos goal, int limit) {
        if (start.equals(goal)) return List.of(start);
        Queue<ChunkPos> open = new ArrayDeque<>();
        Map<ChunkPos, ChunkPos> parent = new HashMap<>();
        open.add(start);
        parent.put(start, start);
        while (!open.isEmpty() && parent.size() < 256) {
            ChunkPos cur = open.poll();
            if (cur.equals(goal)) break;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                ChunkPos next = new ChunkPos(cur.x + d.getStepX(), cur.z + d.getStepZ());
                if (!parent.containsKey(next) && Math.abs(next.x - start.x) <= limit && Math.abs(next.z - start.z) <= limit) {
                    parent.put(next, cur);
                    open.add(next);
                }
            }
        }
        List<ChunkPos> path = new ArrayList<>();
        ChunkPos cur = goal;
        if (!parent.containsKey(goal)) {
            path.add(start);
            return path;
        }
        while (!cur.equals(start)) {
            path.add(0, cur);
            cur = parent.get(cur);
        }
        path.add(0, start);
        return path;
    }
}
