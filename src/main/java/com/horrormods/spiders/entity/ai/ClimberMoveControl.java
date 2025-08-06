package com.horrormods.spiders.entity.ai;

import com.horrormods.spiders.entity.GroundSpiderEntity;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

public class ClimberMoveControl extends MoveControl {

    public ClimberMoveControl(GroundSpiderEntity pMob) {
        super(pMob);
    }

    @Override
    public void tick() {
        if (this.operation != Operation.MOVE_TO) {
            // Stop movement if the goal is reached or there's no goal
            this.mob.setZza(0.0F);
            return;
        }

        this.operation = Operation.WAIT; // Reset operation, will be set again by navigator if needed

        if (this.mob instanceof GroundSpiderEntity spider) {
            Direction attachment = spider.getAttachmentDirection();
            spider.setNoGravity(attachment != Direction.DOWN);

            // --- MOVEMENT ---
            Vec3 targetVec = new Vec3(this.wantedX - mob.getX(), this.wantedY - mob.getY(), this.wantedZ - mob.getZ());
            double distanceToTarget = targetVec.length();

            // Stop if we are very close to the target
            if (distanceToTarget < 0.5D) {
                this.mob.setDeltaMovement(Vec3.ZERO);
                return;
            }

            targetVec = targetVec.normalize();

            if (attachment != Direction.DOWN) {
                // --- THIS IS THE CORRECTED LOGIC FOR WALL/CEILING MOVEMENT ---
                // We directly SET the velocity to a reasonable speed, preventing runaway acceleration.
                double speed = this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
                this.mob.setDeltaMovement(targetVec.scale(speed));
                // ----------------------------------------------------------------

            } else {
                // For ground movement, let the vanilla method handle it, which includes friction.
                this.mob.moveRelative((float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)), new Vec3(this.wantedX - mob.getX(), 0, this.wantedZ - mob.getZ()));
            }

            // --- ROTATION ---
            float targetYaw = (float) (Mth.atan2(targetVec.z, targetVec.x) * (double) (180F / (float) Math.PI)) - 90.0F;
            float currentYaw = mob.getYRot();
            float yawDelta = Mth.wrapDegrees(targetYaw - currentYaw);

            // Use the mob's turn speed attribute for smooth, configurable turning
            float maxTurnSpeed = (float) this.mob.getAttributeValue(Attributes.FLYING_SPEED) * 10; // Or a fixed value

            mob.setYRot(currentYaw + Mth.clamp(yawDelta, -maxTurnSpeed, maxTurnSpeed));
            mob.yBodyRot = mob.getYRot();
        }
    }
}