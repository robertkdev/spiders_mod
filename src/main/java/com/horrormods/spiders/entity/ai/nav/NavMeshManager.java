package com.horrormods.spiders.entity.ai.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages streaming nav‑mesh chunks in and out of memory.
 * <p>
 * The implementation intentionally favours readability while demonstrating a
 * handful of production considerations:
 * <ul>
 *   <li>basic polygon merging to cut node counts</li>
 *   <li>surface validation that rejects thin blocks or low‑clearance overhangs</li>
 *   <li>asynchronous chunk building and timed eviction</li>
 *   <li>incremental rebuild hooks for world changes</li>
 * </ul>
 */
public class NavMeshManager {

    /** Simple cache entry containing the mesh and last access time. */
    private static class Entry {
        final CompletableFuture<SurfaceNavMesh> meshFuture;
        volatile long lastAccess;
        Entry(CompletableFuture<SurfaceNavMesh> meshFuture) {
            this.meshFuture = meshFuture;
            this.lastAccess = System.currentTimeMillis();
        }
    }

    private final Map<ChunkPos, Entry> cache = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    /** Returns the nav mesh for the given chunk, building it asynchronously on demand. */
    public SurfaceNavMesh get(Level level, ChunkPos pos) {
        Entry entry = cache.computeIfAbsent(pos, p ->
                new Entry(CompletableFuture.supplyAsync(() -> buildChunk(level, p), executor)));
        entry.lastAccess = System.currentTimeMillis();
        return entry.meshFuture.join();
    }

    /** Checks and evicts meshes that are far away or stale. */
    public void tick(Vec3 centre, int radius) {
        long now = System.currentTimeMillis();
        int centreChunkX = Mth.floor(centre.x) >> 4;
        int centreChunkZ = Mth.floor(centre.z) >> 4;
        Iterator<Map.Entry<ChunkPos, Entry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ChunkPos, Entry> e = it.next();
            ChunkPos cp = e.getKey();
            if (Math.abs(cp.x - centreChunkX) > radius || Math.abs(cp.z - centreChunkZ) > radius
                    || now - e.getValue().lastAccess > 30_000) {
                e.getValue().meshFuture.cancel(false);
                it.remove();
            }
        }
    }

    /** Rebuilds the mesh for the chunk containing {@code changed} on the next query. */
    public void onBlockChanged(Level level, BlockPos changed) {
        ChunkPos pos = new ChunkPos(changed);
        Entry e = cache.remove(pos);
        if (e != null) {
            e.meshFuture.cancel(false);
        }
        // rebuild asynchronously so next get() will produce an updated mesh
        cache.computeIfAbsent(pos, p ->
                new Entry(CompletableFuture.supplyAsync(() -> buildChunk(level, p), executor)));
    }

    /** Clears all loaded mesh data. */
    public void clear() {
        cache.values().forEach(e -> e.meshFuture.cancel(false));
        cache.clear();
    }

    /** Exposed for tests. */
    int cacheSize() { return cache.size(); }

    // Visible for testing
    void insertTestEntry(ChunkPos pos) {
        cache.put(pos, new Entry(CompletableFuture.completedFuture(new SurfaceNavMesh(List.of()))));
    }

    /**
     * Builds a nav mesh for the specified chunk by merging contiguous exposed
     * faces into larger polygons. The method performs only light optimisation
     * and validation; complex worlds may require further tuning.
     */
    private SurfaceNavMesh buildChunk(Level level, ChunkPos pos) {
        List<SurfaceNavMesh.Polygon> polys = new ArrayList<>();
        Map<BlockPos, List<SurfaceNavMesh.Polygon>> index = new HashMap<>();

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int x0 = pos.getMinBlockX();
        int z0 = pos.getMinBlockZ();

        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos adj = new BlockPos.MutableBlockPos();
        Set<BlockPos> visited = new HashSet<>();

        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    bp.set(x, y, z);
                    BlockState state = level.getBlockState(bp);
                    if (!state.isCollisionShapeFullBlock(level, bp)) continue;

                    for (Direction dir : Direction.values()) {
                        if (visited.contains(bp.relative(dir))) continue;
                        adj.set(bp).move(dir);
                        if (!isValidSurface(level, bp, dir, adj)) continue;

                        // flood‑fill contiguous faces in the plane
                        int ax1 = dir.getAxis() == Direction.Axis.X ? 0 : 1;
                        int ax2 = dir.getAxis() == Direction.Axis.Z ? 1 : 2;
                        Direction[] sweep = new Direction[]{
                                Direction.values()[ax1 * 2], Direction.values()[ax1 * 2 + 1],
                                Direction.values()[ax2 * 2], Direction.values()[ax2 * 2 + 1]
                        };
                        int minX = x, maxX = x, minY2 = y, maxY2 = y, minZ = z, maxZ = z;
                        Deque<BlockPos> stack = new ArrayDeque<>();
                        stack.push(bp.immutable());
                        visited.add(bp.immutable());
                        while (!stack.isEmpty()) {
                            BlockPos p = stack.pop();
                            int px = p.getX(), py = p.getY(), pz = p.getZ();
                            minX = Math.min(minX, px); maxX = Math.max(maxX, px);
                            minY2 = Math.min(minY2, py); maxY2 = Math.max(maxY2, py);
                            minZ = Math.min(minZ, pz); maxZ = Math.max(maxZ, pz);
                            for (Direction s : sweep) {
                                BlockPos np = p.relative(s);
                                if (!visited.contains(np) && np.getX() >= x0 && np.getX() < x0 + 16
                                        && np.getZ() >= z0 && np.getZ() < z0 + 16
                                        && np.getY() >= minY && np.getY() < maxY) {
                                    bp.set(np);
                                    if (isValidSurface(level, np, dir, adj.set(np).move(dir))) {
                                        visited.add(np.immutable());
                                        stack.push(np.immutable());
                                    }
                                }
                            }
                        }

                        double cx = (minX + maxX + 1) * 0.5 + dir.getStepX() * 0.5;
                        double cy = (minY2 + maxY2 + 1) * 0.5 + dir.getStepY() * 0.5;
                        double cz = (minZ + maxZ + 1) * 0.5 + dir.getStepZ() * 0.5;
                        SurfaceNavMesh.Polygon poly = new SurfaceNavMesh.Polygon(new Vec3(cx, cy, cz), dir);
                        polys.add(poly);
                        BlockPos key = new BlockPos(Mth.floor(cx), Mth.floor(cy), Mth.floor(cz));
                        index.computeIfAbsent(key, k -> new ArrayList<>()).add(poly);
                    }
                }
            }
        }

        // Connect polygons using spatial index to avoid O(n^2) scanning
        for (SurfaceNavMesh.Polygon a : polys) {
            BlockPos key = new BlockPos(Mth.floor(a.centre.x), Mth.floor(a.centre.y), Mth.floor(a.centre.z));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos neighbourKey = key.offset(dx, dy, dz);
                        List<SurfaceNavMesh.Polygon> candidates = index.get(neighbourKey);
                        if (candidates == null) continue;
                        for (SurfaceNavMesh.Polygon b : candidates) {
                            if (a == b) continue;
                            if (a.centre.distanceTo(b.centre) <= 1.05) {
                                a.neighbours.add(b);
                            }
                        }
                    }
                }
            }
        }

        return new SurfaceNavMesh(polys);
    }

    /** Validates that the face at {@code pos} in direction {@code dir} is a usable surface. */
    private boolean isValidSurface(Level level, BlockPos pos, Direction dir, BlockPos adj) {
        BlockState state = level.getBlockState(pos);
        if (!state.isFaceSturdy(level, pos, dir)) return false;
        if (!state.getCollisionShape(level, pos).bounds().equals(new AABB(0,0,0,1,1,1))) return false; // reject thin blocks

        BlockState neighbour = level.getBlockState(adj);
        if (!neighbour.getCollisionShape(level, adj).isEmpty()) return false; // need empty space

        // Check clearance in front of the surface for the spider's body
        BlockPos clearance = adj.relative(dir.getOpposite());
        if (!level.getBlockState(clearance).getCollisionShape(level, clearance).isEmpty()) return false;

        return true;
    }
}

