package com.horrormods.spiders.entity.ai.nav;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal representation of a navigation mesh for spider traversal.
 * <p>
 * Each polygon stands for a contiguous surface region—floor, wall, or ceiling.
 * The class stores polygon centres and adjacency information; generating and
 * validating the mesh from world geometry is beyond the scope of this sample.
 */
public class SurfaceNavMesh {

    /**
     * Simple polygon node used by the nav-mesh.
     * <p>
     * Besides its centre point we also keep track of which direction the
     * supporting surface faces. This is required so the caller can ensure the
     * spider remains anchored to a valid surface while travelling between
     * polygons.
     */
    public static class Polygon {
        public final Vec3 centre;
        public final Direction normal;
        public final List<Polygon> neighbours = new ArrayList<>();

        public Polygon(Vec3 centre, Direction normal) {
            this.centre = centre;
            this.normal = normal;
        }
    }

    private final List<Polygon> polygons;

    public SurfaceNavMesh(List<Polygon> polygons) {
        this.polygons = polygons;
    }

    public List<Polygon> getPolygons() {
        return Collections.unmodifiableList(polygons);
    }
}
