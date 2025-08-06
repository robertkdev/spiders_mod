package com.horrormods.spiders.entity.ai;

import com.google.common.collect.ImmutableSet;
import com.horrormods.spiders.entity.GroundSpiderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

public class AdvancedClimberPathNavigator extends GroundPathNavigation {

    public AdvancedClimberPathNavigator(Mob mob, Level level, boolean canClimbWalls, boolean canClimbCeilings) {
        super(mob, level);
        // The move controller is now set in the entity's constructor, where it belongs.
    }

    @Override
    protected PathFinder createPathFinder(int pMaxVisitedNodes) {
        AdvancedWalkNodeEvaluator nodeEval = new AdvancedWalkNodeEvaluator();
        nodeEval.setStartPathOnGround(false);
        nodeEval.setCanPathWalls(true);
        nodeEval.setCanPathCeiling(true);
        this.nodeEvaluator = nodeEval;
        return new PathFinder(this.nodeEvaluator, pMaxVisitedNodes);
    }

    @Override
    public Path createPath(BlockPos pPos, int pAccuracy) {
        return this.createPath(ImmutableSet.of(pPos), 8, false, pAccuracy);
    }

    @Override
    public Path createPath(Entity pEntity, int pAccuracy) {
        return this.createPath(ImmutableSet.of(pEntity.blockPosition()), 16, true, pAccuracy);
    }

    @Override
    protected Vec3 getTempMobPos() {
        return new Vec3(this.mob.getX(), this.mob.getY() + this.mob.getBbHeight() * 0.5D, this.mob.getZ());
    }

    @Override
    public void tick() {
        this.tick++;
        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }
        if (this.isDone()) {
            return;
        }

        if (atWaypoint()) {
            this.path.advance();
        }

        if (!this.isDone()) {
            Vec3 exactTarget = getExactPathingTarget(this.path.getNextNodePos());
            this.mob.getMoveControl().setWantedPosition(exactTarget.x, exactTarget.y, exactTarget.z, this.speedModifier);
        }
    }

    private boolean atWaypoint() {
        Vec3 center = Vec3.atCenterOf(this.path.getNextNodePos());
        return this.mob.position().distanceToSqr(center) < 4.0D;
    }

    private Vec3 getExactPathingTarget(BlockPos nodePos) {
        if (this.mob instanceof GroundSpiderEntity spider && this.path.getEndNode() instanceof AdvancedWalkNodeEvaluator.CustomNode endNode) {
            Direction attachment = endNode.attachment;
            if (attachment != null && attachment.getAxis().isHorizontal()) {
                double halfWidth = spider.getBbWidth() / 2.0;
                double cx = nodePos.getX() + 0.5;
                double cz = nodePos.getZ() + 0.5;

                cx -= attachment.getStepX() * halfWidth;
                cz -= attachment.getStepZ() * halfWidth;

                return new Vec3(cx, nodePos.getY() + 0.5, cz);
            }
        }
        return Vec3.atBottomCenterOf(nodePos);
    }

    @Override
    protected boolean hasValidPathType(BlockPathTypes type) {
        if (type == BlockPathTypes.WATER || type == BlockPathTypes.LAVA || type == BlockPathTypes.DAMAGE_FIRE) {
            return false;
        }
        return type != BlockPathTypes.BLOCKED && type != BlockPathTypes.FENCE;
    }

    @Override
    protected boolean canUpdatePath() {
        return true;
    }

    @Override
    public boolean isStableDestination(BlockPos pos) {
        if (this.nodeEvaluator instanceof AdvancedWalkNodeEvaluator eval) {
            return eval.findValidAttachment(pos) != null;
        }
        return super.isStableDestination(pos);
    }
}