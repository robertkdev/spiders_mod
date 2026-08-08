package com.horrormods.spiders.entity.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for finding and validating attachment surfaces.
 */
public final class AttachmentHelper {

    private AttachmentHelper() {}

    public record SupportedAttachment(Direction attachment, BlockPos airPos, Vec3 anchor) {}

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

    public static SupportedAttachment findClosestSupportedAttachment(Level level, Mob mob, BlockPos origin,
            Direction preferred, Vec3 currentPosition) {
        List<Direction> directions = new ArrayList<>();
        if (preferred != null) {
            directions.add(preferred);
        }
        for (Direction direction : Direction.values()) {
            if (direction != preferred) {
                directions.add(direction);
            }
        }

        SupportedAttachment best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Direction direction : directions) {
            SupportedAttachment candidate = findClosestSupportedAttachmentOn(level, mob, origin, direction, currentPosition);
            if (candidate == null) {
                continue;
            }

            double distance = currentPosition.distanceToSqr(candidate.anchor());
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    public static SupportedAttachment findClosestSupportedAttachmentOn(Level level, Mob mob, BlockPos origin,
            Direction attachment, Vec3 currentPosition) {
        SupportedAttachment best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (BlockPos candidate : nearbyAirPositions(origin, attachment)) {
            if (!hasSupport(level, candidate, attachment) || !aabbFitsOnSurface(level, mob, candidate, attachment)) {
                continue;
            }

            Vec3 anchor = anchorFor(mob, candidate, attachment);
            double distance = currentPosition.distanceToSqr(anchor);
            if (distance < bestDistance) {
                best = new SupportedAttachment(attachment, candidate, anchor);
                bestDistance = distance;
            }
        }
        return best;
    }

    private static List<BlockPos> nearbyAirPositions(BlockPos origin, Direction attachment) {
        List<BlockPos> positions = new ArrayList<>();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (attachment.getAxis().isVertical()) {
                    positions.add(origin.offset(a, 0, b));
                } else if (attachment.getAxis() == Direction.Axis.X) {
                    positions.add(origin.offset(0, a, b));
                } else {
                    positions.add(origin.offset(a, b, 0));
                }
            }
        }
        return positions;
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

    public static Vec3 anchorFor(Mob mob, BlockPos pos, Direction surface) {
        double halfW = mob.getBbWidth() / 2.0;
        double halfH = mob.getBbHeight() / 2.0;
        double height = mob.getBbHeight();
        double eps = 0.03125;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5 - halfH;
        double cz = pos.getZ() + 0.5;

        return switch (surface) {
            case DOWN -> new Vec3(cx, pos.getY() + eps, cz);
            case UP -> new Vec3(cx, pos.getY() + 1.0 - height - eps, cz);
            case NORTH -> new Vec3(cx, cy, pos.getZ() + halfW + eps);
            case SOUTH -> new Vec3(cx, cy, pos.getZ() + 1.0 - halfW - eps);
            case WEST -> new Vec3(pos.getX() + halfW + eps, cy, cz);
            case EAST -> new Vec3(pos.getX() + 1.0 - halfW - eps, cy, cz);
        };
    }

    public static Vec3 projectOntoPlane(Vec3 v, Vec3 n) {
        double dot = v.dot(n);
        return v.subtract(n.scale(dot));
    }
}
