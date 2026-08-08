package com.horrormods.spiders.client.renderer.entity;

import com.horrormods.spiders.entity.util.GroundSpiderAttachmentPose;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroundSpiderAttachmentPoseTest {
    @Test
    void bellyDownVectorRotatesTowardSupportSurface() {
        assertBellyFaces(Direction.DOWN, 0, -1, 0);
        assertBellyFaces(Direction.UP, 0, 1, 0);
        assertBellyFaces(Direction.NORTH, 0, 0, -1);
        assertBellyFaces(Direction.SOUTH, 0, 0, 1);
        assertBellyFaces(Direction.WEST, -1, 0, 0);
        assertBellyFaces(Direction.EAST, 1, 0, 0);
    }

    @Test
    void backUpVectorRotatesAwayFromSupportSurface() {
        assertBackFaces(Direction.DOWN, 0, 1, 0);
        assertBackFaces(Direction.UP, 0, -1, 0);
        assertBackFaces(Direction.NORTH, 0, 0, 1);
        assertBackFaces(Direction.SOUTH, 0, 0, -1);
        assertBackFaces(Direction.WEST, 1, 0, 0);
        assertBackFaces(Direction.EAST, -1, 0, 0);
    }

    private static void assertBellyFaces(Direction attachment, int expectedX, int expectedY, int expectedZ) {
        Vec rotated = rotateVector(attachment, new Vec(0.0D, -1.0D, 0.0D));

        assertEquals(expectedX, Math.round(rotated.x), "belly x for " + attachment);
        assertEquals(expectedY, Math.round(rotated.y), "belly y for " + attachment);
        assertEquals(expectedZ, Math.round(rotated.z), "belly z for " + attachment);
    }

    private static void assertBackFaces(Direction attachment, int expectedX, int expectedY, int expectedZ) {
        Vec rotated = rotateVector(attachment, new Vec(0.0D, 1.0D, 0.0D));

        assertEquals(expectedX, Math.round(rotated.x), "back x for " + attachment);
        assertEquals(expectedY, Math.round(rotated.y), "back y for " + attachment);
        assertEquals(expectedZ, Math.round(rotated.z), "back z for " + attachment);
    }

    private static Vec rotateVector(Direction attachment, Vec vector) {
        double radians = Math.toRadians(GroundSpiderAttachmentPose.rotationDegrees(attachment));
        GroundSpiderAttachmentPose.Axis axis = GroundSpiderAttachmentPose.rotationAxis(attachment);

        switch (axis) {
            case X:
                return new Vec(
                        vector.x,
                        Math.cos(radians) * vector.y - Math.sin(radians) * vector.z,
                        Math.sin(radians) * vector.y + Math.cos(radians) * vector.z);
            case Z:
                return new Vec(
                        Math.cos(radians) * vector.x - Math.sin(radians) * vector.y,
                        Math.sin(radians) * vector.x + Math.cos(radians) * vector.y,
                        vector.z);
            default:
                return vector;
        }
    }

    private static final class Vec {
        private final double x;
        private final double y;
        private final double z;

        private Vec(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
