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
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

public class ClimberPathNavigator extends GroundPathNavigation {

    public static boolean DEBUG = false;

    public ClimberPathNavigator(Mob mob, Level level, boolean canClimbWalls, boolean canClimbCeilings) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisited) {
        ClimberNodeEvaluator eval = new ClimberNodeEvaluator();
        eval.setStartPathOnGround(false);
        eval.setCanPathWalls(true);
        eval.setCanPathCeiling(true);
        this.nodeEvaluator = eval;
        return new PathFinder(this.nodeEvaluator, maxVisited);
    }

    // --- Create-path helpers kept for parity with vanilla ---
    @Override public Path createPath(BlockPos pos, int accuracy) {
        return this.createPath(ImmutableSet.of(pos), 8, false, accuracy);
    }
    @Override public Path createPath(Entity entity, int accuracy) {
        return this.createPath(ImmutableSet.of(entity.blockPosition()), 16, true, accuracy);
    }

    // ---------- ENTRY POINT (only this override is required on 1.19.2) ----------
    @Override
    public boolean moveTo(Path path, double speed) {
        if (path == null) return false;
        snapToFirstNodeIfNeeded(path);
        return super.moveTo(path, speed);
    }

    // ---------- FOLLOW LOOP ----------
    @Override
    protected void followThePath() {
        if (this.path == null || this.isDone()) return;

        Vec3 mobPos = getTempMobPos();
        float advance = Math.max(0.4F, this.mob.getBbWidth() * 0.7F);

        if (mobPos.distanceToSqr(Vec3.atCenterOf(this.path.getNextNodePos())) < advance * advance) {
            this.path.advance();
        }

        if (!this.isDone()) {
            Vec3 exact = getExactPathingTarget(this.path.getNextNode());
            if (DEBUG) {
                System.out.printf("[NAV] t=%d target=(%.2f,%.2f,%.2f)%n", this.tick, exact.x, exact.y, exact.z);
            }
            this.mob.getMoveControl().setWantedPosition(exact.x, exact.y, exact.z, this.speedModifier);
        }
    }

    @Override
    protected Vec3 getTempMobPos() {
        return new Vec3(this.mob.getX(), this.mob.getY() + this.mob.getBbHeight() * 0.5D, this.mob.getZ());
    }

    @Override protected boolean canUpdatePath() { return true; }
    @Override protected boolean hasValidPathType(BlockPathTypes t) {
        if (t == BlockPathTypes.WATER || t == BlockPathTypes.LAVA || t == BlockPathTypes.DAMAGE_FIRE) return false;
        return t != BlockPathTypes.BLOCKED && t != BlockPathTypes.FENCE;
    }
    @Override public boolean isStableDestination(BlockPos pos) {
        if (this.nodeEvaluator instanceof ClimberNodeEvaluator eval) {
            return eval.findValidAttachment(pos) != null;
        }
        return super.isStableDestination(pos);
    }

    // ---------- ANCHORING ----------
    private void snapToFirstNodeIfNeeded(Path path) {
        if (!(this.mob instanceof GroundSpiderEntity spider)) return;
        if (path.getNodeCount() <= 0) return;

        Node first = path.getNode(0);
        Direction a = (first instanceof ClimberNodeEvaluator.CustomNode cn && cn.attachment != null)
                ? cn.attachment : Direction.DOWN;

        Vec3 anchor = getAnchor(spider, first.asBlockPos(), a);
        spider.teleportTo(anchor.x, anchor.y, anchor.z);
        spider.setAttachmentDirection(a);

        if (DEBUG) {
            System.out.printf("[NAV] snap first=%s attach=%s anchor=(%.3f,%.3f,%.3f)%n",
                    first.asBlockPos(), a, anchor.x, anchor.y, anchor.z);
        }
    }

    private Vec3 getExactPathingTarget(Node node) {
        BlockPos pos = node.asBlockPos();
        Direction a = (node instanceof ClimberNodeEvaluator.CustomNode cn && cn.attachment != null)
                ? cn.attachment : Direction.DOWN;
        return getAnchor((GroundSpiderEntity) this.mob, pos, a);
    }

    /** Body-center for each face so AABB just kisses the surface (with a small epsilon). */
    private Vec3 getAnchor(GroundSpiderEntity e, BlockPos airPos, Direction a) {
        double cx = airPos.getX() + 0.5, cy = airPos.getY() + 0.5, cz = airPos.getZ() + 0.5;
        double halfW = e.getBbWidth() / 2.0, halfH = e.getBbHeight() / 2.0, eps = 0.03125;

        return switch (a) {
            case DOWN  -> new Vec3(cx, airPos.getY() + halfH + eps, cz);
            case UP    -> new Vec3(cx, airPos.getY() + 1.0 - halfH - eps, cz);
            case NORTH -> new Vec3(cx, cy, airPos.getZ() + 1.0 - halfW - eps);
            case SOUTH -> new Vec3(cx, cy, airPos.getZ() +       halfW + eps);
            case WEST  -> new Vec3(airPos.getX() + 1.0 - halfW - eps, cy, cz);
            case EAST  -> new Vec3(airPos.getX() +       halfW + eps, cy, cz);
            default    -> new Vec3(cx, airPos.getY() + halfH, cz);
        };
    }
}
