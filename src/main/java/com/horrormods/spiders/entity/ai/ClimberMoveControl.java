package com.horrormods.spiders.entity.ai;

import com.horrormods.spiders.entity.GroundSpiderEntity;
import com.horrormods.spiders.entity.util.AttachmentHelper;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

public class ClimberMoveControl extends MoveControl {

    public static boolean DEBUG = false;

    public ClimberMoveControl(GroundSpiderEntity mob) {
        super(mob);
    }

    @Override
    public void tick() {
        if (this.operation != Operation.MOVE_TO) {
            this.mob.setZza(0.0F);
            return;
        }

        final double tx = this.wantedX, ty = this.wantedY, tz = this.wantedZ;

        this.mob.setZza((float) this.speedModifier);
        if (((GroundSpiderEntity)this.mob).getAttachmentDirection() == Direction.DOWN) {
            float groundSpeed = (float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
            this.mob.setSpeed(groundSpeed);
        }

        Direction attach = ((GroundSpiderEntity)this.mob).getAttachmentDirection();

        double dx = tx - this.mob.getX();
        double dy = ty - this.mob.getY();
        double dz = tz - this.mob.getZ();
        Vec3 toTarget = new Vec3(dx, dy, dz);

        Vec3 n = AttachmentHelper.normal(attach);
        Vec3 tangentDir = (attach == Direction.DOWN) ?
                new Vec3(dx, 0, dz) :
                AttachmentHelper.projectOntoPlane(toTarget, n);

        float targetYaw;
        if (tangentDir.lengthSqr() > 1.0E-6) {
            targetYaw = (float)(Math.atan2(tangentDir.z, tangentDir.x) * (180.0 / Math.PI)) - 90.0F;
        } else {
            targetYaw = this.mob.getYRot();
        }
        float newYaw = this.rotlerp(this.mob.getYRot(), targetYaw, 30.0F);
        this.mob.setYRot(newYaw);
        this.mob.yBodyRot = newYaw;

        if (attach != Direction.DOWN) {
            double horiz = Math.max(1.0E-4, Math.sqrt(tangentDir.x * tangentDir.x + tangentDir.z * tangentDir.z));
            float targetPitch = (float)(-(Math.atan2(tangentDir.y, horiz) * (180.0 / Math.PI)));
            float newPitch = Mth.rotLerp(0.35f, this.mob.getXRot(), targetPitch);
            newPitch = Mth.clamp(newPitch, -85.0F, 85.0F);
            this.mob.setXRot(newPitch);
        }

        if (DEBUG) {
            System.out.printf("[TURN] t=%d yaw=%.1f pitch=%.1f attach=%s to=(%.2f,%.2f,%.2f) tangent=(%.3f,%.3f,%.3f)%n",
                    this.mob.tickCount, this.mob.getYRot(), this.mob.getXRot(), attach,
                    tx, ty, tz, tangentDir.x, tangentDir.y, tangentDir.z);
        }

        this.operation = Operation.WAIT;
    }
}
