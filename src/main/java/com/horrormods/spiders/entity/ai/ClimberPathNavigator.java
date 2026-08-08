package com.horrormods.spiders.entity.ai;

import com.google.common.collect.ImmutableSet;
import com.horrormods.spiders.Spiders;
import com.horrormods.spiders.entity.GroundSpiderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import com.horrormods.spiders.entity.util.AttachmentHelper;
import com.horrormods.spiders.entity.ai.nav.NavMeshPathFinder;
import com.horrormods.spiders.entity.ai.ThetaStar;
import net.minecraftforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ClimberPathNavigator extends GroundPathNavigation {

    public static boolean DEBUG = false;
    private static final int ENTITY_TARGET_THETA_STAR_LIMIT = 512;
    private static final int ENTITY_TARGET_FALLBACK_LIMIT = 2;
    private static final int FAST_PLAYER_PURSUIT_THETA_STAR_LIMIT = 512;
    private static final int FAST_PLAYER_PURSUIT_TARGET_LIMIT = 3;
    private static final int DENSE_PLAYER_PURSUIT_THETA_STAR_LIMIT = 128;
    private static final int DENSE_PLAYER_PURSUIT_TARGET_LIMIT = 1;
    private static final int ENTITY_REPATH_COOLDOWN_TICKS = 14;
    private static final int DENSE_PLAYER_REPATH_COOLDOWN_TICKS = 24;
    private static final int ENTITY_REPATH_STAGGER_TICKS = 8;
    private static final int DENSE_PLAYER_REPATH_STAGGER_TICKS = 16;
    private static final double ENTITY_REPATH_DISTANCE_SQR = 4.0D;
    private static final int DIRECT_SAME_SURFACE_THETA_STAR_LIMIT = 96;
    private static final int BLOCK_TARGET_THETA_STAR_LIMIT = 512;
    private static final int ENTITY_MOVE_REQUEST_REUSE_TICKS = 14;
    private static final int ENTITY_MOVE_REQUEST_FAILURE_BACKOFF_TICKS = 18;
    private static final double ENTITY_MOVE_REQUEST_STABLE_DISTANCE_SQR = 1.0D;
    private static final int DIRECT_CHASE_CACHE_TICKS = 6;
    private static final double DIRECT_CHASE_CACHE_DISTANCE_SQR = 1.0D;
    private static final double ENTITY_BLOCKING_ROUTE_PENALTY = 10000.0D;
    private static final long PERF_PATH_LOG_THRESHOLD_NS = 250_000_000L;
    private static final long PERF_DIRECT_CHASE_LOG_THRESHOLD_NS = 100_000_000L;
    private Vec3 lastTargetPos = Vec3.ZERO;
    private final NavMeshPathFinder meshFinder;
    private int entityRepathCooldown = 0;
    private Entity directChaseCachedTarget;
    private Vec3 directChaseCachedMobPos = Vec3.ZERO;
    private Vec3 directChaseCachedTargetPos = Vec3.ZERO;
    private int directChaseCacheTicks = 0;
    private boolean directChaseCachedResult = false;
    private Entity lastEntityMoveRequestTarget;
    private Vec3 lastEntityMoveRequestMobPos = Vec3.ZERO;
    private Vec3 lastEntityMoveRequestTargetPos = Vec3.ZERO;
    private Direction lastEntityMoveRequestAttachment = Direction.DOWN;
    private int lastEntityMoveRequestTick = Integer.MIN_VALUE;
    private boolean lastEntityMoveRequestSucceeded = false;

    public ClimberPathNavigator(Mob mob, Level level, boolean canClimbWalls, boolean canClimbCeilings) {
        super(mob, level);
        this.meshFinder = new NavMeshPathFinder(level);
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
        if (this.mob instanceof GroundSpiderEntity spider && entity != null) {
            return createPathToEntityTarget(spider, entity);
        }
        return this.createPath(getReachableTargetCandidates(entity), 16, true, accuracy);
    }

    public boolean canReachEntityTarget(Entity entity) {
        if (!(this.mob instanceof GroundSpiderEntity) || entity == null) {
            return false;
        }

        List<BlockPos> candidates = new ArrayList<>(getMeleeTargetCandidates(entity));
        Vec3 start = this.getTempMobPos();
        candidates.sort(Comparator.comparingDouble(pos -> targetCandidateCost(entity, start, pos)));
        for (BlockPos candidate : candidates) {
            Path path = createReachCheckPath(candidate);
            if (path != null) {
                return true;
            }
        }
        return false;
    }

    private Set<BlockPos> getMeleeTargetCandidates(Entity entity) {
        LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
        for (BlockPos candidate : getReachableTargetCandidates(entity)) {
            for (Direction attachment : Direction.values()) {
                if (AttachmentHelper.hasSupport(this.level, candidate, attachment)
                        && AttachmentHelper.aabbFitsOnSurface(this.level, this.mob, candidate, attachment)
                        && attackBoxAt(AttachmentHelper.anchorFor(this.mob, candidate, attachment)).intersects(entity.getBoundingBox())) {
                    candidates.add(candidate);
                    break;
                }
            }
        }
        return candidates;
    }

    private Path createReachCheckPath(BlockPos candidate) {
        if (this.mob instanceof GroundSpiderEntity spider) {
            Vec3 start = this.getTempMobPos();
            return createPathToBlock(spider, start, candidate, 512, false);
        }
        return this.createPath(ImmutableSet.of(candidate), 16, true, 0);
    }

    private AABB attackBoxAt(Vec3 feetPosition) {
        double halfWidth = this.mob.getBbWidth() / 2.0D;
        double height = this.mob.getBbHeight();
        double attackReach = Math.sqrt(2.04F) - 0.6D;
        return new AABB(
                feetPosition.x - halfWidth,
                feetPosition.y,
                feetPosition.z - halfWidth,
                feetPosition.x + halfWidth,
                feetPosition.y + height,
                feetPosition.z + halfWidth)
                .inflate(attackReach, 0.0D, attackReach);
    }

    @Override
    protected Path createPath(Set<BlockPos> positions, int maxVisited, boolean offsetUpward, int accuracy) {
        if (this.mob instanceof GroundSpiderEntity spider && positions != null && !positions.isEmpty()) {
            Vec3 start = this.getTempMobPos();
            List<BlockPos> sortedTargets = new ArrayList<>(positions);
            sortedTargets.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(start)));
            return chooseBestPathToTargets(spider, start, sortedTargets,
                    BLOCK_TARGET_THETA_STAR_LIMIT, true, null);
        }
        return null;
    }

    private Path createPathToEntityTarget(GroundSpiderEntity spider, Entity entity) {
        long startNs = System.nanoTime();
        Vec3 start = this.getTempMobPos();
        Set<BlockPos> meleeTargets = getMeleeTargetCandidates(entity);
        List<BlockPos> sortedTargets = new ArrayList<>(meleeTargets.isEmpty()
                ? getReachableTargetCandidates(entity)
                : meleeTargets);
        sortedTargets.sort(Comparator.comparingDouble(pos -> targetCandidateCost(entity, start, pos)));
        Path result = entity instanceof FakePlayer
                ? null
                : createDirectSameSurfaceEntityPath(spider, entity, start, sortedTargets);
        if (result == null) {
            List<BlockPos> fallbackTargets = sortedTargets.subList(
                    0, Math.min(ENTITY_TARGET_FALLBACK_LIMIT, sortedTargets.size()));
            result = chooseBestPathToTargets(spider, start, fallbackTargets,
                    ENTITY_TARGET_THETA_STAR_LIMIT, false, entity);
        }
        logSlowEntityPath("entity", spider, entity, sortedTargets.size(), result, startNs);
        return result;
    }

    private Path createFastPlayerPursuitPath(GroundSpiderEntity spider, Entity entity) {
        long startNs = System.nanoTime();
        boolean denseLivePlayer = isDenseLivePlayerPursuit(spider, entity);
        Vec3 start = this.getTempMobPos();
        Set<BlockPos> meleeTargets = getMeleeTargetCandidates(entity);
        List<BlockPos> sortedTargets = new ArrayList<>(meleeTargets.isEmpty()
                ? getReachableTargetCandidates(entity)
                : meleeTargets);
        sortedTargets.sort(Comparator.comparingDouble(pos -> targetCandidateCost(entity, start, pos)));

        Path directPath = createDirectSameSurfaceEntityPath(spider, entity, start, sortedTargets);
        if (directPath != null) {
            long elapsedNs = System.nanoTime() - startNs;
            if (elapsedNs >= PERF_PATH_LOG_THRESHOLD_NS) {
                Spiders.LOGGER.info("spiders_perf_path kind=fast_player_direct dense={} entity_id={} elapsed_ms={} candidates={} result=true",
                        denseLivePlayer,
                        spider.getId(),
                        String.format(java.util.Locale.ROOT, "%.3f", elapsedNs / 1_000_000.0D),
                        sortedTargets.size());
            }
            return directPath;
        }

        int targetLimit = denseLivePlayer
                ? DENSE_PLAYER_PURSUIT_TARGET_LIMIT
                : FAST_PLAYER_PURSUIT_TARGET_LIMIT;
        int thetaStarLimit = denseLivePlayer
                ? DENSE_PLAYER_PURSUIT_THETA_STAR_LIMIT
                : FAST_PLAYER_PURSUIT_THETA_STAR_LIMIT;
        int limit = Math.min(targetLimit, sortedTargets.size());
        Path result = null;
        for (int i = 0; i < limit; i++) {
            Path path = createPathToBlock(spider, start, sortedTargets.get(i), thetaStarLimit, false);
            if (path != null) {
                result = path;
                break;
            }
        }
        long elapsedNs = System.nanoTime() - startNs;
        if (elapsedNs >= PERF_PATH_LOG_THRESHOLD_NS) {
            Spiders.LOGGER.info("spiders_perf_path kind=fast_player dense={} entity_id={} elapsed_ms={} candidates={} limit={} theta_limit={} result={}",
                    denseLivePlayer,
                    spider.getId(),
                    String.format(java.util.Locale.ROOT, "%.3f", elapsedNs / 1_000_000.0D),
                    sortedTargets.size(),
                    limit,
                    thetaStarLimit,
                    result != null);
        }
        return result;
    }

    private Path createPathToBlock(GroundSpiderEntity spider, Vec3 start, BlockPos target, int thetaStarLimit) {
        return createPathToBlock(spider, start, target, thetaStarLimit, true);
    }

    private Path createPathToBlock(GroundSpiderEntity spider, Vec3 start, BlockPos target, int thetaStarLimit,
            boolean allowMeshFallback) {
        Vec3 targetCenter = Vec3.atCenterOf(target);
        Path thetaPath = thetaStarLimit > 0
                ? ThetaStar.find(spider, this.level, start, targetCenter, thetaStarLimit)
                : ThetaStar.find(spider, this.level, start, targetCenter);
        if (thetaPath != null) {
            return thetaPath;
        }
        return allowMeshFallback ? meshFinder.findPath(spider, start, target) : null;
    }

    private Path createDirectSameSurfaceEntityPath(GroundSpiderEntity spider, Entity entity, Vec3 start,
            List<BlockPos> sortedTargets) {
        Direction attachment = spider.getAttachmentDirection();
        Vec3 normal = AttachmentHelper.normal(attachment);
        double transitionThreshold = Math.max(1.25D, spider.getBbWidth() * 2.0D);
        int limit = Math.min(3, sortedTargets.size());
        for (int i = 0; i < limit; i++) {
            BlockPos candidate = sortedTargets.get(i);
            if (!AttachmentHelper.hasSupport(this.level, candidate, attachment)
                    || !AttachmentHelper.aabbFitsOnSurface(this.level, spider, candidate, attachment)) {
                continue;
            }
            Vec3 candidateAnchor = AttachmentHelper.anchorFor(spider, candidate, attachment);
            if (Math.abs(candidateAnchor.subtract(start).dot(normal)) > transitionThreshold
                    || isEntityBlockingSegment(entity, start, candidateAnchor)) {
                continue;
            }
            Path path = ThetaStar.find(spider, this.level, start, candidateAnchor,
                    DIRECT_SAME_SURFACE_THETA_STAR_LIMIT);
            if (path != null) {
                return path;
            }
        }
        return null;
    }

    private void logSlowEntityPath(String kind, GroundSpiderEntity spider, Entity entity, int candidateCount,
            Path result, long startNs) {
        long elapsedNs = System.nanoTime() - startNs;
        if (elapsedNs < PERF_PATH_LOG_THRESHOLD_NS) {
            return;
        }
        Spiders.LOGGER.info("spiders_perf_path kind={} entity_id={} target_type={} elapsed_ms={} candidates={} result={} attachment={} distance_sqr={}",
                kind,
                spider.getId(),
                entity.getType().toShortString(),
                String.format(java.util.Locale.ROOT, "%.3f", elapsedNs / 1_000_000.0D),
                candidateCount,
                result != null,
                spider.getAttachmentDirection(),
                String.format(java.util.Locale.ROOT, "%.3f", spider.distanceToSqr(entity)));
    }

    private Path chooseBestPathToTargets(GroundSpiderEntity spider, Vec3 start, List<BlockPos> sortedTargets,
            int thetaStarLimit, boolean allowMeshFallback, Entity targetEntity) {
        Path bestPath = null;
        double bestCost = Double.POSITIVE_INFINITY;

        for (BlockPos target : sortedTargets) {
            double candidatePenalty = targetEntity != null && isEntityBlockingSegment(targetEntity, start, Vec3.atCenterOf(target))
                    ? ENTITY_BLOCKING_ROUTE_PENALTY
                    : 0.0D;
            double lowerBoundCost = start.distanceTo(Vec3.atCenterOf(target)) + candidatePenalty;
            if (lowerBoundCost >= bestCost) {
                continue;
            }

            Path path = createPathToBlock(spider, start, target, thetaStarLimit, allowMeshFallback);
            if (path == null) {
                continue;
            }

            double cost = pathRouteCost(spider, start, path) + candidatePenalty;
            if (cost < bestCost) {
                bestCost = cost;
                bestPath = path;
            }
        }

        return bestPath;
    }

    private Path cheaperPath(GroundSpiderEntity spider, Vec3 start, Path first, Path second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }

        double firstCost = pathRouteCost(spider, start, first);
        double secondCost = pathRouteCost(spider, start, second);
        return secondCost < firstCost ? second : first;
    }

    private double pathRouteCost(GroundSpiderEntity spider, Vec3 start, Path path) {
        if (path == null || path.getNodeCount() <= 0) {
            return Double.POSITIVE_INFINITY;
        }

        double cost = 0.0D;
        Vec3 previous = start;
        for (int i = 0; i < path.getNodeCount(); i++) {
            Vec3 next = anchorForPathCost(spider, path.getNode(i));
            cost += previous.distanceTo(next);
            previous = next;
        }
        return cost;
    }

    private Vec3 anchorForPathCost(GroundSpiderEntity spider, Node node) {
        if (node instanceof ClimberNodeEvaluator.CustomNode cn && !Double.isNaN(cn.px)) {
            return new Vec3(cn.px, cn.py, cn.pz);
        }
        BlockPos pos = node.asBlockPos();
        Direction attachment = (node instanceof ClimberNodeEvaluator.CustomNode cn && cn.attachment != null)
                ? cn.attachment
                : Direction.DOWN;
        return AttachmentHelper.anchorFor(spider, pos, attachment);
    }

    private double targetCandidateCost(Entity targetEntity, Vec3 start, BlockPos candidate) {
        double cost = Vec3.atCenterOf(candidate).distanceToSqr(start);
        if (targetEntity != null && isEntityBlockingSegment(targetEntity, start, Vec3.atCenterOf(candidate))) {
            cost += ENTITY_BLOCKING_ROUTE_PENALTY;
        }
        return cost;
    }

    private Set<BlockPos> getReachableTargetCandidates(Entity entity) {
        LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
        BlockPos target = entity.blockPosition();
        int minY = (int) Math.floor(entity.getBoundingBox().minY) - 1;
        int maxY = (int) Math.floor(entity.getBoundingBox().maxY);
        for (int y = minY; y <= maxY; y++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                candidates.add(new BlockPos(
                        target.getX() + direction.getStepX(),
                        y,
                        target.getZ() + direction.getStepZ()));
            }
        }
        return candidates;
    }


    // ---------- ENTRY POINT (only this override is required on 1.19.2) ----------
    @Override
    public boolean moveTo(Entity entity, double speed) {
        if (!(this.mob instanceof GroundSpiderEntity spider) || entity == null) {
            return false;
        }

        Vec3 mobPos = this.getTempMobPos();
        Vec3 targetPos = entity.position();
        Direction attachment = spider.getAttachmentDirection();
        boolean sameStableRequest = this.lastEntityMoveRequestTarget == entity
                && this.lastEntityMoveRequestAttachment == attachment
                && mobPos.distanceToSqr(this.lastEntityMoveRequestMobPos) <= ENTITY_MOVE_REQUEST_STABLE_DISTANCE_SQR
                && targetPos.distanceToSqr(this.lastEntityMoveRequestTargetPos) <= ENTITY_MOVE_REQUEST_STABLE_DISTANCE_SQR;
        long requestAge = (long) this.mob.tickCount - this.lastEntityMoveRequestTick;
        if (sameStableRequest && requestAge >= 0L) {
            if (this.path != null && !this.isDone() && requestAge < ENTITY_MOVE_REQUEST_REUSE_TICKS) {
                this.setSpeedModifier(speed);
                return true;
            }
            int backoffTicks = this.lastEntityMoveRequestSucceeded
                    ? ENTITY_MOVE_REQUEST_REUSE_TICKS
                    : ENTITY_MOVE_REQUEST_FAILURE_BACKOFF_TICKS;
            if (requestAge < backoffTicks) {
                return false;
            }
        }

        long startNs = System.nanoTime();
        Path requestedPath = shouldUseFastPlayerPursuit(entity)
                ? createFastPlayerPursuitPath(spider, entity)
                : createPathToEntityTarget(spider, entity);
        this.lastEntityMoveRequestTarget = entity;
        this.lastEntityMoveRequestMobPos = mobPos;
        this.lastEntityMoveRequestTargetPos = targetPos;
        this.lastEntityMoveRequestAttachment = attachment;
        this.lastEntityMoveRequestTick = this.mob.tickCount;
        this.lastEntityMoveRequestSucceeded = requestedPath != null;

        long elapsedNs = System.nanoTime() - startNs;
        if (elapsedNs >= PERF_PATH_LOG_THRESHOLD_NS) {
            Spiders.LOGGER.info("spiders_perf_path kind=move_request entity_id={} target_type={} elapsed_ms={} result={} attachment={} distance_sqr={}",
                    spider.getId(),
                    entity.getType().toShortString(),
                    String.format(java.util.Locale.ROOT, "%.3f", elapsedNs / 1_000_000.0D),
                    requestedPath != null,
                    attachment,
                    String.format(java.util.Locale.ROOT, "%.3f", spider.distanceToSqr(entity)));
        }
        return this.moveTo(requestedPath, speed);
    }

    @Override
    public boolean moveTo(Path path, double speed) {
        if (path == null) return false;
        snapToFirstNodeIfNeeded(path);
        return super.moveTo(path, speed);
    }

    // ---------- FOLLOW LOOP ----------
    @Override
    protected void followThePath() {
        Vec3 mobPos = getTempMobPos();
        meshFinder.tick(mobPos);
        if (entityRepathCooldown > 0) {
            entityRepathCooldown--;
        }

        Entity target = this.mob.getTarget();
        if (target != null
                && this.mob instanceof GroundSpiderEntity spider
                && !spider.isCeilingStalking()
                && !spider.isEscapeCutting()
                && this.nodeEvaluator instanceof ClimberNodeEvaluator eval) {
            Vec3 targetPos = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());

            if (canUseDirectChase(spider, target, mobPos, targetPos, eval)) {
                this.mob.getMoveControl().setWantedPosition(targetPos.x, targetPos.y, targetPos.z, this.speedModifier);
                this.lastTargetPos = targetPos;
                this.path = null;
                return;
            }

            if ((this.path == null || this.isDone() || targetPos.distanceToSqr(this.lastTargetPos) > ENTITY_REPATH_DISTANCE_SQR)
                    && entityRepathCooldown <= 0
                    && canStartEntityRepath(target)) {
                this.moveTo(target, this.speedModifier);
                this.lastTargetPos = targetPos;
                this.entityRepathCooldown = entityRepathCooldownFor(target);
            }
        }

        if (this.path == null || this.isDone()) return;

        float advance = Math.max(0.4F, this.mob.getBbWidth() * 0.7F);
        Node next = this.path.getNextNode();
        adoptNextAttachmentIfSupported(next);
        Vec3 exact = getExactPathingTarget(next);
        if (this.mob.position().distanceToSqr(exact) < advance * advance) {
            this.path.advance();
            if (this.isDone()) {
                return;
            }
            next = this.path.getNextNode();
            adoptNextAttachmentIfSupported(next);
            exact = getExactPathingTarget(next);
        }

        if (!this.isDone()) {
            if (DEBUG) {
                System.out.printf("[NAV] t=%d target=(%.2f,%.2f,%.2f)%n", this.tick, exact.x, exact.y, exact.z);
            }
            this.mob.getMoveControl().setWantedPosition(exact.x, exact.y, exact.z, this.speedModifier);
        }
    }

    private boolean shouldUseFastPlayerPursuit(Entity target) {
        return target instanceof Player && !(target instanceof FakePlayer);
    }

    private boolean canStartEntityRepath(Entity target) {
        if (!shouldUseFastPlayerPursuit(target)) {
            return true;
        }
        int stagger = this.mob instanceof GroundSpiderEntity spider && isDenseLivePlayerPursuit(spider, target)
                ? DENSE_PLAYER_REPATH_STAGGER_TICKS
                : ENTITY_REPATH_STAGGER_TICKS;
        return Math.floorMod(this.mob.tickCount + this.mob.getId(), stagger) == 0;
    }

    private int entityRepathCooldownFor(Entity target) {
        boolean denseLivePlayer = this.mob instanceof GroundSpiderEntity spider && isDenseLivePlayerPursuit(spider, target);
        int cooldown = denseLivePlayer ? DENSE_PLAYER_REPATH_COOLDOWN_TICKS : ENTITY_REPATH_COOLDOWN_TICKS;
        if (shouldUseFastPlayerPursuit(target)) {
            int stagger = denseLivePlayer ? DENSE_PLAYER_REPATH_STAGGER_TICKS : ENTITY_REPATH_STAGGER_TICKS;
            cooldown += Math.floorMod(this.mob.getId(), stagger);
        }
        return cooldown;
    }

    private boolean isDenseLivePlayerPursuit(GroundSpiderEntity spider, Entity target) {
        return spider.isDenseLivePlayerSwarmTarget(target);
    }

    private boolean canUseDirectChase(GroundSpiderEntity spider, Entity target, Vec3 mobPos, Vec3 targetPos,
            ClimberNodeEvaluator eval) {
        if (this.directChaseCacheTicks > 0
                && this.directChaseCachedTarget == target
                && mobPos.distanceToSqr(this.directChaseCachedMobPos) <= DIRECT_CHASE_CACHE_DISTANCE_SQR
                && targetPos.distanceToSqr(this.directChaseCachedTargetPos) <= DIRECT_CHASE_CACHE_DISTANCE_SQR) {
            this.directChaseCacheTicks--;
            return this.directChaseCachedResult;
        }

        long startNs = System.nanoTime();
        boolean result = canDirectChaseOnCurrentSurface(spider, mobPos, targetPos)
                && !isEntityBlockingSegment(target, mobPos, targetPos)
                && ThetaStar.hasSurfaceLineOfSight(spider, this.level, eval, mobPos, targetPos);
        long elapsedNs = System.nanoTime() - startNs;
        if (elapsedNs >= PERF_DIRECT_CHASE_LOG_THRESHOLD_NS) {
            Spiders.LOGGER.info("spiders_perf_direct_chase dense={} entity_id={} elapsed_ms={} result={} attachment={} distance_sqr={}",
                    isDenseLivePlayerPursuit(spider, target),
                    spider.getId(),
                    String.format(java.util.Locale.ROOT, "%.3f", elapsedNs / 1_000_000.0D),
                    result,
                    spider.getAttachmentDirection(),
                    String.format(java.util.Locale.ROOT, "%.3f", mobPos.distanceToSqr(targetPos)));
        }
        this.directChaseCachedTarget = target;
        this.directChaseCachedMobPos = mobPos;
        this.directChaseCachedTargetPos = targetPos;
        this.directChaseCachedResult = result;
        this.directChaseCacheTicks = DIRECT_CHASE_CACHE_TICKS;
        return result;
    }

    private boolean hasLineOfSight(Vec3 from, Vec3 to) {
        if (this.mob instanceof GroundSpiderEntity spider && this.nodeEvaluator instanceof ClimberNodeEvaluator eval) {
            return ThetaStar.hasSurfaceLineOfSight(spider, this.level, eval, from, to);
        }
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mob);
        return this.level.clip(ctx).getType() == HitResult.Type.MISS;
    }

    private boolean canDirectChaseOnCurrentSurface(GroundSpiderEntity spider, Vec3 from, Vec3 to) {
        Vec3 diff = to.subtract(from);
        Vec3 normal = AttachmentHelper.normal(spider.getAttachmentDirection());
        double normalDistance = Math.abs(diff.dot(normal));
        double transitionThreshold = Math.max(1.25D, spider.getBbWidth() * 2.0D);
        if (normalDistance > transitionThreshold) {
            return false;
        }
        BlockPos targetBlock = new BlockPos(to);
        Direction attachment = spider.getAttachmentDirection();
        return AttachmentHelper.hasSupport(this.level, targetBlock, attachment)
                && AttachmentHelper.aabbFitsOnSurface(this.level, spider, targetBlock, attachment);
    }

    private boolean isEntityBlockingSegment(Entity target, Vec3 from, Vec3 to) {
        Vec3 fromCenter = from.add(0.0D, this.mob.getBbHeight() * 0.5D, 0.0D);
        double inflate = Math.max(0.4D, this.mob.getBbWidth() * 0.5D);
        AABB corridor = new AABB(fromCenter, to).inflate(inflate, this.mob.getBbHeight() * 0.5D, inflate);
        return this.level.getEntities(this.mob, corridor, entity -> entity != target
                        && entity.isAlive()
                        && !entity.isRemoved()
                        && !(entity instanceof GroundSpiderEntity)
                        && entity.isPickable())
                .stream()
                .anyMatch(entity -> entity.getBoundingBox().inflate(inflate).clip(fromCenter, to).isPresent());
    }

    private void adoptNextAttachmentIfSupported(Node node) {
        if (!(this.mob instanceof GroundSpiderEntity spider)) return;
        if (!(node instanceof ClimberNodeEvaluator.CustomNode cn) || cn.attachment == null) return;
        BlockPos current = spider.blockPosition();
        BlockPos next = node.asBlockPos();
        Direction previousAttachment = spider.getAttachmentDirection();
        boolean nextAttachmentSupported = AttachmentHelper.hasSupport(this.level, next, cn.attachment)
                && AttachmentHelper.aabbFitsOnSurface(this.level, spider, next, cn.attachment);
        if (AttachmentHelper.hasSupport(this.level, current, cn.attachment)
                && AttachmentHelper.aabbFitsOnSurface(this.level, spider, current, cn.attachment)) {
            spider.setAttachmentDirection(cn.attachment);
            snapAcrossSurfaceTransition(spider, cn, current, previousAttachment);
        } else if (nextAttachmentSupported
                && (current.distManhattan(next) <= 4 || cn.attachment != spider.getAttachmentDirection())) {
            spider.setAttachmentDirection(cn.attachment);
            snapAcrossSurfaceTransition(spider, cn, next, previousAttachment);
        }
    }

    private void snapAcrossSurfaceTransition(GroundSpiderEntity spider, ClimberNodeEvaluator.CustomNode node,
            BlockPos anchorBlock, Direction previousAttachment) {
        if (previousAttachment == node.attachment) {
            return;
        }

        Vec3 anchor = AttachmentHelper.anchorFor(spider, anchorBlock, node.attachment);
        double maxSnapDistance = Math.max(1.5D, spider.getBbWidth() + spider.getBbHeight());
        if (spider.position().distanceToSqr(anchor) > maxSnapDistance * maxSnapDistance) {
            return;
        }

        spider.teleportTo(anchor.x, anchor.y, anchor.z);
        if (node.asBlockPos().equals(anchorBlock)) {
            node.px = anchor.x;
            node.py = anchor.y;
            node.pz = anchor.z;
        }
    }

    @Override
    protected Vec3 getTempMobPos() {
        return this.mob.position();
    }

    @Override protected boolean canUpdatePath() { return true; }
    @Override protected boolean hasValidPathType(BlockPathTypes t) {
        if (t == BlockPathTypes.WATER || t == BlockPathTypes.LAVA || t == BlockPathTypes.DAMAGE_FIRE) return false;
        return t != BlockPathTypes.BLOCKED && t != BlockPathTypes.FENCE;
    }
    @Override public boolean isStableDestination(BlockPos pos) {
        if (this.nodeEvaluator instanceof ClimberNodeEvaluator eval) {
            return !eval.findAttachments(pos).isEmpty();
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
        if (first instanceof ClimberNodeEvaluator.CustomNode cn) {
            cn.px = anchor.x;
            cn.py = anchor.y;
            cn.pz = anchor.z;
        }
        spider.teleportTo(anchor.x, anchor.y, anchor.z);
        spider.setAttachmentDirection(a);

        if (DEBUG) {
            System.out.printf("[NAV] snap first=%s attach=%s anchor=(%.3f,%.3f,%.3f)%n",
                    first.asBlockPos(), a, anchor.x, anchor.y, anchor.z);
        }
    }

    private Vec3 getExactPathingTarget(Node node) {
        if (node instanceof ClimberNodeEvaluator.CustomNode cn && !Double.isNaN(cn.px)) {
            return new Vec3(cn.px, cn.py, cn.pz);
        }
        BlockPos pos = node.asBlockPos();
        Direction a = (node instanceof ClimberNodeEvaluator.CustomNode cn && cn.attachment != null)
                ? cn.attachment : Direction.DOWN;
        return getAnchor((GroundSpiderEntity) this.mob, pos, a);
    }

    private Vec3 getAnchor(GroundSpiderEntity e, BlockPos airPos, Direction a) {
        return AttachmentHelper.anchorFor(e, airPos, a);
    }
}
