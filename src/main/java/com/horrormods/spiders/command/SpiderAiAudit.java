package com.horrormods.spiders.command;

import com.horrormods.spiders.entity.GroundSpiderEntity;
import com.horrormods.spiders.entity.util.AttachmentHelper;
import com.horrormods.spiders.entity.util.GroundSpiderAttachmentPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class SpiderAiAudit {
    private static final double BACK_CLEARANCE_EPS = 0.05D;

    private SpiderAiAudit() {
    }

    public static Optional<GroundSpiderEntity> findNearest(ServerLevel level, Vec3 origin, double range) {
        AABB searchBox = new AABB(origin, origin).inflate(range);
        return level.getEntitiesOfClass(GroundSpiderEntity.class, searchBox, GroundSpiderEntity::isAlive)
                .stream()
                .min(Comparator.comparingDouble(spider -> spider.distanceToSqr(origin)));
    }

    public static String describe(GroundSpiderEntity spider) {
        LivingEntity target = spider.getTarget();
        String targetDescription = target == null
                ? " target=none"
                : " target=" + target.getType().getDescriptionId()
                + " target_alive=" + target.isAlive()
                + " target_distance=" + format(Math.sqrt(spider.distanceToSqr(target)))
                + " target_health=" + format(target.getHealth());

        return "spiders_audit found=true"
                + " uuid=" + spider.getUUID()
                + " pos=" + format(spider.getX()) + "," + format(spider.getY()) + "," + format(spider.getZ())
                + " block=" + spider.blockPosition().getX() + "," + spider.blockPosition().getY() + "," + spider.blockPosition().getZ()
                + " attachment=" + spider.getAttachmentDirection().getName()
                + describePose(spider)
                + describeCombatPacing(spider, " ")
                + " navigation=" + spider.getNavigation().getClass().getSimpleName()
                + " health=" + format(spider.getHealth())
                + " max_health=" + format(spider.getMaxHealth())
                + " no_gravity=" + spider.isNoGravity()
                + " forced_path=" + spider.isFollowingForcedPath()
                + " forced_path_index=" + spider.getForcedPathIndex()
                + " forced_path_size=" + spider.getForcedPathSize()
                + targetDescription;
    }

    public static String describeArena(GroundSpiderEntity spider, List<? extends LivingEntity> targets) {
        StringBuilder builder = new StringBuilder(describe(spider))
                .append(" targets=")
                .append(targets.size());

        for (int i = 0; i < targets.size(); i++) {
            LivingEntity target = targets.get(i);
            builder.append(" t").append(i)
                    .append("_alive=").append(target.isAlive())
                    .append(" t").append(i)
                    .append("_hp=").append(format(target.getHealth()))
                    .append(" t").append(i)
                    .append("_dist=").append(format(Math.sqrt(spider.distanceToSqr(target))))
                    .append(" t").append(i)
                    .append("_pos=").append(format(target.getX()))
                    .append(",").append(format(target.getY()))
                    .append(",").append(format(target.getZ()));
        }

        return builder.toString();
    }

    public static String describeSwarm(List<GroundSpiderEntity> spiders, List<? extends LivingEntity> targets, Vec3 origin) {
        StringBuilder builder = new StringBuilder("spiders_swarm_audit spiders=")
                .append(spiders.size())
                .append(" targets=")
                .append(targets.size());

        for (Direction direction : Direction.values()) {
            long count = spiders.stream()
                    .filter(spider -> spider.getAttachmentDirection() == direction)
                    .count();
            builder.append(" ")
                    .append(direction.getName())
                    .append("=")
                    .append(count);
        }

        for (int i = 0; i < spiders.size(); i++) {
            GroundSpiderEntity spider = spiders.get(i);
            LivingEntity target = spider.getTarget();
            builder.append(" s").append(i)
                    .append("_uuid=").append(spider.getUUID())
                    .append(" s").append(i)
                    .append("_attachment=").append(spider.getAttachmentDirection().getName())
                    .append(describePose(spider, " s" + i + "_"))
                    .append(describeCombatPacing(spider, " s" + i + "_"))
                    .append(" s").append(i)
                    .append("_pos=").append(format(spider.getX()))
                    .append(",").append(format(spider.getY()))
                    .append(",").append(format(spider.getZ()))
                    .append(" s").append(i)
                    .append("_health=").append(format(spider.getHealth()))
                    .append(" s").append(i)
                    .append("_no_gravity=").append(spider.isNoGravity())
                    .append(" s").append(i)
                    .append("_forced_path=").append(spider.isFollowingForcedPath());
            if (target == null) {
                builder.append(" s").append(i).append("_target=none");
            } else {
                builder.append(" s").append(i)
                        .append("_target=").append(target.getType().getDescriptionId())
                        .append(" s").append(i)
                        .append("_target_alive=").append(target.isAlive())
                        .append(" s").append(i)
                        .append("_target_distance=").append(format(Math.sqrt(spider.distanceToSqr(target))))
                        .append(" s").append(i)
                        .append("_target_health=").append(format(target.getHealth()));
            }
        }

        for (int i = 0; i < targets.size(); i++) {
            LivingEntity target = targets.get(i);
            builder.append(" t").append(i)
                    .append("_alive=").append(target.isAlive())
                    .append(" t").append(i)
                    .append("_hp=").append(format(target.getHealth()))
                    .append(" t").append(i)
                    .append("_dist_from_origin=").append(format(Math.sqrt(target.distanceToSqr(origin))))
                    .append(" t").append(i)
                    .append("_pos=").append(format(target.getX()))
                    .append(",").append(format(target.getY()))
                    .append(",").append(format(target.getZ()));
        }

        return builder.toString();
    }

    public static String describePlayerPressure(List<GroundSpiderEntity> spiders, ServerPlayer player, Vec3 origin) {
        MobEffectInstance slowness = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        MobEffectInstance blindness = player.getEffect(MobEffects.BLINDNESS);
        StringBuilder builder = new StringBuilder("spiders_player_pressure_audit spiders=")
                .append(spiders.size())
                .append(" player_alive=").append(player.isAlive())
                .append(" player_health=").append(format(player.getHealth()))
                .append(" player_absorption=").append(format(player.getAbsorptionAmount()))
                .append(" player_food=").append(player.getFoodData().getFoodLevel())
                .append(" player_hurt_time=").append(player.hurtTime)
                .append(" player_slowness=").append(slowness != null)
                .append(" player_slowness_duration=").append(slowness == null ? 0 : slowness.getDuration())
                .append(" player_blindness=").append(blindness != null)
                .append(" player_blindness_duration=").append(blindness == null ? 0 : blindness.getDuration())
                .append(" player_cobweb_nearby=").append(hasCobwebNear(player.getLevel(), player.blockPosition()))
                .append(" player_pos=").append(format(player.getX()))
                .append(",").append(format(player.getY()))
                .append(",").append(format(player.getZ()));

        for (Direction direction : Direction.values()) {
            long count = spiders.stream()
                    .filter(spider -> spider.getAttachmentDirection() == direction)
                    .count();
            builder.append(" ")
                    .append(direction.getName())
                    .append("=")
                    .append(count);
        }

        for (int i = 0; i < spiders.size(); i++) {
            GroundSpiderEntity spider = spiders.get(i);
            LivingEntity target = spider.getTarget();
            builder.append(" s").append(i)
                    .append("_uuid=").append(spider.getUUID())
                    .append(" s").append(i)
                    .append("_attachment=").append(spider.getAttachmentDirection().getName())
                    .append(describePose(spider, " s" + i + "_"))
                    .append(describeCombatPacing(spider, " s" + i + "_"))
                    .append(describePlayerContact(spider, player, " s" + i + "_"))
                    .append(" s").append(i)
                    .append("_pos=").append(format(spider.getX()))
                    .append(",").append(format(spider.getY()))
                    .append(",").append(format(spider.getZ()))
                    .append(" s").append(i)
                    .append("_health=").append(format(spider.getHealth()))
                    .append(" s").append(i)
                    .append("_no_gravity=").append(spider.isNoGravity())
                    .append(" s").append(i)
                    .append("_forced_path=").append(spider.isFollowingForcedPath())
                    .append(" s").append(i)
                    .append("_distance_to_player=").append(format(Math.sqrt(spider.distanceToSqr(player))));
            if (target == null) {
                builder.append(" s").append(i).append("_target=none");
            } else {
                builder.append(" s").append(i)
                        .append("_target=").append(target.getType().getDescriptionId())
                        .append(" s").append(i)
                        .append("_target_is_player=").append(target == player)
                        .append(" s").append(i)
                        .append("_target_alive=").append(target.isAlive())
                        .append(" s").append(i)
                        .append("_target_distance=").append(format(Math.sqrt(spider.distanceToSqr(target))))
                        .append(" s").append(i)
                        .append("_target_health=").append(format(target.getHealth()));
            }
        }

        return builder.toString();
    }

    public static String describePlayerPressureCompact(List<GroundSpiderEntity> spiders, ServerPlayer player, Vec3 origin) {
        MobEffectInstance slowness = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        MobEffectInstance blindness = player.getEffect(MobEffects.BLINDNESS);
        StringBuilder builder = new StringBuilder("spiders_player_pressure_audit spiders=")
                .append(spiders.size())
                .append(" player_alive=").append(player.isAlive())
                .append(" player_health=").append(format(player.getHealth()))
                .append(" player_absorption=").append(format(player.getAbsorptionAmount()))
                .append(" player_food=").append(player.getFoodData().getFoodLevel())
                .append(" player_hurt_time=").append(player.hurtTime)
                .append(" player_slowness=").append(slowness != null)
                .append(" player_slowness_duration=").append(slowness == null ? 0 : slowness.getDuration())
                .append(" player_blindness=").append(blindness != null)
                .append(" player_blindness_duration=").append(blindness == null ? 0 : blindness.getDuration())
                .append(" player_cobweb_nearby=").append(hasCobwebNear(player.getLevel(), player.blockPosition()))
                .append(" player_pos=").append(format(player.getX()))
                .append(",").append(format(player.getY()))
                .append(",").append(format(player.getZ()));

        for (Direction direction : Direction.values()) {
            long count = spiders.stream()
                    .filter(spider -> spider.getAttachmentDirection() == direction)
                    .count();
            builder.append(" ")
                    .append(direction.getName())
                    .append("=")
                    .append(count);
        }

        for (int i = 0; i < spiders.size(); i++) {
            GroundSpiderEntity spider = spiders.get(i);
            LivingEntity target = spider.getTarget();
            builder.append(" s").append(i)
                    .append("_uuid=").append(spider.getUUID())
                    .append(" s").append(i)
                    .append("_attachment=").append(spider.getAttachmentDirection().getName())
                    .append(describePose(spider, " s" + i + "_"))
                    .append(describePlayerContact(spider, player, " s" + i + "_"))
                    .append(" s").append(i)
                    .append("_pos=").append(format(spider.getX()))
                    .append(",").append(format(spider.getY()))
                    .append(",").append(format(spider.getZ()))
                    .append(" s").append(i)
                    .append("_health=").append(format(spider.getHealth()))
                    .append(" s").append(i)
                    .append("_no_gravity=").append(spider.isNoGravity())
                    .append(" s").append(i)
                    .append("_forced_path=").append(spider.isFollowingForcedPath())
                    .append(" s").append(i)
                    .append("_distance_to_player=").append(format(Math.sqrt(spider.distanceToSqr(player))));
            if (target == null) {
                builder.append(" s").append(i).append("_target=none");
            } else {
                builder.append(" s").append(i)
                        .append("_target=").append(target.getType().getDescriptionId())
                        .append(" s").append(i)
                        .append("_target_is_player=").append(target == player)
                        .append(" s").append(i)
                        .append("_target_alive=").append(target.isAlive())
                        .append(" s").append(i)
                        .append("_target_distance=").append(format(Math.sqrt(spider.distanceToSqr(target))))
                        .append(" s").append(i)
                        .append("_target_health=").append(format(target.getHealth()));
            }
        }

        return builder.toString();
    }

    public static String describePlayerPressureSummary(List<GroundSpiderEntity> spiders, ServerPlayer player, Vec3 origin) {
        MobEffectInstance slowness = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        MobEffectInstance blindness = player.getEffect(MobEffects.BLINDNESS);
        int targetingSpiders = 0;
        int aliveTargetingSpiders = 0;
        int nonFloorAliveTargetingSpiders = 0;
        int meleeRangeSpiders = 0;
        int closeSpiders = 0;
        int badBackAirSpiders = 0;
        int badClimbingPoseSpiders = 0;
        int unsupportedNonAirborneBellySpiders = 0;
        int stationaryWalkAnimationSpiders = 0;
        int movingIdleAnimationSpiders = 0;
        double distanceTotal = 0.0D;
        double distanceMin = Double.POSITIVE_INFINITY;
        double distanceMax = 0.0D;

        for (GroundSpiderEntity spider : spiders) {
            LivingEntity target = spider.getTarget();
            boolean targetIsPlayer = target == player;
            boolean aliveTargeting = spider.isAlive() && targetIsPlayer && target.isAlive();
            Direction attachment = spider.getAttachmentDirection();
            BlockPos spiderBlock = spider.blockPosition();
            BlockPos backBlock = spiderBlock.relative(attachment.getOpposite());
            Vec3 back = GroundSpiderAttachmentPose.backVector(attachment);
            boolean supportPresent = AttachmentHelper.hasSupport(spider.level, spiderBlock, attachment);
            boolean backAir = spider.level.noCollision(spider, spider.getBoundingBox().move(back.scale(BACK_CLEARANCE_EPS)))
                    && spider.level.getFluidState(backBlock).isEmpty();
            boolean climbingPoseValid = attachment == Direction.DOWN || supportPresent;
            boolean airborneFloorPose = attachment == Direction.DOWN && !supportPresent;
            double distance = Math.sqrt(spider.distanceToSqr(player));

            if (targetIsPlayer) {
                targetingSpiders++;
            }
            if (aliveTargeting) {
                aliveTargetingSpiders++;
                if (attachment != Direction.DOWN) {
                    nonFloorAliveTargetingSpiders++;
                }
            }
            if (spider.isWithinMeleeAttackRange(player)) {
                meleeRangeSpiders++;
            }
            if (distance <= 6.0D) {
                closeSpiders++;
            }
            if (!backAir && !airborneFloorPose) {
                badBackAirSpiders++;
            }
            if (!climbingPoseValid) {
                badClimbingPoseSpiders++;
            }
            if (!supportPresent && !airborneFloorPose) {
                unsupportedNonAirborneBellySpiders++;
            }
            if (isStationaryWalkAnimation(spider)) {
                stationaryWalkAnimationSpiders++;
            }
            if (isMovingIdleAnimation(spider)) {
                movingIdleAnimationSpiders++;
            }

            distanceTotal += distance;
            distanceMin = Math.min(distanceMin, distance);
            distanceMax = Math.max(distanceMax, distance);
        }

        if (spiders.isEmpty()) {
            distanceMin = 0.0D;
        }
        double distanceAverage = spiders.isEmpty() ? 0.0D : distanceTotal / spiders.size();

        StringBuilder builder = new StringBuilder("spiders_player_pressure_audit spiders=")
                .append(spiders.size())
                .append(" summary_only=true")
                .append(" player_alive=").append(player.isAlive())
                .append(" player_health=").append(format(player.getHealth()))
                .append(" player_absorption=").append(format(player.getAbsorptionAmount()))
                .append(" player_food=").append(player.getFoodData().getFoodLevel())
                .append(" player_hurt_time=").append(player.hurtTime)
                .append(" player_slowness=").append(slowness != null)
                .append(" player_slowness_duration=").append(slowness == null ? 0 : slowness.getDuration())
                .append(" player_blindness=").append(blindness != null)
                .append(" player_blindness_duration=").append(blindness == null ? 0 : blindness.getDuration())
                .append(" player_cobweb_nearby=").append(hasCobwebNear(player.getLevel(), player.blockPosition()))
                .append(" targeting_spiders=").append(targetingSpiders)
                .append(" alive_targeting_spiders=").append(aliveTargetingSpiders)
                .append(" non_floor_alive_targeting_spiders=").append(nonFloorAliveTargetingSpiders)
                .append(" melee_range_spiders=").append(meleeRangeSpiders)
                .append(" close_spiders=").append(closeSpiders)
                .append(" bad_back_air_spiders=").append(badBackAirSpiders)
                .append(" bad_climbing_pose_spiders=").append(badClimbingPoseSpiders)
                .append(" unsupported_non_airborne_belly_spiders=").append(unsupportedNonAirborneBellySpiders)
                .append(" stationary_walk_animation_spiders=").append(stationaryWalkAnimationSpiders)
                .append(" moving_idle_animation_spiders=").append(movingIdleAnimationSpiders)
                .append(" distance_min=").append(format(distanceMin))
                .append(" distance_avg=").append(format(distanceAverage))
                .append(" distance_max=").append(format(distanceMax))
                .append(" player_pos=").append(format(player.getX()))
                .append(",").append(format(player.getY()))
                .append(",").append(format(player.getZ()));

        for (Direction direction : Direction.values()) {
            long count = spiders.stream()
                    .filter(spider -> spider.getAttachmentDirection() == direction)
                    .count();
            builder.append(" ")
                    .append(direction.getName())
                    .append("=")
                    .append(count);
        }

        return builder.toString();
    }

    private static String describePlayerContact(GroundSpiderEntity spider, ServerPlayer player, String prefix) {
        AABB spiderBox = spider.getBoundingBox();
        AABB playerBox = player.getBoundingBox();
        double horizontalGap = horizontalGap(spiderBox, playerBox);
        double verticalGap = verticalGap(spiderBox, playerBox);
        boolean bboxIntersects = spiderBox.intersects(playerBox);
        boolean meleeRange = spider.isWithinMeleeAttackRange(player);
        boolean sensingLineOfSight = spider.getSensing().hasLineOfSight(player);
        boolean entityLineOfSight = spider.hasLineOfSight(player);

        return prefix + "player_bbox_intersects=" + bboxIntersects
                + prefix + "player_melee_range=" + meleeRange
                + prefix + "player_sensing_los=" + sensingLineOfSight
                + prefix + "player_entity_los=" + entityLineOfSight
                + prefix + "player_horizontal_gap=" + format(horizontalGap)
                + prefix + "player_vertical_gap=" + format(verticalGap)
                + prefix + "player_center_dx=" + format(player.getX() - spider.getX())
                + prefix + "player_center_dy=" + format(player.getY() - spider.getY())
                + prefix + "player_center_dz=" + format(player.getZ() - spider.getZ());
    }

    private static double horizontalGap(AABB first, AABB second) {
        double xGap = axisGap(first.minX, first.maxX, second.minX, second.maxX);
        double zGap = axisGap(first.minZ, first.maxZ, second.minZ, second.maxZ);
        return Math.sqrt(xGap * xGap + zGap * zGap);
    }

    private static double verticalGap(AABB first, AABB second) {
        return axisGap(first.minY, first.maxY, second.minY, second.maxY);
    }

    private static double axisGap(double firstMin, double firstMax, double secondMin, double secondMax) {
        if (firstMax < secondMin) {
            return secondMin - firstMax;
        }
        if (secondMax < firstMin) {
            return firstMin - secondMax;
        }
        return 0.0D;
    }

    public static String describeSurfaces(List<GroundSpiderEntity> spiders) {
        StringBuilder builder = new StringBuilder("spiders_surface_audit spiders=")
                .append(spiders.size());

        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            long count = spiders.stream()
                    .filter(spider -> spider.getAttachmentDirection() == direction)
                    .count();
            builder.append(" ")
                    .append(direction.getName())
                    .append("=")
                    .append(count);
        }

        for (int i = 0; i < spiders.size(); i++) {
            GroundSpiderEntity spider = spiders.get(i);
            builder.append(" s").append(i)
                    .append("_uuid=").append(spider.getUUID())
                    .append(" s").append(i)
                    .append("_attachment=").append(spider.getAttachmentDirection().getName())
                    .append(describePose(spider, " s" + i + "_"))
                    .append(describeCombatPacing(spider, " s" + i + "_"))
                    .append(" s").append(i)
                    .append("_pos=").append(format(spider.getX()))
                    .append(",").append(format(spider.getY()))
                    .append(",").append(format(spider.getZ()))
                    .append(" s").append(i)
                    .append("_health=").append(format(spider.getHealth()))
                    .append(" s").append(i)
                    .append("_no_gravity=").append(spider.isNoGravity());
        }

        return builder.toString();
    }

    private static String describePose(GroundSpiderEntity spider) {
        return describePose(spider, " ");
    }

    private static String describePose(GroundSpiderEntity spider, String prefix) {
        Direction attachment = spider.getAttachmentDirection();
        Vec3 belly = GroundSpiderAttachmentPose.bellyVector(attachment);
        Vec3 back = GroundSpiderAttachmentPose.backVector(attachment);
        BlockPos spiderBlock = spider.blockPosition();
        BlockPos support = spiderBlock.relative(attachment);
        BlockPos backBlock = spiderBlock.relative(attachment.getOpposite());
        boolean supportPresent = AttachmentHelper.hasSupport(spider.level, spiderBlock, attachment);
        boolean backAir = spider.level.noCollision(spider, spider.getBoundingBox().move(back.scale(BACK_CLEARANCE_EPS)))
                && spider.level.getFluidState(backBlock).isEmpty();
        boolean climbingPoseValid = attachment == Direction.DOWN || supportPresent;
        boolean airborneFloorPose = attachment == Direction.DOWN && !supportPresent;

        return prefix + "belly_vector=" + vector(belly)
                + prefix + "back_vector=" + vector(back)
                + prefix + "support_block=" + support.getX() + "," + support.getY() + "," + support.getZ()
                + prefix + "belly_faces_support=" + supportPresent
                + prefix + "back_faces_air=" + backAir
                + prefix + "climbing_pose_valid=" + climbingPoseValid
                + prefix + "airborne_floor_pose=" + airborneFloorPose
                + prefix + "animation_moving=" + spider.isAnimationMovementActive()
                + prefix + "animation_name=" + spider.getAnimationAuditName()
                + prefix + "animation_motion_sqr=" + format(animationMotionSqr(spider))
                + prefix + "stationary_walk_animation=" + isStationaryWalkAnimation(spider)
                + prefix + "moving_idle_animation=" + isMovingIdleAnimation(spider)
                + prefix + "web_traversal_body_aligned=" + spider.isWebTraversalBodyAligned()
                + prefix + "web_traversal_body_error_degrees=" + format(spider.getWebTraversalBodyAlignmentErrorDegrees())
                + prefix + "web_traversal_body_yaw=" + format(spider.getWebTraversalBodyYawDegrees())
                + prefix + "web_traversal_head_yaw=" + format(spider.getWebTraversalHeadYawDegrees())
                + prefix + "web_traversal_head_pitch=" + format(spider.getWebTraversalHeadPitchDegrees())
                + prefix + "web_traversal_reverse_animation=" + spider.isWebTraversalReverseAnimation();
    }

    private static boolean isStationaryWalkAnimation(GroundSpiderEntity spider) {
        return isWalkLikeAnimationName(spider.getAnimationAuditName())
                && animationMotionSqr(spider) <= 1.0E-5D;
    }

    private static boolean isMovingIdleAnimation(GroundSpiderEntity spider) {
        return "idle".equals(spider.getAnimationAuditName())
                && animationMotionSqr(spider) > 1.0E-4D;
    }

    private static boolean isWalkLikeAnimationName(String animationName) {
        return animationName != null && animationName.contains("walk")
                || "circle_right".equals(animationName)
                || "raised_circle_right".equals(animationName);
    }

    private static double animationMotionSqr(GroundSpiderEntity spider) {
        Vec3 movement = spider.getDeltaMovement();
        Direction attachment = spider.getAttachmentDirection();
        if (attachment != Direction.DOWN) {
            Vec3 normal = AttachmentHelper.normal(attachment);
            movement = movement.subtract(normal.scale(movement.dot(normal)));
        }
        return movement.lengthSqr();
    }

    private static String describeCombatPacing(GroundSpiderEntity spider, String prefix) {
        BlockPos ceilingAnchor = spider.getCeilingStalkAnchor();
        return prefix + "combat_pacing=" + spider.getCombatPacingStateName()
                + prefix + "combat_pacing_ticks=" + spider.getCombatPacingTicks()
                + prefix + "sprint_burst=" + spider.isSprintBurstActive()
                + prefix + "stalking_pause=" + spider.isStalkingPause()
                + prefix + "backpedaling=" + spider.isBackpedalingFacingTarget()
                + prefix + "backpedal_ticks=" + spider.getBackpedalTicks()
                + prefix + "ceiling_stalking=" + spider.isCeilingStalking()
                + prefix + "ceiling_stalk_anchor=" + blockPosOrNone(ceilingAnchor)
                + prefix + "circle_strafing=" + spider.isCircleStrafing()
                + prefix + "circle_strafe_ticks=" + spider.getCircleStrafeTicks()
                + prefix + "circle_strafe_direction=" + spider.getCircleStrafeDirectionName()
                + prefix + "pack_coordination=" + spider.isPackCoordinating()
                + prefix + "pack_role=" + spider.getPackRoleName()
                + prefix + "pack_role_ticks=" + spider.getPackRoleTicks()
                + prefix + "pack_size=" + spider.getPackSize()
                + prefix + "pack_direct_count=" + spider.getPackDirectCount()
                + prefix + "pack_ambush_count=" + spider.getPackAmbushCount()
                + prefix + "pack_flank_count=" + spider.getPackFlankCount()
                + prefix + "escape_cutting=" + spider.isEscapeCutting()
                + prefix + "escape_cutting_status=" + spider.getEscapeCuttingStatus()
                + prefix + "escape_cutting_ticks=" + spider.getEscapeCuttingTicks()
                + prefix + "escape_cutting_cooldown_ticks=" + spider.getEscapeCuttingCooldownTicks()
                + prefix + "escape_cutting_anchor=" + blockPosOrNone(spider.getEscapeCuttingAnchor())
                + prefix + "escape_cutting_route_direction=" + spider.getEscapeCuttingRouteDirectionName()
                + prefix + "escape_cutting_path_started=" + spider.hasEscapeCuttingPathStarted()
                + prefix + "escape_cutting_reached_anchor=" + spider.hasEscapeCuttingReachedAnchor()
                + prefix + "escape_cutting_start_anchor_distance=" + format(spider.getEscapeCuttingStartAnchorDistance())
                + prefix + "escape_cutting_current_anchor_distance=" + format(spider.getEscapeCuttingCurrentAnchorDistance())
                + prefix + "escape_cutting_min_anchor_distance=" + format(spider.getEscapeCuttingMinAnchorDistance())
                + prefix + "escape_cutting_anchor_distance_reduced=" + format(spider.getEscapeCuttingAnchorDistanceReduced())
                + prefix + "threat_display=" + spider.isThreatDisplaying()
                + prefix + "threat_display_status=" + spider.getThreatDisplayStatus()
                + prefix + "threat_display_ticks=" + spider.getThreatDisplayTicks()
                + prefix + "threat_display_cooldown_ticks=" + spider.getThreatDisplayCooldownTicks()
                + prefix + "threat_display_pose=" + spider.getThreatDisplayPoseName()
                + prefix + "threat_display_start_distance=" + format(spider.getThreatDisplayStartDistance())
                + prefix + "threat_display_current_distance=" + format(spider.getThreatDisplayCurrentDistance())
                + prefix + "threat_display_max_movement=" + format(spider.getThreatDisplayMaxMovement())
                + prefix + "threat_display_facing_ticks=" + spider.getThreatDisplayFacingTicks()
                + prefix + "threat_display_faced_target=" + spider.hasThreatDisplayFacedTarget()
                + prefix + "threat_display_held_still=" + spider.hasThreatDisplayHeldStill()
                + prefix + "line_of_sight_stalking=" + spider.isLineOfSightStalking()
                + prefix + "line_of_sight_stalking_status=" + spider.getLineOfSightStalkingStatus()
                + prefix + "line_of_sight_stalking_ticks=" + spider.getLineOfSightStalkingTicks()
                + prefix + "line_of_sight_stalking_cooldown_ticks=" + spider.getLineOfSightStalkingCooldownTicks()
                + prefix + "line_of_sight_stalking_target_looking=" + spider.isLineOfSightStalkingTargetLooking()
                + prefix + "line_of_sight_stalking_watched_ticks=" + spider.getLineOfSightStalkingWatchedTicks()
                + prefix + "line_of_sight_stalking_unwatched_ticks=" + spider.getLineOfSightStalkingUnwatchedTicks()
                + prefix + "line_of_sight_stalking_saw_watched=" + spider.hasLineOfSightStalkingSawWatched()
                + prefix + "line_of_sight_stalking_saw_unwatched_advance=" + spider.hasLineOfSightStalkingSawUnwatchedAdvance()
                + prefix + "line_of_sight_stalking_start_distance=" + format(spider.getLineOfSightStalkingStartDistance())
                + prefix + "line_of_sight_stalking_current_distance=" + format(spider.getLineOfSightStalkingCurrentDistance())
                + prefix + "line_of_sight_stalking_min_distance=" + format(spider.getLineOfSightStalkingMinDistance())
                + prefix + "line_of_sight_stalking_distance_closed=" + format(spider.getLineOfSightStalkingDistanceClosed())
                + prefix + "line_of_sight_stalking_total_movement=" + format(spider.getLineOfSightStalkingTotalMovement())
                + prefix + "line_of_sight_stalking_watched_max_movement=" + format(spider.getLineOfSightStalkingMaxWatchedMovement())
                + prefix + "line_of_sight_stalking_facing_ticks=" + spider.getLineOfSightStalkingFacingTicks()
                + prefix + "line_of_sight_stalking_faced_target=" + spider.hasLineOfSightStalkingFacedTarget()
                + prefix + "line_of_sight_stalking_held_still=" + spider.hasLineOfSightStalkingHeldStillWhileWatched()
                + prefix + "darkness_preference=" + spider.isDarknessPreferenceActive()
                + prefix + "darkness_preference_status=" + spider.getDarknessPreferenceStatus()
                + prefix + "darkness_preference_ticks=" + spider.getDarknessPreferenceTicks()
                + prefix + "darkness_preference_cooldown_ticks=" + spider.getDarknessPreferenceCooldownTicks()
                + prefix + "darkness_preference_anchor=" + blockPosOrNone(spider.getDarknessPreferenceAnchor())
                + prefix + "darkness_preference_attachment=" + spider.getDarknessPreferenceAttachmentName()
                + prefix + "darkness_preference_path_started=" + spider.hasDarknessPreferencePathStarted()
                + prefix + "darkness_preference_reached_anchor=" + spider.hasDarknessPreferenceReachedAnchor()
                + prefix + "darkness_preference_held_anchor=" + spider.hasDarknessPreferenceHeldAnchor()
                + prefix + "darkness_preference_facing_ticks=" + spider.getDarknessPreferenceFacingTicks()
                + prefix + "darkness_preference_faced_target=" + spider.hasDarknessPreferenceFacedTarget()
                + prefix + "darkness_preference_anchor_light=" + spider.getDarknessPreferenceAnchorLight()
                + prefix + "darkness_preference_current_light=" + spider.getDarknessPreferenceCurrentLight()
                + prefix + "darkness_preference_open_light=" + spider.getDarknessPreferenceOpenLight()
                + prefix + "darkness_preference_anchor_darker_than_open=" + spider.isDarknessPreferenceAnchorDarkerThanOpen()
                + prefix + "darkness_preference_cover_count=" + spider.getDarknessPreferenceCoverCount()
                + prefix + "darkness_preference_wall_adjacent_count=" + spider.getDarknessPreferenceWallAdjacentCount()
                + prefix + "darkness_preference_covered=" + spider.isDarknessPreferenceCovered()
                + prefix + "darkness_preference_corner=" + spider.isDarknessPreferenceCorner()
                + prefix + "darkness_preference_anchor_score=" + format(spider.getDarknessPreferenceAnchorScore())
                + prefix + "darkness_preference_open_score=" + format(spider.getDarknessPreferenceOpenScore())
                + prefix + "darkness_preference_score_advantage=" + format(spider.getDarknessPreferenceScoreAdvantage())
                + prefix + "darkness_preference_start_anchor_distance=" + format(spider.getDarknessPreferenceStartAnchorDistance())
                + prefix + "darkness_preference_current_anchor_distance=" + format(spider.getDarknessPreferenceCurrentAnchorDistance())
                + prefix + "darkness_preference_min_anchor_distance=" + format(spider.getDarknessPreferenceMinAnchorDistance())
                + prefix + "darkness_preference_anchor_distance_reduced=" + format(spider.getDarknessPreferenceAnchorDistanceReduced())
                + prefix + "wall_peek=" + spider.isWallPeeking()
                + prefix + "wall_peek_phase=" + spider.getWallPeekPhaseName()
                + prefix + "wall_peek_status=" + spider.getWallPeekStatus()
                + prefix + "wall_peek_ticks=" + spider.getWallPeekTicks()
                + prefix + "wall_peek_cooldown_ticks=" + spider.getWallPeekCooldownTicks()
                + prefix + "wall_peek_cover_anchor=" + blockPosOrNone(spider.getWallPeekCoverAnchor())
                + prefix + "wall_peek_peek_anchor=" + blockPosOrNone(spider.getWallPeekPeekAnchor())
                + prefix + "wall_peek_path_started=" + spider.hasWallPeekPathStarted()
                + prefix + "wall_peek_reached_peek=" + spider.hasWallPeekReachedPeek()
                + prefix + "wall_peek_held_peek=" + spider.hasWallPeekHeldPeek()
                + prefix + "wall_peek_retreated=" + spider.hasWallPeekRetreated()
                + prefix + "wall_peek_target_retained=" + spider.hasWallPeekTargetRetained()
                + prefix + "wall_peek_cover_los_blocked=" + spider.isWallPeekCoverLineOfSightBlocked()
                + prefix + "wall_peek_peek_los_clear=" + spider.isWallPeekPeekLineOfSightClear()
                + prefix + "wall_peek_facing_ticks=" + spider.getWallPeekFacingTicks()
                + prefix + "wall_peek_faced_target=" + spider.hasWallPeekFacedTarget()
                + prefix + "wall_peek_start_peek_distance=" + format(spider.getWallPeekStartPeekDistance())
                + prefix + "wall_peek_current_peek_distance=" + format(spider.getWallPeekCurrentPeekDistance())
                + prefix + "wall_peek_min_peek_distance=" + format(spider.getWallPeekMinPeekDistance())
                + prefix + "wall_peek_peek_distance_reduced=" + format(spider.getWallPeekPeekDistanceReduced())
                + prefix + "wall_peek_start_cover_distance=" + format(spider.getWallPeekStartCoverDistance())
                + prefix + "wall_peek_current_cover_distance=" + format(spider.getWallPeekCurrentCoverDistance())
                + prefix + "wall_peek_min_cover_distance=" + format(spider.getWallPeekMinCoverDistance())
                + prefix + "wall_peek_cover_return_distance_reduced=" + format(spider.getWallPeekCoverReturnDistanceReduced())
                + prefix + "prey_interaction=" + spider.isPreyInteracting()
                + prefix + "prey_interaction_phase=" + spider.getPreyInteractionPhaseName()
                + prefix + "prey_interaction_status=" + spider.getPreyInteractionStatus()
                + prefix + "prey_interaction_ticks=" + spider.getPreyInteractionTicks()
                + prefix + "prey_interaction_cooldown_ticks=" + spider.getPreyInteractionCooldownTicks()
                + prefix + "prey_interaction_prey_type=" + spider.getPreyInteractionPreyType()
                + prefix + "prey_interaction_prey_anchor=" + blockPosOrNone(spider.getPreyInteractionPreyAnchor())
                + prefix + "prey_interaction_guard_anchor=" + blockPosOrNone(spider.getPreyInteractionGuardAnchor())
                + prefix + "prey_interaction_path_started=" + spider.hasPreyInteractionPathStarted()
                + prefix + "prey_interaction_reached_guard=" + spider.hasPreyInteractionReachedGuard()
                + prefix + "prey_interaction_held_guard=" + spider.hasPreyInteractionHeldGuard()
                + prefix + "prey_interaction_placed_web=" + spider.hasPreyInteractionPlacedWeb()
                + prefix + "prey_interaction_placed_web_count=" + spider.getPreyInteractionPlacedWebCount()
                + prefix + "prey_interaction_target_killed=" + spider.hasPreyInteractionTargetKilled()
                + prefix + "prey_interaction_facing_ticks=" + spider.getPreyInteractionFacingTicks()
                + prefix + "prey_interaction_faced_prey_area=" + spider.hasPreyInteractionFacedPreyArea()
                + prefix + "prey_interaction_start_guard_distance=" + format(spider.getPreyInteractionStartGuardDistance())
                + prefix + "prey_interaction_current_guard_distance=" + format(spider.getPreyInteractionCurrentGuardDistance())
                + prefix + "prey_interaction_min_guard_distance=" + format(spider.getPreyInteractionMinGuardDistance())
                + prefix + "prey_interaction_guard_distance_reduced=" + format(spider.getPreyInteractionGuardDistanceReduced())
                + prefix + "drop_attack_phase=" + spider.getDropAttackPhaseName()
                + prefix + "drop_attack_ticks=" + spider.getDropAttackTicks()
                + prefix + "drop_attack_cooldown_ticks=" + spider.getDropAttackCooldownTicks()
                + prefix + "drop_attack_damage_spent=" + spider.isDropAttackDamageSpent()
                + prefix + "web_shot_phase=" + spider.getWebShotPhaseName()
                + prefix + "web_shot_ticks=" + spider.getWebShotTicks()
                + prefix + "web_shot_cooldown_ticks=" + spider.getWebShotCooldownTicks()
                + prefix + "web_shot_fired=" + spider.isWebShotFired()
                + prefix + "web_trap_placement=" + spider.isWebTrapPlacementActive()
                + prefix + "web_trap_placement_status=" + spider.getWebTrapPlacementStatus()
                + prefix + "web_trap_placement_ticks=" + spider.getWebTrapPlacementTicks()
                + prefix + "web_trap_placement_cooldown_ticks=" + spider.getWebTrapPlacementCooldownTicks()
                + prefix + "web_trap_placement_anchor=" + blockPosOrNone(spider.getWebTrapPlacementAnchor())
                + prefix + "web_trap_placement_route_direction=" + spider.getWebTrapPlacementRouteDirectionName()
                + prefix + "web_trap_placement_placed_count=" + spider.getWebTrapPlacementPlacedCount()
                + prefix + "web_trap_placement_placed_behind=" + spider.hasWebTrapPlacementPlacedBehind()
                + prefix + "web_trap_placement_placed_beside=" + spider.hasWebTrapPlacementPlacedBeside()
                + prefix + "web_trap_placement_target_retained=" + spider.hasWebTrapPlacementTargetRetained()
                + prefix + "web_trap_placement_facing_ticks=" + spider.getWebTrapPlacementFacingTicks()
                + prefix + "web_trap_placement_faced_target=" + spider.hasWebTrapPlacementFacedTarget()
                + prefix + "web_trap_placement_start_target_distance=" + format(spider.getWebTrapPlacementStartTargetDistance())
                + prefix + "web_trap_placement_current_target_distance=" + format(spider.getWebTrapPlacementCurrentTargetDistance())
                + prefix + "web_lower_phase=" + spider.getWebLowerPhaseName()
                + prefix + "web_lower_ticks=" + spider.getWebLowerTicks()
                + prefix + "web_lower_cooldown_ticks=" + spider.getWebLowerCooldownTicks()
                + prefix + "web_lower_start_y=" + format(spider.getWebLowerStartY())
                + prefix + "web_lower_lowest_y=" + format(spider.getWebLowerLowestY())
                + prefix + "web_lower_descent=" + format(spider.getWebLowerDescentDistance())
                + prefix + "web_lower_strand_anchor=" + blockPosOrNone(spider.getWebLowerStrandAnchor())
                + prefix + "pounce_phase=" + spider.getPouncePhaseName()
                + prefix + "pounce_ticks=" + spider.getPounceTicks()
                + prefix + "pounce_cooldown_ticks=" + spider.getPounceCooldownTicks()
                + prefix + "pounce_launched=" + spider.isPounceLaunched()
                + prefix + "pounce_damage_spent=" + spider.isPounceDamageSpent()
                + prefix + "retreat_phase=" + spider.getRetreatPhaseName()
                + prefix + "retreat_ticks=" + spider.getRetreatTicks()
                + prefix + "retreat_cooldown_ticks=" + spider.getRetreatCooldownTicks()
                + prefix + "retreat_trigger_damage=" + spider.isRetreatTriggeredByDamage()
                + prefix + "retreat_trigger_miss=" + spider.isRetreatTriggeredByMiss()
                + prefix + "retreat_anchor=" + blockPosOrNone(spider.getRetreatAnchor())
                + prefix + "retreat_start_distance=" + format(spider.getRetreatStartDistance())
                + prefix + "retreat_max_distance=" + format(spider.getRetreatMaxDistance())
                + prefix + "fake_retreat_phase=" + spider.getFakeRetreatPhaseName()
                + prefix + "fake_retreat_ticks=" + spider.getFakeRetreatTicks()
                + prefix + "fake_retreat_cooldown_ticks=" + spider.getFakeRetreatCooldownTicks()
                + prefix + "fake_retreat_trigger_damage=" + spider.isFakeRetreatTriggeredByDamage()
                + prefix + "fake_retreat_trigger_miss=" + spider.isFakeRetreatTriggeredByMiss()
                + prefix + "fake_retreat_anchor=" + blockPosOrNone(spider.getFakeRetreatAnchor())
                + prefix + "fake_retreat_reengage_started=" + spider.hasFakeRetreatReengageStarted()
                + prefix + "fake_retreat_start_distance=" + format(spider.getFakeRetreatStartDistance())
                + prefix + "fake_retreat_max_distance=" + format(spider.getFakeRetreatMaxDistance())
                + prefix + "fake_retreat_distance_gained=" + format(spider.getFakeRetreatDistanceGained())
                + prefix + "fake_retreat_return_start_distance=" + format(spider.getFakeRetreatReturnStartDistance())
                + prefix + "fake_retreat_min_return_distance=" + format(spider.getFakeRetreatMinReturnDistance())
                + prefix + "fake_retreat_return_closed=" + format(spider.getFakeRetreatReturnClosedDistance())
                + prefix + "grab_pull_phase=" + spider.getGrabPullPhaseName()
                + prefix + "grab_pull_ticks=" + spider.getGrabPullTicks()
                + prefix + "grab_pull_cooldown_ticks=" + spider.getGrabPullCooldownTicks()
                + prefix + "grab_pull_trigger_web=" + spider.isGrabPullTriggeredByWeb()
                + prefix + "grab_pull_moved_target=" + spider.hasGrabPullMovedTarget()
                + prefix + "grab_pull_saw_pulling=" + spider.hasGrabPullSeenPulling()
                + prefix + "grab_pull_start_distance=" + format(spider.getGrabPullStartDistance())
                + prefix + "grab_pull_current_distance=" + format(spider.getGrabPullCurrentDistance())
                + prefix + "grab_pull_min_distance=" + format(spider.getGrabPullMinDistance())
                + prefix + "grab_pull_distance_reduced=" + format(spider.getGrabPullDistanceReduced())
                + prefix + "grab_pull_target_start_y=" + format(spider.getGrabPullTargetStartY())
                + prefix + "grab_pull_target_max_y=" + format(spider.getGrabPullTargetMaxY())
                + prefix + "grab_pull_target_lift=" + format(spider.getGrabPullTargetLift())
                + prefix + "drag_nest_phase=" + spider.getDragNestPhaseName()
                + prefix + "drag_nest_ticks=" + spider.getDragNestTicks()
                + prefix + "drag_nest_cooldown_ticks=" + spider.getDragNestCooldownTicks()
                + prefix + "drag_nest_anchor=" + blockPosOrNone(spider.getDragNestAnchor())
                + prefix + "drag_nest_moved_target=" + spider.hasDragNestMovedTarget()
                + prefix + "drag_nest_reached_anchor=" + spider.hasDragNestReachedAnchor()
                + prefix + "drag_nest_saw_windup=" + spider.hasDragNestSeenWindup()
                + prefix + "drag_nest_saw_dragging=" + spider.hasDragNestSeenDragging()
                + prefix + "drag_nest_saw_recovery=" + spider.hasDragNestSeenRecovery()
                + prefix + "drag_nest_start_anchor_distance=" + format(spider.getDragNestStartAnchorDistance())
                + prefix + "drag_nest_current_anchor_distance=" + format(spider.getDragNestCurrentAnchorDistance())
                + prefix + "drag_nest_min_anchor_distance=" + format(spider.getDragNestMinAnchorDistance())
                + prefix + "drag_nest_anchor_distance_reduced=" + format(spider.getDragNestAnchorDistanceReduced());
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static String vector(Vec3 vector) {
        return (int) vector.x + "," + (int) vector.y + "," + (int) vector.z;
    }

    private static String blockPosOrNone(BlockPos pos) {
        return pos == null ? "none" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static boolean hasCobwebNear(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 1))) {
            if (level.getBlockState(pos).is(Blocks.COBWEB)) {
                return true;
            }
        }
        return false;
    }
}
