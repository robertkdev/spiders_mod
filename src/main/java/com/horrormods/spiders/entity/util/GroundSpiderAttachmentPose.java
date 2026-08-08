package com.horrormods.spiders.entity.util;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class GroundSpiderAttachmentPose {
    public enum Axis {
        NONE,
        X,
        Z
    }

    private GroundSpiderAttachmentPose() {
    }

    public static Axis rotationAxis(Direction attachment) {
        switch (attachment) {
            case UP:
            case NORTH:
            case SOUTH:
                return Axis.X;
            case WEST:
            case EAST:
                return Axis.Z;
            default:
                return Axis.NONE;
        }
    }

    public static float rotationDegrees(Direction attachment) {
        switch (attachment) {
            case UP:
                return 180.0F;
            case NORTH:
                return 90.0F;
            case SOUTH:
                return -90.0F;
            case WEST:
                return -90.0F;
            case EAST:
                return 90.0F;
            default:
                return 0.0F;
        }
    }

    public static Vec3 bellyVector(Direction attachment) {
        return new Vec3(attachment.getStepX(), attachment.getStepY(), attachment.getStepZ());
    }

    public static Vec3 backVector(Direction attachment) {
        return bellyVector(attachment).scale(-1.0D);
    }
}
