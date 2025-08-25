package com.horrormods.spiders.entity.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Helpers for finding and validating attachment surfaces.
 */
public final class AttachmentHelper {

    private AttachmentHelper() {}

    public static Direction findAttachment(Level level, Mob mob, BlockPos pos) {
        // Check floor, walls and ceiling in priority order
        Direction[] order = {
                Direction.DOWN,
                Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
                Direction.UP
        };
        for (Direction d : order) {
            if (hasSupport(level, pos, d) && aabbFitsOnSurface(level, mob, pos, d)) {
                return d;
            }
        }
        return null;
    }

    public static boolean hasSupport(Level level, BlockPos pos, Direction d) {
        BlockPos support = pos.relative(d);
        BlockState st = level.getBlockState(support);
        return !st.getCollisionShape(level, support).isEmpty();
    }

    public static boolean aabbFitsOnSurface(Level level, Mob mob, BlockPos pos, Direction surface) {
        double halfW = mob.getBbWidth() / 2.0;
        double h = mob.getBbHeight();
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        AABB box;
        if (surface.getAxis().isHorizontal()) {
            cx -= surface.getStepX() * halfW;
            cz -= surface.getStepZ() * halfW;
            box = new AABB(cx - halfW, cy - h * 0.5, cz - halfW,
                    cx + halfW, cy + h * 0.5, cz + halfW);
        } else {
            box = new AABB(cx - halfW, pos.getY(), cz - halfW,
                    cx + halfW, pos.getY() + h, cz + halfW);
        }
        return level.noCollision(mob, box);
    }

    public static Vec3 normal(Direction d) {
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    public static Vec3 projectOntoPlane(Vec3 v, Vec3 n) {
        double dot = v.dot(n);
        return v.subtract(n.scale(dot));
    }
}
