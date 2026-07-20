package com.horrormods.spiders.entity;

import com.horrormods.spiders.Spiders;
import com.horrormods.spiders.entity.ai.ClimberMoveControl;
import com.horrormods.spiders.entity.ai.ClimberPathNavigator;
import com.horrormods.spiders.entity.ai.NearestReachableAttackableTargetGoal;
import com.horrormods.spiders.entity.util.AttachmentHelper;
import com.horrormods.spiders.block.SingleThreadWebBlock;
import com.horrormods.spiders.block.entity.SingleThreadWebBlockEntity;
import com.horrormods.spiders.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import software.bernie.geckolib3.util.GeckoLibUtil;
import net.minecraftforge.common.util.FakePlayer;

public class GroundSpiderEntity extends Monster implements IAnimatable, IClimberEntity {

    private static final String ANIMATION_IDLE = "idle";
    private static final String ANIMATION_WALK = "walk_forward";
    private static final String ANIMATION_ATTACHED_WALK = "walk_forward_on_web";
    private static final String ANIMATION_CIRCLE_RIGHT = "circle_right";
    private static final String ANIMATION_RAISED_CIRCLE_RIGHT = "raised_circle_right";
    private static final String ANIMATION_RAISED_WALK = "raised_walk_forward";
    private static final String ANIMATION_RAISED_WALK_RIGHT = "raised_walk_forward_right";
    private static final String ANIMATION_RAISED_WALK_LEFT = "raised_walk_forward_left";
    private static final String ANIMATION_DROP_ATTACK = "ground_spider_jump_forward";
    private static final String ANIMATION_THREAT_DISPLAY = "threat_display";
    public static final String DROP_ATTACK_TEST_TARGET_TAG = "spiders_drop_attack_test_target";
    public static final String CEILING_STALK_TEST_TARGET_TAG = "spiders_ceiling_stalk_test_target";
    public static final String WEB_SHOT_TEST_TARGET_TAG = "spiders_web_shot_test_target";
    public static final String WEB_TRAP_PLACEMENT_TEST_TARGET_TAG = "spiders_web_trap_placement_test_target";
    public static final String WEB_LOWER_TEST_TARGET_TAG = "spiders_web_lower_test_target";
    public static final String POUNCE_TEST_TARGET_TAG = "spiders_pounce_test_target";
    public static final String RETREAT_TEST_TARGET_TAG = "spiders_retreat_test_target";
    public static final String GRAB_PULL_TEST_TARGET_TAG = "spiders_grab_pull_test_target";
    public static final String DRAG_NEST_TEST_TARGET_TAG = "spiders_drag_nest_test_target";
    public static final String PACK_COORDINATION_TEST_TARGET_TAG = "spiders_pack_coordination_test_target";
    public static final String ESCAPE_CUTTING_TEST_TARGET_TAG = "spiders_escape_cutting_test_target";
    public static final String THREAT_DISPLAY_TEST_TARGET_TAG = "spiders_threat_display_test_target";
    public static final String LINE_OF_SIGHT_STALKING_TEST_TARGET_TAG = "spiders_line_of_sight_stalking_test_target";
    public static final String DARKNESS_PREFERENCE_TEST_TARGET_TAG = "spiders_darkness_preference_test_target";
    public static final String WALL_PEEK_TEST_TARGET_TAG = "spiders_wall_peek_test_target";
    public static final String PREY_INTERACTION_TEST_TARGET_TAG = "spiders_prey_interaction_test_target";
    public static final String BASIC_MELEE_TEST_TARGET_TAG = "spiders_basic_melee_test_target";
    private static final double ANIMATION_MOVEMENT_EPSILON_SQR = 1.0E-5D;
    private static final float WEB_TRAVERSAL_HEAD_YAW_LIMIT = 65.0F;
    private static final float WEB_TRAVERSAL_HEAD_PITCH_LIMIT = 35.0F;
    private static final double WEB_TRAVERSAL_ALIGNMENT_MAX_DEGREES = 10.0D;
    private static final double WEB_TRAVERSAL_DIRECTION_EPSILON_SQR = 1.0E-6D;
    private static final int COMBAT_PACING_NONE = 0;
    private static final int COMBAT_PACING_STALK = 1;
    private static final int COMBAT_PACING_BURST = 2;
    private static final int DROP_ATTACK_NONE = 0;
    private static final int DROP_ATTACK_WINDUP = 1;
    private static final int DROP_ATTACK_DROPPING = 2;
    private static final int DROP_ATTACK_RECOVERING = 3;
    private static final int WEB_SHOT_NONE = 0;
    private static final int WEB_SHOT_WINDUP = 1;
    private static final int WEB_SHOT_RECOVERING = 2;
    private static final int WEB_LOWER_NONE = 0;
    private static final int WEB_LOWER_WINDUP = 1;
    private static final int WEB_LOWER_LOWERING = 2;
    private static final int WEB_LOWER_RECOVERING = 3;
    private static final int POUNCE_NONE = 0;
    private static final int POUNCE_WINDUP = 1;
    private static final int POUNCE_LEAPING = 2;
    private static final int POUNCE_RECOVERING = 3;
    private static final int RETREAT_NONE = 0;
    private static final int RETREAT_MOVING = 1;
    private static final int RETREAT_RECOVERING = 2;
    private static final int FAKE_RETREAT_NONE = 0;
    private static final int FAKE_RETREAT_FLEEING = 1;
    private static final int FAKE_RETREAT_REPOSITIONING = 2;
    private static final int FAKE_RETREAT_REENGAGING = 3;
    private static final int FAKE_RETREAT_RECOVERING = 4;
    private static final int GRAB_PULL_NONE = 0;
    private static final int GRAB_PULL_WINDUP = 1;
    private static final int GRAB_PULL_PULLING = 2;
    private static final int GRAB_PULL_RECOVERING = 3;
    private static final int DRAG_NEST_NONE = 0;
    private static final int DRAG_NEST_WINDUP = 1;
    private static final int DRAG_NEST_DRAGGING = 2;
    private static final int DRAG_NEST_RECOVERING = 3;
    private static final int PACK_ROLE_NONE = 0;
    private static final int PACK_ROLE_DIRECT = 1;
    private static final int PACK_ROLE_AMBUSH = 2;
    private static final int PACK_ROLE_FLANK = 3;
    private static final int STALK_PHASE_TICKS = 28;
    private static final int BURST_PHASE_TICKS = 16;
    private static final double BURST_ENGAGE_DISTANCE_SQR = 24.0D * 24.0D;
    private static final double STALK_SPEED_MODIFIER = -0.95D;
    private static final double BURST_SPEED_MODIFIER = 0.85D;
    private static final int BACKPEDAL_PHASE_TICKS = 28;
    private static final double BACKPEDAL_TRIGGER_DISTANCE_SQR = 5.75D * 5.75D;
    private static final double BACKPEDAL_TARGET_ADVANCE_EPSILON = 0.025D;
    private static final double BACKPEDAL_SPEED = 0.18D;
    private static final double BACKPEDAL_MIN_STEP_SQR = 1.0E-6D;
    private static final int CEILING_STALK_REPATH_TICKS = 10;
    private static final int CEILING_STALK_MAX_OVERHEAD_BLOCKS = 8;
    private static final int CEILING_STALK_BEHIND_BLOCKS = 2;
    private static final int CEILING_STALK_HORIZONTAL_SEARCH = 3;
    private static final double CEILING_STALK_NAVIGATION_SPEED = 0.72D;
    private static final double CEILING_STALK_HOLD_DISTANCE_SQR = 0.7D * 0.7D;
    private static final int CIRCLE_STRAFE_PHASE_TICKS = 12;
    private static final int CIRCLE_STRAFE_COOLDOWN_TICKS = 20;
    private static final double CIRCLE_STRAFE_MIN_DISTANCE_SQR = 2.35D * 2.35D;
    private static final double CIRCLE_STRAFE_MAX_DISTANCE_SQR = 5.75D * 5.75D;
    private static final double CIRCLE_STRAFE_IDEAL_DISTANCE = 3.75D;
    private static final double CIRCLE_STRAFE_SPEED = 0.16D;
    private static final double CIRCLE_STRAFE_RADIAL_CORRECTION = 0.45D;
    private static final double CIRCLE_STRAFE_MIN_STEP_SQR = 1.0E-6D;
    private static final int DROP_ATTACK_WINDUP_TICKS = 40;
    private static final int DROP_ATTACK_COMMIT_TICKS = 18;
    private static final int DROP_ATTACK_RECOVERY_TICKS = 40;
    private static final int DROP_ATTACK_COOLDOWN_TICKS = 70;
    private static final double DROP_ATTACK_TRIGGER_HORIZONTAL_SQR = 2.85D * 2.85D;
    private static final double DROP_ATTACK_MIN_VERTICAL = 1.35D;
    private static final double DROP_ATTACK_MAX_VERTICAL = 6.75D;
    private static final double DROP_ATTACK_HORIZONTAL_SPEED = 0.30D;
    private static final double DROP_ATTACK_MIN_FALL_SPEED = 0.34D;
    private static final double DROP_ATTACK_MAX_FALL_SPEED = 0.82D;
    private static final double DROP_ATTACK_DAMAGE_RANGE_SQR = 2.15D * 2.15D;
    private static final double DROP_ATTACK_DAMAGE = 4.5D;
    private static final int WEB_SHOT_WINDUP_TICKS = 60;
    private static final int WEB_SHOT_RECOVERY_TICKS = 40;
    private static final int WEB_SHOT_COOLDOWN_TICKS = 140;
    private static final double WEB_SHOT_MIN_RANGE_SQR = 3.5D * 3.5D;
    private static final double WEB_SHOT_MAX_RANGE_SQR = 12.0D * 12.0D;
    private static final float WEB_SHOT_PROJECTILE_SPEED = 1.25F;
    private static final float WEB_SHOT_PROJECTILE_INACCURACY = 0.0F;
    private static final int WEB_TRAP_PLACEMENT_TICKS = 34;
    private static final int WEB_TRAP_PLACEMENT_COOLDOWN_TICKS = 180;
    private static final double WEB_TRAP_PLACEMENT_MIN_RANGE_SQR = 2.0D * 2.0D;
    private static final double WEB_TRAP_PLACEMENT_MAX_RANGE_SQR = 8.5D * 8.5D;
    private static final double WEB_TRAP_PLACEMENT_FACING_DEGREES = 65.0D;
    private static final int WEB_LOWER_WINDUP_TICKS = 60;
    private static final int WEB_LOWER_LOWERING_TICKS = 220;
    private static final int WEB_LOWER_RECOVERY_TICKS = 24;
    private static final int WEB_LOWER_COOLDOWN_TICKS = 160;
    private static final double WEB_LOWER_TRIGGER_HORIZONTAL_SQR = 4.35D * 4.35D;
    private static final double WEB_LOWER_MIN_HORIZONTAL_SQR = 0.65D * 0.65D;
    private static final double WEB_LOWER_MIN_VERTICAL = 2.0D;
    private static final double WEB_LOWER_MAX_VERTICAL = 9.75D;
    private static final double WEB_LOWER_DESCENT_SPEED = 0.020D;
    private static final double WEB_LOWER_RECOVERY_HEIGHT = 0.55D;
    private static final int POUNCE_WINDUP_TICKS = 40;
    private static final int POUNCE_COMMIT_TICKS = 14;
    private static final double POUNCE_MIN_DAMAGE_TRAVEL = 1.35D;
    private static final int POUNCE_RECOVERY_TICKS = 36;
    private static final int POUNCE_COOLDOWN_TICKS = 140;
    private static final double POUNCE_MIN_RANGE_SQR = 2.2D * 2.2D;
    private static final double POUNCE_MAX_RANGE_SQR = 6.5D * 6.5D;
    private static final double POUNCE_HORIZONTAL_SPEED = 0.46D;
    private static final double POUNCE_FLOOR_VERTICAL_SPEED = 0.34D;
    private static final double POUNCE_WALL_VERTICAL_SPEED = 0.18D;
    private static final double POUNCE_DAMAGE_RANGE_SQR = 2.65D * 2.65D;
    private static final double POUNCE_DAMAGE = 3.5D;
    private static final int RETREAT_MOVE_TICKS = 44;
    private static final int RETREAT_RECOVERY_TICKS = 24;
    private static final int RETREAT_COOLDOWN_TICKS = 90;
    private static final int RETREAT_REPATH_TICKS = 8;
    private static final int RETREAT_SEARCH_DISTANCE = 6;
    private static final int RETREAT_SEARCH_VERTICAL = 4;
    private static final double RETREAT_TRIGGER_RANGE_SQR = 16.0D * 16.0D;
    private static final double RETREAT_NAVIGATION_SPEED = 1.18D;
    private static final double RETREAT_FALLBACK_SPEED = 0.26D;
    private static final double RETREAT_ANCHOR_REACHED_SQR = 0.9D * 0.9D;
    private static final double RETREAT_DISTANCE_GAIN_EPSILON = 0.35D;
    private static final int FAKE_RETREAT_REENGAGE_TICKS = 70;
    private static final int FAKE_RETREAT_RECOVERY_TICKS = 20;
    private static final int FAKE_RETREAT_COOLDOWN_TICKS = 240;
    private static final double FAKE_RETREAT_REENGAGE_SPEED = 1.72D;
    private static final double FAKE_RETREAT_REENGAGE_STEP_SPEED = 0.42D;
    private static final double FAKE_RETREAT_RETURN_CLOSURE_EPSILON = 0.45D;
    private static final int GRAB_PULL_WINDUP_TICKS = 90;
    private static final int GRAB_PULL_PULL_TICKS = 60;
    private static final int GRAB_PULL_RECOVERY_TICKS = 24;
    private static final int GRAB_PULL_COOLDOWN_TICKS = 170;
    private static final double GRAB_PULL_CLOSE_RANGE_SQR = 2.15D * 2.15D;
    private static final double GRAB_PULL_WEB_CONTROL_RANGE_SQR = 8.0D * 8.0D;
    private static final double GRAB_PULL_STOP_DISTANCE_SQR = 1.25D * 1.25D;
    private static final double GRAB_PULL_STEP = 0.065D;
    private static final double GRAB_PULL_MAX_UP_STEP = 0.135D;
    private static final double GRAB_PULL_MAX_DOWN_STEP = 0.045D;
    private static final double GRAB_PULL_MIN_EFFECT_DISTANCE = 0.45D;
    private static final double GRAB_PULL_MIN_EFFECT_LIFT = 0.22D;
    private static final int DRAG_NEST_WINDUP_TICKS = 36;
    private static final int DRAG_NEST_DRAG_TICKS = 100;
    private static final int DRAG_NEST_RECOVERY_TICKS = 28;
    private static final int DRAG_NEST_COOLDOWN_TICKS = 260;
    private static final int DRAG_NEST_ANCHOR_SEARCH_HORIZONTAL = 7;
    private static final int DRAG_NEST_ANCHOR_SEARCH_VERTICAL = 3;
    private static final double DRAG_NEST_TRIGGER_MAX_RANGE_SQR = 9.0D * 9.0D;
    private static final double DRAG_NEST_MIN_ANCHOR_DISTANCE_SQR = 1.45D * 1.45D;
    private static final double DRAG_NEST_ANCHOR_REACHED_SQR = 0.85D * 0.85D;
    private static final double DRAG_NEST_TARGET_STEP = 0.050D;
    private static final double DRAG_NEST_SPIDER_STEP = 0.040D;
    private static final double DRAG_NEST_SPIDER_ANCHOR_STOP_SQR = 1.05D * 1.05D;
    private static final int PACK_COORDINATION_MIN_SIZE = 2;
    private static final int PACK_COORDINATION_CACHE_MAX_ENTRIES = 64;
    private static final double PACK_COORDINATION_RANGE = 18.0D;
    private static final double PACK_COORDINATION_RANGE_SQR = PACK_COORDINATION_RANGE * PACK_COORDINATION_RANGE;
    private static final long SPIDER_PERF_LOG_THRESHOLD_NS = 500_000_000L;
    private static final int PLAYER_EXPERIENCE_NO_ATTACK_TICKS = 160;
    private static final int PLAYER_EXPERIENCE_STANDOFF_TICKS = 100;
    private static final int PLAYER_EXPERIENCE_STATIONARY_WALK_TICKS = 40;
    private static final int PLAYER_EXPERIENCE_LOG_COOLDOWN_TICKS = 100;
    private static final int PLAYER_EXPERIENCE_TARGET_RECENT_ATTACK_TICKS = 120;
    private static final double PLAYER_EXPERIENCE_STANDOFF_MIN_DISTANCE_SQR = 3.5D * 3.5D;
    private static final double PLAYER_EXPERIENCE_STANDOFF_MAX_DISTANCE_SQR = 12.0D * 12.0D;
    private static final double PLAYER_EXPERIENCE_SNAP_DISTANCE_SQR = 4.0D * 4.0D;
    private static final double PLAYER_EXPERIENCE_STATIONARY_MOVE_SQR = 1.0E-4D;
    private static final int LIVE_PLAYER_DENSE_PACK_SIZE = 8;
    private static final int LIVE_PLAYER_COMBAT_START_STAGGER_TICKS = 6;
    private static final int LIVE_PLAYER_DIRECT_NAVIGATION_INTERVAL_TICKS = 12;
    private static final int READABLE_LIVE_PLAYER_CONTACT_ATTACK_INTERVAL_TICKS = 20;
    private static final double READABLE_LIVE_PLAYER_CONTACT_ATTACK_RANGE_SQR = 4.25D * 4.25D;
    private static final double READABLE_LIVE_PLAYER_HOLD_PRESSURE_RANGE_SQR = 3.1D * 3.1D;
    private static final int LIVE_PLAYER_DENSE_DAMAGE_STAGGER_TICKS = 6;
    private static final float LIVE_PLAYER_DENSE_DAMAGE_FLOOR_HEALTH = 12.0F;
    private static final int LIVE_PLAYER_DENSE_DIRECT_CONTACT_SLOTS = 3;
    private static final double LIVE_PLAYER_DENSE_PRESSURE_STOP_DISTANCE_SQR = 1.55D * 1.55D;
    private static final double LIVE_PLAYER_DENSE_NON_FLOOR_HOLD_DISTANCE_SQR = 4.75D * 4.75D;
    private static final double PACK_DIRECT_PRESSURE_STOP_DISTANCE_SQR = 1.55D * 1.55D;
    private static final double PACK_DIRECT_PRESSURE_SPEED = 0.55D;
    private static final double READABLE_LIVE_PLAYER_DIRECT_ADVANCE_SPEED = 0.18D;
    private static final double READABLE_LIVE_PLAYER_ATTACHED_FALLBACK_SPEED = 0.08D;
    private static final int ESCAPE_CUTTING_ROUTE_SEARCH_DISTANCE = 8;
    private static final int ESCAPE_CUTTING_ACTIVE_TICKS = 260;
    private static final int ESCAPE_CUTTING_REPATH_TICKS = 8;
    private static final int ESCAPE_CUTTING_COOLDOWN_TICKS = 80;
    private static final double ESCAPE_CUTTING_NAVIGATION_SPEED = 1.05D;
    private static final double ESCAPE_CUTTING_STEP = 0.16D;
    private static final double ESCAPE_CUTTING_HOLD_DISTANCE_SQR = 0.9D * 0.9D;
    private static final double ESCAPE_CUTTING_MIN_TARGET_ROUTE_DISTANCE_SQR = 2.0D * 2.0D;
    private static final double ESCAPE_CUTTING_MIN_TARGET_ANCHOR_SEPARATION_SQR = 2.0D * 2.0D;
    private static final double ESCAPE_CUTTING_LOOK_ALIGNMENT_WEIGHT = 4.0D;
    private static final int THREAT_DISPLAY_TICKS = 54;
    private static final int THREAT_DISPLAY_COOLDOWN_TICKS = 150;
    private static final double THREAT_DISPLAY_MIN_RANGE_SQR = 1.3D * 1.3D;
    private static final double THREAT_DISPLAY_MAX_RANGE_SQR = 4.4D * 4.4D;
    private static final double THREAT_DISPLAY_HELD_STILL_DISTANCE = 0.32D;
    private static final double THREAT_DISPLAY_FACING_DEGREES = 40.0D;
    private static final int LINE_OF_SIGHT_STALKING_TICKS = 90;
    private static final int LINE_OF_SIGHT_STALKING_COOLDOWN_TICKS = 140;
    private static final double LINE_OF_SIGHT_STALKING_MIN_RANGE_SQR = 2.2D * 2.2D;
    private static final double LINE_OF_SIGHT_STALKING_MAX_RANGE_SQR = 9.0D * 9.0D;
    private static final double LINE_OF_SIGHT_STALKING_LOOK_DOT = 0.62D;
    private static final double LINE_OF_SIGHT_STALKING_HOLD_DISTANCE = 0.35D;
    private static final double LINE_OF_SIGHT_STALKING_ADVANCE_SPEED = 0.82D;
    private static final double LINE_OF_SIGHT_STALKING_ADVANCE_STEP = 0.09D;
    private static final double LINE_OF_SIGHT_STALKING_CLOSE_STOP_SQR = 1.7D * 1.7D;
    private static final double LINE_OF_SIGHT_STALKING_FACING_DEGREES = 45.0D;
    private static final int DARKNESS_PREFERENCE_TICKS = 90;
    private static final int DARKNESS_PREFERENCE_COOLDOWN_TICKS = 120;
    private static final int DARKNESS_PREFERENCE_REPATH_TICKS = 8;
    private static final int DARKNESS_PREFERENCE_HORIZONTAL_SEARCH = 7;
    private static final int DARKNESS_PREFERENCE_VERTICAL_SEARCH = 5;
    private static final double DARKNESS_PREFERENCE_NAVIGATION_SPEED = 0.78D;
    private static final double DARKNESS_PREFERENCE_STEP = 0.12D;
    private static final double DARKNESS_PREFERENCE_HOLD_DISTANCE_SQR = 0.9D * 0.9D;
    private static final double DARKNESS_PREFERENCE_MIN_TARGET_SEPARATION_SQR = 2.0D * 2.0D;
    private static final double DARKNESS_PREFERENCE_MIN_SCORE_ADVANTAGE = 4.0D;
    private static final double DARKNESS_PREFERENCE_FACING_DEGREES = 55.0D;
    private static final int WALL_PEEK_NONE = 0;
    private static final int WALL_PEEK_EMERGING = 1;
    private static final int WALL_PEEK_HOLDING = 2;
    private static final int WALL_PEEK_RETREATING = 3;
    private static final int WALL_PEEK_EMERGE_TICKS = 120;
    private static final int WALL_PEEK_EMERGE_MIN_VISIBLE_TICKS = 80;
    private static final int WALL_PEEK_HOLD_TICKS = 80;
    private static final int WALL_PEEK_RETREAT_TICKS = 60;
    private static final int WALL_PEEK_COOLDOWN_TICKS = 160;
    private static final int WALL_PEEK_REPATH_TICKS = 8;
    private static final int WALL_PEEK_SEARCH_HORIZONTAL = 5;
    private static final double WALL_PEEK_NAVIGATION_SPEED = 0.82D;
    private static final double WALL_PEEK_STEP = 0.12D;
    private static final double WALL_PEEK_REACHED_SQR = 0.35D * 0.35D;
    private static final double WALL_PEEK_MIN_RANGE_SQR = 3.0D * 3.0D;
    private static final double WALL_PEEK_MAX_RANGE_SQR = 10.0D * 10.0D;
    private static final double WALL_PEEK_FACING_DEGREES = 55.0D;
    private static final int PREY_INTERACTION_NONE = 0;
    private static final int PREY_INTERACTION_WEBBING = 1;
    private static final int PREY_INTERACTION_GUARDING = 2;
    private static final int PREY_INTERACTION_WEBBING_TICKS = 72;
    private static final int PREY_INTERACTION_GUARD_TICKS = 120;
    private static final int PREY_INTERACTION_COOLDOWN_TICKS = 220;
    private static final int PREY_INTERACTION_REPATH_TICKS = 8;
    private static final int PREY_INTERACTION_WEB_SEARCH_HORIZONTAL = 2;
    private static final int PREY_INTERACTION_MAX_WEBS = 4;
    private static final double PREY_INTERACTION_NAVIGATION_SPEED = 0.74D;
    private static final double PREY_INTERACTION_STEP = 0.105D;
    private static final double PREY_INTERACTION_REACHED_SQR = 1.05D * 1.05D;
    private static final double PREY_INTERACTION_FACING_DEGREES = 60.0D;
    private static final UUID COMBAT_PACING_SPEED_MODIFIER_ID =
            UUID.fromString("8d64b1d4-49f7-45a9-a37b-e5b1db98cc07");
    private static final EntityDataAccessor<Direction> ATTACHMENT_DIRECTION =
            SynchedEntityData.defineId(GroundSpiderEntity.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Integer> COMBAT_PACING =
            SynchedEntityData.defineId(GroundSpiderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DROP_ATTACK_PHASE =
            SynchedEntityData.defineId(GroundSpiderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WEB_SHOT_PHASE =
            SynchedEntityData.defineId(GroundSpiderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WEB_LOWER_PHASE =
            SynchedEntityData.defineId(GroundSpiderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POUNCE_PHASE =
            SynchedEntityData.defineId(GroundSpiderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RETREAT_PHASE =
            SynchedEntityData.defineId(GroundSpiderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FAKE_RETREAT_PHASE =
            SynchedEntityData.defineId(GroundSpiderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GRAB_PULL_PHASE =
            SynchedEntityData.defineId(GroundSpiderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DRAG_NEST_PHASE =
            SynchedEntityData.defineId(GroundSpiderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WEB_TRAVERSAL_REVERSE =
            SynchedEntityData.defineId(GroundSpiderEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimationFactory factory = GeckoLibUtil.createFactory(this);

    private static final Map<String, PackCoordinationCacheEntry> PACK_COORDINATION_CACHE = new HashMap<>();
    private static final Map<String, DenseLivePlayerSwarmCacheEntry> DENSE_LIVE_PLAYER_SWARM_CACHE = new HashMap<>();
    private static final Map<UUID, Long> PLAYER_EXPERIENCE_TARGET_ATTACK_TICKS = new HashMap<>();
    private static long perfTickGameTime = Long.MIN_VALUE;
    private static int perfTickSpiderCount = 0;
    private static long perfTickTotalNs = 0L;
    private static long perfTickPreemptNs = 0L;
    private static long perfTickSuperNs = 0L;
    private static long perfTickPostNs = 0L;
    private static long perfTickCombatNs = 0L;
    private static long perfTickMaxNs = 0L;
    private static int perfTickMaxEntityId = -1;
    private static long perfTickMaxEntityPreemptNs = 0L;
    private static long perfTickMaxEntitySuperNs = 0L;
    private static long perfTickMaxEntityPostNs = 0L;
    private static long perfTickMaxEntityCombatNs = 0L;
    private static String perfTickMaxEntityState = "none";

    private Direction pendingAttachment = Direction.DOWN;
    private int pendingAttachTicks = 0;
    private static final int ATTACH_CONFIRM_TICKS = 4;
    private static final int TARGET_LOSS_ATTACHMENT_GRACE_TICKS = 100;
    private int targetLossAttachmentGraceTicks = 0;
    private int combatPacingTicks = 0;
    private int backpedalTicks = 0;
    private UUID backpedalTargetId = null;
    private Vec3 previousBackpedalTargetPosition = Vec3.ZERO;
    private boolean ceilingStalking = false;
    private int ceilingStalkRepathTicks = 0;
    private BlockPos ceilingStalkAnchor = null;
    private BlockPos lastCeilingStalkPathAnchor = null;
    private int circleStrafeTicks = 0;
    private int circleStrafeCooldownTicks = 0;
    private boolean circleStrafeClockwise = true;
    private UUID circleStrafeTargetId = null;
    private int dropAttackTicks = 0;
    private int dropAttackCooldownTicks = 0;
    private UUID dropAttackTargetId = null;
    private boolean dropAttackDamageSpent = false;
    private int webShotTicks = 0;
    private int webShotCooldownTicks = 0;
    private UUID webShotTargetId = null;
    private boolean webShotFired = false;
    private boolean webTrapPlacement = false;
    private int webTrapPlacementTicks = 0;
    private int webTrapPlacementCooldownTicks = 0;
    private UUID webTrapPlacementTargetId = null;
    private BlockPos webTrapPlacementAnchor = null;
    private Direction webTrapPlacementRouteDirection = null;
    private int webTrapPlacementPlacedCount = 0;
    private boolean webTrapPlacementPlacedBehind = false;
    private boolean webTrapPlacementPlacedBeside = false;
    private boolean webTrapPlacementTargetRetained = false;
    private int webTrapPlacementFacingTicks = 0;
    private double webTrapPlacementStartTargetDistance = 0.0D;
    private double webTrapPlacementCurrentTargetDistance = 0.0D;
    private String webTrapPlacementStatus = "idle";
    private int webLowerTicks = 0;
    private int webLowerCooldownTicks = 0;
    private UUID webLowerTargetId = null;
    private double webLowerStartY = 0.0D;
    private double webLowerLowestY = 0.0D;
    private BlockPos webLowerStrandAnchor = null;
    private int pounceTicks = 0;
    private int pounceCooldownTicks = 0;
    private UUID pounceTargetId = null;
    private boolean pounceLaunched = false;
    private boolean pounceDamageSpent = false;
    private Vec3 pounceLaunchVelocity = Vec3.ZERO;
    private double pounceTravelDistance = 0.0D;
    private int retreatTicks = 0;
    private int retreatCooldownTicks = 0;
    private UUID retreatTargetId = null;
    private UUID pendingRetreatTargetId = null;
    private boolean pendingRetreatFromDamage = false;
    private boolean pendingRetreatFromMiss = false;
    private boolean retreatTriggeredByDamage = false;
    private boolean retreatTriggeredByMiss = false;
    private BlockPos retreatAnchor = null;
    private BlockPos lastRetreatPathAnchor = null;
    private int retreatRepathTicks = 0;
    private double retreatStartDistance = 0.0D;
    private double retreatMaxDistance = 0.0D;
    private int fakeRetreatTicks = 0;
    private int fakeRetreatCooldownTicks = 0;
    private UUID fakeRetreatTargetId = null;
    private boolean fakeRetreatTriggeredByDamage = false;
    private boolean fakeRetreatTriggeredByMiss = false;
    private BlockPos fakeRetreatAnchor = null;
    private double fakeRetreatStartDistance = 0.0D;
    private double fakeRetreatMaxDistance = 0.0D;
    private double fakeRetreatReturnStartDistance = 0.0D;
    private double fakeRetreatMinReturnDistance = 0.0D;
    private boolean fakeRetreatReengageStarted = false;
    private int grabPullTicks = 0;
    private int grabPullCooldownTicks = 0;
    private UUID grabPullTargetId = null;
    private boolean grabPullTriggeredByWeb = false;
    private boolean grabPullMovedTarget = false;
    private boolean grabPullSawPulling = false;
    private double grabPullStartDistance = 0.0D;
    private double grabPullMinDistance = 0.0D;
    private double grabPullStartTargetY = 0.0D;
    private double grabPullMaxTargetY = 0.0D;
    private Vec3 grabPullHoldPosition = Vec3.ZERO;
    private int dragNestTicks = 0;
    private int dragNestCooldownTicks = 0;
    private UUID dragNestTargetId = null;
    private UUID pendingDragNestTargetId = null;
    private BlockPos dragNestAnchor = null;
    private boolean dragNestMovedTarget = false;
    private boolean dragNestReachedAnchor = false;
    private boolean dragNestSawWindup = false;
    private boolean dragNestSawDragging = false;
    private boolean dragNestSawRecovery = false;
    private double dragNestStartAnchorDistance = 0.0D;
    private double dragNestCurrentAnchorDistance = 0.0D;
    private double dragNestMinAnchorDistance = 0.0D;
    private int packRole = PACK_ROLE_NONE;
    private int packRoleTicks = 0;
    private UUID packTargetId = null;
    private int packSize = 0;
    private int packDirectCount = 0;
    private int packAmbushCount = 0;
    private int packFlankCount = 0;
    private boolean escapeCutting = false;
    private int escapeCuttingTicks = 0;
    private int escapeCuttingCooldownTicks = 0;
    private int escapeCuttingRepathTicks = 0;
    private UUID escapeCuttingTargetId = null;
    private BlockPos escapeCuttingAnchor = null;
    private Direction escapeCuttingRouteDirection = null;
    private boolean escapeCuttingPathStarted = false;
    private boolean escapeCuttingReachedAnchor = false;
    private double escapeCuttingStartAnchorDistance = 0.0D;
    private double escapeCuttingCurrentAnchorDistance = 0.0D;
    private double escapeCuttingMinAnchorDistance = 0.0D;
    private String escapeCuttingStatus = "idle";
    private int threatDisplayTicks = 0;
    private int threatDisplayCooldownTicks = 0;
    private UUID threatDisplayTargetId = null;
    private Vec3 threatDisplayStartPosition = Vec3.ZERO;
    private double threatDisplayStartDistance = 0.0D;
    private double threatDisplayCurrentDistance = 0.0D;
    private double threatDisplayMaxMovement = 0.0D;
    private int threatDisplayFacingTicks = 0;
    private boolean threatDisplayPlayedSound = false;
    private String threatDisplayStatus = "idle";
    private int lineOfSightStalkingTicks = 0;
    private int lineOfSightStalkingCooldownTicks = 0;
    private UUID lineOfSightStalkingTargetId = null;
    private Vec3 lineOfSightStalkingStartPosition = Vec3.ZERO;
    private double lineOfSightStalkingStartDistance = 0.0D;
    private double lineOfSightStalkingCurrentDistance = 0.0D;
    private double lineOfSightStalkingMinDistance = 0.0D;
    private double lineOfSightStalkingTotalMovement = 0.0D;
    private double lineOfSightStalkingMaxWatchedMovement = 0.0D;
    private int lineOfSightStalkingWatchedTicks = 0;
    private int lineOfSightStalkingUnwatchedTicks = 0;
    private int lineOfSightStalkingFacingTicks = 0;
    private boolean lineOfSightStalkingTargetLooking = false;
    private boolean lineOfSightStalkingSawWatched = false;
    private boolean lineOfSightStalkingSawUnwatchedAdvance = false;
    private String lineOfSightStalkingStatus = "idle";
    private boolean darknessPreference = false;
    private int darknessPreferenceTicks = 0;
    private int darknessPreferenceCooldownTicks = 0;
    private int darknessPreferenceRepathTicks = 0;
    private UUID darknessPreferenceTargetId = null;
    private BlockPos darknessPreferenceAnchor = null;
    private Direction darknessPreferenceAttachment = Direction.DOWN;
    private boolean darknessPreferencePathStarted = false;
    private boolean darknessPreferenceReachedAnchor = false;
    private boolean darknessPreferenceHeldAnchor = false;
    private int darknessPreferenceFacingTicks = 0;
    private int darknessPreferenceAnchorLight = 0;
    private int darknessPreferenceCurrentLight = 0;
    private int darknessPreferenceOpenLight = 0;
    private int darknessPreferenceCoverCount = 0;
    private int darknessPreferenceWallAdjacentCount = 0;
    private boolean darknessPreferenceCovered = false;
    private boolean darknessPreferenceCorner = false;
    private double darknessPreferenceAnchorScore = 0.0D;
    private double darknessPreferenceOpenScore = 0.0D;
    private double darknessPreferenceStartAnchorDistance = 0.0D;
    private double darknessPreferenceCurrentAnchorDistance = 0.0D;
    private double darknessPreferenceMinAnchorDistance = 0.0D;
    private String darknessPreferenceStatus = "idle";
    private int wallPeekPhase = WALL_PEEK_NONE;
    private int wallPeekTicks = 0;
    private int wallPeekCooldownTicks = 0;
    private int wallPeekRepathTicks = 0;
    private UUID wallPeekTargetId = null;
    private BlockPos wallPeekCoverAnchor = null;
    private BlockPos wallPeekPeekAnchor = null;
    private boolean wallPeekPathStarted = false;
    private boolean wallPeekReachedPeek = false;
    private boolean wallPeekHeldPeek = false;
    private boolean wallPeekRetreated = false;
    private boolean wallPeekTargetRetained = false;
    private boolean wallPeekCoverLineOfSightBlocked = false;
    private boolean wallPeekPeekLineOfSightClear = false;
    private int wallPeekFacingTicks = 0;
    private double wallPeekStartPeekDistance = 0.0D;
    private double wallPeekCurrentPeekDistance = 0.0D;
    private double wallPeekMinPeekDistance = 0.0D;
    private double wallPeekStartCoverDistance = 0.0D;
    private double wallPeekCurrentCoverDistance = 0.0D;
    private double wallPeekMinCoverDistance = 0.0D;
    private String wallPeekStatus = "idle";
    private int preyInteractionPhase = PREY_INTERACTION_NONE;
    private int preyInteractionTicks = 0;
    private int preyInteractionCooldownTicks = 0;
    private int preyInteractionRepathTicks = 0;
    private UUID preyInteractionTargetId = null;
    private String preyInteractionPreyType = "none";
    private BlockPos preyInteractionPreyAnchor = null;
    private BlockPos preyInteractionGuardAnchor = null;
    private boolean preyInteractionPathStarted = false;
    private boolean preyInteractionReachedGuard = false;
    private boolean preyInteractionHeldGuard = false;
    private boolean preyInteractionPlacedWeb = false;
    private int preyInteractionPlacedWebCount = 0;
    private boolean preyInteractionTargetKilled = false;
    private int preyInteractionFacingTicks = 0;
    private double preyInteractionStartGuardDistance = 0.0D;
    private double preyInteractionCurrentGuardDistance = 0.0D;
    private double preyInteractionMinGuardDistance = 0.0D;
    private String preyInteractionStatus = "idle";
    private UUID playerExperienceTargetId = null;
    private Vec3 playerExperiencePreviousPosition = Vec3.ZERO;
    private boolean playerExperienceHasPreviousPosition = false;
    private int playerExperienceNoAttackTicks = 0;
    private int playerExperienceStandoffTicks = 0;
    private int playerExperienceStationaryWalkTicks = 0;
    private int playerExperienceLogCooldownTicks = 0;
    private int readableLivePlayerContactAttackCooldownTicks = 0;

    // Forced path following fields
    private List<BlockPos> forcedPath = null;
    private int forcedPathIndex = 0;
    private double forcedSpeed = 0.0D;
    private float webTraversalBodyYaw = 0.0F;
    private float webTraversalHeadYaw = 0.0F;
    private float webTraversalHeadPitch = 0.0F;

    public GroundSpiderEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new ClimberMoveControl(this);
        this.navigation = new ClimberPathNavigator(this, this.level, true, true);
    }

    // Begin moving along a forced path at a constant speed
    public void startForcedPath(List<BlockPos> path, double speed) {
        if (path == null || path.isEmpty()) return;
        this.forcedPath = path;
        this.forcedSpeed = speed;
        // Assume caller has already placed us at the first node
        this.forcedPathIndex = 1;
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public boolean isFollowingForcedPath() {
        return this.forcedPath != null;
    }

    public int getForcedPathIndex() {
        return this.forcedPathIndex;
    }

    public int getForcedPathSize() {
        return this.forcedPath == null ? 0 : this.forcedPath.size();
    }

    private void clearForcedPath() {
        this.forcedPath = null;
        this.forcedPathIndex = 0;
        this.forcedSpeed = 0.0D;
        this.noPhysics = false;
        this.setNoGravity(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ATTACHMENT_DIRECTION, Direction.DOWN);
        this.entityData.define(COMBAT_PACING, COMBAT_PACING_NONE);
        this.entityData.define(DROP_ATTACK_PHASE, DROP_ATTACK_NONE);
        this.entityData.define(WEB_SHOT_PHASE, WEB_SHOT_NONE);
        this.entityData.define(WEB_LOWER_PHASE, WEB_LOWER_NONE);
        this.entityData.define(POUNCE_PHASE, POUNCE_NONE);
        this.entityData.define(RETREAT_PHASE, RETREAT_NONE);
        this.entityData.define(FAKE_RETREAT_PHASE, FAKE_RETREAT_NONE);
        this.entityData.define(GRAB_PULL_PHASE, GRAB_PULL_NONE);
        this.entityData.define(DRAG_NEST_PHASE, DRAG_NEST_NONE);
        this.entityData.define(WEB_TRAVERSAL_REVERSE, false);
    }

    @Override
    public Direction getAttachmentDirection() {
        return this.entityData.get(ATTACHMENT_DIRECTION);
    }

    @Override
    public void setAttachmentDirection(Direction d) {
        this.entityData.set(ATTACHMENT_DIRECTION, d);
    }

    @Override
    public void tick() {
        long perfStartNs = !this.level.isClientSide ? System.nanoTime() : 0L;
        long preemptNs = 0L;
        long superNs = 0L;
        long postNs = 0L;
        long combatNs = 0L;
        if (!this.level.isClientSide) {
            long preemptStartNs = System.nanoTime();
            preemptGrabPullBeforeBaseTick();
            preemptDragNestBeforeBaseTick();
            preemptThreatDisplayBeforeBaseTick();
            preemptNs = System.nanoTime() - preemptStartNs;
        }
        long superStartNs = !this.level.isClientSide ? System.nanoTime() : 0L;
        super.tick();
        if (!this.level.isClientSide) {
            superNs = System.nanoTime() - superStartNs;
        }
        if (!this.level.isClientSide) {
            long postStartNs = System.nanoTime();
            var currentTarget = this.getTarget();
            if (currentTarget != null && (!currentTarget.isAlive() || currentTarget.isRemoved())) {
                this.setTarget(null);
                this.navigation.stop();
                this.targetLossAttachmentGraceTicks = TARGET_LOSS_ATTACHMENT_GRACE_TICKS;
            } else if (currentTarget != null) {
                this.targetLossAttachmentGraceTicks = 0;
            }

            Direction current = getAttachmentDirection();
            BlockPos currentBlock = blockPosition();
            boolean currentSupported = AttachmentHelper.hasSupport(level, currentBlock, current);
            Direction found = null;
            if (isDropAttackDropping() || isWebLowerLowering()) {
                found = Direction.DOWN;
            } else {
                if (currentSupported) {
                    found = current;
                }

                if (found == null) {
                    AttachmentHelper.SupportedAttachment nearest =
                            AttachmentHelper.findClosestSupportedAttachment(level, this, currentBlock, current, position());
                    double maxSnapDistance = Math.max(1.25D, this.getBbWidth() + this.getBbHeight());
                    if (nearest != null && position().distanceToSqr(nearest.anchor()) <= maxSnapDistance * maxSnapDistance) {
                        teleportTo(nearest.anchor().x, nearest.anchor().y, nearest.anchor().z);
                        setDeltaMovement(AttachmentHelper.projectOntoPlane(getDeltaMovement(), AttachmentHelper.normal(nearest.attachment())));
                        found = nearest.attachment();
                    }
                }

                if (found == null) {
                    found = AttachmentHelper.findAttachment(this.level, this, this.blockPosition());
                }
                if (found == null) found = Direction.DOWN;
            }

            if (!currentSupported && found != current) {
                setAttachmentDirection(found);
                pendingAttachTicks = 0;
                pendingAttachment = found;
            } else if (found == current) {
                pendingAttachTicks = 0;
                pendingAttachment = found;
            } else {
                if (pendingAttachment != found) {
                    pendingAttachment = found;
                    pendingAttachTicks = 1;
                } else {
                    pendingAttachTicks++;
                    if (pendingAttachTicks >= ATTACH_CONFIRM_TICKS) {
                        setAttachmentDirection(pendingAttachment);
                        pendingAttachTicks = 0;
                    }
                }
            }

            // Always ensure noGravity while attached
            if (getAttachmentDirection() != Direction.DOWN && !this.isNoGravity()) {
                this.setNoGravity(true);
            } else if (getAttachmentDirection() == Direction.DOWN && this.isNoGravity() && !this.isNoAi()) {
                this.setNoGravity(false);
            }

            // Handle forced path movement
            if (this.forcedPath != null && this.forcedPathIndex < this.forcedPath.size()) {
                BlockPos targetBlock = this.forcedPath.get(this.forcedPathIndex);
                Vec3 target = new Vec3(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5);
                Vec3 pos = this.position();
                Vec3 diff = target.subtract(pos);
                double dist = diff.length();
                updateSingleThreadWebTraversalOrientation(diff);

                if (dist <= this.forcedSpeed) {
                    this.setPos(target.x, target.y, target.z);
                    this.forcedPathIndex++;
                } else {
                    Vec3 step = diff.normalize().scale(this.forcedSpeed);
                    this.setPos(pos.add(step));
                }

                if (isOnSingleThreadWeb() && this.getTarget() != null) {
                    lookAtWithUnlockedHead(this.getTarget());
                }

                if (this.forcedPathIndex >= this.forcedPath.size()) {
                    clearForcedPath();
                }
            } else if (this.forcedPath != null) {
                clearForcedPath();
            }

            if (this.forcedPath == null && isOnSingleThreadWeb()) {
                updateSingleThreadWebTraversalOrientation(this.getDeltaMovement());
                if (this.getTarget() != null) {
                    lookAtWithUnlockedHead(this.getTarget());
                }
            }

            if (this.getTarget() != null) {
                this.targetLossAttachmentGraceTicks = 0;
            } else if (this.targetLossAttachmentGraceTicks > 0) {
                this.targetLossAttachmentGraceTicks--;
            }

            postNs = System.nanoTime() - postStartNs;
            long combatStartNs = System.nanoTime();
            updateCombatPacing();
            combatNs = System.nanoTime() - combatStartNs;
            updatePlayerExperienceWatchdog();
            recordSpiderTickPerf(System.nanoTime() - perfStartNs, preemptNs, superNs, postNs, combatNs);
        }
    }

    private void updatePlayerExperienceWatchdog() {
        if (this.playerExperienceLogCooldownTicks > 0) {
            this.playerExperienceLogCooldownTicks--;
        }

        LivingEntity target = this.getTarget();
        if (!(target instanceof Player) || target instanceof FakePlayer || !target.isAlive()
                || hasFocusedCombatTestTag(target)) {
            resetPlayerExperienceWatchdog();
            return;
        }

        if (!target.getUUID().equals(this.playerExperienceTargetId)) {
            this.playerExperienceTargetId = target.getUUID();
            this.playerExperienceNoAttackTicks = 0;
            this.playerExperienceStandoffTicks = 0;
            this.playerExperienceStationaryWalkTicks = 0;
            this.playerExperienceLogCooldownTicks = 0;
            this.playerExperienceHasPreviousPosition = false;
        }

        Vec3 currentPosition = this.position();
        double tickMoveSqr = this.playerExperienceHasPreviousPosition
                ? currentPosition.distanceToSqr(this.playerExperiencePreviousPosition)
                : 0.0D;
        this.playerExperiencePreviousPosition = currentPosition;
        this.playerExperienceHasPreviousPosition = true;

        double distanceSqr = this.distanceToSqr(target);
        boolean targetRecentlyAttacked = hasRecentPlayerExperienceTargetAttack(target);
        boolean playerAboveDenseDamageFloor = target.getHealth() > LIVE_PLAYER_DENSE_DAMAGE_FLOOR_HEALTH + 0.25F;
        if (playerAboveDenseDamageFloor && !targetRecentlyAttacked) {
            this.playerExperienceNoAttackTicks++;
        } else {
            this.playerExperienceNoAttackTicks = 0;
        }

        if (distanceSqr >= PLAYER_EXPERIENCE_STANDOFF_MIN_DISTANCE_SQR
                && distanceSqr <= PLAYER_EXPERIENCE_STANDOFF_MAX_DISTANCE_SQR
                && this.getSensing().hasLineOfSight(target)
                && !targetRecentlyAttacked) {
            this.playerExperienceStandoffTicks++;
        } else {
            this.playerExperienceStandoffTicks = 0;
        }

        String animation = getAnimationAuditName();
        boolean walkingAnimation = isWalkLikeAnimationName(animation);
        if (walkingAnimation && tickMoveSqr <= PLAYER_EXPERIENCE_STATIONARY_MOVE_SQR && !targetRecentlyAttacked) {
            this.playerExperienceStationaryWalkTicks++;
        } else {
            this.playerExperienceStationaryWalkTicks = 0;
        }

        if (tickMoveSqr >= PLAYER_EXPERIENCE_SNAP_DISTANCE_SQR) {
            logPlayerExperienceIssue("snap_move", target, tickMoveSqr);
        } else if (this.playerExperienceNoAttackTicks >= PLAYER_EXPERIENCE_NO_ATTACK_TICKS) {
            logPlayerExperienceIssue("no_recent_attack", target, tickMoveSqr);
        } else if (this.playerExperienceStandoffTicks >= PLAYER_EXPERIENCE_STANDOFF_TICKS) {
            logPlayerExperienceIssue("standoff_distance", target, tickMoveSqr);
        } else if (this.playerExperienceStationaryWalkTicks >= PLAYER_EXPERIENCE_STATIONARY_WALK_TICKS) {
            logPlayerExperienceIssue("stationary_walk_animation", target, tickMoveSqr);
        }
    }

    private void resetPlayerExperienceWatchdog() {
        this.playerExperienceTargetId = null;
        this.playerExperienceHasPreviousPosition = false;
        this.playerExperienceNoAttackTicks = 0;
        this.playerExperienceStandoffTicks = 0;
        this.playerExperienceStationaryWalkTicks = 0;
        this.playerExperienceLogCooldownTicks = 0;
    }

    private void recordPlayerExperienceAttack(LivingEntity target) {
        if (target instanceof Player && !(target instanceof FakePlayer)) {
            PLAYER_EXPERIENCE_TARGET_ATTACK_TICKS.put(target.getUUID(), this.level.getGameTime());
            this.playerExperienceNoAttackTicks = 0;
            this.playerExperienceStandoffTicks = 0;
        }
    }

    private boolean hasRecentPlayerExperienceTargetAttack(LivingEntity target) {
        Long lastAttackTick = PLAYER_EXPERIENCE_TARGET_ATTACK_TICKS.get(target.getUUID());
        if (lastAttackTick == null) {
            return false;
        }
        return this.level.getGameTime() - lastAttackTick <= PLAYER_EXPERIENCE_TARGET_RECENT_ATTACK_TICKS;
    }

    private void logPlayerExperienceIssue(String issue, LivingEntity target, double tickMoveSqr) {
        if (this.playerExperienceLogCooldownTicks > 0) {
            return;
        }
        this.playerExperienceLogCooldownTicks = PLAYER_EXPERIENCE_LOG_COOLDOWN_TICKS;
        Spiders.LOGGER.warn("spiders_player_experience_watchdog issue={} uuid={} target={} distance={} melee_range={} no_attack_ticks={} standoff_ticks={} stationary_walk_ticks={} tick_move={} animation={} attachment={} state={}",
                issue,
                this.getUUID(),
                target.getType().toShortString(),
                String.format(java.util.Locale.ROOT, "%.3f", Math.sqrt(this.distanceToSqr(target))),
                this.isWithinMeleeAttackRange(target),
                this.playerExperienceNoAttackTicks,
                this.playerExperienceStandoffTicks,
                this.playerExperienceStationaryWalkTicks,
                String.format(java.util.Locale.ROOT, "%.3f", Math.sqrt(tickMoveSqr)),
                getAnimationAuditName(),
                this.getAttachmentDirection().getName(),
                describePerfState());
    }

    private void recordSpiderTickPerf(long elapsedNs, long preemptNs, long superNs, long postNs, long combatNs) {
        long gameTime = this.level.getGameTime();
        if (perfTickGameTime != gameTime) {
            flushSpiderTickPerf();
            perfTickGameTime = gameTime;
            perfTickSpiderCount = 0;
            perfTickTotalNs = 0L;
            perfTickPreemptNs = 0L;
            perfTickSuperNs = 0L;
            perfTickPostNs = 0L;
            perfTickCombatNs = 0L;
            perfTickMaxNs = 0L;
            perfTickMaxEntityId = -1;
            perfTickMaxEntityPreemptNs = 0L;
            perfTickMaxEntitySuperNs = 0L;
            perfTickMaxEntityPostNs = 0L;
            perfTickMaxEntityCombatNs = 0L;
            perfTickMaxEntityState = "none";
        }

        perfTickSpiderCount++;
        perfTickTotalNs += elapsedNs;
        perfTickPreemptNs += preemptNs;
        perfTickSuperNs += superNs;
        perfTickPostNs += postNs;
        perfTickCombatNs += combatNs;
        if (elapsedNs > perfTickMaxNs) {
            perfTickMaxNs = elapsedNs;
            perfTickMaxEntityId = this.getId();
            perfTickMaxEntityPreemptNs = preemptNs;
            perfTickMaxEntitySuperNs = superNs;
            perfTickMaxEntityPostNs = postNs;
            perfTickMaxEntityCombatNs = combatNs;
            perfTickMaxEntityState = describePerfState();
        }
    }

    private static void flushSpiderTickPerf() {
        if (perfTickGameTime == Long.MIN_VALUE || perfTickTotalNs < SPIDER_PERF_LOG_THRESHOLD_NS) {
            return;
        }
        Spiders.LOGGER.info("spiders_perf_tick game_time={} spiders={} total_ms={} preempt_ms={} super_ms={} post_ms={} combat_ms={} max_ms={} max_entity_id={} max_preempt_ms={} max_super_ms={} max_post_ms={} max_combat_ms={} max_state={}",
                perfTickGameTime,
                perfTickSpiderCount,
                String.format(java.util.Locale.ROOT, "%.3f", perfTickTotalNs / 1_000_000.0D),
                String.format(java.util.Locale.ROOT, "%.3f", perfTickPreemptNs / 1_000_000.0D),
                String.format(java.util.Locale.ROOT, "%.3f", perfTickSuperNs / 1_000_000.0D),
                String.format(java.util.Locale.ROOT, "%.3f", perfTickPostNs / 1_000_000.0D),
                String.format(java.util.Locale.ROOT, "%.3f", perfTickCombatNs / 1_000_000.0D),
                String.format(java.util.Locale.ROOT, "%.3f", perfTickMaxNs / 1_000_000.0D),
                perfTickMaxEntityId,
                String.format(java.util.Locale.ROOT, "%.3f", perfTickMaxEntityPreemptNs / 1_000_000.0D),
                String.format(java.util.Locale.ROOT, "%.3f", perfTickMaxEntitySuperNs / 1_000_000.0D),
                String.format(java.util.Locale.ROOT, "%.3f", perfTickMaxEntityPostNs / 1_000_000.0D),
                String.format(java.util.Locale.ROOT, "%.3f", perfTickMaxEntityCombatNs / 1_000_000.0D),
                perfTickMaxEntityState);
    }

    private String describePerfState() {
        if (isDropAttackActive()) return "drop_attack:" + getDropAttackPhaseName();
        if (isWebLowerActive()) return "web_lower:" + getWebLowerPhaseName();
        if (isWebTrapPlacementActive()) return "web_trap:" + this.webTrapPlacementStatus;
        if (isWebShotActive()) return "web_shot:" + getWebShotPhaseName();
        if (isPounceActive()) return "pounce:" + getPouncePhaseName();
        if (isRetreatActive()) return "retreat:" + getRetreatPhaseName();
        if (isFakeRetreatActive()) return "fake_retreat:" + getFakeRetreatPhaseName();
        if (isGrabPullActive()) return "grab_pull:" + getGrabPullPhaseName();
        if (isDragNestActive()) return "drag_nest:" + getDragNestPhaseName();
        if (isThreatDisplaying()) return "threat_display:" + this.threatDisplayStatus;
        if (isLineOfSightStalking()) return "line_of_sight:" + this.lineOfSightStalkingStatus;
        if (isDarknessPreferenceActive()) return "darkness:" + this.darknessPreferenceStatus;
        if (isWallPeeking()) return "wall_peek:" + this.wallPeekStatus;
        if (isPreyInteracting()) return "prey_interaction:" + this.preyInteractionStatus;
        if (isEscapeCutting()) return "escape_cutting:" + this.escapeCuttingStatus;
        if (isPackCoordinating()) return "pack:" + getPackRoleName();
        if (isCeilingStalking()) return "ceiling_stalk";
        if (isFollowingForcedPath()) return "forced_path";
        if (this.getTarget() != null) return "targeting:" + this.getTarget().getType().toShortString();
        return "idle:" + getCombatPacingStateName();
    }

    private void preemptGrabPullBeforeBaseTick() {
        LivingEntity target = resolveActiveCombatTarget(this.getTarget());
        if (!canUseCombatPacing(target)) {
            return;
        }

        if (isGrabPullActive()) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            holdGrabPullPosition();
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            return;
        }

        int pacing = getCombatPacing();
        if (pacing != COMBAT_PACING_NONE && pacing != COMBAT_PACING_STALK) {
            return;
        }
        if (!canRunInactiveCombatStartSweep(target)) {
            return;
        }
        if (!canStartGrabPull(target)) {
            return;
        }

        resetCeilingStalk();
        resetCircleStrafe();
        resetDropAttack(false);
        resetWebShot(false);
        resetWebLower(false);
        resetPounce(false);
        resetRetreat(false);
        clearCombatPacingSpeedModifier();
        startGrabPullWindup(target);
    }

    private void preemptDragNestBeforeBaseTick() {
        LivingEntity target = resolveActiveCombatTarget(this.getTarget());
        if (!canUseCombatPacing(target) || !isDragNestActive()) {
            return;
        }

        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setSpeed(0.0F);
        this.setXxa(0.0F);
        this.setZza(0.0F);
        faceCombatTarget(target);
    }

    private void preemptThreatDisplayBeforeBaseTick() {
        LivingEntity target = resolveActiveCombatTarget(this.getTarget());
        if (!canUseCombatPacing(target)) {
            return;
        }

        if (!isThreatDisplaying()) {
            if (!canRunInactiveCombatStartSweep(target) || !canStartThreatDisplay(target)) {
                return;
            }
            startThreatDisplay(target);
        }

        holdThreatDisplay(target);
    }

    private void updateCombatPacing() {
        LivingEntity target = resolveActiveCombatTarget(this.getTarget());
        if (this.dropAttackCooldownTicks > 0) {
            this.dropAttackCooldownTicks--;
        }
        if (this.webShotCooldownTicks > 0) {
            this.webShotCooldownTicks--;
        }
        if (this.webTrapPlacementCooldownTicks > 0) {
            this.webTrapPlacementCooldownTicks--;
        }
        if (this.webLowerCooldownTicks > 0) {
            this.webLowerCooldownTicks--;
        }
        if (this.pounceCooldownTicks > 0) {
            this.pounceCooldownTicks--;
        }
        if (this.retreatCooldownTicks > 0) {
            this.retreatCooldownTicks--;
        }
        if (this.fakeRetreatCooldownTicks > 0) {
            this.fakeRetreatCooldownTicks--;
        }
        if (this.grabPullCooldownTicks > 0) {
            this.grabPullCooldownTicks--;
        }
        if (this.dragNestCooldownTicks > 0) {
            this.dragNestCooldownTicks--;
        }
        if (this.escapeCuttingCooldownTicks > 0) {
            this.escapeCuttingCooldownTicks--;
        }
        if (this.threatDisplayCooldownTicks > 0) {
            this.threatDisplayCooldownTicks--;
        }
        if (this.lineOfSightStalkingCooldownTicks > 0) {
            this.lineOfSightStalkingCooldownTicks--;
        }
        if (this.darknessPreferenceCooldownTicks > 0) {
            this.darknessPreferenceCooldownTicks--;
        }
        if (this.wallPeekCooldownTicks > 0) {
            this.wallPeekCooldownTicks--;
        }
        if (this.preyInteractionCooldownTicks > 0) {
            this.preyInteractionCooldownTicks--;
        }
        if (this.circleStrafeCooldownTicks > 0) {
            this.circleStrafeCooldownTicks--;
        }
        if (isPreyInteracting()) {
            setCombatPacing(COMBAT_PACING_NONE, 0);
            resetBackpedalTargetMemory();
            resetCeilingStalk();
            resetCircleStrafe();
            resetDropAttack(false);
            resetWebShot(false);
            resetWebTrapPlacement(false);
            resetWebLower(false);
            resetPounce(false);
            resetRetreat(false);
            resetFakeRetreat(false);
            resetGrabPull(false);
            resetDragNest(false);
            resetPackCoordination();
            resetEscapeCutting(false);
            resetThreatDisplay(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            clearPendingRetreat();
            clearCombatPacingSpeedModifier();
            if (updatePreyInteraction()) {
                return;
            }
        }
        if (!canUseCombatPacing(target)) {
            setCombatPacing(COMBAT_PACING_NONE, 0);
            this.backpedalTicks = 0;
            resetBackpedalTargetMemory();
            resetCeilingStalk();
            resetCircleStrafe();
            resetDropAttack(false);
            resetWebShot(false);
            resetWebTrapPlacement(false);
            resetWebLower(false);
            resetPounce(false);
            resetRetreat(false);
            resetFakeRetreat(false);
            resetGrabPull(false);
            resetDragNest(false);
            resetPackCoordination();
            resetEscapeCutting(false);
            resetThreatDisplay(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            resetPreyInteraction(false);
            clearPendingRetreat();
            clearCombatPacingSpeedModifier();
            return;
        }

        if (isReadableLivePlayerPressureTarget(target)) {
            resetPackCoordination();
            updateDenseLivePlayerPressure(target);
            return;
        }

        updatePackCoordination(target);
        if (hasHigherPriorityCombatStateActive()) {
            resetEscapeCutting(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            resetPreyInteraction(false);
            if (!isWebTrapPlacementActive()) {
                resetWebTrapPlacement(false);
            }
            if (!isThreatDisplaying()) {
                resetThreatDisplay(false);
            }
        }

        if (isThreatDisplaying()) {
            resetCeilingStalk();
            resetCircleStrafe();
            resetDropAttack(false);
            resetWebShot(false);
            resetWebTrapPlacement(false);
            resetWebLower(false);
            resetPounce(false);
            resetRetreat(false);
            resetFakeRetreat(false);
            resetGrabPull(false);
            resetDragNest(false);
            resetEscapeCutting(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            resetPreyInteraction(false);
            clearCombatPacingSpeedModifier();
            if (updateThreatDisplay(target)) {
                return;
            }
        }

        if (isDropAttackActive()) {
            resetCeilingStalk();
            resetCircleStrafe();
            resetWebShot(false);
            resetWebTrapPlacement(false);
            resetWebLower(false);
            resetPounce(false);
            resetRetreat(false);
            resetFakeRetreat(false);
            resetGrabPull(false);
            resetDragNest(false);
            resetEscapeCutting(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            if (updateDropAttack(target)) {
                return;
            }
        }

        if (isWebLowerActive()) {
            resetCeilingStalk();
            resetCircleStrafe();
            resetWebShot(false);
            resetWebTrapPlacement(false);
            resetPounce(false);
            resetRetreat(false);
            resetFakeRetreat(false);
            resetGrabPull(false);
            resetDragNest(false);
            resetEscapeCutting(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            if (updateWebLower(target)) {
                return;
            }
        }

        if (isWebTrapPlacementActive()) {
            resetCeilingStalk();
            resetCircleStrafe();
            resetWebShot(false);
            resetWebLower(false);
            resetPounce(false);
            resetRetreat(false);
            resetFakeRetreat(false);
            resetGrabPull(false);
            resetDragNest(false);
            resetEscapeCutting(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            if (updateWebTrapPlacement(target)) {
                return;
            }
        }

        if (isWebShotActive()) {
            resetCeilingStalk();
            resetCircleStrafe();
            resetWebLower(false);
            resetWebTrapPlacement(false);
            resetPounce(false);
            resetRetreat(false);
            resetFakeRetreat(false);
            resetGrabPull(false);
            resetDragNest(false);
            resetEscapeCutting(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            if (updateWebShot(target)) {
                return;
            }
        }

        if (isPounceActive()) {
            resetCeilingStalk();
            resetCircleStrafe();
            resetWebShot(false);
            resetWebTrapPlacement(false);
            resetWebLower(false);
            resetRetreat(false);
            resetFakeRetreat(false);
            resetGrabPull(false);
            resetDragNest(false);
            resetEscapeCutting(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            if (updatePounce(target)) {
                return;
            }
        }

        if (isRetreatActive()) {
            resetCeilingStalk();
            resetCircleStrafe();
            resetDropAttack(false);
            resetWebShot(false);
            resetWebTrapPlacement(false);
            resetWebLower(false);
            resetPounce(false);
            resetGrabPull(false);
            resetDragNest(false);
            resetEscapeCutting(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            if (updateRetreat(target)) {
                return;
            }
        }

        if (isFakeRetreatActive()) {
            resetCeilingStalk();
            resetCircleStrafe();
            resetDropAttack(false);
            resetWebShot(false);
            resetWebTrapPlacement(false);
            resetWebLower(false);
            resetPounce(false);
            resetGrabPull(false);
            resetDragNest(false);
            resetEscapeCutting(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            if (updateFakeRetreat(target)) {
                return;
            }
        }

        if (isGrabPullActive()) {
            resetCeilingStalk();
            resetCircleStrafe();
            resetDropAttack(false);
            resetWebShot(false);
            resetWebTrapPlacement(false);
            resetWebLower(false);
            resetPounce(false);
            resetEscapeCutting(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            if (updateGrabPull(target)) {
                return;
            }
        }

        if (tryStartPendingRetreat(target)) {
            resetCeilingStalk();
            resetCircleStrafe();
            resetDropAttack(false);
            resetWebShot(false);
            resetWebTrapPlacement(false);
            resetWebLower(false);
            resetPounce(false);
            resetGrabPull(false);
            resetDragNest(false);
            resetEscapeCutting(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            return;
        }

        if (isDragNestActive()) {
            resetCeilingStalk();
            resetCircleStrafe();
            resetDropAttack(false);
            resetWebShot(false);
            resetWebTrapPlacement(false);
            resetWebLower(false);
            resetPounce(false);
            resetGrabPull(false);
            resetEscapeCutting(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            if (updateDragNest(target)) {
                return;
            }
        }

        boolean targetAdvancing = isTargetAdvancingIntoBackpedalRange(target);
        rememberBackpedalTargetPosition(target);
        if (targetAdvancing) {
            this.backpedalTicks = BACKPEDAL_PHASE_TICKS;
        }
        if (this.backpedalTicks > 0) {
            resetCeilingStalk();
            resetCircleStrafe();
            resetWebShot(false);
            resetWebTrapPlacement(false);
            resetWebLower(false);
            resetPounce(false);
            resetRetreat(false);
            resetGrabPull(false);
            resetDragNest(false);
            resetEscapeCutting(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            if (applyBackpedalMovement(target)) {
                this.backpedalTicks--;
                return;
            }
            this.backpedalTicks = 0;
        }

        if (isLineOfSightStalking()) {
            resetCeilingStalk();
            resetCircleStrafe();
            resetDarknessPreference(false);
            resetWallPeek(false);
            clearCombatPacingSpeedModifier();
            if (updateLineOfSightStalking(target)) {
                return;
            }
        }

        if (isDarknessPreferenceActive()) {
            if (updateLineOfSightStalking(target)) {
                resetDarknessPreference(false);
                resetWallPeek(false);
                resetCeilingStalk();
                resetCircleStrafe();
                return;
            }
            resetCeilingStalk();
            resetCircleStrafe();
            clearCombatPacingSpeedModifier();
            if (updateDarknessPreference(target)) {
                return;
            }
        }

        if (isWallPeeking()) {
            resetCeilingStalk();
            resetCircleStrafe();
            clearCombatPacingSpeedModifier();
            if (updateWallPeek(target)) {
                return;
            }
        }

        int phase = getCombatPacing();
        if (phase == COMBAT_PACING_NONE) {
            setCombatPacing(COMBAT_PACING_STALK, STALK_PHASE_TICKS);
            phase = COMBAT_PACING_STALK;
        } else if (this.combatPacingTicks > 0) {
            this.combatPacingTicks--;
        }

        if (this.combatPacingTicks <= 0) {
            if (phase == COMBAT_PACING_STALK) {
                setCombatPacing(COMBAT_PACING_BURST, BURST_PHASE_TICKS);
                phase = COMBAT_PACING_BURST;
            } else {
                setCombatPacing(COMBAT_PACING_STALK, STALK_PHASE_TICKS);
                phase = COMBAT_PACING_STALK;
            }
        }

        faceCombatTarget(target);
        if (this.escapeCutting && updateEscapeCutting(target)) {
            resetCeilingStalk();
            resetCircleStrafe();
            return;
        }
        boolean runInactiveCombatStarts = canRunInactiveCombatStartSweep(target);
        if (phase == COMBAT_PACING_STALK) {
            if (runInactiveCombatStarts) {
                if (updateDropAttack(target)) {
                    resetCeilingStalk();
                    resetCircleStrafe();
                    resetWebShot(false);
                    resetWebTrapPlacement(false);
                    resetWebLower(false);
                    resetPounce(false);
                    resetDragNest(false);
                    resetWallPeek(false);
                    return;
                }
                if (updateWebLower(target)) {
                    resetCeilingStalk();
                    resetCircleStrafe();
                    resetWebShot(false);
                    resetWebTrapPlacement(false);
                    resetPounce(false);
                    resetRetreat(false);
                    resetDragNest(false);
                    resetWallPeek(false);
                    return;
                }
                if (updateWebTrapPlacement(target)) {
                    resetCeilingStalk();
                    resetCircleStrafe();
                    resetWebShot(false);
                    resetWebLower(false);
                    resetPounce(false);
                    resetRetreat(false);
                    resetDragNest(false);
                    resetEscapeCutting(false);
                    resetLineOfSightStalking(false);
                    resetDarknessPreference(false);
                    resetWallPeek(false);
                    return;
                }
                if (updateWebShot(target)) {
                    resetCeilingStalk();
                    resetCircleStrafe();
                    resetWebLower(false);
                    resetWebTrapPlacement(false);
                    resetPounce(false);
                    resetDragNest(false);
                    resetWallPeek(false);
                    return;
                }
                if (updatePounce(target)) {
                    resetCeilingStalk();
                    resetCircleStrafe();
                    resetRetreat(false);
                    resetDragNest(false);
                    resetWallPeek(false);
                    return;
                }
                if (updateGrabPull(target)) {
                    resetCeilingStalk();
                    resetCircleStrafe();
                    resetRetreat(false);
                    resetDragNest(false);
                    resetWallPeek(false);
                    return;
                }
                if (updateThreatDisplay(target)) {
                    resetCeilingStalk();
                    resetCircleStrafe();
                    resetRetreat(false);
                    resetDragNest(false);
                    resetEscapeCutting(false);
                    resetWallPeek(false);
                    return;
                }
                if (updateDragNest(target)) {
                    resetCeilingStalk();
                    resetCircleStrafe();
                    resetRetreat(false);
                    resetEscapeCutting(false);
                    resetWallPeek(false);
                    return;
                }
                if (updateEscapeCutting(target)) {
                    resetCeilingStalk();
                    resetCircleStrafe();
                    return;
                }
            }
            if (applyPackCoordinationRole(target)) {
                return;
            }
            if (runInactiveCombatStarts) {
                if (updateLineOfSightStalking(target)) {
                    resetCeilingStalk();
                    resetCircleStrafe();
                    resetDarknessPreference(false);
                    resetWallPeek(false);
                    return;
                }
                if (updateDarknessPreference(target)) {
                    resetCeilingStalk();
                    resetCircleStrafe();
                    resetWallPeek(false);
                    return;
                }
                if (updateWallPeek(target)) {
                    resetCeilingStalk();
                    resetCircleStrafe();
                    return;
                }
                if (canStartCeilingStalk(target) && updateCeilingStalk(target)) {
                    resetCircleStrafe();
                    return;
                }
                if (updateCircleStrafe(target)) {
                    return;
                }
            }
            applyCombatPacingSpeedModifier(STALK_SPEED_MODIFIER);
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
        } else {
            resetCeilingStalk();
            resetCircleStrafe();
            resetDropAttack(false);
            resetWebShot(false);
            resetWebTrapPlacement(false);
            resetWebLower(false);
            resetPounce(false);
            resetRetreat(false);
            resetGrabPull(false);
            resetDragNest(false);
            resetEscapeCutting(false);
            resetLineOfSightStalking(false);
            resetDarknessPreference(false);
            resetWallPeek(false);
            applyCombatPacingSpeedModifier(BURST_SPEED_MODIFIER);
        }
    }

    private LivingEntity resolveActiveCombatTarget(LivingEntity currentTarget) {
        UUID activeTargetId = null;
        if (isDropAttackActive()) {
            activeTargetId = this.dropAttackTargetId;
        } else if (isWebLowerActive()) {
            activeTargetId = this.webLowerTargetId;
        } else if (isWebTrapPlacementActive()) {
            activeTargetId = this.webTrapPlacementTargetId;
        } else if (isWebShotActive()) {
            activeTargetId = this.webShotTargetId;
        } else if (isPounceActive()) {
            activeTargetId = this.pounceTargetId;
        } else if (isRetreatActive()) {
            activeTargetId = this.retreatTargetId;
        } else if (isFakeRetreatActive()) {
            activeTargetId = this.fakeRetreatTargetId;
        } else if (isGrabPullActive()) {
            activeTargetId = this.grabPullTargetId;
        } else if (isDragNestActive()) {
            activeTargetId = this.dragNestTargetId;
        } else if (isThreatDisplaying()) {
            activeTargetId = this.threatDisplayTargetId;
        } else if (isLineOfSightStalking()) {
            activeTargetId = this.lineOfSightStalkingTargetId;
        } else if (isDarknessPreferenceActive()) {
            activeTargetId = this.darknessPreferenceTargetId;
        } else if (isWallPeeking()) {
            activeTargetId = this.wallPeekTargetId;
        } else if (this.escapeCutting) {
            activeTargetId = this.escapeCuttingTargetId;
        }

        if (activeTargetId == null) {
            return currentTarget;
        }

        LivingEntity activeTarget = findLivingEntityByUuid(activeTargetId);
        if (activeTarget != null) {
            if (currentTarget != activeTarget) {
                this.setTarget(activeTarget);
            }
            return activeTarget;
        }
        return currentTarget;
    }

    private LivingEntity findLivingEntityByUuid(UUID uuid) {
        if (uuid == null || !(this.level instanceof ServerLevel serverLevel)) {
            return null;
        }

        Entity entity = serverLevel.getEntity(uuid);
        return entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved()
                ? living
                : null;
    }

    private boolean updateCeilingStalk(LivingEntity target) {
        BlockPos anchor = findCeilingStalkAnchor(target);
        if (anchor == null) {
            resetCeilingStalk();
            return false;
        }

        this.ceilingStalking = true;
        this.ceilingStalkAnchor = anchor;
        clearCombatPacingSpeedModifier();

        Vec3 anchorPosition = AttachmentHelper.anchorFor(this, anchor, Direction.UP);
        if (this.getAttachmentDirection() == Direction.UP
                && this.position().distanceToSqr(anchorPosition) <= CEILING_STALK_HOLD_DISTANCE_SQR) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            return true;
        }

        if (this.ceilingStalkRepathTicks > 0) {
            this.ceilingStalkRepathTicks--;
        }
        boolean anchorChanged = !anchor.equals(this.lastCeilingStalkPathAnchor);
        if (anchorChanged || this.ceilingStalkRepathTicks <= 0 || this.getNavigation().isDone()) {
            Path path = this.getNavigation().createPath(anchor, 0);
            if (path == null) {
                resetCeilingStalk();
                return false;
            }
            this.getNavigation().moveTo(path, CEILING_STALK_NAVIGATION_SPEED);
            this.lastCeilingStalkPathAnchor = anchor;
            this.ceilingStalkRepathTicks = CEILING_STALK_REPATH_TICKS;
        }
        return true;
    }

    private BlockPos findCeilingStalkAnchor(LivingEntity target) {
        Vec3 behind = horizontalBehindDirection(target);
        Vec3 desired = target.position().add(behind.scale(CEILING_STALK_BEHIND_BLOCKS));
        BlockPos targetBlock = target.blockPosition();
        int minY = Mth.floor(target.getBoundingBox().maxY) + 1;
        int maxY = minY + CEILING_STALK_MAX_OVERHEAD_BLOCKS;

        BlockPos best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int y = minY; y <= maxY; y++) {
            for (int dx = -CEILING_STALK_HORIZONTAL_SEARCH; dx <= CEILING_STALK_HORIZONTAL_SEARCH; dx++) {
                for (int dz = -CEILING_STALK_HORIZONTAL_SEARCH; dz <= CEILING_STALK_HORIZONTAL_SEARCH; dz++) {
                    BlockPos candidate = new BlockPos(targetBlock.getX() + dx, y, targetBlock.getZ() + dz);
                    if (!AttachmentHelper.hasSupport(this.level, candidate, Direction.UP)
                            || !AttachmentHelper.aabbFitsOnSurface(this.level, this, candidate, Direction.UP)) {
                        continue;
                    }

                    double centerX = candidate.getX() + 0.5D;
                    double centerZ = candidate.getZ() + 0.5D;
                    double horizontalScore = (centerX - desired.x) * (centerX - desired.x)
                            + (centerZ - desired.z) * (centerZ - desired.z);
                    double verticalScore = Math.abs((candidate.getY() + 0.5D) - (target.getY() + 3.5D)) * 0.15D;
                    double currentDistanceScore = this.position().distanceToSqr(AttachmentHelper.anchorFor(this, candidate, Direction.UP)) * 0.02D;
                    double score = horizontalScore + verticalScore + currentDistanceScore;
                    if (score < bestScore) {
                        best = candidate;
                        bestScore = score;
                    }
                }
            }
        }
        return best;
    }

    private Vec3 horizontalBehindDirection(LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() <= 1.0E-6D) {
            horizontal = this.position().subtract(target.position());
            horizontal = new Vec3(horizontal.x, 0.0D, horizontal.z);
        }
        if (horizontal.lengthSqr() <= 1.0E-6D) {
            return new Vec3(-1.0D, 0.0D, 0.0D);
        }
        return horizontal.normalize().scale(-1.0D);
    }

    private void resetCeilingStalk() {
        this.ceilingStalking = false;
        this.ceilingStalkAnchor = null;
        this.lastCeilingStalkPathAnchor = null;
        this.ceilingStalkRepathTicks = 0;
    }

    private boolean updateCircleStrafe(LivingEntity target) {
        if (!canUseCircleStrafe(target)) {
            resetCircleStrafe();
            return false;
        }

        UUID targetId = target.getUUID();
        if (!targetId.equals(this.circleStrafeTargetId)) {
            if (this.circleStrafeCooldownTicks > 0) {
                resetCircleStrafe();
                return false;
            }
            this.circleStrafeClockwise = !this.circleStrafeClockwise;
            this.circleStrafeTicks = CIRCLE_STRAFE_PHASE_TICKS;
            this.circleStrafeTargetId = targetId;
        } else if (this.circleStrafeTicks <= 0) {
            this.circleStrafeCooldownTicks = CIRCLE_STRAFE_COOLDOWN_TICKS;
            resetCircleStrafe();
            return false;
        }

        Vec3 radial = this.position().subtract(target.position());
        radial = new Vec3(radial.x, 0.0D, radial.z);
        double distance = radial.length();
        if (distance <= 1.0E-6D) {
            resetCircleStrafe();
            return false;
        }

        Vec3 radialUnit = radial.scale(1.0D / distance);
        Vec3 tangent = this.circleStrafeClockwise
                ? new Vec3(-radialUnit.z, 0.0D, radialUnit.x)
                : new Vec3(radialUnit.z, 0.0D, -radialUnit.x);
        double radialError = distance - CIRCLE_STRAFE_IDEAL_DISTANCE;
        Vec3 correction = radialUnit.scale(-radialError * CIRCLE_STRAFE_RADIAL_CORRECTION);
        Vec3 desired = tangent.add(correction);
        if (desired.lengthSqr() <= CIRCLE_STRAFE_MIN_STEP_SQR) {
            desired = tangent;
        }

        Vec3 step = desired.normalize().scale(CIRCLE_STRAFE_SPEED);
        if (!canTakeCircleStrafeStep(step)) {
            resetCircleStrafe();
            return false;
        }

        clearCombatPacingSpeedModifier();
        this.getNavigation().stop();
        this.setDeltaMovement(step);
        this.move(MoverType.SELF, step);
        this.setSpeed((float) CIRCLE_STRAFE_SPEED);
        this.setXxa((float) (this.circleStrafeClockwise ? 0.65D : -0.65D));
        this.setZza(0.2F);
        faceCombatTarget(target);
        this.circleStrafeTicks--;
        return true;
    }

    private boolean canUseCircleStrafe(LivingEntity target) {
        if (this.getAttachmentDirection() != Direction.DOWN) {
            return false;
        }
        double distanceSqr = this.distanceToSqr(target);
        return distanceSqr >= CIRCLE_STRAFE_MIN_DISTANCE_SQR
                && distanceSqr <= CIRCLE_STRAFE_MAX_DISTANCE_SQR;
    }

    private boolean canTakeCircleStrafeStep(Vec3 step) {
        if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
            return false;
        }
        BlockPos nextBlock = new BlockPos(this.position().add(step));
        return AttachmentHelper.hasSupport(this.level, nextBlock, Direction.DOWN)
                && AttachmentHelper.aabbFitsOnSurface(this.level, this, nextBlock, Direction.DOWN);
    }

    private void resetCircleStrafe() {
        this.circleStrafeTicks = 0;
        this.circleStrafeTargetId = null;
    }

    private boolean updateDropAttack(LivingEntity target) {
        int phase = getDropAttackPhase();
        if (phase == DROP_ATTACK_NONE) {
            if (!canStartDropAttack(target)) {
                return false;
            }
            startDropAttackWindup(target);
            return true;
        }

        if (this.dropAttackTargetId == null || !this.dropAttackTargetId.equals(target.getUUID())) {
            resetDropAttack(false);
            return false;
        }

        if (phase == DROP_ATTACK_WINDUP) {
            if (this.getAttachmentDirection() != Direction.UP) {
                resetDropAttack(false);
                return false;
            }
            this.getNavigation().stop();
            this.setNoGravity(true);
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            faceCombatTarget(target);
            this.dropAttackTicks--;
            if (this.dropAttackTicks <= 0) {
                commitDropAttack(target);
            }
            return true;
        }

        if (phase == DROP_ATTACK_DROPPING) {
            this.noPhysics = false;
            this.setNoGravity(false);
            this.setAttachmentDirection(Direction.DOWN);
            faceCombatTarget(target);
            applyDropAttackMovement(target);
            trySpendDropAttackDamage(target);
            this.dropAttackTicks--;
            if (hasDropAttackLanded() || this.dropAttackTicks <= 0) {
                if (!this.dropAttackDamageSpent) {
                    queueRetreat(target, false, true);
                }
                startDropAttackRecovery();
            }
            return true;
        }

        if (phase == DROP_ATTACK_RECOVERING) {
            this.noPhysics = false;
            this.setNoGravity(false);
            this.setAttachmentDirection(Direction.DOWN);
            this.getNavigation().stop();
            faceCombatTarget(target);
            if (AttachmentHelper.hasSupport(this.level, this.blockPosition(), Direction.DOWN)) {
                this.setDeltaMovement(Vec3.ZERO);
                this.setSpeed(0.0F);
                this.setXxa(0.0F);
                this.setZza(0.0F);
            }
            this.dropAttackTicks--;
            if (this.dropAttackTicks <= 0) {
                resetDropAttack(false);
                tryStartPendingRetreat(target);
            }
            return true;
        }

        resetDropAttack(false);
        return false;
    }

    private boolean canStartDropAttack(LivingEntity target) {
        if (this.dropAttackCooldownTicks > 0 || this.getAttachmentDirection() != Direction.UP) {
            return false;
        }
        if (!(target instanceof Player) && !target.getTags().contains(DROP_ATTACK_TEST_TARGET_TAG)) {
            return false;
        }

        Vec3 toTarget = target.position().subtract(this.position());
        double verticalDrop = -toTarget.y;
        if (verticalDrop < DROP_ATTACK_MIN_VERTICAL || verticalDrop > DROP_ATTACK_MAX_VERTICAL) {
            return false;
        }

        double horizontalSqr = toTarget.x * toTarget.x + toTarget.z * toTarget.z;
        return horizontalSqr <= DROP_ATTACK_TRIGGER_HORIZONTAL_SQR;
    }

    private void startDropAttackWindup(LivingEntity target) {
        this.dropAttackTargetId = target.getUUID();
        this.dropAttackDamageSpent = false;
        setDropAttackPhase(DROP_ATTACK_WINDUP, DROP_ATTACK_WINDUP_TICKS);
        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.playSound(SoundEvents.SPIDER_AMBIENT, 0.45F, 0.65F);
        faceCombatTarget(target);
    }

    private void commitDropAttack(LivingEntity target) {
        setDropAttackPhase(DROP_ATTACK_DROPPING, DROP_ATTACK_COMMIT_TICKS);
        this.setAttachmentDirection(Direction.DOWN);
        this.setNoGravity(false);
        this.noPhysics = false;
        this.fallDistance = 0.0F;
        applyDropAttackMovement(target);
    }

    private void applyDropAttackMovement(LivingEntity target) {
        Vec3 toTarget = target.position().add(0.0D, target.getBbHeight() * 0.25D, 0.0D).subtract(this.position());
        Vec3 horizontal = new Vec3(toTarget.x, 0.0D, toTarget.z);
        Vec3 horizontalStep = Vec3.ZERO;
        if (horizontal.lengthSqr() > 1.0E-6D) {
            horizontalStep = horizontal.normalize().scale(DROP_ATTACK_HORIZONTAL_SPEED);
        }

        double fallSpeed = Mth.clamp(-toTarget.y * 0.22D,
                DROP_ATTACK_MIN_FALL_SPEED, DROP_ATTACK_MAX_FALL_SPEED);
        Vec3 step = new Vec3(horizontalStep.x, -fallSpeed, horizontalStep.z);
        if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
            step = new Vec3(horizontalStep.x * 0.45D, -Math.min(fallSpeed, DROP_ATTACK_MIN_FALL_SPEED), horizontalStep.z * 0.45D);
        }

        this.getNavigation().stop();
        this.setDeltaMovement(step);
        this.move(MoverType.SELF, step);
        this.setSpeed((float) Math.sqrt(step.x * step.x + step.z * step.z));
        this.setXxa(0.0F);
        this.setZza(0.85F);
    }

    private void trySpendDropAttackDamage(LivingEntity target) {
        if (this.dropAttackDamageSpent || !target.isAlive() || this.distanceToSqr(target) > DROP_ATTACK_DAMAGE_RANGE_SQR) {
            return;
        }

        float damage = (float) Math.max(DROP_ATTACK_DAMAGE, this.getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (!canSpendDenseLivePlayerDamage(target)) {
            return;
        }
        if (target.hurt(DamageSource.mobAttack(this), damage)) {
            this.dropAttackDamageSpent = true;
            maybeStartPreyInteractionAfterDamage(target);
        }
    }

    private boolean hasDropAttackLanded() {
        return this.onGround
                || (this.getDeltaMovement().y <= 0.0D
                && AttachmentHelper.hasSupport(this.level, this.blockPosition(), Direction.DOWN));
    }

    private void startDropAttackRecovery() {
        setDropAttackPhase(DROP_ATTACK_RECOVERING, DROP_ATTACK_RECOVERY_TICKS);
        this.dropAttackCooldownTicks = Math.max(this.dropAttackCooldownTicks, DROP_ATTACK_COOLDOWN_TICKS);
        this.setAttachmentDirection(Direction.DOWN);
        this.setNoGravity(false);
        this.fallDistance = 0.0F;
    }

    private void resetDropAttack(boolean clearCooldown) {
        setDropAttackPhase(DROP_ATTACK_NONE, 0);
        this.dropAttackTargetId = null;
        this.dropAttackDamageSpent = false;
        if (clearCooldown) {
            this.dropAttackCooldownTicks = 0;
        }
    }

    private boolean updateWebShot(LivingEntity target) {
        int phase = getWebShotPhase();
        if (phase == WEB_SHOT_NONE) {
            if (!canStartWebShot(target)) {
                return false;
            }
            startWebShotWindup(target);
            return true;
        }

        if (this.webShotTargetId == null || !this.webShotTargetId.equals(target.getUUID())) {
            resetWebShot(false);
            return false;
        }

        if (phase == WEB_SHOT_WINDUP) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            faceCombatTarget(target);
            this.webShotTicks--;
            if (this.webShotTicks <= 0) {
                fireWebShot(target);
                startWebShotRecovery();
            }
            return true;
        }

        if (phase == WEB_SHOT_RECOVERING) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            faceCombatTarget(target);
            this.webShotTicks--;
            if (this.webShotTicks <= 0) {
                resetWebShot(false);
            }
            return true;
        }

        resetWebShot(false);
        return false;
    }

    private boolean canStartWebShot(LivingEntity target) {
        if (this.webShotCooldownTicks > 0 || this.pounceCooldownTicks > 0 || isDropAttackActive() || isWebLowerActive()) {
            return false;
        }
        if (!(target instanceof Player) && !target.getTags().contains(WEB_SHOT_TEST_TARGET_TAG)) {
            return false;
        }
        if (isDenseLivePlayerCombatTarget(target) && !target.getTags().contains(WEB_SHOT_TEST_TARGET_TAG)) {
            return false;
        }
        double distanceSqr = this.distanceToSqr(target);
        return distanceSqr >= WEB_SHOT_MIN_RANGE_SQR
                && distanceSqr <= WEB_SHOT_MAX_RANGE_SQR
                && this.hasLineOfSight(target);
    }

    private void startWebShotWindup(LivingEntity target) {
        this.webShotTargetId = target.getUUID();
        this.webShotFired = false;
        setWebShotPhase(WEB_SHOT_WINDUP, WEB_SHOT_WINDUP_TICKS);
        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setDeltaMovement(Vec3.ZERO);
        this.playSound(SoundEvents.SPIDER_AMBIENT, 0.35F, 1.35F);
        faceCombatTarget(target);
    }

    private void fireWebShot(LivingEntity target) {
        this.webShotFired = true;
        if (this.level.isClientSide) {
            return;
        }

        Vec3 toTarget = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D)
                .subtract(this.position().add(0.0D, this.getBbHeight() * 0.62D, 0.0D));
        Vec3 muzzleOffset = toTarget.lengthSqr() <= 1.0E-6D
                ? Vec3.ZERO
                : toTarget.normalize().scale(0.55D);
        double startX = this.getX() + muzzleOffset.x;
        double startY = this.getY(0.62D) + muzzleOffset.y;
        double startZ = this.getZ() + muzzleOffset.z;

        WebShotEntity projectile = new WebShotEntity(this.level, this);
        projectile.setPos(startX, startY, startZ);

        double dx = target.getX() - startX;
        double dy = target.getY(0.55D) - startY;
        double dz = target.getZ() - startZ;
        projectile.shoot(dx, dy, dz, WEB_SHOT_PROJECTILE_SPEED, WEB_SHOT_PROJECTILE_INACCURACY);
        this.level.addFreshEntity(projectile);
        this.playSound(SoundEvents.SPIDER_AMBIENT, 0.55F, 0.55F);
    }

    private void startWebShotRecovery() {
        setWebShotPhase(WEB_SHOT_RECOVERING, WEB_SHOT_RECOVERY_TICKS);
        this.webShotCooldownTicks = Math.max(this.webShotCooldownTicks, WEB_SHOT_COOLDOWN_TICKS);
    }

    private void resetWebShot(boolean clearCooldown) {
        setWebShotPhase(WEB_SHOT_NONE, 0);
        this.webShotTargetId = null;
        this.webShotFired = false;
        if (clearCooldown) {
            this.webShotCooldownTicks = 0;
        }
    }

    private boolean updateWebTrapPlacement(LivingEntity target) {
        if (!isWebTrapPlacementActive()) {
            if (!canStartWebTrapPlacement(target)) {
                return false;
            }
            WebTrapPlacementCandidate candidate = findWebTrapPlacementCandidate(target);
            if (candidate == null) {
                this.webTrapPlacementStatus = "no_candidate";
                return false;
            }
            startWebTrapPlacement(target, candidate);
            return true;
        }

        if (this.webTrapPlacementTargetId == null || !this.webTrapPlacementTargetId.equals(target.getUUID())) {
            resetWebTrapPlacement(false);
            return false;
        }

        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setDeltaMovement(Vec3.ZERO);
        this.setSpeed(0.0F);
        this.setXxa(0.0F);
        this.setZza(0.0F);
        this.webTrapPlacementTargetRetained = this.getTarget() == target;
        updateWebTrapPlacementTargetDistance(target);
        faceCombatTarget(target);
        if (isFacingCombatTarget(target, WEB_TRAP_PLACEMENT_FACING_DEGREES)) {
            this.webTrapPlacementFacingTicks++;
        }
        this.webTrapPlacementStatus = this.webTrapPlacementPlacedCount > 0 ? "holding" : "failed";
        this.webTrapPlacementTicks--;
        if (this.webTrapPlacementTicks <= 0) {
            finishWebTrapPlacement();
        }
        return true;
    }

    private boolean canStartWebTrapPlacement(LivingEntity target) {
        if (!canUseCombatPacing(target)) {
            this.webTrapPlacementStatus = "combat_unavailable";
            return false;
        }
        if (this.webTrapPlacementCooldownTicks > 0) {
            this.webTrapPlacementStatus = "cooldown";
            return false;
        }
        if (isDropAttackActive()
                || isWebLowerActive()
                || isWebShotActive()
                || isPounceActive()
                || isRetreatActive()
                || isFakeRetreatActive()
                || isGrabPullActive()
                || isDragNestActive()
                || isThreatDisplaying()
                || isLineOfSightStalking()
                || isDarknessPreferenceActive()
                || this.escapeCutting
                || isPackCoordinating()
                || this.backpedalTicks > 0
                || this.isFollowingForcedPath()) {
            this.webTrapPlacementStatus = "higher_priority_active";
            return false;
        }
        if (!(target instanceof Player) && !target.getTags().contains(WEB_TRAP_PLACEMENT_TEST_TARGET_TAG)) {
            this.webTrapPlacementStatus = "target_ineligible";
            return false;
        }
        if (isDenseLivePlayerCombatTarget(target) && !target.getTags().contains(WEB_TRAP_PLACEMENT_TEST_TARGET_TAG)) {
            this.webTrapPlacementStatus = "dense_live_player";
            return false;
        }
        if (!this.hasLineOfSight(target)) {
            this.webTrapPlacementStatus = "no_line_of_sight";
            return false;
        }

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr < WEB_TRAP_PLACEMENT_MIN_RANGE_SQR || distanceSqr > WEB_TRAP_PLACEMENT_MAX_RANGE_SQR) {
            this.webTrapPlacementStatus = "range";
            return false;
        }
        this.webTrapPlacementStatus = "eligible";
        return true;
    }

    private WebTrapPlacementCandidate findWebTrapPlacementCandidate(LivingEntity target) {
        Direction routeDirection = horizontalRouteDirection(target);
        BlockPos targetBlock = target.blockPosition();
        BlockPos behind = firstValidWebTrapCell(target, targetBlock,
                new BlockPos[] {
                        targetBlock.relative(routeDirection.getOpposite()),
                        targetBlock.relative(routeDirection.getOpposite(), 2)
                });
        BlockPos clockwise = firstValidWebTrapCell(target, targetBlock,
                new BlockPos[] {
                        targetBlock.relative(routeDirection.getClockWise()),
                        targetBlock.relative(routeDirection.getOpposite()).relative(routeDirection.getClockWise())
                });
        BlockPos counterClockwise = firstValidWebTrapCell(target, targetBlock,
                new BlockPos[] {
                        targetBlock.relative(routeDirection.getCounterClockWise()),
                        targetBlock.relative(routeDirection.getOpposite()).relative(routeDirection.getCounterClockWise())
                });

        BlockPos side = chooseBetterWebTrapSide(target, clockwise, counterClockwise);
        if (behind == null && side == null) {
            return null;
        }
        return new WebTrapPlacementCandidate(behind, side, routeDirection);
    }

    private BlockPos firstValidWebTrapCell(LivingEntity target, BlockPos targetBlock, BlockPos[] candidates) {
        for (BlockPos candidate : candidates) {
            if (!candidate.equals(targetBlock) && isValidWebTrapCell(target, candidate)) {
                return candidate.immutable();
            }
        }
        return null;
    }

    private BlockPos chooseBetterWebTrapSide(LivingEntity target, BlockPos first, BlockPos second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        double firstScore = this.position().distanceToSqr(Vec3.atCenterOf(first))
                + target.position().distanceToSqr(Vec3.atCenterOf(first)) * 0.15D;
        double secondScore = this.position().distanceToSqr(Vec3.atCenterOf(second))
                + target.position().distanceToSqr(Vec3.atCenterOf(second)) * 0.15D;
        return firstScore <= secondScore ? first : second;
    }

    private boolean isValidWebTrapCell(LivingEntity target, BlockPos pos) {
        if (pos.getY() <= this.level.getMinBuildHeight() || pos.getY() >= this.level.getMaxBuildHeight() - 1) {
            return false;
        }
        if (!this.level.getBlockState(pos).isAir() || !this.level.getBlockState(pos.above()).isAir()) {
            return false;
        }
        if (!hasStandingSupport(pos.below())) {
            return false;
        }

        AABB trapBox = new AABB(pos);
        return !trapBox.intersects(target.getBoundingBox())
                && !trapBox.intersects(this.getBoundingBox().inflate(0.05D));
    }

    private Direction horizontalRouteDirection(LivingEntity target) {
        Vec3 horizontal = horizontalFacingDirection(target);
        if (horizontal.lengthSqr() <= 1.0E-6D) {
            horizontal = target.position().subtract(this.position());
            horizontal = new Vec3(horizontal.x, 0.0D, horizontal.z);
        }
        if (horizontal.lengthSqr() <= 1.0E-6D) {
            return Direction.SOUTH;
        }
        return Math.abs(horizontal.x) >= Math.abs(horizontal.z)
                ? horizontal.x >= 0.0D ? Direction.EAST : Direction.WEST
                : horizontal.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private void startWebTrapPlacement(LivingEntity target, WebTrapPlacementCandidate candidate) {
        this.webTrapPlacement = true;
        this.webTrapPlacementTicks = WEB_TRAP_PLACEMENT_TICKS;
        this.webTrapPlacementTargetId = target.getUUID();
        this.webTrapPlacementAnchor = null;
        this.webTrapPlacementRouteDirection = candidate.routeDirection;
        this.webTrapPlacementPlacedCount = 0;
        this.webTrapPlacementPlacedBehind = false;
        this.webTrapPlacementPlacedBeside = false;
        this.webTrapPlacementTargetRetained = this.getTarget() == target;
        this.webTrapPlacementFacingTicks = 0;
        this.webTrapPlacementStartTargetDistance = Math.sqrt(this.distanceToSqr(target));
        this.webTrapPlacementCurrentTargetDistance = this.webTrapPlacementStartTargetDistance;
        this.webTrapPlacementStatus = "placing";

        boolean placedThread = placeWebTrapThread(candidate);
        if (!placedThread) {
            placeWebTrapBlock(candidate.behind, true);
            placeWebTrapBlock(candidate.side, false);
        }
        if (this.webTrapPlacementPlacedCount <= 0) {
            this.webTrapPlacementStatus = "placement_failed";
            resetWebTrapPlacement(false);
            return;
        }

        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setDeltaMovement(Vec3.ZERO);
        this.setSpeed(0.0F);
        this.setXxa(0.0F);
        this.setZza(0.0F);
        this.playSound(SoundEvents.SPIDER_AMBIENT, 0.45F, 0.65F);
        faceCombatTarget(target);
    }

    private boolean placeWebTrapThread(WebTrapPlacementCandidate candidate) {
        if (candidate.behind == null || candidate.side == null || this.level.isClientSide) {
            return false;
        }

        boolean placed = spinSingleThreadWeb(candidate.behind, candidate.side);
        if (!placed) {
            return false;
        }

        this.webTrapPlacementAnchor = candidate.behind.immutable();
        this.webTrapPlacementPlacedBehind = this.level.getBlockState(candidate.behind).is(BlockRegistry.SINGLE_THREAD_WEB.get());
        this.webTrapPlacementPlacedBeside = this.level.getBlockState(candidate.side).is(BlockRegistry.SINGLE_THREAD_WEB.get());
        this.webTrapPlacementPlacedCount = 0;
        for (BlockPos pos : SingleThreadWebBlock.positionsBetween(candidate.behind, candidate.side)) {
            if (this.level.getBlockState(pos).is(BlockRegistry.SINGLE_THREAD_WEB.get())) {
                this.webTrapPlacementPlacedCount++;
            }
        }
        return this.webTrapPlacementPlacedBehind && this.webTrapPlacementPlacedBeside
                && this.webTrapPlacementPlacedCount > 0;
    }

    private void placeWebTrapBlock(BlockPos pos, boolean behind) {
        if (pos == null || this.level.isClientSide || !this.level.getBlockState(pos).isAir()) {
            return;
        }
        this.level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
        if (this.webTrapPlacementAnchor == null) {
            this.webTrapPlacementAnchor = pos.immutable();
        }
        this.webTrapPlacementPlacedCount++;
        if (behind) {
            this.webTrapPlacementPlacedBehind = true;
        } else {
            this.webTrapPlacementPlacedBeside = true;
        }
    }

    private void updateWebTrapPlacementTargetDistance(LivingEntity target) {
        this.webTrapPlacementCurrentTargetDistance = Math.sqrt(this.distanceToSqr(target));
    }

    private void finishWebTrapPlacement() {
        this.webTrapPlacementCooldownTicks =
                Math.max(this.webTrapPlacementCooldownTicks, WEB_TRAP_PLACEMENT_COOLDOWN_TICKS);
        resetWebTrapPlacement(false);
    }

    private void resetWebTrapPlacement(boolean clearCooldown) {
        this.webTrapPlacement = false;
        this.webTrapPlacementTicks = 0;
        this.webTrapPlacementTargetId = null;
        this.webTrapPlacementStatus = "idle";
        if (clearCooldown) {
            this.webTrapPlacementCooldownTicks = 0;
            this.webTrapPlacementAnchor = null;
            this.webTrapPlacementRouteDirection = null;
            this.webTrapPlacementPlacedCount = 0;
            this.webTrapPlacementPlacedBehind = false;
            this.webTrapPlacementPlacedBeside = false;
            this.webTrapPlacementTargetRetained = false;
            this.webTrapPlacementFacingTicks = 0;
            this.webTrapPlacementStartTargetDistance = 0.0D;
            this.webTrapPlacementCurrentTargetDistance = 0.0D;
        }
    }

    private static final class WebTrapPlacementCandidate {
        private final BlockPos behind;
        private final BlockPos side;
        private final Direction routeDirection;

        private WebTrapPlacementCandidate(BlockPos behind, BlockPos side, Direction routeDirection) {
            this.behind = behind;
            this.side = side;
            this.routeDirection = routeDirection;
        }
    }

    private boolean updateWebLower(LivingEntity target) {
        int phase = getWebLowerPhase();
        if (phase == WEB_LOWER_NONE) {
            if (!canStartWebLower(target)) {
                return false;
            }
            startWebLowerWindup(target);
            return true;
        }

        if (this.webLowerTargetId == null || !this.webLowerTargetId.equals(target.getUUID())) {
            resetWebLower(false);
            return false;
        }

        if (phase == WEB_LOWER_WINDUP) {
            if (this.getAttachmentDirection() != Direction.UP) {
                resetWebLower(false);
                return false;
            }
            this.getNavigation().stop();
            this.setNoGravity(true);
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            faceCombatTarget(target);
            this.webLowerTicks--;
            if (this.webLowerTicks <= 0) {
                startWebLowerDescent();
            }
            return true;
        }

        if (phase == WEB_LOWER_LOWERING) {
            this.noPhysics = false;
            this.setNoGravity(true);
            this.fallDistance = 0.0F;
            this.setAttachmentDirection(Direction.DOWN);
            faceCombatTarget(target);
            boolean descended = applyWebLowerMovement(target);
            this.webLowerTicks--;
            if (hasWebLowerReachedRecoveryHeight(target) || this.webLowerTicks <= 0 || !descended) {
                startWebLowerRecovery();
            }
            return true;
        }

        if (phase == WEB_LOWER_RECOVERING) {
            this.noPhysics = false;
            this.setAttachmentDirection(Direction.DOWN);
            if (AttachmentHelper.hasSupport(this.level, this.blockPosition(), Direction.DOWN)) {
                this.setNoGravity(false);
            }
            this.getNavigation().stop();
            faceCombatTarget(target);
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            this.fallDistance = 0.0F;
            this.webLowerTicks--;
            if (this.webLowerTicks <= 0) {
                resetWebLower(false);
            }
            return true;
        }

        resetWebLower(false);
        return false;
    }

    private boolean canStartWebLower(LivingEntity target) {
        if (this.webLowerCooldownTicks > 0
                || this.getAttachmentDirection() != Direction.UP
                || isDropAttackActive()
                || isWebShotActive()
                || isPounceActive()
                || isRetreatActive()) {
            return false;
        }
        if (!(target instanceof Player) && !target.getTags().contains(WEB_LOWER_TEST_TARGET_TAG)) {
            return false;
        }
        if (canStartDropAttack(target)) {
            return false;
        }

        Vec3 toTarget = target.position().subtract(this.position());
        double verticalDrop = -toTarget.y;
        if (verticalDrop < WEB_LOWER_MIN_VERTICAL || verticalDrop > WEB_LOWER_MAX_VERTICAL) {
            return false;
        }

        double horizontalSqr = toTarget.x * toTarget.x + toTarget.z * toTarget.z;
        return horizontalSqr >= WEB_LOWER_MIN_HORIZONTAL_SQR
                && horizontalSqr <= WEB_LOWER_TRIGGER_HORIZONTAL_SQR
                && this.hasLineOfSight(target);
    }

    private void startWebLowerWindup(LivingEntity target) {
        this.webLowerTargetId = target.getUUID();
        this.webLowerStartY = this.getY();
        this.webLowerLowestY = this.getY();
        this.webLowerStrandAnchor = this.blockPosition().relative(Direction.UP);
        setWebLowerPhase(WEB_LOWER_WINDUP, WEB_LOWER_WINDUP_TICKS);
        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.playSound(SoundEvents.SPIDER_AMBIENT, 0.35F, 0.45F);
        faceCombatTarget(target);
    }

    private void startWebLowerDescent() {
        setWebLowerPhase(WEB_LOWER_LOWERING, WEB_LOWER_LOWERING_TICKS);
        this.setAttachmentDirection(Direction.DOWN);
        this.setNoGravity(true);
        this.noPhysics = false;
        this.fallDistance = 0.0F;
    }

    private boolean applyWebLowerMovement(LivingEntity target) {
        Vec3 step = new Vec3(0.0D, -WEB_LOWER_DESCENT_SPEED, 0.0D);
        if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
            step = new Vec3(0.0D, -WEB_LOWER_DESCENT_SPEED * 0.5D, 0.0D);
            if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
                return false;
            }
        }

        this.getNavigation().stop();
        this.setDeltaMovement(step);
        this.move(MoverType.SELF, step);
        this.webLowerLowestY = Math.min(this.webLowerLowestY, this.getY());
        this.setSpeed((float) Math.sqrt(step.x * step.x + step.z * step.z));
        this.setXxa(0.0F);
        this.setZza(0.32F);
        this.fallDistance = 0.0F;
        return true;
    }

    private boolean hasWebLowerReachedRecoveryHeight(LivingEntity target) {
        return AttachmentHelper.hasSupport(this.level, this.blockPosition(), Direction.DOWN)
                || this.getY() <= target.getY() + WEB_LOWER_RECOVERY_HEIGHT;
    }

    private void startWebLowerRecovery() {
        setWebLowerPhase(WEB_LOWER_RECOVERING, WEB_LOWER_RECOVERY_TICKS);
        this.webLowerCooldownTicks = Math.max(this.webLowerCooldownTicks, WEB_LOWER_COOLDOWN_TICKS);
        this.setAttachmentDirection(Direction.DOWN);
        this.setNoGravity(!AttachmentHelper.hasSupport(this.level, this.blockPosition(), Direction.DOWN));
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
    }

    private void resetWebLower(boolean clearCooldown) {
        setWebLowerPhase(WEB_LOWER_NONE, 0);
        this.webLowerTargetId = null;
        if (clearCooldown) {
            this.webLowerCooldownTicks = 0;
        }
    }

    private boolean updatePounce(LivingEntity target) {
        int phase = getPouncePhase();
        if (phase == POUNCE_NONE) {
            if (!canStartPounce(target)) {
                return false;
            }
            startPounceWindup(target);
            return true;
        }

        if (this.pounceTargetId == null || !this.pounceTargetId.equals(target.getUUID())) {
            resetPounce(false);
            return false;
        }

        if (phase == POUNCE_WINDUP) {
            if (this.getAttachmentDirection() == Direction.UP) {
                resetPounce(false);
                return false;
            }
            this.getNavigation().stop();
            this.setNoGravity(this.getAttachmentDirection() != Direction.DOWN);
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            faceCombatTarget(target);
            this.pounceTicks--;
            if (this.pounceTicks <= 0) {
                commitPounce(target);
            }
            return true;
        }

        if (phase == POUNCE_LEAPING) {
            this.noPhysics = false;
            this.setNoGravity(false);
            this.setAttachmentDirection(Direction.DOWN);
            this.getNavigation().stop();
            faceCombatTarget(target);
            applyPounceMovement();
            trySpendPounceDamage(target);
            this.pounceTicks--;
            if (this.pounceDamageSpent || this.pounceTicks <= 0) {
                if (!this.pounceDamageSpent) {
                    queueRetreat(target, false, true);
                }
                startPounceRecovery();
            }
            return true;
        }

        if (phase == POUNCE_RECOVERING) {
            this.noPhysics = false;
            this.setNoGravity(false);
            this.setAttachmentDirection(Direction.DOWN);
            this.getNavigation().stop();
            faceCombatTarget(target);
            if (AttachmentHelper.hasSupport(this.level, this.blockPosition(), Direction.DOWN)) {
                this.setDeltaMovement(Vec3.ZERO);
                this.setSpeed(0.0F);
                this.setXxa(0.0F);
                this.setZza(0.0F);
            }
            this.pounceTicks--;
            if (this.pounceTicks <= 0) {
                resetPounce(false);
                tryStartPendingRetreat(target);
            }
            return true;
        }

        resetPounce(false);
        return false;
    }

    private boolean canStartPounce(LivingEntity target) {
        if (this.pounceCooldownTicks > 0 || isDropAttackActive() || isWebShotActive() || isWebLowerActive()) {
            return false;
        }
        if (this.getAttachmentDirection() == Direction.UP) {
            return false;
        }
        if (!(target instanceof Player) && !target.getTags().contains(POUNCE_TEST_TARGET_TAG)) {
            return false;
        }
        if (!this.hasLineOfSight(target)) {
            return false;
        }

        double distanceSqr = this.distanceToSqr(target);
        return distanceSqr >= POUNCE_MIN_RANGE_SQR
                && distanceSqr <= POUNCE_MAX_RANGE_SQR;
    }

    private void startPounceWindup(LivingEntity target) {
        this.pounceTargetId = target.getUUID();
        this.pounceLaunched = false;
        this.pounceDamageSpent = false;
        this.pounceLaunchVelocity = Vec3.ZERO;
        this.pounceTravelDistance = 0.0D;
        setPouncePhase(POUNCE_WINDUP, POUNCE_WINDUP_TICKS);
        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setDeltaMovement(Vec3.ZERO);
        this.playSound(SoundEvents.SPIDER_AMBIENT, 0.45F, 0.85F);
        faceCombatTarget(target);
    }

    private void commitPounce(LivingEntity target) {
        this.pounceLaunched = true;
        this.pounceLaunchVelocity = computePounceLaunchVelocity(target);
        this.pounceTravelDistance = 0.0D;
        setPouncePhase(POUNCE_LEAPING, POUNCE_COMMIT_TICKS);
        this.setAttachmentDirection(Direction.DOWN);
        this.setNoGravity(false);
        this.noPhysics = false;
        this.fallDistance = 0.0F;
        applyPounceMovement();
    }

    private Vec3 computePounceLaunchVelocity(LivingEntity target) {
        Vec3 toTarget = target.position()
                .add(0.0D, target.getBbHeight() * 0.35D, 0.0D)
                .subtract(this.position());
        Vec3 horizontal = new Vec3(toTarget.x, 0.0D, toTarget.z);
        if (horizontal.lengthSqr() <= 1.0E-6D) {
            Vec3 look = this.getLookAngle();
            horizontal = new Vec3(look.x, 0.0D, look.z);
        }
        if (horizontal.lengthSqr() <= 1.0E-6D) {
            horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        }

        double vertical = this.getAttachmentDirection() == Direction.DOWN
                ? POUNCE_FLOOR_VERTICAL_SPEED
                : POUNCE_WALL_VERTICAL_SPEED;
        vertical += Mth.clamp(toTarget.y * 0.08D, -0.08D, 0.16D);

        Vec3 direction = horizontal.normalize();
        return new Vec3(direction.x * POUNCE_HORIZONTAL_SPEED, vertical, direction.z * POUNCE_HORIZONTAL_SPEED);
    }

    private void applyPounceMovement() {
        Vec3 step = this.pounceLaunchVelocity;
        if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
            step = new Vec3(step.x * 0.45D, Math.min(step.y, 0.02D), step.z * 0.45D);
        }

        this.getNavigation().stop();
        this.setDeltaMovement(step);
        this.move(MoverType.SELF, step);
        double horizontalStep = Math.sqrt(step.x * step.x + step.z * step.z);
        this.pounceTravelDistance += horizontalStep;
        this.setSpeed((float) horizontalStep);
        this.setXxa(0.0F);
        this.setZza(0.9F);
        this.pounceLaunchVelocity = new Vec3(step.x * 0.92D,
                Math.max(step.y - 0.08D, -0.42D),
                step.z * 0.92D);
    }

    private void trySpendPounceDamage(LivingEntity target) {
        if (this.pounceTravelDistance < POUNCE_MIN_DAMAGE_TRAVEL) {
            return;
        }
        if (this.pounceDamageSpent || !target.isAlive() || this.distanceToSqr(target) > POUNCE_DAMAGE_RANGE_SQR) {
            return;
        }

        float damage = (float) Math.max(POUNCE_DAMAGE, this.getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (!canSpendDenseLivePlayerDamage(target)) {
            return;
        }
        if (target.hurt(DamageSource.mobAttack(this), damage)) {
            this.pounceDamageSpent = true;
            maybeStartPreyInteractionAfterDamage(target);
        }
    }

    private void startPounceRecovery() {
        setPouncePhase(POUNCE_RECOVERING, POUNCE_RECOVERY_TICKS);
        this.pounceCooldownTicks = Math.max(this.pounceCooldownTicks, POUNCE_COOLDOWN_TICKS);
        this.pounceLaunchVelocity = Vec3.ZERO;
        this.setAttachmentDirection(Direction.DOWN);
        this.setNoGravity(false);
        this.fallDistance = 0.0F;
    }

    private void resetPounce(boolean clearCooldown) {
        setPouncePhase(POUNCE_NONE, 0);
        this.pounceTargetId = null;
        this.pounceLaunched = false;
        this.pounceDamageSpent = false;
        this.pounceLaunchVelocity = Vec3.ZERO;
        this.pounceTravelDistance = 0.0D;
        if (clearCooldown) {
            this.pounceCooldownTicks = 0;
        }
    }

    private boolean updateRetreat(LivingEntity target) {
        int phase = getRetreatPhase();
        if (phase == RETREAT_NONE) {
            return false;
        }

        if (this.retreatTargetId == null || !this.retreatTargetId.equals(target.getUUID())) {
            resetRetreat(false);
            return false;
        }

        double retreatDistance = Math.sqrt(this.distanceToSqr(target));
        this.retreatMaxDistance = Math.max(this.retreatMaxDistance, retreatDistance);
        if (isFakeRetreatActive()) {
            this.fakeRetreatMaxDistance = Math.max(this.fakeRetreatMaxDistance, retreatDistance);
        }

        if (phase == RETREAT_MOVING) {
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            boolean movingToAnchor = moveTowardRetreatAnchor();
            boolean fallbackMoved = false;
            if (!movingToAnchor) {
                fallbackMoved = applyRetreatFallbackMovement(target);
            }

            this.retreatTicks--;
            if (hasReachedRetreatAnchor()
                    || this.retreatTicks <= 0
                    || (!movingToAnchor && !fallbackMoved)) {
                startRetreatRecovery();
            }
            return true;
        }

        if (phase == RETREAT_RECOVERING) {
            this.getNavigation().stop();
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            this.retreatTicks--;
            if (this.retreatTicks <= 0) {
                if (isFakeRetreatRepositioning() && canContinueFakeRetreat(target)) {
                    resetRetreat(false);
                    startFakeRetreatReengage(target);
                    return true;
                }
                resetRetreat(false);
            }
            return true;
        }

        resetRetreat(false);
        return false;
    }

    private void queueRetreat(LivingEntity target, boolean fromDamage, boolean fromMiss) {
        if (target == null || !target.isAlive() || target.isRemoved()) {
            return;
        }
        if (!isValidRetreatTarget(target)) {
            return;
        }
        this.pendingRetreatTargetId = target.getUUID();
        this.pendingRetreatFromDamage |= fromDamage;
        this.pendingRetreatFromMiss |= fromMiss;
    }

    private boolean tryStartPendingRetreat(LivingEntity fallbackTarget) {
        if (this.pendingRetreatTargetId == null) {
            return false;
        }

        LivingEntity target = findLivingEntityByUuid(this.pendingRetreatTargetId);
        if (target == null && fallbackTarget != null
                && fallbackTarget.isAlive()
                && this.pendingRetreatTargetId.equals(fallbackTarget.getUUID())) {
            target = fallbackTarget;
        }

        if (target == null || !canStartRetreat(target)) {
            if (target == null || !target.isAlive() || target.isRemoved()) {
                clearPendingRetreat();
            }
            return false;
        }

        boolean fromDamage = this.pendingRetreatFromDamage;
        boolean fromMiss = this.pendingRetreatFromMiss;
        clearPendingRetreat();
        startRetreat(target, fromDamage, fromMiss);
        return true;
    }

    private void clearPendingRetreat() {
        this.pendingRetreatTargetId = null;
        this.pendingRetreatFromDamage = false;
        this.pendingRetreatFromMiss = false;
    }

    private boolean canStartRetreat(LivingEntity target) {
        return this.retreatCooldownTicks <= 0
                && !isRetreatActive()
                && !isDropAttackActive()
                && !isWebShotActive()
                && !isWebLowerActive()
                && !isPounceActive()
                && !isFakeRetreatActive()
                && !this.isNoAi()
                && !this.isFollowingForcedPath()
                && isValidRetreatTarget(target)
                && this.distanceToSqr(target) <= RETREAT_TRIGGER_RANGE_SQR;
    }

    private boolean isValidRetreatTarget(LivingEntity target) {
        return target instanceof Player || target.getTags().contains(RETREAT_TEST_TARGET_TAG);
    }

    private void startRetreat(LivingEntity target, boolean fromDamage, boolean fromMiss) {
        this.retreatTargetId = target.getUUID();
        this.retreatTriggeredByDamage = fromDamage;
        this.retreatTriggeredByMiss = fromMiss;
        this.retreatAnchor = findRetreatAnchor(target);
        this.lastRetreatPathAnchor = null;
        this.retreatRepathTicks = 0;
        this.retreatStartDistance = Math.sqrt(this.distanceToSqr(target));
        this.retreatMaxDistance = this.retreatStartDistance;
        armFakeRetreat(target, fromDamage, fromMiss);
        setRetreatPhase(RETREAT_MOVING, RETREAT_MOVE_TICKS);
        this.retreatCooldownTicks = Math.max(this.retreatCooldownTicks, RETREAT_COOLDOWN_TICKS);
        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.playSound(SoundEvents.SPIDER_HURT, 0.45F, 1.45F);
        faceCombatTarget(target);
    }

    private BlockPos findRetreatAnchor(LivingEntity target) {
        Vec3 away = this.position().subtract(target.position());
        Vec3 horizontalAway = new Vec3(away.x, 0.0D, away.z);
        if (horizontalAway.lengthSqr() <= 1.0E-6D) {
            Vec3 look = this.getLookAngle();
            horizontalAway = new Vec3(-look.x, 0.0D, -look.z);
        }
        if (horizontalAway.lengthSqr() <= 1.0E-6D) {
            horizontalAway = new Vec3(1.0D, 0.0D, 0.0D);
        }

        Vec3 awayUnit = horizontalAway.normalize();
        Vec3 sideUnit = new Vec3(-awayUnit.z, 0.0D, awayUnit.x);
        BlockPos origin = this.blockPosition();
        double currentTargetDistanceSqr = this.distanceToSqr(target);
        BlockPos best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int distance = 2; distance <= RETREAT_SEARCH_DISTANCE; distance++) {
            for (int side = -2; side <= 2; side++) {
                for (int y = 0; y <= RETREAT_SEARCH_VERTICAL; y++) {
                    Vec3 offset = awayUnit.scale(distance).add(sideUnit.scale(side));
                    BlockPos candidate = new BlockPos(
                            origin.getX() + Mth.floor(offset.x),
                            origin.getY() + y,
                            origin.getZ() + Mth.floor(offset.z));
                    Direction attachment = bestRetreatAttachment(candidate);
                    if (attachment == null) {
                        continue;
                    }
                    Vec3 anchor = AttachmentHelper.anchorFor(this, candidate, attachment);
                    double candidateTargetDistanceSqr = anchor.distanceToSqr(target.position());
                    if (candidateTargetDistanceSqr <= currentTargetDistanceSqr + RETREAT_DISTANCE_GAIN_EPSILON) {
                        continue;
                    }
                    double surfaceBonus = attachment == Direction.DOWN ? 4.0D : (attachment == Direction.UP ? -2.0D : -4.0D);
                    double heightBonus = -Math.max(0, candidate.getY() - origin.getY()) * 0.35D;
                    double pathCost = this.position().distanceToSqr(anchor) * 0.08D;
                    double targetDistanceScore = -candidateTargetDistanceSqr * 0.05D;
                    double score = surfaceBonus + heightBonus + pathCost + targetDistanceScore + Math.abs(side) * 0.18D;
                    if (score < bestScore) {
                        best = candidate;
                        bestScore = score;
                    }
                }
            }
        }
        return best;
    }

    private Direction bestRetreatAttachment(BlockPos candidate) {
        Direction[] preferred = {
                Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
                Direction.UP, Direction.DOWN
        };
        for (Direction direction : preferred) {
            if (AttachmentHelper.hasSupport(this.level, candidate, direction)
                    && AttachmentHelper.aabbFitsOnSurface(this.level, this, candidate, direction)) {
                return direction;
            }
        }
        return null;
    }

    private boolean moveTowardRetreatAnchor() {
        if (this.retreatAnchor == null) {
            return false;
        }

        if (this.retreatRepathTicks > 0) {
            this.retreatRepathTicks--;
        }

        boolean anchorChanged = !this.retreatAnchor.equals(this.lastRetreatPathAnchor);
        if (anchorChanged || this.retreatRepathTicks <= 0 || this.getNavigation().isDone()) {
            Path path = this.getNavigation().createPath(this.retreatAnchor, 0);
            if (path == null) {
                this.retreatAnchor = null;
                this.lastRetreatPathAnchor = null;
                return false;
            }
            this.getNavigation().moveTo(path, RETREAT_NAVIGATION_SPEED);
            this.lastRetreatPathAnchor = this.retreatAnchor;
            this.retreatRepathTicks = RETREAT_REPATH_TICKS;
        }

        this.setSpeed((float) RETREAT_NAVIGATION_SPEED);
        this.setXxa(0.0F);
        this.setZza(-0.85F);
        return true;
    }

    private boolean hasReachedRetreatAnchor() {
        if (this.retreatAnchor == null) {
            return false;
        }
        Direction attachment = bestRetreatAttachment(this.retreatAnchor);
        Vec3 anchor = attachment == null
                ? Vec3.atCenterOf(this.retreatAnchor)
                : AttachmentHelper.anchorFor(this, this.retreatAnchor, attachment);
        return this.position().distanceToSqr(anchor) <= RETREAT_ANCHOR_REACHED_SQR;
    }

    private boolean applyRetreatFallbackMovement(LivingEntity target) {
        Vec3 awayFromTarget = this.position().subtract(target.position());
        Direction attachment = this.getAttachmentDirection();
        Vec3 tangent = attachment == Direction.DOWN
                ? new Vec3(awayFromTarget.x, 0.0D, awayFromTarget.z)
                : AttachmentHelper.projectOntoPlane(awayFromTarget, AttachmentHelper.normal(attachment));
        if (attachment != Direction.DOWN && attachment != Direction.UP) {
            tangent = tangent.add(0.0D, 0.65D, 0.0D);
        }
        if (tangent.lengthSqr() <= BACKPEDAL_MIN_STEP_SQR) {
            return false;
        }

        Vec3 step = tangent.normalize().scale(RETREAT_FALLBACK_SPEED);
        if (!canTakeRetreatFallbackStep(step)) {
            Vec3 sideStep = new Vec3(-step.z, step.y * 0.4D, step.x).normalize().scale(RETREAT_FALLBACK_SPEED * 0.75D);
            if (canTakeRetreatFallbackStep(sideStep)) {
                step = sideStep;
            } else {
                return false;
            }
        }

        this.getNavigation().stop();
        this.setDeltaMovement(step);
        this.move(MoverType.SELF, step);
        this.setSpeed((float) RETREAT_FALLBACK_SPEED);
        this.setXxa(0.0F);
        this.setZza(-0.8F);
        return true;
    }

    private boolean canTakeRetreatFallbackStep(Vec3 step) {
        if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
            return false;
        }
        BlockPos nextBlock = new BlockPos(this.position().add(step));
        Direction attachment = this.getAttachmentDirection();
        return AttachmentHelper.hasSupport(this.level, nextBlock, attachment)
                && AttachmentHelper.aabbFitsOnSurface(this.level, this, nextBlock, attachment);
    }

    private void startRetreatRecovery() {
        if (isFakeRetreatFleeing()) {
            startFakeRetreatRepositioning();
        }
        setRetreatPhase(RETREAT_RECOVERING, RETREAT_RECOVERY_TICKS);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.retreatRepathTicks = 0;
    }

    private void resetRetreat(boolean clearCooldown) {
        setRetreatPhase(RETREAT_NONE, 0);
        this.retreatTargetId = null;
        this.retreatTriggeredByDamage = false;
        this.retreatTriggeredByMiss = false;
        this.retreatAnchor = null;
        this.lastRetreatPathAnchor = null;
        this.retreatRepathTicks = 0;
        this.retreatStartDistance = 0.0D;
        this.retreatMaxDistance = 0.0D;
        if (clearCooldown) {
            this.retreatCooldownTicks = 0;
        }
    }

    private boolean updateFakeRetreat(LivingEntity target) {
        int phase = getFakeRetreatPhase();
        if (phase == FAKE_RETREAT_NONE) {
            return false;
        }

        if (!canContinueFakeRetreat(target)) {
            resetFakeRetreat(false);
            return false;
        }

        updateFakeRetreatDistanceMetrics(target);

        if (phase == FAKE_RETREAT_FLEEING) {
            startFakeRetreatRepositioning();
            return true;
        }

        if (phase == FAKE_RETREAT_REPOSITIONING) {
            this.getNavigation().stop();
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            this.fakeRetreatTicks--;
            if (this.fakeRetreatTicks <= 0) {
                startFakeRetreatReengage(target);
            }
            return true;
        }

        if (phase == FAKE_RETREAT_REENGAGING) {
            this.fakeRetreatReengageStarted = true;
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            if (this.tickCount % 4 == 0 || this.getNavigation().isDone()) {
                this.getNavigation().moveTo(target, FAKE_RETREAT_REENGAGE_SPEED);
            }
            applyFakeRetreatReengageMovement(target);
            updateFakeRetreatDistanceMetrics(target);
            this.setSpeed((float) FAKE_RETREAT_REENGAGE_SPEED);
            this.setXxa(0.0F);
            this.setZza(1.0F);
            this.fakeRetreatTicks--;
            boolean heldReengageLongEnough = this.fakeRetreatTicks <= FAKE_RETREAT_REENGAGE_TICKS - 8;
            if (this.fakeRetreatTicks <= 0
                    || (heldReengageLongEnough
                    && fakeRetreatReturnClosedDistanceValue() >= FAKE_RETREAT_RETURN_CLOSURE_EPSILON)) {
                startFakeRetreatRecovery();
            }
            return true;
        }

        if (phase == FAKE_RETREAT_RECOVERING) {
            this.getNavigation().stop();
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            this.fakeRetreatTicks--;
            if (this.fakeRetreatTicks <= 0) {
                resetFakeRetreat(false);
            }
            return true;
        }

        resetFakeRetreat(false);
        return false;
    }

    private boolean applyFakeRetreatReengageMovement(LivingEntity target) {
        Vec3 toTarget = target.position().subtract(this.position());
        Direction attachment = this.getAttachmentDirection();
        Vec3 tangent = attachment == Direction.DOWN
                ? new Vec3(toTarget.x, 0.0D, toTarget.z)
                : AttachmentHelper.projectOntoPlane(toTarget, AttachmentHelper.normal(attachment));
        if (tangent.lengthSqr() <= BACKPEDAL_MIN_STEP_SQR) {
            return false;
        }

        Vec3 step = tangent.normalize().scale(FAKE_RETREAT_REENGAGE_STEP_SPEED);
        if (!canTakeFakeRetreatReengageStep(step)) {
            return false;
        }

        this.getNavigation().stop();
        this.setDeltaMovement(step);
        this.move(MoverType.SELF, step);
        return true;
    }

    private boolean canTakeFakeRetreatReengageStep(Vec3 step) {
        if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
            return false;
        }
        BlockPos nextBlock = new BlockPos(this.position().add(step));
        Direction attachment = this.getAttachmentDirection();
        return AttachmentHelper.hasSupport(this.level, nextBlock, attachment)
                && AttachmentHelper.aabbFitsOnSurface(this.level, this, nextBlock, attachment);
    }

    private void armFakeRetreat(LivingEntity target, boolean fromDamage, boolean fromMiss) {
        if ((!fromDamage && !fromMiss)
                || this.fakeRetreatCooldownTicks > 0
                || isFakeRetreatActive()
                || target == null
                || !target.isAlive()) {
            return;
        }

        this.fakeRetreatTargetId = target.getUUID();
        this.fakeRetreatTriggeredByDamage = fromDamage;
        this.fakeRetreatTriggeredByMiss = fromMiss;
        this.fakeRetreatAnchor = this.retreatAnchor == null ? this.blockPosition() : this.retreatAnchor;
        this.fakeRetreatStartDistance = Math.sqrt(this.distanceToSqr(target));
        this.fakeRetreatMaxDistance = this.fakeRetreatStartDistance;
        this.fakeRetreatReturnStartDistance = 0.0D;
        this.fakeRetreatMinReturnDistance = 0.0D;
        this.fakeRetreatReengageStarted = false;
        this.fakeRetreatCooldownTicks = Math.max(this.fakeRetreatCooldownTicks, FAKE_RETREAT_COOLDOWN_TICKS);
        setFakeRetreatPhase(FAKE_RETREAT_FLEEING, RETREAT_MOVE_TICKS);
    }

    private void startFakeRetreatRepositioning() {
        if (this.fakeRetreatAnchor == null) {
            this.fakeRetreatAnchor = this.blockPosition();
        }
        setFakeRetreatPhase(FAKE_RETREAT_REPOSITIONING, RETREAT_RECOVERY_TICKS);
    }

    private void startFakeRetreatReengage(LivingEntity target) {
        double currentDistance = Math.sqrt(this.distanceToSqr(target));
        this.fakeRetreatReturnStartDistance = Math.max(currentDistance, this.fakeRetreatMaxDistance);
        this.fakeRetreatMinReturnDistance = currentDistance;
        this.fakeRetreatReengageStarted = true;
        setFakeRetreatPhase(FAKE_RETREAT_REENGAGING, FAKE_RETREAT_REENGAGE_TICKS);
        this.getNavigation().moveTo(target, FAKE_RETREAT_REENGAGE_SPEED);
        this.playSound(SoundEvents.SPIDER_AMBIENT, 0.65F, 0.55F);
        faceCombatTarget(target);
    }

    private void startFakeRetreatRecovery() {
        setFakeRetreatPhase(FAKE_RETREAT_RECOVERING, FAKE_RETREAT_RECOVERY_TICKS);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
    }

    private boolean canContinueFakeRetreat(LivingEntity target) {
        return target != null
                && target.isAlive()
                && !target.isRemoved()
                && this.fakeRetreatTargetId != null
                && this.fakeRetreatTargetId.equals(target.getUUID())
                && !this.isNoAi()
                && !this.isFollowingForcedPath();
    }

    private void updateFakeRetreatDistanceMetrics(LivingEntity target) {
        double distance = Math.sqrt(this.distanceToSqr(target));
        this.fakeRetreatMaxDistance = Math.max(this.fakeRetreatMaxDistance, distance);
        if (isFakeRetreatReengaging() || isFakeRetreatRecovering()) {
            this.fakeRetreatReturnStartDistance = Math.max(
                    this.fakeRetreatReturnStartDistance,
                    this.fakeRetreatMaxDistance);
            if (this.fakeRetreatMinReturnDistance <= 0.0D) {
                this.fakeRetreatMinReturnDistance = distance;
            } else {
                this.fakeRetreatMinReturnDistance = Math.min(this.fakeRetreatMinReturnDistance, distance);
            }
        }
    }

    private double fakeRetreatDistanceGainedValue() {
        return Math.max(0.0D, this.fakeRetreatMaxDistance - this.fakeRetreatStartDistance);
    }

    private double fakeRetreatReturnClosedDistanceValue() {
        return Math.max(0.0D, this.fakeRetreatReturnStartDistance - this.fakeRetreatMinReturnDistance);
    }

    private void resetFakeRetreat(boolean clearCooldown) {
        setFakeRetreatPhase(FAKE_RETREAT_NONE, 0);
        this.fakeRetreatTargetId = null;
        this.fakeRetreatTriggeredByDamage = false;
        this.fakeRetreatTriggeredByMiss = false;
        this.fakeRetreatAnchor = null;
        this.fakeRetreatStartDistance = 0.0D;
        this.fakeRetreatMaxDistance = 0.0D;
        this.fakeRetreatReturnStartDistance = 0.0D;
        this.fakeRetreatMinReturnDistance = 0.0D;
        this.fakeRetreatReengageStarted = false;
        if (clearCooldown) {
            this.fakeRetreatCooldownTicks = 0;
        }
    }

    private boolean updateGrabPull(LivingEntity target) {
        int phase = getGrabPullPhase();
        if (phase == GRAB_PULL_NONE) {
            if (!canStartGrabPull(target)) {
                return false;
            }
            startGrabPullWindup(target);
            return true;
        }

        if (this.grabPullTargetId == null || !this.grabPullTargetId.equals(target.getUUID())) {
            resetGrabPull(false);
            return false;
        }

        if (phase == GRAB_PULL_WINDUP) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            holdGrabPullPosition();
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            faceCombatTarget(target);
            this.grabPullTicks--;
            if (this.grabPullTicks <= 0) {
                startGrabPulling();
            }
            return true;
        }

        if (phase == GRAB_PULL_PULLING) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            holdGrabPullPosition();
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            faceCombatTarget(target);
            boolean movedTarget = applyGrabPullTargetMovement(target);
            this.grabPullMinDistance = Math.min(this.grabPullMinDistance, Math.sqrt(this.distanceToSqr(target)));
            this.grabPullMaxTargetY = Math.max(this.grabPullMaxTargetY, target.getY());
            this.grabPullTicks--;
            if (this.distanceToSqr(target) <= GRAB_PULL_STOP_DISTANCE_SQR || this.grabPullTicks <= 0 || !movedTarget) {
                startGrabPullRecovery(target);
            }
            return true;
        }

        if (phase == GRAB_PULL_RECOVERING) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            holdGrabPullPosition();
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            faceCombatTarget(target);
            this.grabPullTicks--;
            if (this.grabPullTicks <= 0) {
                resetGrabPull(false);
            }
            return true;
        }

        resetGrabPull(false);
        return false;
    }

    private boolean canStartGrabPull(LivingEntity target) {
        if (this.grabPullCooldownTicks > 0
                || isDropAttackActive()
                || isWebShotActive()
                || isWebLowerActive()
                || isPounceActive()
                || isRetreatActive()
                || isDragNestActive()
                || this.pendingDragNestTargetId != null
                || this.pendingRetreatTargetId != null
                || this.isFollowingForcedPath()) {
            return false;
        }
        if (!(target instanceof Player) && !target.getTags().contains(GRAB_PULL_TEST_TARGET_TAG)) {
            return false;
        }
        if (isDenseLivePlayerCombatTarget(target) && !target.getTags().contains(GRAB_PULL_TEST_TARGET_TAG)) {
            return false;
        }
        if (!this.hasLineOfSight(target)) {
            return false;
        }

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr <= GRAB_PULL_STOP_DISTANCE_SQR || distanceSqr > GRAB_PULL_WEB_CONTROL_RANGE_SQR) {
            return false;
        }

        boolean webControlled = isTargetWebControlled(target);
        return webControlled || distanceSqr <= GRAB_PULL_CLOSE_RANGE_SQR;
    }

    private boolean isTargetWebControlled(LivingEntity target) {
        if (target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || target.hasEffect(MobEffects.BLINDNESS)) {
            return true;
        }

        BlockPos origin = target.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 1))) {
            if (this.level.getBlockState(pos).is(Blocks.COBWEB)) {
                return true;
            }
        }
        return false;
    }

    private void startGrabPullWindup(LivingEntity target) {
        this.grabPullTargetId = target.getUUID();
        this.grabPullTriggeredByWeb = isTargetWebControlled(target);
        this.grabPullMovedTarget = false;
        this.grabPullSawPulling = false;
        this.grabPullStartDistance = Math.sqrt(this.distanceToSqr(target));
        this.grabPullMinDistance = this.grabPullStartDistance;
        this.grabPullStartTargetY = target.getY();
        this.grabPullMaxTargetY = target.getY();
        this.grabPullHoldPosition = this.position();
        setGrabPullPhase(GRAB_PULL_WINDUP, GRAB_PULL_WINDUP_TICKS);
        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setDeltaMovement(Vec3.ZERO);
        this.playSound(SoundEvents.SPIDER_AMBIENT, 0.45F, 0.35F);
        faceCombatTarget(target);
    }

    private void startGrabPulling() {
        this.grabPullSawPulling = true;
        setGrabPullPhase(GRAB_PULL_PULLING, GRAB_PULL_PULL_TICKS);
    }

    private boolean applyGrabPullTargetMovement(LivingEntity target) {
        Vec3 spiderPoint = this.position().add(0.0D, this.getBbHeight() * 0.45D, 0.0D);
        Vec3 targetPoint = target.position().add(0.0D, target.getBbHeight() * 0.45D, 0.0D);
        Vec3 pull = spiderPoint.subtract(targetPoint);
        if (pull.lengthSqr() <= 1.0E-6D) {
            return false;
        }

        double currentDistance = Math.sqrt(this.distanceToSqr(target));
        double stopDistance = Math.sqrt(GRAB_PULL_STOP_DISTANCE_SQR);
        double maxPullStep = Math.min(GRAB_PULL_STEP, Math.max(0.0D, currentDistance - stopDistance));
        if (maxPullStep <= 0.01D) {
            return false;
        }

        Vec3 step = pull.normalize().scale(maxPullStep);
        double yStep = Mth.clamp(step.y, -GRAB_PULL_MAX_DOWN_STEP, GRAB_PULL_MAX_UP_STEP);
        if (yStep < 0.0D && (target.isOnGround() || this.getY() <= target.getY() + GRAB_PULL_MIN_EFFECT_LIFT)) {
            yStep = 0.0D;
        }
        step = new Vec3(step.x, yStep, step.z);
        if (step.lengthSqr() <= 1.0E-6D) {
            return false;
        }
        if (!target.level.noCollision(target, target.getBoundingBox().move(step))) {
            step = step.scale(0.5D);
            if (!target.level.noCollision(target, target.getBoundingBox().move(step))) {
                step = step.scale(0.5D);
                if (!target.level.noCollision(target, target.getBoundingBox().move(step))) {
                    return false;
                }
            }
        }

        target.setDeltaMovement(step);
        target.move(MoverType.SELF, step);
        target.fallDistance = 0.0F;
        this.grabPullMovedTarget = true;
        return true;
    }

    private void holdGrabPullPosition() {
        if (this.grabPullHoldPosition.lengthSqr() <= 1.0E-9D) {
            return;
        }
        this.teleportTo(this.grabPullHoldPosition.x, this.grabPullHoldPosition.y, this.grabPullHoldPosition.z);
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void startGrabPullRecovery(LivingEntity target) {
        armDragNestFromGrabPull(target);
        setGrabPullPhase(GRAB_PULL_RECOVERING, GRAB_PULL_RECOVERY_TICKS);
        this.grabPullCooldownTicks = Math.max(this.grabPullCooldownTicks, GRAB_PULL_COOLDOWN_TICKS);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void resetGrabPull(boolean clearCooldown) {
        setGrabPullPhase(GRAB_PULL_NONE, 0);
        this.grabPullTargetId = null;
        if (clearCooldown) {
            this.grabPullCooldownTicks = 0;
            this.grabPullTriggeredByWeb = false;
            this.grabPullMovedTarget = false;
            this.grabPullStartDistance = 0.0D;
            this.grabPullMinDistance = 0.0D;
            this.grabPullStartTargetY = 0.0D;
            this.grabPullMaxTargetY = 0.0D;
            this.grabPullSawPulling = false;
            this.grabPullHoldPosition = Vec3.ZERO;
        }
    }

    private boolean updateDragNest(LivingEntity target) {
        int phase = getDragNestPhase();
        if (phase == DRAG_NEST_NONE) {
            if (!canStartDragNest(target)) {
                return false;
            }
            startDragNestWindup(target);
            return true;
        }

        if (this.dragNestTargetId == null || !this.dragNestTargetId.equals(target.getUUID())) {
            resetDragNest(false);
            return false;
        }

        if (phase == DRAG_NEST_WINDUP) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            faceCombatTarget(target);
            updateDragNestAnchorDistance(target);
            this.dragNestTicks--;
            if (this.dragNestTicks <= 0) {
                startDragNestDragging();
            }
            return true;
        }

        if (phase == DRAG_NEST_DRAGGING) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            faceCombatTarget(target);
            boolean movedTarget = applyDragNestTargetMovement(target);
            applyDragNestSpiderMovement();
            updateDragNestAnchorDistance(target);
            faceCombatTarget(target);
            this.dragNestTicks--;
            if (this.dragNestReachedAnchor || this.dragNestTicks <= 0 || !movedTarget) {
                startDragNestRecovery();
            }
            return true;
        }

        if (phase == DRAG_NEST_RECOVERING) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            faceCombatTarget(target);
            updateDragNestAnchorDistance(target);
            this.dragNestTicks--;
            if (this.dragNestTicks <= 0) {
                resetDragNest(false);
            }
            return true;
        }

        resetDragNest(false);
        return false;
    }

    private boolean canStartDragNest(LivingEntity target) {
        if (this.dragNestCooldownTicks > 0
                || this.pendingDragNestTargetId == null
                || !this.pendingDragNestTargetId.equals(target.getUUID())
                || isDropAttackActive()
                || isWebShotActive()
                || isWebLowerActive()
                || isPounceActive()
                || isRetreatActive()
                || isGrabPullActive()
                || this.pendingRetreatTargetId != null
                || this.isFollowingForcedPath()) {
            return false;
        }
        if (!isDragNestEligibleTarget(target) || !this.hasLineOfSight(target)) {
            return false;
        }
        if (this.distanceToSqr(target) > DRAG_NEST_TRIGGER_MAX_RANGE_SQR) {
            return false;
        }

        if (this.dragNestAnchor == null) {
            this.dragNestAnchor = findDragNestAnchor(target);
        }
        if (this.dragNestAnchor == null) {
            return false;
        }

        return distanceFromTargetToDragNestAnchor(target) * distanceFromTargetToDragNestAnchor(target)
                > DRAG_NEST_ANCHOR_REACHED_SQR;
    }

    private boolean isDragNestEligibleTarget(LivingEntity target) {
        if (isDenseLivePlayerCombatTarget(target) && !target.getTags().contains(DRAG_NEST_TEST_TARGET_TAG)) {
            return false;
        }
        return target instanceof Player || target.getTags().contains(DRAG_NEST_TEST_TARGET_TAG);
    }

    private void armDragNestFromGrabPull(LivingEntity target) {
        if (!this.grabPullMovedTarget || this.dragNestCooldownTicks > 0 || !isDragNestEligibleTarget(target)) {
            return;
        }

        BlockPos anchor = findDragNestAnchor(target);
        if (anchor == null) {
            return;
        }

        this.pendingDragNestTargetId = target.getUUID();
        this.dragNestAnchor = anchor;
        this.dragNestStartAnchorDistance = distanceFromTargetToDragNestAnchor(target);
        this.dragNestCurrentAnchorDistance = this.dragNestStartAnchorDistance;
        this.dragNestMinAnchorDistance = this.dragNestStartAnchorDistance;
        this.dragNestMovedTarget = false;
        this.dragNestReachedAnchor = false;
        this.dragNestSawWindup = false;
        this.dragNestSawDragging = false;
        this.dragNestSawRecovery = false;
    }

    private void startDragNestWindup(LivingEntity target) {
        if (this.dragNestAnchor == null) {
            this.dragNestAnchor = findDragNestAnchor(target);
        }
        this.dragNestTargetId = target.getUUID();
        this.pendingDragNestTargetId = null;
        this.dragNestMovedTarget = false;
        this.dragNestReachedAnchor = false;
        this.dragNestSawWindup = true;
        this.dragNestSawDragging = false;
        this.dragNestSawRecovery = false;
        this.dragNestStartAnchorDistance = distanceFromTargetToDragNestAnchor(target);
        this.dragNestCurrentAnchorDistance = this.dragNestStartAnchorDistance;
        this.dragNestMinAnchorDistance = this.dragNestStartAnchorDistance;
        setDragNestPhase(DRAG_NEST_WINDUP, DRAG_NEST_WINDUP_TICKS);
        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setDeltaMovement(Vec3.ZERO);
        this.playSound(SoundEvents.SPIDER_AMBIENT, 0.55F, 0.28F);
        faceCombatTarget(target);
    }

    private void startDragNestDragging() {
        this.dragNestSawDragging = true;
        setDragNestPhase(DRAG_NEST_DRAGGING, DRAG_NEST_DRAG_TICKS);
    }

    private boolean applyDragNestTargetMovement(LivingEntity target) {
        if (this.dragNestAnchor == null) {
            return false;
        }

        double currentDistance = distanceFromTargetToDragNestAnchor(target);
        this.dragNestCurrentAnchorDistance = currentDistance;
        if (currentDistance * currentDistance <= DRAG_NEST_ANCHOR_REACHED_SQR) {
            this.dragNestReachedAnchor = true;
            return false;
        }

        Vec3 anchorPoint = dragNestAnchorPoint(target.getY());
        Vec3 toAnchor = anchorPoint.subtract(target.position());
        Vec3 horizontal = new Vec3(toAnchor.x, 0.0D, toAnchor.z);
        if (horizontal.lengthSqr() <= 1.0E-6D) {
            return false;
        }

        double stopDistance = Math.sqrt(DRAG_NEST_ANCHOR_REACHED_SQR);
        double stepSize = Math.min(DRAG_NEST_TARGET_STEP, Math.max(0.0D, currentDistance - stopDistance));
        if (stepSize <= 0.005D) {
            this.dragNestReachedAnchor = true;
            return false;
        }

        Vec3 step = horizontal.normalize().scale(stepSize);
        if (!target.level.noCollision(target, target.getBoundingBox().move(step))) {
            step = step.scale(0.5D);
            if (!target.level.noCollision(target, target.getBoundingBox().move(step))) {
                step = step.scale(0.5D);
                if (!target.level.noCollision(target, target.getBoundingBox().move(step))) {
                    return false;
                }
            }
        }

        target.setDeltaMovement(step);
        target.move(MoverType.SELF, step);
        target.fallDistance = 0.0F;
        this.dragNestMovedTarget = true;
        return true;
    }

    private boolean applyDragNestSpiderMovement() {
        if (this.dragNestAnchor == null || this.getAttachmentDirection() != Direction.DOWN) {
            return false;
        }

        Vec3 anchorPoint = dragNestAnchorPoint(this.getY());
        Vec3 toAnchor = anchorPoint.subtract(this.position());
        Vec3 horizontal = new Vec3(toAnchor.x, 0.0D, toAnchor.z);
        if (horizontal.lengthSqr() <= DRAG_NEST_SPIDER_ANCHOR_STOP_SQR) {
            return false;
        }

        Vec3 step = horizontal.normalize().scale(DRAG_NEST_SPIDER_STEP);
        if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
            return false;
        }

        this.move(MoverType.SELF, step);
        this.setDeltaMovement(step);
        return true;
    }

    private void updateDragNestAnchorDistance(LivingEntity target) {
        double distance = distanceFromTargetToDragNestAnchor(target);
        this.dragNestCurrentAnchorDistance = distance;
        if (this.dragNestMinAnchorDistance <= 0.0D) {
            this.dragNestMinAnchorDistance = distance;
        } else {
            this.dragNestMinAnchorDistance = Math.min(this.dragNestMinAnchorDistance, distance);
        }
        if (distance * distance <= DRAG_NEST_ANCHOR_REACHED_SQR) {
            this.dragNestReachedAnchor = true;
        }
    }

    private double distanceFromTargetToDragNestAnchor(LivingEntity target) {
        if (this.dragNestAnchor == null) {
            return 0.0D;
        }
        return target.position().distanceTo(dragNestAnchorPoint(target.getY()));
    }

    private Vec3 dragNestAnchorPoint(double y) {
        if (this.dragNestAnchor == null) {
            return this.position();
        }
        return new Vec3(this.dragNestAnchor.getX() + 0.5D, y, this.dragNestAnchor.getZ() + 0.5D);
    }

    private BlockPos findDragNestAnchor(LivingEntity target) {
        BlockPos webAnchor = findDragNestWebAnchor(target);
        return webAnchor != null ? webAnchor : findDragNestCornerAnchor(target);
    }

    private BlockPos findDragNestWebAnchor(LivingEntity target) {
        BlockPos origin = this.blockPosition();
        BlockPos best = null;
        double bestDistanceSqr = 0.0D;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-DRAG_NEST_ANCHOR_SEARCH_HORIZONTAL, -DRAG_NEST_ANCHOR_SEARCH_VERTICAL,
                        -DRAG_NEST_ANCHOR_SEARCH_HORIZONTAL),
                origin.offset(DRAG_NEST_ANCHOR_SEARCH_HORIZONTAL, DRAG_NEST_ANCHOR_SEARCH_VERTICAL,
                        DRAG_NEST_ANCHOR_SEARCH_HORIZONTAL))) {
            if (!this.level.getBlockState(pos).is(Blocks.COBWEB)) {
                continue;
            }
            double distanceSqr = target.position().distanceToSqr(Vec3.atCenterOf(pos));
            if (distanceSqr < DRAG_NEST_MIN_ANCHOR_DISTANCE_SQR || distanceSqr > DRAG_NEST_TRIGGER_MAX_RANGE_SQR) {
                continue;
            }
            if (best == null || distanceSqr > bestDistanceSqr) {
                best = pos.immutable();
                bestDistanceSqr = distanceSqr;
            }
        }
        return best;
    }

    private BlockPos findDragNestCornerAnchor(LivingEntity target) {
        BlockPos origin = this.blockPosition();
        BlockPos best = null;
        double bestDistanceSqr = 0.0D;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-DRAG_NEST_ANCHOR_SEARCH_HORIZONTAL, -1, -DRAG_NEST_ANCHOR_SEARCH_HORIZONTAL),
                origin.offset(DRAG_NEST_ANCHOR_SEARCH_HORIZONTAL, 1, DRAG_NEST_ANCHOR_SEARCH_HORIZONTAL))) {
            if (!this.level.getBlockState(pos).isAir() || !this.level.getBlockState(pos.above()).isAir()) {
                continue;
            }
            if (!this.level.getBlockState(pos.below()).isSolidRender(this.level, pos.below())) {
                continue;
            }

            int wallCount = 0;
            for (Direction direction : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
                BlockPos wall = pos.relative(direction);
                if (this.level.getBlockState(wall).isSolidRender(this.level, wall)) {
                    wallCount++;
                }
            }
            if (wallCount < 2) {
                continue;
            }

            double distanceSqr = target.position().distanceToSqr(new Vec3(pos.getX() + 0.5D, target.getY(), pos.getZ() + 0.5D));
            if (distanceSqr < DRAG_NEST_MIN_ANCHOR_DISTANCE_SQR || distanceSqr > DRAG_NEST_TRIGGER_MAX_RANGE_SQR) {
                continue;
            }
            if (best == null || distanceSqr > bestDistanceSqr) {
                best = pos.immutable();
                bestDistanceSqr = distanceSqr;
            }
        }
        return best;
    }

    private void startDragNestRecovery() {
        this.dragNestSawRecovery = true;
        setDragNestPhase(DRAG_NEST_RECOVERING, DRAG_NEST_RECOVERY_TICKS);
        this.dragNestCooldownTicks = Math.max(this.dragNestCooldownTicks, DRAG_NEST_COOLDOWN_TICKS);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void resetDragNest(boolean clearCooldown) {
        setDragNestPhase(DRAG_NEST_NONE, 0);
        this.dragNestTargetId = null;
        this.pendingDragNestTargetId = null;
        if (clearCooldown) {
            this.dragNestCooldownTicks = 0;
            this.dragNestAnchor = null;
            this.dragNestMovedTarget = false;
            this.dragNestReachedAnchor = false;
            this.dragNestStartAnchorDistance = 0.0D;
            this.dragNestCurrentAnchorDistance = 0.0D;
            this.dragNestMinAnchorDistance = 0.0D;
            this.dragNestSawWindup = false;
            this.dragNestSawDragging = false;
            this.dragNestSawRecovery = false;
        }
    }

    private boolean hasHigherPriorityCombatStateActive() {
        return isDropAttackActive()
                || isWebLowerActive()
                || isWebTrapPlacementActive()
                || isWebShotActive()
                || isPounceActive()
                || isRetreatActive()
                || isFakeRetreatActive()
                || isGrabPullActive()
                || isDragNestActive()
                || isThreatDisplaying();
    }

    private boolean shouldSuspendMeleeAttackGoal() {
        return hasHigherPriorityCombatStateActive()
                || isLineOfSightStalking()
                || isDarknessPreferenceActive()
                || isWallPeeking()
                || isPreyInteracting()
                || isEscapeCutting();
    }

    private boolean updateThreatDisplay(LivingEntity target) {
        if (!canUseCombatPacing(target)) {
            resetThreatDisplay(false);
            return false;
        }

        UUID targetId = target.getUUID();
        if (isThreatDisplaying() && (this.threatDisplayTargetId == null || !this.threatDisplayTargetId.equals(targetId))) {
            resetThreatDisplay(false);
        }

        if (!isThreatDisplaying()) {
            if (!canStartThreatDisplay(target)) {
                return false;
            }
            startThreatDisplay(target);
        }

        if (this.threatDisplayTicks <= 0) {
            finishThreatDisplay();
            return false;
        }

        holdThreatDisplay(target);
        this.threatDisplayTicks--;
        if (this.threatDisplayTicks <= 0) {
            finishThreatDisplay();
        }
        return true;
    }

    private boolean canStartThreatDisplay(LivingEntity target) {
        if (!canUseCombatPacing(target)) {
            this.threatDisplayStatus = "combat_unavailable";
            return false;
        }
        if (this.threatDisplayCooldownTicks > 0) {
            this.threatDisplayStatus = "cooldown";
            return false;
        }
        if (isDropAttackActive()
                || isWebLowerActive()
                || isWebShotActive()
                || isPounceActive()
                || isRetreatActive()
                || isFakeRetreatActive()
                || isGrabPullActive()
                || isDragNestActive()
                || this.escapeCutting
                || isPackCoordinating()) {
            this.threatDisplayStatus = "higher_priority_active";
            return false;
        }
        if (!(target instanceof Player) && !target.getTags().contains(THREAT_DISPLAY_TEST_TARGET_TAG)) {
            this.threatDisplayStatus = "target_ineligible";
            return false;
        }
        if (!this.hasLineOfSight(target)) {
            this.threatDisplayStatus = "no_line_of_sight";
            return false;
        }

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr < THREAT_DISPLAY_MIN_RANGE_SQR || distanceSqr > THREAT_DISPLAY_MAX_RANGE_SQR) {
            this.threatDisplayStatus = "range";
            return false;
        }
        this.threatDisplayStatus = "eligible";
        return true;
    }

    private void startThreatDisplay(LivingEntity target) {
        this.threatDisplayTargetId = target.getUUID();
        this.threatDisplayTicks = THREAT_DISPLAY_TICKS;
        this.threatDisplayStartPosition = this.position();
        this.threatDisplayStartDistance = Math.sqrt(this.distanceToSqr(target));
        this.threatDisplayCurrentDistance = this.threatDisplayStartDistance;
        this.threatDisplayMaxMovement = 0.0D;
        this.threatDisplayFacingTicks = 0;
        this.threatDisplayPlayedSound = false;
        this.threatDisplayStatus = "displaying";
        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setDeltaMovement(Vec3.ZERO);
        this.setSpeed(0.0F);
        this.setXxa(0.0F);
        this.setZza(0.0F);
        faceCombatTarget(target);
    }

    private void holdThreatDisplay(LivingEntity target) {
        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setDeltaMovement(Vec3.ZERO);
        this.setSpeed(0.0F);
        this.setXxa(0.0F);
        this.setZza(0.0F);
        faceCombatTarget(target);
        this.threatDisplayCurrentDistance = Math.sqrt(this.distanceToSqr(target));
        this.threatDisplayMaxMovement = Math.max(this.threatDisplayMaxMovement,
                this.position().distanceTo(this.threatDisplayStartPosition));
        if (isFacingCombatTarget(target, THREAT_DISPLAY_FACING_DEGREES)) {
            this.threatDisplayFacingTicks++;
        }
        if (!this.threatDisplayPlayedSound && this.threatDisplayTicks <= THREAT_DISPLAY_TICKS - 6) {
            this.playSound(SoundEvents.SPIDER_AMBIENT, 0.62F, 0.45F);
            this.threatDisplayPlayedSound = true;
        }
        this.threatDisplayStatus = "displaying";
    }

    private void finishThreatDisplay() {
        this.threatDisplayCooldownTicks = Math.max(this.threatDisplayCooldownTicks, THREAT_DISPLAY_COOLDOWN_TICKS);
        resetThreatDisplay(false);
    }

    private void resetThreatDisplay(boolean clearCooldown) {
        this.threatDisplayTicks = 0;
        this.threatDisplayTargetId = null;
        this.threatDisplayStartPosition = Vec3.ZERO;
        this.threatDisplayStartDistance = 0.0D;
        this.threatDisplayCurrentDistance = 0.0D;
        this.threatDisplayMaxMovement = 0.0D;
        this.threatDisplayFacingTicks = 0;
        this.threatDisplayPlayedSound = false;
        this.threatDisplayStatus = "idle";
        if (clearCooldown) {
            this.threatDisplayCooldownTicks = 0;
        }
    }

    private boolean updateLineOfSightStalking(LivingEntity target) {
        if (!canUseCombatPacing(target)) {
            resetLineOfSightStalking(false);
            return false;
        }

        UUID targetId = target.getUUID();
        if (isLineOfSightStalking()
                && (this.lineOfSightStalkingTargetId == null || !this.lineOfSightStalkingTargetId.equals(targetId))) {
            resetLineOfSightStalking(false);
        }

        if (!isLineOfSightStalking()) {
            if (!canStartLineOfSightStalking(target)) {
                return false;
            }
            startLineOfSightStalking(target);
        }

        if (!this.hasLineOfSight(target)) {
            this.lineOfSightStalkingStatus = "lost_line_of_sight";
            finishLineOfSightStalking();
            return false;
        }
        if (this.distanceToSqr(target) <= LINE_OF_SIGHT_STALKING_CLOSE_STOP_SQR) {
            this.lineOfSightStalkingStatus = "close_enough";
            finishLineOfSightStalking();
            return false;
        }
        if (this.lineOfSightStalkingTicks <= 0) {
            finishLineOfSightStalking();
            return false;
        }

        boolean targetLooking = isTargetLookingAtSpider(target);
        this.lineOfSightStalkingTargetLooking = targetLooking;
        if (targetLooking) {
            holdLineOfSightStalking(target);
        } else {
            advanceLineOfSightStalking(target);
        }

        this.lineOfSightStalkingTicks--;
        if (this.lineOfSightStalkingTicks <= 0) {
            finishLineOfSightStalking();
        }
        return true;
    }

    private boolean canStartLineOfSightStalking(LivingEntity target) {
        if (!canUseCombatPacing(target)) {
            this.lineOfSightStalkingStatus = "combat_unavailable";
            return false;
        }
        if (this.lineOfSightStalkingCooldownTicks > 0) {
            this.lineOfSightStalkingStatus = "cooldown";
            return false;
        }
        if (hasHigherPriorityCombatStateActive()
                || this.escapeCutting
                || isPackCoordinating()
                || this.backpedalTicks > 0
                || this.isFollowingForcedPath()) {
            this.lineOfSightStalkingStatus = "higher_priority_active";
            return false;
        }
        if (!(target instanceof Player) && !target.getTags().contains(LINE_OF_SIGHT_STALKING_TEST_TARGET_TAG)) {
            this.lineOfSightStalkingStatus = "target_ineligible";
            return false;
        }
        if (!this.hasLineOfSight(target) || !target.hasLineOfSight(this)) {
            this.lineOfSightStalkingStatus = "no_line_of_sight";
            return false;
        }

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr < LINE_OF_SIGHT_STALKING_MIN_RANGE_SQR
                || distanceSqr > LINE_OF_SIGHT_STALKING_MAX_RANGE_SQR) {
            this.lineOfSightStalkingStatus = "range";
            return false;
        }
        if (!isTargetLookingAtSpider(target)) {
            this.lineOfSightStalkingStatus = "target_not_watching";
            return false;
        }
        this.lineOfSightStalkingStatus = "eligible";
        return true;
    }

    private void startLineOfSightStalking(LivingEntity target) {
        this.lineOfSightStalkingTargetId = target.getUUID();
        this.lineOfSightStalkingTicks = LINE_OF_SIGHT_STALKING_TICKS;
        this.lineOfSightStalkingStartPosition = this.position();
        this.lineOfSightStalkingStartDistance = Math.sqrt(this.distanceToSqr(target));
        this.lineOfSightStalkingCurrentDistance = this.lineOfSightStalkingStartDistance;
        this.lineOfSightStalkingMinDistance = this.lineOfSightStalkingStartDistance;
        this.lineOfSightStalkingTotalMovement = 0.0D;
        this.lineOfSightStalkingMaxWatchedMovement = 0.0D;
        this.lineOfSightStalkingWatchedTicks = 0;
        this.lineOfSightStalkingUnwatchedTicks = 0;
        this.lineOfSightStalkingFacingTicks = 0;
        this.lineOfSightStalkingTargetLooking = true;
        this.lineOfSightStalkingSawWatched = false;
        this.lineOfSightStalkingSawUnwatchedAdvance = false;
        this.lineOfSightStalkingStatus = "watched_hold";
        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setDeltaMovement(Vec3.ZERO);
        this.setSpeed(0.0F);
        this.setXxa(0.0F);
        this.setZza(0.0F);
        faceCombatTarget(target);
    }

    private void holdLineOfSightStalking(LivingEntity target) {
        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setDeltaMovement(Vec3.ZERO);
        this.setSpeed(0.0F);
        this.setXxa(0.0F);
        this.setZza(0.0F);
        faceCombatTarget(target);
        updateLineOfSightStalkingMetrics(target, true);
        this.lineOfSightStalkingWatchedTicks++;
        this.lineOfSightStalkingSawWatched = true;
        this.lineOfSightStalkingStatus = "watched_hold";
    }

    private void advanceLineOfSightStalking(LivingEntity target) {
        clearCombatPacingSpeedModifier();
        faceCombatTarget(target);
        this.getNavigation().stop();

        Vec3 toTarget = target.position().subtract(this.position());
        Vec3 horizontal = new Vec3(toTarget.x, 0.0D, toTarget.z);
        if (horizontal.lengthSqr() > 1.0E-6D && this.distanceToSqr(target) > LINE_OF_SIGHT_STALKING_CLOSE_STOP_SQR) {
            Vec3 step = horizontal.normalize().scale(Math.min(LINE_OF_SIGHT_STALKING_ADVANCE_STEP, horizontal.length() * 0.35D));
            if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
                step = step.scale(0.5D);
                if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
                    step = step.scale(0.5D);
                    if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
                        step = Vec3.ZERO;
                    }
                }
            }
            if (step.lengthSqr() > 1.0E-6D) {
                this.move(MoverType.SELF, step);
                this.setDeltaMovement(step);
                this.setSpeed((float) Math.max(LINE_OF_SIGHT_STALKING_ADVANCE_SPEED * 0.35D,
                        step.horizontalDistance()));
                this.setXxa((float) step.x);
                this.setZza((float) step.z);
            }
        }

        updateLineOfSightStalkingMetrics(target, false);
        this.lineOfSightStalkingUnwatchedTicks++;
        this.lineOfSightStalkingSawUnwatchedAdvance = true;
        this.lineOfSightStalkingStatus = "unwatched_advance";
    }

    private void updateLineOfSightStalkingMetrics(LivingEntity target, boolean watched) {
        this.lineOfSightStalkingCurrentDistance = Math.sqrt(this.distanceToSqr(target));
        this.lineOfSightStalkingMinDistance = Math.min(this.lineOfSightStalkingMinDistance,
                this.lineOfSightStalkingCurrentDistance);
        double movement = this.position().distanceTo(this.lineOfSightStalkingStartPosition);
        this.lineOfSightStalkingTotalMovement = Math.max(this.lineOfSightStalkingTotalMovement, movement);
        if (watched) {
            this.lineOfSightStalkingMaxWatchedMovement =
                    Math.max(this.lineOfSightStalkingMaxWatchedMovement, movement);
        }
        if (isFacingCombatTarget(target, LINE_OF_SIGHT_STALKING_FACING_DEGREES)) {
            this.lineOfSightStalkingFacingTicks++;
        }
    }

    private boolean isTargetLookingAtSpider(LivingEntity target) {
        if (!target.hasLineOfSight(this)) {
            return false;
        }
        Vec3 look = target.getLookAngle();
        if (look.lengthSqr() <= 1.0E-6D) {
            return false;
        }
        Vec3 toSpider = this.getEyePosition().subtract(target.getEyePosition());
        if (toSpider.lengthSqr() <= 1.0E-6D) {
            return true;
        }
        return look.normalize().dot(toSpider.normalize()) >= LINE_OF_SIGHT_STALKING_LOOK_DOT;
    }

    private void finishLineOfSightStalking() {
        this.lineOfSightStalkingCooldownTicks =
                Math.max(this.lineOfSightStalkingCooldownTicks, LINE_OF_SIGHT_STALKING_COOLDOWN_TICKS);
        resetLineOfSightStalking(false);
    }

    private void resetLineOfSightStalking(boolean clearCooldown) {
        this.lineOfSightStalkingTicks = 0;
        this.lineOfSightStalkingTargetId = null;
        this.lineOfSightStalkingStartPosition = Vec3.ZERO;
        this.lineOfSightStalkingStartDistance = 0.0D;
        this.lineOfSightStalkingCurrentDistance = 0.0D;
        this.lineOfSightStalkingMinDistance = 0.0D;
        this.lineOfSightStalkingTotalMovement = 0.0D;
        this.lineOfSightStalkingMaxWatchedMovement = 0.0D;
        this.lineOfSightStalkingWatchedTicks = 0;
        this.lineOfSightStalkingUnwatchedTicks = 0;
        this.lineOfSightStalkingFacingTicks = 0;
        this.lineOfSightStalkingTargetLooking = false;
        this.lineOfSightStalkingSawWatched = false;
        this.lineOfSightStalkingSawUnwatchedAdvance = false;
        this.lineOfSightStalkingStatus = "idle";
        if (clearCooldown) {
            this.lineOfSightStalkingCooldownTicks = 0;
        }
    }

    private boolean updateDarknessPreference(LivingEntity target) {
        if (!canUseCombatPacing(target)) {
            resetDarknessPreference(false);
            return false;
        }

        UUID targetId = target.getUUID();
        if (isDarknessPreferenceActive()
                && (this.darknessPreferenceTargetId == null || !this.darknessPreferenceTargetId.equals(targetId))) {
            resetDarknessPreference(false);
        }

        if (!isDarknessPreferenceActive()) {
            if (!canStartDarknessPreference(target)) {
                return false;
            }
            DarknessPreferenceCandidate candidate = findDarknessPreferenceCandidate(target);
            if (candidate == null) {
                this.darknessPreferenceStatus = "no_candidate";
                return false;
            }
            startDarknessPreference(target, candidate);
        }

        if (this.darknessPreferenceAnchor == null
                || !isDarknessPreferenceCandidateCell(this.darknessPreferenceAnchor, this.darknessPreferenceAttachment)) {
            this.darknessPreferenceStatus = "invalid_anchor";
            resetDarknessPreference(false);
            return false;
        }
        if (this.darknessPreferenceTicks <= 0) {
            finishDarknessPreference();
            return false;
        }

        this.darknessPreferenceTicks--;
        return applyDarknessPreferenceMovement(target);
    }

    private boolean canStartDarknessPreference(LivingEntity target) {
        if (!canUseCombatPacing(target)) {
            this.darknessPreferenceStatus = "combat_unavailable";
            return false;
        }
        if (this.darknessPreferenceCooldownTicks > 0) {
            this.darknessPreferenceStatus = "cooldown";
            return false;
        }
        if (hasHigherPriorityCombatStateActive()
                || this.escapeCutting
                || isLineOfSightStalking()
                || isPackCoordinating()
                || this.backpedalTicks > 0
                || this.isFollowingForcedPath()) {
            this.darknessPreferenceStatus = "higher_priority_active";
            return false;
        }
        if (!(target instanceof Player) && !target.getTags().contains(DARKNESS_PREFERENCE_TEST_TARGET_TAG)) {
            this.darknessPreferenceStatus = "target_ineligible";
            return false;
        }
        this.darknessPreferenceStatus = "eligible";
        return true;
    }

    private DarknessPreferenceCandidate findDarknessPreferenceCandidate(LivingEntity target) {
        BlockPos targetBlock = target.blockPosition();
        int minY = Math.max(this.level.getMinBuildHeight() + 1, targetBlock.getY());
        int maxY = Math.min(this.level.getMaxBuildHeight() - 2, targetBlock.getY() + DARKNESS_PREFERENCE_VERTICAL_SEARCH);
        int openLight = darknessPreferenceLight(targetBlock);
        double openScore = darknessPreferenceOpenScore(targetBlock);
        DarknessPreferenceCandidate best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int dy = 0; dy <= maxY - minY; dy++) {
            int y = minY + dy;
            for (int dx = -DARKNESS_PREFERENCE_HORIZONTAL_SEARCH; dx <= DARKNESS_PREFERENCE_HORIZONTAL_SEARCH; dx++) {
                for (int dz = -DARKNESS_PREFERENCE_HORIZONTAL_SEARCH; dz <= DARKNESS_PREFERENCE_HORIZONTAL_SEARCH; dz++) {
                    BlockPos candidatePos = new BlockPos(targetBlock.getX() + dx, y, targetBlock.getZ() + dz);
                    for (Direction attachment : Direction.values()) {
                        if (!isDarknessPreferenceCandidateCell(candidatePos, attachment)) {
                            continue;
                        }
                        Vec3 anchor = AttachmentHelper.anchorFor(this, candidatePos, attachment);
                        if (anchor.distanceToSqr(target.position()) < DARKNESS_PREFERENCE_MIN_TARGET_SEPARATION_SQR) {
                            continue;
                        }

                        int light = darknessPreferenceLight(candidatePos);
                        int coverCount = countDarknessPreferenceCover(candidatePos, attachment);
                        int wallAdjacentCount = countHorizontalSolidNeighbors(candidatePos);
                        boolean covered = hasDarknessPreferenceCover(candidatePos, attachment);
                        boolean corner = wallAdjacentCount >= 2;
                        double score = darknessPreferenceScore(candidatePos, attachment, target, light, coverCount,
                                wallAdjacentCount, covered, corner);
                        boolean meaningfullyBetter = score <= openScore - DARKNESS_PREFERENCE_MIN_SCORE_ADVANTAGE
                                && (light <= openLight - 2 || covered || corner || attachment != Direction.DOWN);
                        if (!meaningfullyBetter) {
                            continue;
                        }
                        if (score < bestScore) {
                            bestScore = score;
                            best = new DarknessPreferenceCandidate(candidatePos.immutable(), attachment, score, openScore,
                                    light, openLight, coverCount, wallAdjacentCount, covered, corner);
                        }
                    }
                }
            }
        }
        return best;
    }

    private boolean isDarknessPreferenceCandidateCell(BlockPos pos, Direction attachment) {
        if (!AttachmentHelper.hasSupport(this.level, pos, attachment)
                || !AttachmentHelper.aabbFitsOnSurface(this.level, this, pos, attachment)) {
            return false;
        }
        if (attachment == Direction.DOWN) {
            return isWalkableEscapeCell(pos);
        }
        BlockState state = this.level.getBlockState(pos);
        return state.getCollisionShape(this.level, pos).isEmpty()
                && this.level.getFluidState(pos).isEmpty();
    }

    private double darknessPreferenceScore(BlockPos pos, Direction attachment, LivingEntity target, int light,
            int coverCount, int wallAdjacentCount, boolean covered, boolean corner) {
        double attachmentBonus = attachment == Direction.UP ? 2.8D
                : attachment.getAxis().isHorizontal() ? 2.0D : 0.0D;
        double currentDistanceScore = this.position().distanceToSqr(AttachmentHelper.anchorFor(this, pos, attachment)) * 0.045D;
        double targetDistance = target.position().distanceToSqr(AttachmentHelper.anchorFor(this, pos, attachment));
        double targetDistanceScore = Math.abs(targetDistance - 18.0D) * 0.035D;
        double skyPenalty = this.level.canSeeSky(pos.above()) ? 5.0D : 0.0D;
        return light * 3.0D
                - coverCount * 1.15D
                - wallAdjacentCount * 1.75D
                - (covered ? 2.0D : 0.0D)
                - (corner ? 4.0D : 0.0D)
                - attachmentBonus
                + currentDistanceScore
                + targetDistanceScore
                + skyPenalty;
    }

    private double darknessPreferenceOpenScore(BlockPos pos) {
        int light = darknessPreferenceLight(pos);
        int coverCount = countDarknessPreferenceCover(pos, Direction.DOWN);
        int wallAdjacentCount = countHorizontalSolidNeighbors(pos);
        boolean covered = hasDarknessPreferenceCover(pos, Direction.DOWN);
        boolean corner = wallAdjacentCount >= 2;
        double skyPenalty = this.level.canSeeSky(pos.above()) ? 5.0D : 0.0D;
        return light * 3.0D
                - coverCount * 1.15D
                - wallAdjacentCount * 1.75D
                - (covered ? 2.0D : 0.0D)
                - (corner ? 4.0D : 0.0D)
                + skyPenalty;
    }

    private int darknessPreferenceLight(BlockPos pos) {
        return this.level.getMaxLocalRawBrightness(pos);
    }

    private int countDarknessPreferenceCover(BlockPos pos, Direction attachment) {
        int count = 0;
        if (AttachmentHelper.hasSupport(this.level, pos, Direction.UP)) {
            count += 2;
        }
        if (hasStandingSupport(pos.above(2))) {
            count++;
        }
        if (attachment.getAxis().isHorizontal()) {
            count++;
        }
        count += Math.min(2, countHorizontalSolidNeighbors(pos));
        return count;
    }

    private boolean hasDarknessPreferenceCover(BlockPos pos, Direction attachment) {
        return AttachmentHelper.hasSupport(this.level, pos, Direction.UP)
                || hasStandingSupport(pos.above(2))
                || attachment.getAxis().isHorizontal()
                || countHorizontalSolidNeighbors(pos) >= 2;
    }

    private int countHorizontalSolidNeighbors(BlockPos pos) {
        int count = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (hasStandingSupport(pos.relative(direction))) {
                count++;
            }
        }
        return count;
    }

    private void startDarknessPreference(LivingEntity target, DarknessPreferenceCandidate candidate) {
        this.darknessPreference = true;
        this.darknessPreferenceTicks = DARKNESS_PREFERENCE_TICKS;
        this.darknessPreferenceTargetId = target.getUUID();
        this.darknessPreferenceAnchor = candidate.anchor;
        this.darknessPreferenceAttachment = candidate.attachment;
        this.darknessPreferencePathStarted = false;
        this.darknessPreferenceReachedAnchor = false;
        this.darknessPreferenceHeldAnchor = false;
        this.darknessPreferenceFacingTicks = 0;
        this.darknessPreferenceAnchorLight = candidate.anchorLight;
        this.darknessPreferenceCurrentLight = darknessPreferenceLight(this.blockPosition());
        this.darknessPreferenceOpenLight = candidate.openLight;
        this.darknessPreferenceCoverCount = candidate.coverCount;
        this.darknessPreferenceWallAdjacentCount = candidate.wallAdjacentCount;
        this.darknessPreferenceCovered = candidate.covered;
        this.darknessPreferenceCorner = candidate.corner;
        this.darknessPreferenceAnchorScore = candidate.anchorScore;
        this.darknessPreferenceOpenScore = candidate.openScore;
        double distance = distanceToDarknessPreferenceAnchor();
        this.darknessPreferenceStartAnchorDistance = distance;
        this.darknessPreferenceCurrentAnchorDistance = distance;
        this.darknessPreferenceMinAnchorDistance = distance;
        this.darknessPreferenceRepathTicks = 0;
        this.darknessPreferenceStatus = "started";
        clearCombatPacingSpeedModifier();
        faceCombatTarget(target);
    }

    private boolean applyDarknessPreferenceMovement(LivingEntity target) {
        if (this.darknessPreferenceAnchor == null) {
            return false;
        }

        faceCombatTarget(target);
        clearCombatPacingSpeedModifier();
        this.darknessPreferenceCurrentLight = darknessPreferenceLight(this.blockPosition());
        double distance = distanceToDarknessPreferenceAnchor();
        this.darknessPreferenceCurrentAnchorDistance = distance;
        this.darknessPreferenceMinAnchorDistance = Math.min(this.darknessPreferenceMinAnchorDistance, distance);
        if (isFacingCombatTarget(target, DARKNESS_PREFERENCE_FACING_DEGREES)) {
            this.darknessPreferenceFacingTicks++;
        }

        if (distance * distance <= DARKNESS_PREFERENCE_HOLD_DISTANCE_SQR) {
            this.darknessPreferenceReachedAnchor = true;
            this.darknessPreferenceHeldAnchor = true;
            this.darknessPreferenceStatus = "holding";
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            return true;
        }

        if (this.darknessPreferenceRepathTicks > 0) {
            this.darknessPreferenceRepathTicks--;
        }
        if (this.darknessPreferenceRepathTicks <= 0 || this.getNavigation().isDone()) {
            Path path = this.getNavigation().createPath(this.darknessPreferenceAnchor, 0);
            if (path != null) {
                boolean started = this.getNavigation().moveTo(path, DARKNESS_PREFERENCE_NAVIGATION_SPEED);
                this.darknessPreferencePathStarted = this.darknessPreferencePathStarted || started;
                this.darknessPreferenceStatus = started ? "moving" : "path_not_started";
            } else {
                this.darknessPreferenceStatus = "path_null";
            }
            this.darknessPreferenceRepathTicks = DARKNESS_PREFERENCE_REPATH_TICKS;
        }

        Vec3 anchorPoint = darknessPreferenceAnchorPoint();
        this.getMoveControl().setWantedPosition(anchorPoint.x, anchorPoint.y, anchorPoint.z,
                DARKNESS_PREFERENCE_NAVIGATION_SPEED);
        boolean stepped = applyDarknessPreferenceAnchorStep();
        if (stepped) {
            this.darknessPreferencePathStarted = true;
            this.darknessPreferenceStatus = "moving_direct";
        } else {
            this.darknessPreferenceStatus = "moving";
        }
        return true;
    }

    private boolean applyDarknessPreferenceAnchorStep() {
        if (this.darknessPreferenceAnchor == null || this.getAttachmentDirection() != Direction.DOWN) {
            return false;
        }

        Vec3 toAnchor = darknessPreferenceAnchorPoint().subtract(this.position());
        Vec3 horizontal = new Vec3(toAnchor.x, 0.0D, toAnchor.z);
        double distance = horizontal.length();
        if (distance <= 1.0E-6D) {
            return false;
        }

        double stopDistance = Math.sqrt(DARKNESS_PREFERENCE_HOLD_DISTANCE_SQR);
        double stepSize = Math.min(DARKNESS_PREFERENCE_STEP, Math.max(0.0D, distance - stopDistance));
        if (stepSize <= 0.005D) {
            this.darknessPreferenceReachedAnchor = true;
            return false;
        }

        Vec3 step = horizontal.normalize().scale(stepSize);
        if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
            step = step.scale(0.5D);
            if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
                step = step.scale(0.5D);
                if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
                    return false;
                }
            }
        }

        this.move(MoverType.SELF, step);
        this.setDeltaMovement(step);
        this.setSpeed((float) step.horizontalDistance());
        this.setXxa((float) step.x);
        this.setZza((float) step.z);
        double updatedDistance = distanceToDarknessPreferenceAnchor();
        this.darknessPreferenceCurrentAnchorDistance = updatedDistance;
        this.darknessPreferenceMinAnchorDistance = Math.min(this.darknessPreferenceMinAnchorDistance, updatedDistance);
        if (updatedDistance * updatedDistance <= DARKNESS_PREFERENCE_HOLD_DISTANCE_SQR) {
            this.darknessPreferenceReachedAnchor = true;
        }
        return true;
    }

    private double distanceToDarknessPreferenceAnchor() {
        if (this.darknessPreferenceAnchor == null) {
            return 0.0D;
        }
        Vec3 anchorPoint = darknessPreferenceAnchorPoint();
        return this.position().distanceTo(anchorPoint);
    }

    private Vec3 darknessPreferenceAnchorPoint() {
        if (this.darknessPreferenceAnchor == null) {
            return this.position();
        }
        return AttachmentHelper.anchorFor(this, this.darknessPreferenceAnchor, this.darknessPreferenceAttachment);
    }

    private void finishDarknessPreference() {
        this.darknessPreferenceCooldownTicks =
                Math.max(this.darknessPreferenceCooldownTicks, DARKNESS_PREFERENCE_COOLDOWN_TICKS);
        resetDarknessPreference(false);
    }

    private void resetDarknessPreference(boolean clearCooldown) {
        this.darknessPreference = false;
        this.darknessPreferenceTicks = 0;
        this.darknessPreferenceRepathTicks = 0;
        this.darknessPreferenceTargetId = null;
        this.darknessPreferenceAnchor = null;
        this.darknessPreferenceAttachment = Direction.DOWN;
        this.darknessPreferencePathStarted = false;
        this.darknessPreferenceReachedAnchor = false;
        this.darknessPreferenceHeldAnchor = false;
        this.darknessPreferenceFacingTicks = 0;
        this.darknessPreferenceAnchorLight = 0;
        this.darknessPreferenceCurrentLight = 0;
        this.darknessPreferenceOpenLight = 0;
        this.darknessPreferenceCoverCount = 0;
        this.darknessPreferenceWallAdjacentCount = 0;
        this.darknessPreferenceCovered = false;
        this.darknessPreferenceCorner = false;
        this.darknessPreferenceAnchorScore = 0.0D;
        this.darknessPreferenceOpenScore = 0.0D;
        this.darknessPreferenceStartAnchorDistance = 0.0D;
        this.darknessPreferenceCurrentAnchorDistance = 0.0D;
        this.darknessPreferenceMinAnchorDistance = 0.0D;
        this.darknessPreferenceStatus = "idle";
        if (clearCooldown) {
            this.darknessPreferenceCooldownTicks = 0;
        }
    }

    private static final class DarknessPreferenceCandidate {
        private final BlockPos anchor;
        private final Direction attachment;
        private final double anchorScore;
        private final double openScore;
        private final int anchorLight;
        private final int openLight;
        private final int coverCount;
        private final int wallAdjacentCount;
        private final boolean covered;
        private final boolean corner;

        private DarknessPreferenceCandidate(BlockPos anchor, Direction attachment, double anchorScore, double openScore,
                int anchorLight, int openLight, int coverCount, int wallAdjacentCount, boolean covered, boolean corner) {
            this.anchor = anchor;
            this.attachment = attachment;
            this.anchorScore = anchorScore;
            this.openScore = openScore;
            this.anchorLight = anchorLight;
            this.openLight = openLight;
            this.coverCount = coverCount;
            this.wallAdjacentCount = wallAdjacentCount;
            this.covered = covered;
            this.corner = corner;
        }
    }

    private boolean updateWallPeek(LivingEntity target) {
        if (!canUseCombatPacing(target)) {
            resetWallPeek(false);
            return false;
        }

        UUID targetId = target.getUUID();
        if (isWallPeeking() && (this.wallPeekTargetId == null || !this.wallPeekTargetId.equals(targetId))) {
            resetWallPeek(false);
        }

        if (!isWallPeeking()) {
            if (!canStartWallPeek(target)) {
                return false;
            }
            WallPeekCandidate candidate = findWallPeekCandidate(target);
            if (candidate == null) {
                this.wallPeekStatus = "no_candidate";
                return false;
            }
            startWallPeek(target, candidate);
        }

        if (this.wallPeekCoverAnchor == null
                || this.wallPeekPeekAnchor == null
                || !isValidWallPeekFloorCell(this.wallPeekCoverAnchor)
                || !isValidWallPeekFloorCell(this.wallPeekPeekAnchor)) {
            this.wallPeekStatus = "invalid_anchor";
            resetWallPeek(false);
            return false;
        }

        this.wallPeekTargetRetained = this.getTarget() == target;
        updateWallPeekLineOfSight(target);
        updateWallPeekDistances();
        faceCombatTarget(target);
        if (isFacingCombatTarget(target, WALL_PEEK_FACING_DEGREES)) {
            this.wallPeekFacingTicks++;
        }

        if (this.wallPeekPhase == WALL_PEEK_EMERGING) {
            this.wallPeekStatus = "emerging";
            moveTowardWallPeekAnchor(this.wallPeekPeekAnchor);
            if (this.wallPeekCurrentPeekDistance * this.wallPeekCurrentPeekDistance <= WALL_PEEK_REACHED_SQR) {
                this.wallPeekReachedPeek = true;
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);
                this.setSpeed(0.0F);
                this.setXxa(0.0F);
                this.setZza(0.0F);
                if (this.wallPeekTicks > WALL_PEEK_EMERGE_TICKS - WALL_PEEK_EMERGE_MIN_VISIBLE_TICKS) {
                    this.wallPeekTicks--;
                    return true;
                }
                startWallPeekHold();
                return true;
            }
            this.wallPeekTicks--;
            if (this.wallPeekTicks <= 0) {
                startWallPeekHold();
            }
            return true;
        }

        if (this.wallPeekPhase == WALL_PEEK_HOLDING) {
            this.wallPeekStatus = "holding";
            this.wallPeekHeldPeek = true;
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            this.wallPeekTicks--;
            if (this.wallPeekTicks <= 0) {
                startWallPeekRetreat();
            }
            return true;
        }

        if (this.wallPeekPhase == WALL_PEEK_RETREATING) {
            this.wallPeekStatus = "retreating";
            moveTowardWallPeekAnchor(this.wallPeekCoverAnchor);
            if (this.wallPeekCurrentCoverDistance * this.wallPeekCurrentCoverDistance <= WALL_PEEK_REACHED_SQR) {
                this.wallPeekRetreated = true;
                finishWallPeek();
                return true;
            }
            this.wallPeekTicks--;
            if (this.wallPeekTicks <= 0) {
                finishWallPeek();
            }
            return true;
        }

        resetWallPeek(false);
        return false;
    }

    private boolean canStartWallPeek(LivingEntity target) {
        if (!canUseCombatPacing(target)) {
            this.wallPeekStatus = "combat_unavailable";
            return false;
        }
        if (this.wallPeekCooldownTicks > 0) {
            this.wallPeekStatus = "cooldown";
            return false;
        }
        if (hasHigherPriorityCombatStateActive()
                || this.escapeCutting
                || isLineOfSightStalking()
                || isDarknessPreferenceActive()
                || isPackCoordinating()
                || this.backpedalTicks > 0
                || this.isFollowingForcedPath()) {
            this.wallPeekStatus = "higher_priority_active";
            return false;
        }
        if (!(target instanceof Player) && !target.getTags().contains(WALL_PEEK_TEST_TARGET_TAG)) {
            this.wallPeekStatus = "target_ineligible";
            return false;
        }

        double distanceSqr = this.distanceToSqr(target);
        if (distanceSqr < WALL_PEEK_MIN_RANGE_SQR || distanceSqr > WALL_PEEK_MAX_RANGE_SQR) {
            this.wallPeekStatus = "range";
            return false;
        }
        this.wallPeekStatus = "eligible";
        return true;
    }

    private WallPeekCandidate findWallPeekCandidate(LivingEntity target) {
        BlockPos spiderBlock = this.blockPosition();
        BlockPos targetBlock = target.blockPosition();
        int minY = Math.max(this.level.getMinBuildHeight() + 1,
                Math.min(spiderBlock.getY(), targetBlock.getY()) - 1);
        int maxY = Math.min(this.level.getMaxBuildHeight() - 2,
                Math.max(spiderBlock.getY(), targetBlock.getY()) + 1);
        WallPeekCandidate best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int y = minY; y <= maxY; y++) {
            for (int dx = -WALL_PEEK_SEARCH_HORIZONTAL; dx <= WALL_PEEK_SEARCH_HORIZONTAL; dx++) {
                for (int dz = -WALL_PEEK_SEARCH_HORIZONTAL; dz <= WALL_PEEK_SEARCH_HORIZONTAL; dz++) {
                    BlockPos cover = new BlockPos(spiderBlock.getX() + dx, y, spiderBlock.getZ() + dz);
                    if (!isValidWallPeekFloorCell(cover) || countHorizontalSolidNeighbors(cover) < 1) {
                        continue;
                    }
                    boolean coverBlocked = !hasLineOfSightFrom(wallPeekSightPoint(cover), target);
                    if (!coverBlocked) {
                        continue;
                    }

                    for (Direction peekDirection : Direction.Plane.HORIZONTAL) {
                        BlockPos peek = cover.relative(peekDirection);
                        if (!isValidWallPeekFloorCell(peek) || peek.equals(targetBlock)) {
                            continue;
                        }
                        boolean peekClear = hasLineOfSightFrom(wallPeekSightPoint(peek), target);
                        if (!peekClear) {
                            continue;
                        }
                        Vec3 coverPoint = wallPeekAnchorPoint(cover);
                        Vec3 peekPoint = wallPeekAnchorPoint(peek);
                        double coverStartDistanceSqr = this.position().distanceToSqr(coverPoint);
                        double peekStartDistanceSqr = this.position().distanceToSqr(peekPoint);
                        if (peekStartDistanceSqr <= coverStartDistanceSqr + 0.25D) {
                            continue;
                        }
                        double targetDistanceSqr = peekPoint.distanceToSqr(target.position());
                        if (targetDistanceSqr < WALL_PEEK_MIN_RANGE_SQR || targetDistanceSqr > WALL_PEEK_MAX_RANGE_SQR) {
                            continue;
                        }
                        double score = coverStartDistanceSqr
                                + coverPoint.distanceToSqr(peekPoint) * 0.35D
                                + Math.abs(targetDistanceSqr - 36.0D) * 0.025D;
                        if (score < bestScore) {
                            bestScore = score;
                            best = new WallPeekCandidate(cover.immutable(), peek.immutable(), coverBlocked, peekClear);
                        }
                    }
                }
            }
        }
        return best;
    }

    private boolean isValidWallPeekFloorCell(BlockPos pos) {
        return pos.getY() > this.level.getMinBuildHeight()
                && pos.getY() < this.level.getMaxBuildHeight() - 1
                && isBodyOpen(pos)
                && isBodyOpen(pos.above())
                && hasStandingSupport(pos.below());
    }

    private boolean hasLineOfSightFrom(Vec3 from, LivingEntity target) {
        ClipContext context = new ClipContext(from, target.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this);
        HitResult result = this.level.clip(context);
        return result.getType() == HitResult.Type.MISS;
    }

    private void startWallPeek(LivingEntity target, WallPeekCandidate candidate) {
        this.wallPeekPhase = WALL_PEEK_EMERGING;
        this.wallPeekTicks = WALL_PEEK_EMERGE_TICKS;
        this.wallPeekRepathTicks = 0;
        this.wallPeekTargetId = target.getUUID();
        this.wallPeekCoverAnchor = candidate.cover;
        this.wallPeekPeekAnchor = candidate.peek;
        this.wallPeekPathStarted = false;
        this.wallPeekReachedPeek = false;
        this.wallPeekHeldPeek = false;
        this.wallPeekRetreated = false;
        this.wallPeekTargetRetained = this.getTarget() == target;
        this.wallPeekCoverLineOfSightBlocked = candidate.coverLineOfSightBlocked;
        this.wallPeekPeekLineOfSightClear = candidate.peekLineOfSightClear;
        this.wallPeekFacingTicks = 0;
        this.wallPeekStartPeekDistance = distanceToWallPeekAnchor(this.wallPeekPeekAnchor);
        this.wallPeekCurrentPeekDistance = this.wallPeekStartPeekDistance;
        this.wallPeekMinPeekDistance = this.wallPeekStartPeekDistance;
        this.wallPeekStartCoverDistance = distanceToWallPeekAnchor(this.wallPeekCoverAnchor);
        this.wallPeekCurrentCoverDistance = this.wallPeekStartCoverDistance;
        this.wallPeekMinCoverDistance = this.wallPeekStartCoverDistance;
        this.wallPeekStatus = "emerging";
        clearCombatPacingSpeedModifier();
        this.playSound(SoundEvents.SPIDER_AMBIENT, 0.38F, 0.75F);
        faceCombatTarget(target);
    }

    private void startWallPeekHold() {
        this.wallPeekPhase = WALL_PEEK_HOLDING;
        this.wallPeekTicks = WALL_PEEK_HOLD_TICKS;
        this.wallPeekHeldPeek = true;
        this.wallPeekStatus = "holding";
        this.wallPeekRepathTicks = 0;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        updateWallPeekDistances();
    }

    private void startWallPeekRetreat() {
        this.wallPeekPhase = WALL_PEEK_RETREATING;
        this.wallPeekTicks = WALL_PEEK_RETREAT_TICKS;
        this.wallPeekRepathTicks = 0;
        this.wallPeekStatus = "retreating";
        this.wallPeekStartCoverDistance = distanceToWallPeekAnchor(this.wallPeekCoverAnchor);
        this.wallPeekCurrentCoverDistance = this.wallPeekStartCoverDistance;
        this.wallPeekMinCoverDistance = this.wallPeekStartCoverDistance;
    }

    private void moveTowardWallPeekAnchor(BlockPos anchor) {
        if (anchor == null) {
            return;
        }
        clearCombatPacingSpeedModifier();
        if (this.wallPeekRepathTicks > 0) {
            this.wallPeekRepathTicks--;
        }
        if (this.wallPeekRepathTicks <= 0 || this.getNavigation().isDone()) {
            Path path = this.getNavigation().createPath(anchor, 0);
            if (path != null) {
                boolean started = this.getNavigation().moveTo(path, WALL_PEEK_NAVIGATION_SPEED);
                this.wallPeekPathStarted = this.wallPeekPathStarted || started;
            }
            this.wallPeekRepathTicks = WALL_PEEK_REPATH_TICKS;
        }

        Vec3 anchorPoint = wallPeekAnchorPoint(anchor);
        this.getMoveControl().setWantedPosition(anchorPoint.x, anchorPoint.y, anchorPoint.z,
                WALL_PEEK_NAVIGATION_SPEED);
        if (applyWallPeekAnchorStep(anchor)) {
            this.wallPeekPathStarted = true;
        }
        updateWallPeekDistances();
    }

    private boolean applyWallPeekAnchorStep(BlockPos anchor) {
        if (anchor == null || this.getAttachmentDirection() != Direction.DOWN) {
            return false;
        }
        Vec3 toAnchor = wallPeekAnchorPoint(anchor).subtract(this.position());
        Vec3 horizontal = new Vec3(toAnchor.x, 0.0D, toAnchor.z);
        double distance = horizontal.length();
        if (distance <= 1.0E-6D) {
            return false;
        }

        double stopDistance = Math.sqrt(WALL_PEEK_REACHED_SQR);
        double stepSize = Math.min(WALL_PEEK_STEP, Math.max(0.0D, distance - stopDistance));
        if (stepSize <= 0.005D) {
            return false;
        }

        Vec3 step = horizontal.normalize().scale(stepSize);
        if (!canTakeWallPeekStep(step)) {
            step = step.scale(0.5D);
            if (!canTakeWallPeekStep(step)) {
                step = step.scale(0.5D);
                if (!canTakeWallPeekStep(step)) {
                    return false;
                }
            }
        }

        this.move(MoverType.SELF, step);
        this.setDeltaMovement(step);
        this.setSpeed((float) Math.max(WALL_PEEK_NAVIGATION_SPEED * 0.35D, step.horizontalDistance()));
        this.setXxa((float) step.x);
        this.setZza((float) step.z);
        return true;
    }

    private boolean canTakeWallPeekStep(Vec3 step) {
        if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
            return false;
        }
        BlockPos next = new BlockPos(this.getX() + step.x, this.getY(), this.getZ() + step.z);
        return isValidWallPeekFloorCell(next);
    }

    private void updateWallPeekLineOfSight(LivingEntity target) {
        if (this.wallPeekCoverAnchor != null) {
            this.wallPeekCoverLineOfSightBlocked = !hasLineOfSightFrom(wallPeekSightPoint(this.wallPeekCoverAnchor), target);
        }
        if (this.wallPeekPeekAnchor != null) {
            this.wallPeekPeekLineOfSightClear = hasLineOfSightFrom(wallPeekSightPoint(this.wallPeekPeekAnchor), target);
        }
    }

    private void updateWallPeekDistances() {
        this.wallPeekCurrentPeekDistance = distanceToWallPeekAnchor(this.wallPeekPeekAnchor);
        this.wallPeekCurrentCoverDistance = distanceToWallPeekAnchor(this.wallPeekCoverAnchor);
        this.wallPeekMinPeekDistance = Math.min(this.wallPeekMinPeekDistance, this.wallPeekCurrentPeekDistance);
        this.wallPeekMinCoverDistance = Math.min(this.wallPeekMinCoverDistance, this.wallPeekCurrentCoverDistance);
    }

    private double distanceToWallPeekAnchor(BlockPos anchor) {
        return anchor == null ? 0.0D : this.position().distanceTo(wallPeekAnchorPoint(anchor));
    }

    private Vec3 wallPeekAnchorPoint(BlockPos pos) {
        return pos == null ? this.position() : new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    private Vec3 wallPeekSightPoint(BlockPos pos) {
        return pos == null ? this.getEyePosition()
                : new Vec3(pos.getX() + 0.5D, pos.getY() + Math.min(1.0D, this.getBbHeight() * 0.65D),
                        pos.getZ() + 0.5D);
    }

    private void finishWallPeek() {
        this.wallPeekCooldownTicks = Math.max(this.wallPeekCooldownTicks, WALL_PEEK_COOLDOWN_TICKS);
        resetWallPeek(false);
    }

    private void resetWallPeek(boolean clearCooldown) {
        this.wallPeekPhase = WALL_PEEK_NONE;
        this.wallPeekTicks = 0;
        this.wallPeekRepathTicks = 0;
        this.wallPeekTargetId = null;
        this.wallPeekStatus = "idle";
        if (clearCooldown) {
            this.wallPeekCooldownTicks = 0;
            this.wallPeekCoverAnchor = null;
            this.wallPeekPeekAnchor = null;
            this.wallPeekPathStarted = false;
            this.wallPeekReachedPeek = false;
            this.wallPeekHeldPeek = false;
            this.wallPeekRetreated = false;
            this.wallPeekTargetRetained = false;
            this.wallPeekCoverLineOfSightBlocked = false;
            this.wallPeekPeekLineOfSightClear = false;
            this.wallPeekFacingTicks = 0;
            this.wallPeekStartPeekDistance = 0.0D;
            this.wallPeekCurrentPeekDistance = 0.0D;
            this.wallPeekMinPeekDistance = 0.0D;
            this.wallPeekStartCoverDistance = 0.0D;
            this.wallPeekCurrentCoverDistance = 0.0D;
            this.wallPeekMinCoverDistance = 0.0D;
        }
    }

    private void maybeStartPreyInteractionAfterDamage(LivingEntity target) {
        if (target == null || target.isAlive() || this.level.isClientSide) {
            return;
        }
        if (!canStartPreyInteraction(target)) {
            return;
        }
        startPreyInteraction(target);
    }

    private boolean canStartPreyInteraction(LivingEntity prey) {
        if (this.isNoAi()) {
            this.preyInteractionStatus = "no_ai";
            return false;
        }
        if (this.isFollowingForcedPath()) {
            this.preyInteractionStatus = "forced_path";
            return false;
        }
        if (this.preyInteractionCooldownTicks > 0 || isPreyInteracting()) {
            this.preyInteractionStatus = "cooldown";
            return false;
        }
        if (hasHigherPriorityCombatStateActive()
                || this.escapeCutting
                || isLineOfSightStalking()
                || isDarknessPreferenceActive()
                || isWallPeeking()
                || isPackCoordinating()) {
            this.preyInteractionStatus = "higher_priority_active";
            return false;
        }
        if (!isPreyInteractionTarget(prey)) {
            this.preyInteractionStatus = "target_ineligible";
            return false;
        }
        this.preyInteractionStatus = "eligible";
        return true;
    }

    private boolean isPreyInteractionTarget(LivingEntity prey) {
        if (prey == null || prey instanceof Player || prey instanceof GroundSpiderEntity || prey instanceof IronGolem) {
            return false;
        }
        if (prey.getTags().contains(PREY_INTERACTION_TEST_TARGET_TAG)) {
            return true;
        }
        return prey instanceof Animal && !(prey instanceof Monster);
    }

    private void startPreyInteraction(LivingEntity prey) {
        BlockPos preyAnchor = normalizePreyInteractionAnchor(prey.blockPosition());
        BlockPos guardAnchor = findPreyInteractionGuardAnchor(preyAnchor);
        if (guardAnchor == null) {
            this.preyInteractionStatus = "no_guard_anchor";
            return;
        }

        this.preyInteractionPhase = PREY_INTERACTION_WEBBING;
        this.preyInteractionTicks = PREY_INTERACTION_WEBBING_TICKS;
        this.preyInteractionRepathTicks = 0;
        this.preyInteractionTargetId = prey.getUUID();
        this.preyInteractionPreyType = prey.getType().getDescriptionId();
        this.preyInteractionPreyAnchor = preyAnchor;
        this.preyInteractionGuardAnchor = guardAnchor;
        this.preyInteractionPathStarted = false;
        this.preyInteractionReachedGuard = false;
        this.preyInteractionHeldGuard = false;
        this.preyInteractionPlacedWeb = false;
        this.preyInteractionPlacedWebCount = 0;
        this.preyInteractionTargetKilled = true;
        this.preyInteractionFacingTicks = 0;
        this.preyInteractionStartGuardDistance = distanceToPreyInteractionGuard();
        this.preyInteractionCurrentGuardDistance = this.preyInteractionStartGuardDistance;
        this.preyInteractionMinGuardDistance = this.preyInteractionStartGuardDistance;
        this.preyInteractionStatus = "webbing";
        this.setTarget(null);
        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.playSound(SoundEvents.SPIDER_AMBIENT, 0.34F, 0.62F);
        facePreyInteractionAnchor();
    }

    private BlockPos normalizePreyInteractionAnchor(BlockPos anchor) {
        BlockPos clamped = new BlockPos(anchor.getX(),
                Mth.clamp(anchor.getY(), this.level.getMinBuildHeight() + 1, this.level.getMaxBuildHeight() - 2),
                anchor.getZ());
        if (isBodyOpen(clamped) && hasStandingSupport(clamped.below())) {
            return clamped.immutable();
        }
        if (isBodyOpen(clamped.above()) && hasStandingSupport(clamped)) {
            return clamped.above().immutable();
        }
        if (isBodyOpen(clamped.below()) && hasStandingSupport(clamped.below().below())) {
            return clamped.below().immutable();
        }
        return clamped.immutable();
    }

    private boolean updatePreyInteraction() {
        if (this.isNoAi() || this.isFollowingForcedPath()) {
            this.preyInteractionStatus = this.isFollowingForcedPath() ? "forced_path" : "no_ai";
            resetPreyInteraction(false);
            return false;
        }
        if (this.preyInteractionPreyAnchor == null || this.preyInteractionGuardAnchor == null) {
            this.preyInteractionStatus = "missing_anchor";
            resetPreyInteraction(false);
            return false;
        }
        if (!isValidPreyInteractionGuardCell(this.preyInteractionGuardAnchor)) {
            BlockPos replacement = findPreyInteractionGuardAnchor(this.preyInteractionPreyAnchor);
            if (replacement == null) {
                this.preyInteractionStatus = "invalid_guard_anchor";
                resetPreyInteraction(false);
                return false;
            }
            this.preyInteractionGuardAnchor = replacement;
            this.preyInteractionRepathTicks = 0;
        }

        updatePreyInteractionDistances();
        facePreyInteractionAnchor();
        if (isFacingPreyInteractionAnchor()) {
            this.preyInteractionFacingTicks++;
        }

        if (this.preyInteractionPhase == PREY_INTERACTION_WEBBING) {
            this.preyInteractionStatus = "webbing";
            placePreyInteractionWebs();
            moveTowardPreyInteractionGuard();
            this.preyInteractionTicks--;
            if (this.preyInteractionTicks <= 0) {
                startPreyInteractionGuarding();
            }
            return true;
        }

        if (this.preyInteractionPhase == PREY_INTERACTION_GUARDING) {
            this.preyInteractionStatus = "guarding";
            if (this.preyInteractionCurrentGuardDistance * this.preyInteractionCurrentGuardDistance > PREY_INTERACTION_REACHED_SQR) {
                moveTowardPreyInteractionGuard();
                if (this.preyInteractionCurrentGuardDistance * this.preyInteractionCurrentGuardDistance <= PREY_INTERACTION_REACHED_SQR) {
                    holdPreyInteractionGuard();
                }
            } else {
                holdPreyInteractionGuard();
            }

            this.preyInteractionTicks--;
            if (this.preyInteractionTicks <= 0) {
                finishPreyInteraction();
            }
            return true;
        }

        resetPreyInteraction(false);
        return false;
    }

    private void holdPreyInteractionGuard() {
        this.preyInteractionReachedGuard = true;
        this.preyInteractionHeldGuard = true;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setSpeed(0.0F);
        this.setXxa(0.0F);
        this.setZza(0.0F);
    }

    private void startPreyInteractionGuarding() {
        this.preyInteractionPhase = PREY_INTERACTION_GUARDING;
        this.preyInteractionTicks = PREY_INTERACTION_GUARD_TICKS;
        this.preyInteractionRepathTicks = 0;
        this.preyInteractionStatus = "guarding";
        updatePreyInteractionDistances();
    }

    private void placePreyInteractionWebs() {
        if (this.preyInteractionPreyAnchor == null || this.preyInteractionPlacedWebCount >= PREY_INTERACTION_MAX_WEBS) {
            return;
        }

        int placedThisTick = 0;
        for (int radius = 0; radius <= PREY_INTERACTION_WEB_SEARCH_HORIZONTAL; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos pos = this.preyInteractionPreyAnchor.offset(dx, 0, dz);
                    if (!canPlacePreyInteractionWeb(pos)) {
                        continue;
                    }
                    this.level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
                    this.preyInteractionPlacedWeb = true;
                    this.preyInteractionPlacedWebCount++;
                    placedThisTick++;
                    if (placedThisTick >= 2 || this.preyInteractionPlacedWebCount >= PREY_INTERACTION_MAX_WEBS) {
                        return;
                    }
                }
            }
        }
    }

    private boolean canPlacePreyInteractionWeb(BlockPos pos) {
        if (pos == null
                || pos.getY() <= this.level.getMinBuildHeight()
                || pos.getY() >= this.level.getMaxBuildHeight() - 1
                || pos.equals(this.preyInteractionGuardAnchor)) {
            return false;
        }
        BlockState state = this.level.getBlockState(pos);
        if (!state.isAir()) {
            return false;
        }
        if (!hasStandingSupport(pos.below())) {
            return false;
        }
        if (this.getBoundingBox().intersects(new AABB(pos))) {
            return false;
        }
        return this.level.getEntitiesOfClass(Player.class, new AABB(pos), Player::isAlive).isEmpty();
    }

    private BlockPos findPreyInteractionGuardAnchor(BlockPos preyAnchor) {
        if (preyAnchor == null) {
            return null;
        }
        BlockPos best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos candidate = preyAnchor.offset(dx, 0, dz);
                    if (!isValidPreyInteractionGuardCell(candidate)) {
                        continue;
                    }
                    double score = this.position().distanceToSqr(preyInteractionGuardPoint(candidate))
                            + preyAnchor.distSqr(candidate) * 0.22D;
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate.immutable();
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return best;
    }

    private boolean isValidPreyInteractionGuardCell(BlockPos pos) {
        return pos != null
                && pos.getY() > this.level.getMinBuildHeight()
                && pos.getY() < this.level.getMaxBuildHeight() - 1
                && isBodyOpen(pos)
                && isBodyOpen(pos.above())
                && !this.level.getBlockState(pos).is(Blocks.COBWEB)
                && !this.level.getBlockState(pos.above()).is(Blocks.COBWEB)
                && hasStandingSupport(pos.below())
                && AttachmentHelper.aabbFitsOnSurface(this.level, this, pos, Direction.DOWN);
    }

    private void moveTowardPreyInteractionGuard() {
        if (this.preyInteractionGuardAnchor == null) {
            return;
        }
        clearCombatPacingSpeedModifier();
        if (this.preyInteractionRepathTicks > 0) {
            this.preyInteractionRepathTicks--;
        }
        if (this.preyInteractionRepathTicks <= 0 || this.getNavigation().isDone()) {
            Path path = this.getNavigation().createPath(this.preyInteractionGuardAnchor, 0);
            if (path != null) {
                boolean started = this.getNavigation().moveTo(path, PREY_INTERACTION_NAVIGATION_SPEED);
                this.preyInteractionPathStarted = this.preyInteractionPathStarted || started;
            }
            this.preyInteractionRepathTicks = PREY_INTERACTION_REPATH_TICKS;
        }

        Vec3 guardPoint = preyInteractionGuardPoint(this.preyInteractionGuardAnchor);
        this.getMoveControl().setWantedPosition(guardPoint.x, guardPoint.y, guardPoint.z,
                PREY_INTERACTION_NAVIGATION_SPEED);
        if (applyPreyInteractionGuardStep()) {
            this.preyInteractionPathStarted = true;
        }
        updatePreyInteractionDistances();
    }

    private boolean applyPreyInteractionGuardStep() {
        if (this.preyInteractionGuardAnchor == null || this.getAttachmentDirection() != Direction.DOWN) {
            return false;
        }
        Vec3 toAnchor = preyInteractionGuardPoint(this.preyInteractionGuardAnchor).subtract(this.position());
        Vec3 horizontal = new Vec3(toAnchor.x, 0.0D, toAnchor.z);
        double distance = horizontal.length();
        if (distance <= 1.0E-6D) {
            return false;
        }

        double stopDistance = Math.sqrt(PREY_INTERACTION_REACHED_SQR);
        double stepSize = Math.min(PREY_INTERACTION_STEP, Math.max(0.0D, distance - stopDistance));
        if (stepSize <= 0.005D) {
            return false;
        }

        Vec3 step = horizontal.normalize().scale(stepSize);
        if (!canTakePreyInteractionStep(step)) {
            step = step.scale(0.5D);
            if (!canTakePreyInteractionStep(step)) {
                step = step.scale(0.5D);
                if (!canTakePreyInteractionStep(step)) {
                    return false;
                }
            }
        }

        this.move(MoverType.SELF, step);
        this.setDeltaMovement(step);
        this.setSpeed((float) Math.max(PREY_INTERACTION_NAVIGATION_SPEED * 0.3D, step.horizontalDistance()));
        this.setXxa((float) step.x);
        this.setZza((float) step.z);
        return true;
    }

    private boolean canTakePreyInteractionStep(Vec3 step) {
        if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
            return false;
        }
        BlockPos next = new BlockPos(this.getX() + step.x, this.getY(), this.getZ() + step.z);
        return isValidPreyInteractionGuardCell(next);
    }

    private void updatePreyInteractionDistances() {
        this.preyInteractionCurrentGuardDistance = distanceToPreyInteractionGuard();
        if (this.preyInteractionMinGuardDistance <= 0.0D && this.preyInteractionStartGuardDistance <= 0.0D) {
            this.preyInteractionStartGuardDistance = this.preyInteractionCurrentGuardDistance;
            this.preyInteractionMinGuardDistance = this.preyInteractionCurrentGuardDistance;
        } else {
            this.preyInteractionMinGuardDistance = Math.min(this.preyInteractionMinGuardDistance,
                    this.preyInteractionCurrentGuardDistance);
        }
    }

    private double distanceToPreyInteractionGuard() {
        return this.preyInteractionGuardAnchor == null ? 0.0D
                : this.position().distanceTo(preyInteractionGuardPoint(this.preyInteractionGuardAnchor));
    }

    private Vec3 preyInteractionPreyPoint() {
        return this.preyInteractionPreyAnchor == null ? this.position()
                : new Vec3(this.preyInteractionPreyAnchor.getX() + 0.5D,
                        this.preyInteractionPreyAnchor.getY() + 0.25D,
                        this.preyInteractionPreyAnchor.getZ() + 0.5D);
    }

    private Vec3 preyInteractionGuardPoint(BlockPos pos) {
        return pos == null ? this.position() : new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    private void facePreyInteractionAnchor() {
        Vec3 point = preyInteractionPreyPoint();
        if (isOnSingleThreadWeb()) {
            updateSingleThreadWebTraversalOrientation(this.getDeltaMovement());
            lookAtPointWithUnlockedHead(point);
            return;
        }

        Vec3 toTarget = point.subtract(this.position());
        Direction attachment = this.getAttachmentDirection();
        Vec3 normal = AttachmentHelper.normal(attachment);
        Vec3 tangent = attachment == Direction.DOWN
                ? new Vec3(toTarget.x, 0.0D, toTarget.z)
                : AttachmentHelper.projectOntoPlane(toTarget, normal);
        if (tangent.lengthSqr() <= 1.0E-6D) {
            return;
        }
        float targetYaw = (float) (Math.atan2(tangent.z, tangent.x) * (180.0D / Math.PI)) - 90.0F;
        float newYaw = Mth.rotLerp(0.35F, this.getYRot(), targetYaw);
        this.setYRot(newYaw);
        this.yBodyRot = newYaw;
        this.yHeadRot = newYaw;
        this.getLookControl().setLookAt(point.x, point.y, point.z, 30.0F, 30.0F);
    }

    private boolean isFacingPreyInteractionAnchor() {
        Vec3 toTarget = preyInteractionPreyPoint().subtract(this.position());
        Direction attachment = this.getAttachmentDirection();
        Vec3 tangent = attachment == Direction.DOWN
                ? new Vec3(toTarget.x, 0.0D, toTarget.z)
                : AttachmentHelper.projectOntoPlane(toTarget, AttachmentHelper.normal(attachment));
        if (tangent.lengthSqr() <= 1.0E-6D) {
            return true;
        }

        float expectedYaw = (float) (Math.atan2(tangent.z, tangent.x) * (180.0D / Math.PI)) - 90.0F;
        return Math.abs(Mth.wrapDegrees(this.getYRot() - expectedYaw)) <= PREY_INTERACTION_FACING_DEGREES;
    }

    private void finishPreyInteraction() {
        updatePreyInteractionDistances();
        if (!this.preyInteractionHeldGuard
                && (this.preyInteractionCurrentGuardDistance * this.preyInteractionCurrentGuardDistance <= 1.25D * 1.25D
                || getPreyInteractionGuardDistanceReduced() >= 0.35D)) {
            holdPreyInteractionGuard();
        }
        this.preyInteractionCooldownTicks = Math.max(this.preyInteractionCooldownTicks,
                PREY_INTERACTION_COOLDOWN_TICKS);
        resetPreyInteraction(false);
        this.preyInteractionStatus = "cooldown";
    }

    private void resetPreyInteraction(boolean clearCooldown) {
        this.preyInteractionPhase = PREY_INTERACTION_NONE;
        this.preyInteractionTicks = 0;
        this.preyInteractionRepathTicks = 0;
        this.preyInteractionTargetId = null;
        this.preyInteractionStatus = "idle";
        if (clearCooldown) {
            this.preyInteractionCooldownTicks = 0;
            this.preyInteractionPreyType = "none";
            this.preyInteractionPreyAnchor = null;
            this.preyInteractionGuardAnchor = null;
            this.preyInteractionPathStarted = false;
            this.preyInteractionReachedGuard = false;
            this.preyInteractionHeldGuard = false;
            this.preyInteractionPlacedWeb = false;
            this.preyInteractionPlacedWebCount = 0;
            this.preyInteractionTargetKilled = false;
            this.preyInteractionFacingTicks = 0;
            this.preyInteractionStartGuardDistance = 0.0D;
            this.preyInteractionCurrentGuardDistance = 0.0D;
            this.preyInteractionMinGuardDistance = 0.0D;
        }
    }

    private static final class WallPeekCandidate {
        private final BlockPos cover;
        private final BlockPos peek;
        private final boolean coverLineOfSightBlocked;
        private final boolean peekLineOfSightClear;

        private WallPeekCandidate(BlockPos cover, BlockPos peek, boolean coverLineOfSightBlocked,
                boolean peekLineOfSightClear) {
            this.cover = cover;
            this.peek = peek;
            this.coverLineOfSightBlocked = coverLineOfSightBlocked;
            this.peekLineOfSightClear = peekLineOfSightClear;
        }
    }

    private boolean isFacingCombatTarget(LivingEntity target, double maxDegrees) {
        Vec3 toTarget = target.position().subtract(this.position());
        Direction attachment = this.getAttachmentDirection();
        Vec3 tangent = attachment == Direction.DOWN
                ? new Vec3(toTarget.x, 0.0D, toTarget.z)
                : AttachmentHelper.projectOntoPlane(toTarget, AttachmentHelper.normal(attachment));
        if (tangent.lengthSqr() <= 1.0E-6D) {
            return true;
        }

        float expectedYaw = (float) (Math.atan2(tangent.z, tangent.x) * (180.0D / Math.PI)) - 90.0F;
        return Math.abs(Mth.wrapDegrees(this.getYRot() - expectedYaw)) <= maxDegrees;
    }

    private boolean updateEscapeCutting(LivingEntity target) {
        if (!canUseEscapeCutting(target)) {
            resetEscapeCutting(false);
            return false;
        }

        UUID targetId = target.getUUID();
        if (this.escapeCutting && (this.escapeCuttingTargetId == null || !this.escapeCuttingTargetId.equals(targetId))) {
            resetEscapeCutting(false);
        }

        if (!this.escapeCutting) {
            EscapeCuttingCandidate candidate = findEscapeCuttingCandidate(target);
            if (candidate == null) {
                this.escapeCuttingStatus = "no_candidate";
                return false;
            }
            startEscapeCutting(target, candidate);
        }

        if (this.escapeCuttingAnchor == null
                || this.escapeCuttingRouteDirection == null
                || !isLikelyDoorwayRoute(this.escapeCuttingAnchor, this.escapeCuttingRouteDirection)) {
            this.escapeCuttingStatus = "invalid_anchor";
            resetEscapeCutting(false);
            return false;
        }

        if (this.escapeCuttingTicks <= 0) {
            this.escapeCuttingStatus = "expired";
            this.escapeCuttingCooldownTicks = Math.max(this.escapeCuttingCooldownTicks, ESCAPE_CUTTING_COOLDOWN_TICKS);
            resetEscapeCutting(false);
            return false;
        }

        this.escapeCuttingTicks--;
        return applyEscapeCuttingMovement(target);
    }

    private boolean canUseEscapeCutting(LivingEntity target) {
        if (!canUseCombatPacing(target)) {
            this.escapeCuttingStatus = "combat_unavailable";
            return false;
        }
        if (this.escapeCuttingCooldownTicks > 0) {
            this.escapeCuttingStatus = "cooldown";
            return false;
        }
        if (hasHigherPriorityCombatStateActive()) {
            this.escapeCuttingStatus = "higher_priority_active";
            return false;
        }
        if (!(target instanceof Player) && !target.getTags().contains(ESCAPE_CUTTING_TEST_TARGET_TAG)) {
            this.escapeCuttingStatus = "target_ineligible";
            return false;
        }
        this.escapeCuttingStatus = "eligible";
        return true;
    }

    private EscapeCuttingCandidate findEscapeCuttingCandidate(LivingEntity target) {
        BlockPos targetBlock = target.blockPosition();
        Vec3 targetLook = horizontalFacingDirection(target);
        EscapeCuttingCandidate best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Vec3 directionVector = new Vec3(direction.getStepX(), 0.0D, direction.getStepZ());
            double alignment = targetLook.lengthSqr() <= 1.0E-6D ? 0.0D : targetLook.dot(directionVector);
            for (int distance = 2; distance <= ESCAPE_CUTTING_ROUTE_SEARCH_DISTANCE; distance++) {
                BlockPos candidate = targetBlock.relative(direction, distance);
                if (candidate.distSqr(targetBlock) < ESCAPE_CUTTING_MIN_TARGET_ROUTE_DISTANCE_SQR
                        || !isLikelyDoorwayRoute(candidate, direction)) {
                    continue;
                }

                double anchorSeparation = candidate.distSqr(targetBlock);
                if (anchorSeparation < ESCAPE_CUTTING_MIN_TARGET_ANCHOR_SEPARATION_SQR) {
                    continue;
                }

                double score = anchorSeparation
                        + this.blockPosition().distSqr(candidate) * 0.08D
                        - Math.max(0.0D, alignment) * ESCAPE_CUTTING_LOOK_ALIGNMENT_WEIGHT;
                if (score < bestScore) {
                    bestScore = score;
                    best = new EscapeCuttingCandidate(candidate.immutable(), direction);
                }
            }
        }
        return best;
    }

    private Vec3 horizontalFacingDirection(LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        return horizontal.lengthSqr() <= 1.0E-6D ? Vec3.ZERO : horizontal.normalize();
    }

    private boolean isLikelyDoorwayRoute(BlockPos candidate, Direction direction) {
        if (!isWalkableEscapeCell(candidate)
                || !isWalkableEscapeCell(candidate.relative(direction))
                || !isWalkableEscapeCell(candidate.relative(direction.getOpposite()))) {
            return false;
        }

        return hasDoorSideColumns(candidate, direction);
    }

    private boolean isWalkableEscapeCell(BlockPos pos) {
        return isBodyOpen(pos)
                && isBodyOpen(pos.above())
                && hasStandingSupport(pos.below())
                && AttachmentHelper.aabbFitsOnSurface(this.level, this, pos, Direction.DOWN);
    }

    private boolean isBodyOpen(BlockPos pos) {
        BlockState state = this.level.getBlockState(pos);
        return state.getCollisionShape(this.level, pos).isEmpty()
                && this.level.getFluidState(pos).isEmpty();
    }

    private boolean hasStandingSupport(BlockPos pos) {
        BlockState state = this.level.getBlockState(pos);
        return !state.getCollisionShape(this.level, pos).isEmpty();
    }

    private boolean hasDoorSideColumn(BlockPos pos) {
        return hasStandingSupport(pos) && hasStandingSupport(pos.above());
    }

    private boolean hasDoorSideColumns(BlockPos candidate, Direction direction) {
        Direction left = direction.getCounterClockWise();
        Direction right = direction.getClockWise();
        int minSideOffset = Math.max(1, (int) Math.ceil(this.getBbWidth()));
        for (int sideOffset = minSideOffset; sideOffset <= minSideOffset + 1; sideOffset++) {
            if (hasClearDoorOpeningSide(candidate, left, sideOffset)
                    && hasClearDoorOpeningSide(candidate, right, sideOffset)
                    && hasDoorSideColumn(candidate.relative(left, sideOffset))
                    && hasDoorSideColumn(candidate.relative(right, sideOffset))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasClearDoorOpeningSide(BlockPos candidate, Direction side, int sideOffset) {
        for (int offset = 1; offset < sideOffset; offset++) {
            BlockPos pos = candidate.relative(side, offset);
            if (!isBodyOpen(pos) || !isBodyOpen(pos.above()) || !hasStandingSupport(pos.below())) {
                return false;
            }
        }
        return true;
    }

    private void startEscapeCutting(LivingEntity target, EscapeCuttingCandidate candidate) {
        this.escapeCutting = true;
        this.escapeCuttingTicks = ESCAPE_CUTTING_ACTIVE_TICKS;
        this.escapeCuttingTargetId = target.getUUID();
        this.escapeCuttingAnchor = candidate.anchor;
        this.escapeCuttingRouteDirection = candidate.routeDirection;
        this.escapeCuttingPathStarted = false;
        this.escapeCuttingReachedAnchor = false;
        this.escapeCuttingRepathTicks = 0;
        double distance = distanceToEscapeCuttingAnchor();
        this.escapeCuttingStartAnchorDistance = distance;
        this.escapeCuttingCurrentAnchorDistance = distance;
        this.escapeCuttingMinAnchorDistance = distance;
        this.escapeCuttingStatus = "started";
    }

    private boolean applyEscapeCuttingMovement(LivingEntity target) {
        if (this.escapeCuttingAnchor == null) {
            return false;
        }

        double distance = distanceToEscapeCuttingAnchor();
        this.escapeCuttingCurrentAnchorDistance = distance;
        this.escapeCuttingMinAnchorDistance = Math.min(this.escapeCuttingMinAnchorDistance, distance);
        faceCombatTarget(target);
        clearCombatPacingSpeedModifier();

        if (distance * distance <= ESCAPE_CUTTING_HOLD_DISTANCE_SQR) {
            this.escapeCuttingReachedAnchor = true;
            this.escapeCuttingStatus = "reached";
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.setSpeed(0.0F);
            this.setXxa(0.0F);
            this.setZza(0.0F);
            return true;
        }

        if (this.escapeCuttingRepathTicks > 0) {
            this.escapeCuttingRepathTicks--;
        }
        if (this.escapeCuttingRepathTicks <= 0 || this.getNavigation().isDone()) {
            Path path = this.getNavigation().createPath(this.escapeCuttingAnchor, 0);
            if (path == null) {
                this.escapeCuttingStatus = "path_null";
                resetEscapeCutting(false);
                return false;
            }
            boolean started = this.getNavigation().moveTo(path, ESCAPE_CUTTING_NAVIGATION_SPEED);
            this.escapeCuttingPathStarted = this.escapeCuttingPathStarted || started;
            this.escapeCuttingStatus = started ? "moving" : "path_not_started";
            this.escapeCuttingRepathTicks = ESCAPE_CUTTING_REPATH_TICKS;
        }

        Vec3 anchorPoint = escapeCuttingAnchorPoint();
        this.getMoveControl().setWantedPosition(anchorPoint.x, anchorPoint.y, anchorPoint.z,
                ESCAPE_CUTTING_NAVIGATION_SPEED);
        applyEscapeCuttingAnchorStep();
        this.escapeCuttingStatus = "moving";
        return true;
    }

    private boolean applyEscapeCuttingAnchorStep() {
        if (this.escapeCuttingAnchor == null || this.getAttachmentDirection() != Direction.DOWN) {
            return false;
        }

        Vec3 toAnchor = escapeCuttingAnchorPoint().subtract(this.position());
        Vec3 horizontal = new Vec3(toAnchor.x, 0.0D, toAnchor.z);
        double distance = horizontal.length();
        if (distance <= 1.0E-6D) {
            return false;
        }

        double stopDistance = Math.sqrt(ESCAPE_CUTTING_HOLD_DISTANCE_SQR);
        double stepSize = Math.min(ESCAPE_CUTTING_STEP, Math.max(0.0D, distance - stopDistance));
        if (stepSize <= 0.005D) {
            this.escapeCuttingReachedAnchor = true;
            return false;
        }

        Vec3 step = horizontal.normalize().scale(stepSize);
        if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
            step = step.scale(0.5D);
            if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
                step = step.scale(0.5D);
                if (!this.level.noCollision(this, this.getBoundingBox().move(step))) {
                    return false;
                }
            }
        }

        this.move(MoverType.SELF, step);
        this.setDeltaMovement(step);
        this.setSpeed((float) step.horizontalDistance());
        double updatedDistance = distanceToEscapeCuttingAnchor();
        this.escapeCuttingCurrentAnchorDistance = updatedDistance;
        this.escapeCuttingMinAnchorDistance = Math.min(this.escapeCuttingMinAnchorDistance, updatedDistance);
        if (updatedDistance * updatedDistance <= ESCAPE_CUTTING_HOLD_DISTANCE_SQR) {
            this.escapeCuttingReachedAnchor = true;
        }
        return true;
    }

    private double distanceToEscapeCuttingAnchor() {
        if (this.escapeCuttingAnchor == null) {
            return 0.0D;
        }
        Vec3 anchorPoint = escapeCuttingAnchorPoint();
        double dx = this.getX() - anchorPoint.x;
        double dy = this.getY() - anchorPoint.y;
        double dz = this.getZ() - anchorPoint.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private Vec3 escapeCuttingAnchorPoint() {
        if (this.escapeCuttingAnchor == null) {
            return this.position();
        }
        return new Vec3(
                this.escapeCuttingAnchor.getX() + 0.5D,
                this.escapeCuttingAnchor.getY(),
                this.escapeCuttingAnchor.getZ() + 0.5D);
    }

    private void resetEscapeCutting(boolean clearCooldown) {
        this.escapeCutting = false;
        this.escapeCuttingTicks = 0;
        this.escapeCuttingRepathTicks = 0;
        this.escapeCuttingTargetId = null;
        this.escapeCuttingAnchor = null;
        this.escapeCuttingRouteDirection = null;
        this.escapeCuttingPathStarted = false;
        this.escapeCuttingReachedAnchor = false;
        this.escapeCuttingStartAnchorDistance = 0.0D;
        this.escapeCuttingCurrentAnchorDistance = 0.0D;
        this.escapeCuttingMinAnchorDistance = 0.0D;
        if (clearCooldown) {
            this.escapeCuttingCooldownTicks = 0;
            this.escapeCuttingStatus = "idle";
        }
    }

    private static final class EscapeCuttingCandidate {
        private final BlockPos anchor;
        private final Direction routeDirection;

        private EscapeCuttingCandidate(BlockPos anchor, Direction routeDirection) {
            this.anchor = anchor;
            this.routeDirection = routeDirection;
        }
    }

    private void updatePackCoordination(LivingEntity target) {
        if (!canUsePackCoordination(target)) {
            resetPackCoordination();
            return;
        }

        List<GroundSpiderEntity> pack = findSameTargetPack(target);
        if (pack.size() < PACK_COORDINATION_MIN_SIZE) {
            resetPackCoordination();
            return;
        }

        int rank = pack.indexOf(this);
        if (rank < 0) {
            resetPackCoordination();
            return;
        }

        int role = rank == 0
                ? PACK_ROLE_DIRECT
                : rank == 1 ? PACK_ROLE_AMBUSH : PACK_ROLE_FLANK;
        int directCount = 1;
        int ambushCount = pack.size() >= 2 ? 1 : 0;
        int flankCount = Math.max(0, pack.size() - directCount - ambushCount);
        setPackRole(role, target, pack.size(), directCount, ambushCount, flankCount);
    }

    private boolean canUsePackCoordination(LivingEntity target) {
        return canUseCombatPacing(target)
                && (target instanceof Player || target.getTags().contains(PACK_COORDINATION_TEST_TARGET_TAG));
    }

    private boolean canRunInactiveCombatStartSweep(LivingEntity target) {
        if (!(target instanceof Player) || target instanceof FakePlayer) {
            return true;
        }
        if (!isDenseLivePlayerSwarmTarget(target)) {
            return true;
        }
        return Math.floorMod(this.tickCount + this.getId(), LIVE_PLAYER_COMBAT_START_STAGGER_TICKS) == 0;
    }

    private boolean isDenseLivePlayerCombatTarget(LivingEntity target) {
        return isDenseLivePlayerSwarmTarget(target);
    }

    private boolean isReadableLivePlayerPressureTarget(LivingEntity target) {
        return target instanceof Player
                && !(target instanceof FakePlayer)
                && !hasFocusedCombatTestTag(target);
    }

    private boolean usesDenseLivePlayerFastCombat() {
        LivingEntity target = this.getTarget();
        return target != null
                && isReadableLivePlayerPressureTarget(target)
                && isDenseLivePlayerCombatTarget(target);
    }

    public boolean isDenseLivePlayerSwarmTarget(Entity target) {
        if (!(target instanceof LivingEntity)
                || !(target instanceof Player)
                || target instanceof FakePlayer) {
            return false;
        }
        if (this.packSize >= LIVE_PLAYER_DENSE_PACK_SIZE) {
            return true;
        }
        return countDenseLivePlayerSwarm((LivingEntity) target) >= LIVE_PLAYER_DENSE_PACK_SIZE;
    }

    private boolean hasFocusedCombatTestTag(LivingEntity target) {
        return target.getTags().contains(DROP_ATTACK_TEST_TARGET_TAG)
                || target.getTags().contains(CEILING_STALK_TEST_TARGET_TAG)
                || target.getTags().contains(WEB_SHOT_TEST_TARGET_TAG)
                || target.getTags().contains(WEB_TRAP_PLACEMENT_TEST_TARGET_TAG)
                || target.getTags().contains(WEB_LOWER_TEST_TARGET_TAG)
                || target.getTags().contains(POUNCE_TEST_TARGET_TAG)
                || target.getTags().contains(RETREAT_TEST_TARGET_TAG)
                || target.getTags().contains(GRAB_PULL_TEST_TARGET_TAG)
                || target.getTags().contains(DRAG_NEST_TEST_TARGET_TAG)
                || target.getTags().contains(PACK_COORDINATION_TEST_TARGET_TAG)
                || target.getTags().contains(ESCAPE_CUTTING_TEST_TARGET_TAG)
                || target.getTags().contains(THREAT_DISPLAY_TEST_TARGET_TAG)
                || target.getTags().contains(LINE_OF_SIGHT_STALKING_TEST_TARGET_TAG)
                || target.getTags().contains(DARKNESS_PREFERENCE_TEST_TARGET_TAG)
                || target.getTags().contains(WALL_PEEK_TEST_TARGET_TAG)
                || target.getTags().contains(PREY_INTERACTION_TEST_TARGET_TAG)
                || target.getTags().contains(BASIC_MELEE_TEST_TARGET_TAG);
    }

    private boolean canStartCeilingStalk(LivingEntity target) {
        return target instanceof Player || target.getTags().contains(CEILING_STALK_TEST_TARGET_TAG);
    }

    private void updateDenseLivePlayerPressure(LivingEntity target) {
        resetDenseLivePlayerSpecialTactics();
        if (this.readableLivePlayerContactAttackCooldownTicks > 0) {
            this.readableLivePlayerContactAttackCooldownTicks--;
        }

        int phase = getCombatPacing();
        if (phase == COMBAT_PACING_NONE) {
            setCombatPacing(COMBAT_PACING_BURST, BURST_PHASE_TICKS);
        } else if (this.combatPacingTicks > 0) {
            this.combatPacingTicks--;
        } else {
            setCombatPacing(COMBAT_PACING_BURST, BURST_PHASE_TICKS);
        }

        faceCombatTarget(target);
        trySpendReadableLivePlayerContactAttack(target);
        if (holdDenseLivePlayerNonFloorPressure(target)) {
            return;
        }
        boolean denseLivePlayer = isDenseLivePlayerCombatTarget(target);
        boolean directContactSlot = denseLivePlayer && isDenseLivePlayerDirectContactSlot(target);
        if (denseLivePlayer
                && !directContactSlot
                && this.getAttachmentDirection() == Direction.DOWN
                && this.distanceToSqr(target) <= READABLE_LIVE_PLAYER_HOLD_PRESSURE_RANGE_SQR) {
            holdReadableLivePlayerPressurePosition();
            return;
        }
        if (!isDenseLivePlayerCombatTarget(target)
                && this.getAttachmentDirection() == Direction.DOWN
                && this.distanceToSqr(target) <= READABLE_LIVE_PLAYER_HOLD_PRESSURE_RANGE_SQR) {
            holdReadableLivePlayerPressurePosition();
            return;
        }

        if (denseLivePlayer && directContactSlot) {
            trySpendDenseLivePlayerContactDamage(target);
        }
        double stopDistanceSqr = LIVE_PLAYER_DENSE_PRESSURE_STOP_DISTANCE_SQR;
        if (this.distanceToSqr(target) > stopDistanceSqr || !this.isWithinMeleeAttackRange(target)) {
            applyDenseLivePlayerDirectPressure(target);
            return;
        }

        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setDeltaMovement(Vec3.ZERO);
        this.setSpeed(0.0F);
        this.setXxa(0.0F);
        this.setZza(0.0F);
    }

    private void applyDenseLivePlayerDirectPressure(LivingEntity target) {
        applyCombatPacingSpeedModifier(BURST_SPEED_MODIFIER * 0.5D);
        this.setSpeed((float) PACK_DIRECT_PRESSURE_SPEED);

        if (isDenseLivePlayerCombatTarget(target)) {
            if (this.getAttachmentDirection() != Direction.DOWN) {
                if (shouldRefreshDirectPressureNavigation(target) || this.getNavigation().isDone()) {
                    this.getNavigation().moveTo(target, PACK_DIRECT_PRESSURE_SPEED);
                }
                return;
            }
            this.getNavigation().stop();
            this.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), PACK_DIRECT_PRESSURE_SPEED);
            this.setXxa(0.0F);
            this.setZza(0.55F);
            return;
        }

        if (this.getAttachmentDirection() != Direction.DOWN) {
            this.getNavigation().stop();
            applyReadableLivePlayerDirectAdvance(target);
            return;
        }

        if (shouldRefreshDirectPressureNavigation(target)) {
            this.getNavigation().moveTo(target, PACK_DIRECT_PRESSURE_SPEED);
        }
        applyReadableLivePlayerDirectAdvance(target);
    }

    private void trySpendReadableLivePlayerContactAttack(LivingEntity target) {
        if (!isReadableLivePlayerPressureTarget(target)
                || isDenseLivePlayerCombatTarget(target)
                || !target.isAlive()
                || this.readableLivePlayerContactAttackCooldownTicks > 0
                || this.distanceToSqr(target) > READABLE_LIVE_PLAYER_CONTACT_ATTACK_RANGE_SQR) {
            return;
        }

        this.readableLivePlayerContactAttackCooldownTicks = READABLE_LIVE_PLAYER_CONTACT_ATTACK_INTERVAL_TICKS;
        this.swing(InteractionHand.MAIN_HAND);
        this.doHurtTarget(target);
    }

    private void applyReadableLivePlayerDirectAdvance(LivingEntity target) {
        Direction attachment = this.getAttachmentDirection();
        if (this.isWithinMeleeAttackRange(target)) {
            return;
        }

        double movementSpeed = this.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (movementSpeed <= 0.0D) {
            if (attachment == Direction.DOWN) {
                return;
            }
            movementSpeed = READABLE_LIVE_PLAYER_ATTACHED_FALLBACK_SPEED;
        }

        Vec3 targetCenter = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());
        Vec3 toTarget = targetCenter.subtract(this.position());
        Vec3 tangent = attachment == Direction.DOWN
                ? new Vec3(toTarget.x, 0.0D, toTarget.z)
                : AttachmentHelper.projectOntoPlane(toTarget, AttachmentHelper.normal(attachment));
        if (attachment.getAxis().isHorizontal()
                && this.distanceToSqr(target) <= READABLE_LIVE_PLAYER_CONTACT_ATTACK_RANGE_SQR) {
            tangent = new Vec3(tangent.x, 0.0D, tangent.z);
        }
        if (tangent.lengthSqr() <= 1.0E-6D) {
            return;
        }

        Vec3 step = tangent.normalize().scale(Math.min(READABLE_LIVE_PLAYER_DIRECT_ADVANCE_SPEED,
                Math.max(movementSpeed * PACK_DIRECT_PRESSURE_SPEED, movementSpeed)));
        if (attachment != Direction.DOWN) {
            Vec3 next = this.position().add(step);
            BlockPos nextBlock = new BlockPos(next);
            if (!AttachmentHelper.hasSupport(this.level, nextBlock, attachment)
                    || !AttachmentHelper.aabbFitsOnSurface(this.level, this, nextBlock, attachment)) {
                return;
            }
        }

        this.setDeltaMovement(step);
        this.move(MoverType.SELF, step);
    }

    private void holdReadableLivePlayerPressurePosition() {
        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setDeltaMovement(Vec3.ZERO);
        this.setSpeed(0.0F);
        this.setXxa(0.0F);
        this.setZza(0.0F);
        if (this.getAttachmentDirection() != Direction.DOWN) {
            this.setNoGravity(true);
        }
    }

    private boolean holdDenseLivePlayerNonFloorPressure(LivingEntity target) {
        if (!isDenseLivePlayerCombatTarget(target)
                || this.getAttachmentDirection() == Direction.DOWN
                || this.distanceToSqr(target) > LIVE_PLAYER_DENSE_NON_FLOOR_HOLD_DISTANCE_SQR) {
            return false;
        }

        this.getNavigation().stop();
        clearCombatPacingSpeedModifier();
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.setSpeed(0.0F);
        this.setXxa(0.0F);
        this.setZza(0.0F);
        faceCombatTarget(target);
        return true;
    }

    private void trySpendDenseLivePlayerContactDamage(LivingEntity target) {
        if (!isDenseLivePlayerCombatTarget(target)
                || !target.isAlive()
                || !isDenseLivePlayerDirectContactSlot(target)
                || !this.isWithinMeleeAttackRange(target)) {
            return;
        }
        if (!canSpendDenseLivePlayerDamage(target)) {
            return;
        }
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (target.hurt(DamageSource.mobAttack(this), damage)) {
            recordPlayerExperienceAttack(target);
            maybeStartPreyInteractionAfterDamage(target);
        }
    }

    private boolean isDenseLivePlayerDirectContactSlot(LivingEntity target) {
        if (!isDenseLivePlayerCombatTarget(target) || this.getAttachmentDirection() != Direction.DOWN) {
            return false;
        }
        int floorRank = 0;
        for (GroundSpiderEntity candidate : findSameTargetPack(target)) {
            if (candidate.getAttachmentDirection() != Direction.DOWN) {
                continue;
            }
            if (candidate == this) {
                return floorRank < LIVE_PLAYER_DENSE_DIRECT_CONTACT_SLOTS;
            }
            floorRank++;
        }
        return false;
    }

    private void resetDenseLivePlayerSpecialTactics() {
        this.backpedalTicks = 0;
        resetBackpedalTargetMemory();
        resetCeilingStalk();
        resetCircleStrafe();
        resetDropAttack(false);
        resetWebShot(false);
        resetWebTrapPlacement(false);
        resetWebLower(false);
        resetPounce(false);
        resetRetreat(false);
        resetFakeRetreat(false);
        resetGrabPull(false);
        resetDragNest(false);
        resetEscapeCutting(false);
        resetThreatDisplay(false);
        resetLineOfSightStalking(false);
        resetDarknessPreference(false);
        resetWallPeek(false);
        resetPreyInteraction(false);
        clearPendingRetreat();
    }

    private boolean shouldRefreshDirectPressureNavigation(LivingEntity target) {
        if (!isDenseLivePlayerCombatTarget(target)) {
            return this.tickCount % 6 == 0 || this.getNavigation().isDone();
        }
        return Math.floorMod(this.tickCount + this.getId(), LIVE_PLAYER_DIRECT_NAVIGATION_INTERVAL_TICKS) == 0;
    }

    private boolean canSpendDenseLivePlayerDamage(LivingEntity target) {
        if (!isDenseLivePlayerCombatTarget(target)) {
            return true;
        }
        if (target.getHealth() <= LIVE_PLAYER_DENSE_DAMAGE_FLOOR_HEALTH) {
            return false;
        }
        return Math.floorMod(this.tickCount + this.getId(), LIVE_PLAYER_DENSE_DAMAGE_STAGGER_TICKS) == 0;
    }

    private List<GroundSpiderEntity> findSameTargetPack(LivingEntity target) {
        long gameTime = this.level.getGameTime();
        String cacheKey = this.level.dimension().location() + ":" + target.getUUID();
        PackCoordinationCacheEntry cached = PACK_COORDINATION_CACHE.get(cacheKey);
        if (cached != null && cached.gameTime == gameTime) {
            return cached.pack;
        }

        List<GroundSpiderEntity> pack = this.level.getEntitiesOfClass(GroundSpiderEntity.class,
                target.getBoundingBox().inflate(PACK_COORDINATION_RANGE),
                candidate -> candidate.isAlive()
                        && !candidate.isRemoved()
                        && !candidate.isNoAi()
                        && !candidate.isFollowingForcedPath()
                        && candidate.distanceToSqr(target) <= PACK_COORDINATION_RANGE_SQR
                        && candidate.hasSamePackTarget(target));
        pack.sort(Comparator
                .comparingDouble((GroundSpiderEntity spider) -> spider.horizontalDistanceToTargetSqr(target))
                .thenComparing(spider -> spider.getUUID().toString()));
        PACK_COORDINATION_CACHE.put(cacheKey, new PackCoordinationCacheEntry(gameTime, pack));
        trimPackCoordinationCache(gameTime);
        return pack;
    }

    private static void trimPackCoordinationCache(long gameTime) {
        if (PACK_COORDINATION_CACHE.size() <= PACK_COORDINATION_CACHE_MAX_ENTRIES) {
            return;
        }
        PACK_COORDINATION_CACHE.entrySet().removeIf(entry -> entry.getValue().gameTime != gameTime);
    }

    private static final class PackCoordinationCacheEntry {
        private final long gameTime;
        private final List<GroundSpiderEntity> pack;

        private PackCoordinationCacheEntry(long gameTime, List<GroundSpiderEntity> pack) {
            this.gameTime = gameTime;
            this.pack = pack;
        }
    }

    private int countDenseLivePlayerSwarm(LivingEntity target) {
        long gameTime = this.level.getGameTime();
        String cacheKey = this.level.dimension().location() + ":" + target.getUUID();
        DenseLivePlayerSwarmCacheEntry cached = DENSE_LIVE_PLAYER_SWARM_CACHE.get(cacheKey);
        if (cached != null && cached.gameTime == gameTime) {
            return cached.count;
        }

        int count = this.level.getEntitiesOfClass(GroundSpiderEntity.class,
                target.getBoundingBox().inflate(PACK_COORDINATION_RANGE),
                candidate -> candidate.isAlive()
                        && !candidate.isRemoved()
                        && !candidate.isNoAi()
                        && !candidate.isFollowingForcedPath()
                        && candidate.distanceToSqr(target) <= PACK_COORDINATION_RANGE_SQR
                        && candidate.hasSamePackTarget(target)).size();
        DENSE_LIVE_PLAYER_SWARM_CACHE.put(cacheKey, new DenseLivePlayerSwarmCacheEntry(gameTime, count));
        trimDenseLivePlayerSwarmCache(gameTime);
        return count;
    }

    private static void trimDenseLivePlayerSwarmCache(long gameTime) {
        if (DENSE_LIVE_PLAYER_SWARM_CACHE.size() <= PACK_COORDINATION_CACHE_MAX_ENTRIES) {
            return;
        }
        DENSE_LIVE_PLAYER_SWARM_CACHE.entrySet().removeIf(entry -> entry.getValue().gameTime != gameTime);
    }

    private static final class DenseLivePlayerSwarmCacheEntry {
        private final long gameTime;
        private final int count;

        private DenseLivePlayerSwarmCacheEntry(long gameTime, int count) {
            this.gameTime = gameTime;
            this.count = count;
        }
    }

    private double horizontalDistanceToTargetSqr(LivingEntity target) {
        double dx = this.getX() - target.getX();
        double dz = this.getZ() - target.getZ();
        return dx * dx + dz * dz;
    }

    private boolean hasSamePackTarget(LivingEntity target) {
        UUID targetId = target.getUUID();
        LivingEntity currentTarget = this.getTarget();
        if (currentTarget != null && currentTarget.getUUID().equals(targetId)) {
            return true;
        }
        return targetId.equals(this.dropAttackTargetId)
                || targetId.equals(this.webShotTargetId)
                || targetId.equals(this.webTrapPlacementTargetId)
                || targetId.equals(this.webLowerTargetId)
                || targetId.equals(this.pounceTargetId)
                || targetId.equals(this.retreatTargetId)
                || targetId.equals(this.fakeRetreatTargetId)
                || targetId.equals(this.grabPullTargetId)
                || targetId.equals(this.dragNestTargetId)
                || targetId.equals(this.escapeCuttingTargetId);
    }

    private boolean applyPackCoordinationRole(LivingEntity target) {
        if (!isPackCoordinating() || this.packTargetId == null || !this.packTargetId.equals(target.getUUID())) {
            return false;
        }

        if (this.packRole == PACK_ROLE_DIRECT) {
            resetCeilingStalk();
            resetCircleStrafe();
            clearCombatPacingSpeedModifier();
            faceCombatTarget(target);
            if (this.distanceToSqr(target) > PACK_DIRECT_PRESSURE_STOP_DISTANCE_SQR
                    || !this.isWithinMeleeAttackRange(target)) {
                if (shouldRefreshDirectPressureNavigation(target)) {
                    this.getNavigation().moveTo(target, PACK_DIRECT_PRESSURE_SPEED);
                }
                this.setSpeed((float) PACK_DIRECT_PRESSURE_SPEED);
                this.setXxa(0.0F);
                this.setZza(0.55F);
            } else {
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);
                this.setSpeed(0.0F);
                this.setXxa(0.0F);
                this.setZza(0.0F);
            }
            return true;
        }

        if (this.packRole == PACK_ROLE_AMBUSH && updateCeilingStalk(target)) {
            resetCircleStrafe();
            return true;
        }

        if (this.packRole == PACK_ROLE_FLANK && updatePackFlankMovement(target)) {
            resetCeilingStalk();
            return true;
        }

        return false;
    }

    private boolean updatePackFlankMovement(LivingEntity target) {
        if (updateCircleStrafe(target)) {
            return true;
        }
        if (this.circleStrafeCooldownTicks > 0) {
            return false;
        }
        if (this.getAttachmentDirection() != Direction.DOWN) {
            return false;
        }

        Vec3 radial = this.position().subtract(target.position());
        radial = new Vec3(radial.x, 0.0D, radial.z);
        double distance = radial.length();
        if (distance <= 1.0E-6D) {
            return false;
        }

        Vec3 radialUnit = radial.scale(1.0D / distance);
        Vec3[] tangents = new Vec3[] {
                new Vec3(-radialUnit.z, 0.0D, radialUnit.x),
                new Vec3(radialUnit.z, 0.0D, -radialUnit.x)
        };
        double radialError = distance - CIRCLE_STRAFE_IDEAL_DISTANCE;
        Vec3 correction = radialUnit.scale(-radialError * CIRCLE_STRAFE_RADIAL_CORRECTION);
        for (Vec3 tangent : tangents) {
            Vec3 desired = tangent.add(correction);
            if (desired.lengthSqr() <= CIRCLE_STRAFE_MIN_STEP_SQR) {
                desired = tangent;
            }
            Vec3 fullStep = desired.normalize().scale(CIRCLE_STRAFE_SPEED);
            for (double scale : new double[] { 1.0D, 0.5D }) {
                Vec3 step = fullStep.scale(scale);
                if (canTakeCircleStrafeStep(step)) {
                    clearCombatPacingSpeedModifier();
                    this.getNavigation().stop();
                    this.setDeltaMovement(step);
                    this.move(MoverType.SELF, step);
                    this.circleStrafeTicks = Math.max(this.circleStrafeTicks, CIRCLE_STRAFE_PHASE_TICKS);
                    this.circleStrafeTargetId = target.getUUID();
                    this.setSpeed((float) step.horizontalDistance());
                    this.setXxa((float) Math.signum(tangent.x == 0.0D ? tangent.z : tangent.x) * 0.65F);
                    this.setZza(0.2F);
                    faceCombatTarget(target);
                    return true;
                }
            }
        }
        return false;
    }

    private void setPackRole(int role, LivingEntity target, int size, int directCount, int ambushCount, int flankCount) {
        UUID targetId = target == null ? null : target.getUUID();
        if (this.packRole == role && this.packTargetId != null && this.packTargetId.equals(targetId)) {
            this.packRoleTicks++;
        } else {
            this.packRoleTicks = role == PACK_ROLE_NONE ? 0 : 1;
        }

        this.packRole = role;
        this.packTargetId = targetId;
        this.packSize = size;
        this.packDirectCount = directCount;
        this.packAmbushCount = ambushCount;
        this.packFlankCount = flankCount;
    }

    private void resetPackCoordination() {
        this.packRole = PACK_ROLE_NONE;
        this.packRoleTicks = 0;
        this.packTargetId = null;
        this.packSize = 0;
        this.packDirectCount = 0;
        this.packAmbushCount = 0;
        this.packFlankCount = 0;
    }

    private boolean isTargetAdvancingIntoBackpedalRange(LivingEntity target) {
        if (this.distanceToSqr(target) > BACKPEDAL_TRIGGER_DISTANCE_SQR
                || this.backpedalTargetId == null
                || !this.backpedalTargetId.equals(target.getUUID())) {
            return false;
        }

        Vec3 targetMovement = target.position().subtract(this.previousBackpedalTargetPosition);
        Vec3 toSpider = this.position().subtract(target.position());
        if (targetMovement.lengthSqr() <= BACKPEDAL_MIN_STEP_SQR || toSpider.lengthSqr() <= BACKPEDAL_MIN_STEP_SQR) {
            return false;
        }

        double approachSpeed = targetMovement.dot(toSpider.normalize());
        double previousDistance = Math.sqrt(this.previousBackpedalTargetPosition.distanceToSqr(this.position()));
        double currentDistance = Math.sqrt(target.distanceToSqr(this));
        return approachSpeed > BACKPEDAL_TARGET_ADVANCE_EPSILON
                || previousDistance - currentDistance > BACKPEDAL_TARGET_ADVANCE_EPSILON;
    }

    private void rememberBackpedalTargetPosition(LivingEntity target) {
        this.backpedalTargetId = target.getUUID();
        this.previousBackpedalTargetPosition = target.position();
    }

    private void resetBackpedalTargetMemory() {
        this.backpedalTargetId = null;
        this.previousBackpedalTargetPosition = Vec3.ZERO;
    }

    private boolean applyBackpedalMovement(LivingEntity target) {
        Vec3 awayFromTarget = this.position().subtract(target.position());
        Direction attachment = this.getAttachmentDirection();
        Vec3 tangent = attachment == Direction.DOWN
                ? new Vec3(awayFromTarget.x, 0.0D, awayFromTarget.z)
                : AttachmentHelper.projectOntoPlane(awayFromTarget, AttachmentHelper.normal(attachment));
        if (tangent.lengthSqr() <= BACKPEDAL_MIN_STEP_SQR) {
            return false;
        }

        Vec3 step = tangent.normalize().scale(BACKPEDAL_SPEED);
        this.getNavigation().stop();
        this.setDeltaMovement(step);
        this.move(MoverType.SELF, step);
        this.setSpeed((float) BACKPEDAL_SPEED);
        this.setXxa(0.0F);
        this.setZza(-0.6F);
        return true;
    }

    private boolean canUseCombatPacing(LivingEntity target) {
        return target != null
                && target.isAlive()
                && !target.isRemoved()
                && !target.getTags().contains(BASIC_MELEE_TEST_TARGET_TAG)
                && !this.isNoAi()
                && !this.isFollowingForcedPath()
                && this.distanceToSqr(target) <= BURST_ENGAGE_DISTANCE_SQR;
    }

    private void faceCombatTarget(LivingEntity target) {
        if (isOnSingleThreadWeb()) {
            updateSingleThreadWebTraversalOrientation(this.getDeltaMovement());
            lookAtWithUnlockedHead(target);
            return;
        }

        Vec3 toTarget = target.position().subtract(this.position());
        Direction attachment = this.getAttachmentDirection();
        Vec3 normal = AttachmentHelper.normal(attachment);
        Vec3 tangent = attachment == Direction.DOWN
                ? new Vec3(toTarget.x, 0.0D, toTarget.z)
                : AttachmentHelper.projectOntoPlane(toTarget, normal);
        if (tangent.lengthSqr() <= 1.0E-6D) {
            return;
        }
        float targetYaw = (float) (Math.atan2(tangent.z, tangent.x) * (180.0D / Math.PI)) - 90.0F;
        float newYaw = Mth.rotLerp(0.35F, this.getYRot(), targetYaw);
        this.setYRot(newYaw);
        this.yBodyRot = newYaw;
        this.yHeadRot = newYaw;
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private void applyCombatPacingSpeedModifier(double amount) {
        var movement = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) {
            return;
        }
        movement.removeModifier(COMBAT_PACING_SPEED_MODIFIER_ID);
        movement.addTransientModifier(new AttributeModifier(COMBAT_PACING_SPEED_MODIFIER_ID,
                "Ground spider combat pacing", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private void clearCombatPacingSpeedModifier() {
        var movement = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            movement.removeModifier(COMBAT_PACING_SPEED_MODIFIER_ID);
        }
    }

    private int getCombatPacing() {
        return this.entityData.get(COMBAT_PACING);
    }

    private void setCombatPacing(int phase, int ticks) {
        if (getCombatPacing() != phase) {
            this.entityData.set(COMBAT_PACING, phase);
        }
        this.combatPacingTicks = ticks;
    }

    private int getDropAttackPhase() {
        return this.entityData.get(DROP_ATTACK_PHASE);
    }

    private void setDropAttackPhase(int phase, int ticks) {
        if (getDropAttackPhase() != phase) {
            this.entityData.set(DROP_ATTACK_PHASE, phase);
        }
        this.dropAttackTicks = ticks;
    }

    private int getWebShotPhase() {
        return this.entityData.get(WEB_SHOT_PHASE);
    }

    private void setWebShotPhase(int phase, int ticks) {
        if (getWebShotPhase() != phase) {
            this.entityData.set(WEB_SHOT_PHASE, phase);
        }
        this.webShotTicks = ticks;
    }

    private int getWebLowerPhase() {
        return this.entityData.get(WEB_LOWER_PHASE);
    }

    private void setWebLowerPhase(int phase, int ticks) {
        if (getWebLowerPhase() != phase) {
            this.entityData.set(WEB_LOWER_PHASE, phase);
        }
        this.webLowerTicks = ticks;
    }

    private int getPouncePhase() {
        return this.entityData.get(POUNCE_PHASE);
    }

    private void setPouncePhase(int phase, int ticks) {
        if (getPouncePhase() != phase) {
            this.entityData.set(POUNCE_PHASE, phase);
        }
        this.pounceTicks = ticks;
    }

    private int getRetreatPhase() {
        return this.entityData.get(RETREAT_PHASE);
    }

    private void setRetreatPhase(int phase, int ticks) {
        if (getRetreatPhase() != phase) {
            this.entityData.set(RETREAT_PHASE, phase);
        }
        this.retreatTicks = ticks;
    }

    private int getFakeRetreatPhase() {
        return this.entityData.get(FAKE_RETREAT_PHASE);
    }

    private void setFakeRetreatPhase(int phase, int ticks) {
        if (getFakeRetreatPhase() != phase) {
            this.entityData.set(FAKE_RETREAT_PHASE, phase);
        }
        this.fakeRetreatTicks = ticks;
    }

    private int getGrabPullPhase() {
        return this.entityData.get(GRAB_PULL_PHASE);
    }

    private void setGrabPullPhase(int phase, int ticks) {
        if (getGrabPullPhase() != phase) {
            this.entityData.set(GRAB_PULL_PHASE, phase);
        }
        this.grabPullTicks = ticks;
    }

    private int getDragNestPhase() {
        return this.entityData.get(DRAG_NEST_PHASE);
    }

    private void setDragNestPhase(int phase, int ticks) {
        if (getDragNestPhase() != phase) {
            this.entityData.set(DRAG_NEST_PHASE, phase);
        }
        this.dragNestTicks = ticks;
    }

    public boolean isCombatStalking() {
        return getCombatPacing() == COMBAT_PACING_STALK;
    }

    public boolean isStalkingPause() {
        return isCombatStalking()
                && !isBackpedalingFacingTarget()
                && !isCeilingStalking()
                && !isCircleStrafing()
                && !isDropAttackActive()
                && !isWebShotActive()
                && !isWebLowerActive()
                && !isPounceActive()
                && !isRetreatActive()
                && !isFakeRetreatActive()
                && !isGrabPullActive()
                && !isDragNestActive()
                && !isLineOfSightStalking()
                && !isDarknessPreferenceActive()
                && !isWallPeeking()
                && !isPreyInteracting()
                && !isEscapeCutting()
                && !isPackCoordinating();
    }

    public boolean isSprintBurstActive() {
        return getCombatPacing() == COMBAT_PACING_BURST;
    }

    public boolean isBackpedalingFacingTarget() {
        return this.backpedalTicks > 0;
    }

    public int getBackpedalTicks() {
        return this.backpedalTicks;
    }

    public boolean isCeilingStalking() {
        return this.ceilingStalking;
    }

    public BlockPos getCeilingStalkAnchor() {
        return this.ceilingStalkAnchor;
    }

    public boolean isCircleStrafing() {
        return this.circleStrafeTicks > 0;
    }

    public int getCircleStrafeTicks() {
        return this.circleStrafeTicks;
    }

    public String getCircleStrafeDirectionName() {
        return isCircleStrafing()
                ? (this.circleStrafeClockwise ? "right" : "left")
                : "none";
    }

    public boolean isDropAttackActive() {
        return getDropAttackPhase() != DROP_ATTACK_NONE;
    }

    public boolean isDropAttackWindup() {
        return getDropAttackPhase() == DROP_ATTACK_WINDUP;
    }

    public boolean isDropAttackDropping() {
        return getDropAttackPhase() == DROP_ATTACK_DROPPING;
    }

    public boolean isDropAttackRecovering() {
        return getDropAttackPhase() == DROP_ATTACK_RECOVERING;
    }

    public int getDropAttackTicks() {
        return this.dropAttackTicks;
    }

    public int getDropAttackCooldownTicks() {
        return this.dropAttackCooldownTicks;
    }

    public boolean isDropAttackDamageSpent() {
        return this.dropAttackDamageSpent;
    }

    public String getDropAttackPhaseName() {
        switch (getDropAttackPhase()) {
            case DROP_ATTACK_WINDUP:
                return "windup";
            case DROP_ATTACK_DROPPING:
                return "dropping";
            case DROP_ATTACK_RECOVERING:
                return "recovering";
            default:
                return "none";
        }
    }

    public boolean isWebShotActive() {
        return getWebShotPhase() != WEB_SHOT_NONE;
    }

    public boolean isWebShotWindup() {
        return getWebShotPhase() == WEB_SHOT_WINDUP;
    }

    public boolean isWebShotRecovering() {
        return getWebShotPhase() == WEB_SHOT_RECOVERING;
    }

    public int getWebShotTicks() {
        return this.webShotTicks;
    }

    public int getWebShotCooldownTicks() {
        return this.webShotCooldownTicks;
    }

    public boolean isWebShotFired() {
        return this.webShotFired;
    }

    public String getWebShotPhaseName() {
        switch (getWebShotPhase()) {
            case WEB_SHOT_WINDUP:
                return "windup";
            case WEB_SHOT_RECOVERING:
                return "recovering";
            default:
                return "none";
        }
    }

    public boolean isWebTrapPlacementActive() {
        return this.webTrapPlacement && this.webTrapPlacementTargetId != null;
    }

    public int getWebTrapPlacementTicks() {
        return this.webTrapPlacementTicks;
    }

    public int getWebTrapPlacementCooldownTicks() {
        return this.webTrapPlacementCooldownTicks;
    }

    public String getWebTrapPlacementStatus() {
        return this.webTrapPlacementStatus;
    }

    public BlockPos getWebTrapPlacementAnchor() {
        return this.webTrapPlacementAnchor;
    }

    public String getWebTrapPlacementRouteDirectionName() {
        return this.webTrapPlacementRouteDirection == null ? "none" : this.webTrapPlacementRouteDirection.getName();
    }

    public int getWebTrapPlacementPlacedCount() {
        return this.webTrapPlacementPlacedCount;
    }

    public boolean hasWebTrapPlacementPlacedBehind() {
        return this.webTrapPlacementPlacedBehind;
    }

    public boolean hasWebTrapPlacementPlacedBeside() {
        return this.webTrapPlacementPlacedBeside;
    }

    public boolean hasWebTrapPlacementTargetRetained() {
        return this.webTrapPlacementTargetRetained;
    }

    public int getWebTrapPlacementFacingTicks() {
        return this.webTrapPlacementFacingTicks;
    }

    public boolean hasWebTrapPlacementFacedTarget() {
        return this.webTrapPlacementFacingTicks >= 4;
    }

    public double getWebTrapPlacementStartTargetDistance() {
        return this.webTrapPlacementStartTargetDistance;
    }

    public double getWebTrapPlacementCurrentTargetDistance() {
        return this.webTrapPlacementCurrentTargetDistance;
    }

    public boolean isWebLowerActive() {
        return getWebLowerPhase() != WEB_LOWER_NONE;
    }

    public boolean isWebLowerWindup() {
        return getWebLowerPhase() == WEB_LOWER_WINDUP;
    }

    public boolean isWebLowerLowering() {
        return getWebLowerPhase() == WEB_LOWER_LOWERING;
    }

    public boolean isWebLowerRecovering() {
        return getWebLowerPhase() == WEB_LOWER_RECOVERING;
    }

    public int getWebLowerTicks() {
        return this.webLowerTicks;
    }

    public int getWebLowerCooldownTicks() {
        return this.webLowerCooldownTicks;
    }

    public double getWebLowerStartY() {
        return this.webLowerStartY;
    }

    public double getWebLowerLowestY() {
        return this.webLowerLowestY;
    }

    public double getWebLowerDescentDistance() {
        return Math.max(0.0D, this.webLowerStartY - this.webLowerLowestY);
    }

    public BlockPos getWebLowerStrandAnchor() {
        return this.webLowerStrandAnchor;
    }

    public String getWebLowerPhaseName() {
        switch (getWebLowerPhase()) {
            case WEB_LOWER_WINDUP:
                return "windup";
            case WEB_LOWER_LOWERING:
                return "lowering";
            case WEB_LOWER_RECOVERING:
                return "recovering";
            default:
                return "none";
        }
    }

    public boolean isPounceActive() {
        return getPouncePhase() != POUNCE_NONE;
    }

    public boolean isPounceWindup() {
        return getPouncePhase() == POUNCE_WINDUP;
    }

    public boolean isPounceLeaping() {
        return getPouncePhase() == POUNCE_LEAPING;
    }

    public boolean isPounceRecovering() {
        return getPouncePhase() == POUNCE_RECOVERING;
    }

    public int getPounceTicks() {
        return this.pounceTicks;
    }

    public int getPounceCooldownTicks() {
        return this.pounceCooldownTicks;
    }

    public boolean isPounceLaunched() {
        return this.pounceLaunched;
    }

    public boolean isPounceDamageSpent() {
        return this.pounceDamageSpent;
    }

    public String getPouncePhaseName() {
        switch (getPouncePhase()) {
            case POUNCE_WINDUP:
                return "windup";
            case POUNCE_LEAPING:
                return "leaping";
            case POUNCE_RECOVERING:
                return "recovering";
            default:
                return "none";
        }
    }

    public boolean isRetreatActive() {
        return getRetreatPhase() != RETREAT_NONE;
    }

    public boolean isRetreatMoving() {
        return getRetreatPhase() == RETREAT_MOVING;
    }

    public boolean isRetreatRecovering() {
        return getRetreatPhase() == RETREAT_RECOVERING;
    }

    public int getRetreatTicks() {
        return this.retreatTicks;
    }

    public int getRetreatCooldownTicks() {
        return this.retreatCooldownTicks;
    }

    public boolean isRetreatTriggeredByDamage() {
        return this.retreatTriggeredByDamage;
    }

    public boolean isRetreatTriggeredByMiss() {
        return this.retreatTriggeredByMiss;
    }

    public BlockPos getRetreatAnchor() {
        return this.retreatAnchor;
    }

    public double getRetreatStartDistance() {
        return this.retreatStartDistance;
    }

    public double getRetreatMaxDistance() {
        return this.retreatMaxDistance;
    }

    public String getRetreatPhaseName() {
        switch (getRetreatPhase()) {
            case RETREAT_MOVING:
                return "moving";
            case RETREAT_RECOVERING:
                return "recovering";
            default:
                return "none";
        }
    }

    public boolean isFakeRetreatActive() {
        return getFakeRetreatPhase() != FAKE_RETREAT_NONE;
    }

    public boolean isFakeRetreatFleeing() {
        return getFakeRetreatPhase() == FAKE_RETREAT_FLEEING;
    }

    public boolean isFakeRetreatRepositioning() {
        return getFakeRetreatPhase() == FAKE_RETREAT_REPOSITIONING;
    }

    public boolean isFakeRetreatReengaging() {
        return getFakeRetreatPhase() == FAKE_RETREAT_REENGAGING;
    }

    public boolean isFakeRetreatRecovering() {
        return getFakeRetreatPhase() == FAKE_RETREAT_RECOVERING;
    }

    public int getFakeRetreatTicks() {
        return this.fakeRetreatTicks;
    }

    public int getFakeRetreatCooldownTicks() {
        return this.fakeRetreatCooldownTicks;
    }

    public boolean isFakeRetreatTriggeredByDamage() {
        return this.fakeRetreatTriggeredByDamage;
    }

    public boolean isFakeRetreatTriggeredByMiss() {
        return this.fakeRetreatTriggeredByMiss;
    }

    public BlockPos getFakeRetreatAnchor() {
        return this.fakeRetreatAnchor;
    }

    public boolean hasFakeRetreatReengageStarted() {
        return this.fakeRetreatReengageStarted;
    }

    public double getFakeRetreatStartDistance() {
        return this.fakeRetreatStartDistance;
    }

    public double getFakeRetreatMaxDistance() {
        return this.fakeRetreatMaxDistance;
    }

    public double getFakeRetreatDistanceGained() {
        return fakeRetreatDistanceGainedValue();
    }

    public double getFakeRetreatReturnStartDistance() {
        return this.fakeRetreatReturnStartDistance;
    }

    public double getFakeRetreatMinReturnDistance() {
        return this.fakeRetreatMinReturnDistance;
    }

    public double getFakeRetreatReturnClosedDistance() {
        return fakeRetreatReturnClosedDistanceValue();
    }

    public String getFakeRetreatPhaseName() {
        switch (getFakeRetreatPhase()) {
            case FAKE_RETREAT_FLEEING:
                return "fleeing";
            case FAKE_RETREAT_REPOSITIONING:
                return "repositioning";
            case FAKE_RETREAT_REENGAGING:
                return "reengaging";
            case FAKE_RETREAT_RECOVERING:
                return "recovering";
            default:
                return "none";
        }
    }

    public boolean isGrabPullActive() {
        return getGrabPullPhase() != GRAB_PULL_NONE;
    }

    public boolean isGrabPullWindup() {
        return getGrabPullPhase() == GRAB_PULL_WINDUP;
    }

    public boolean isGrabPullPulling() {
        return getGrabPullPhase() == GRAB_PULL_PULLING;
    }

    public boolean isGrabPullRecovering() {
        return getGrabPullPhase() == GRAB_PULL_RECOVERING;
    }

    public int getGrabPullTicks() {
        return this.grabPullTicks;
    }

    public int getGrabPullCooldownTicks() {
        return this.grabPullCooldownTicks;
    }

    public boolean isGrabPullTriggeredByWeb() {
        return this.grabPullTriggeredByWeb;
    }

    public boolean hasGrabPullMovedTarget() {
        return this.grabPullMovedTarget;
    }

    public boolean hasGrabPullSeenPulling() {
        return this.grabPullSawPulling;
    }

    public double getGrabPullStartDistance() {
        return this.grabPullStartDistance;
    }

    public double getGrabPullMinDistance() {
        return this.grabPullMinDistance;
    }

    public double getGrabPullCurrentDistance() {
        LivingEntity target = this.grabPullTargetId == null ? this.getTarget() : findLivingEntityByUuid(this.grabPullTargetId);
        return target == null ? 0.0D : Math.sqrt(this.distanceToSqr(target));
    }

    public double getGrabPullDistanceReduced() {
        return Math.max(0.0D, this.grabPullStartDistance - this.grabPullMinDistance);
    }

    public double getGrabPullTargetStartY() {
        return this.grabPullStartTargetY;
    }

    public double getGrabPullTargetMaxY() {
        return this.grabPullMaxTargetY;
    }

    public double getGrabPullTargetLift() {
        return Math.max(0.0D, this.grabPullMaxTargetY - this.grabPullStartTargetY);
    }

    public String getGrabPullPhaseName() {
        switch (getGrabPullPhase()) {
            case GRAB_PULL_WINDUP:
                return "windup";
            case GRAB_PULL_PULLING:
                return "pulling";
            case GRAB_PULL_RECOVERING:
                return "recovering";
            default:
                return "none";
        }
    }

    public boolean isDragNestActive() {
        return getDragNestPhase() != DRAG_NEST_NONE;
    }

    public boolean isDragNestWindup() {
        return getDragNestPhase() == DRAG_NEST_WINDUP;
    }

    public boolean isDragNestDragging() {
        return getDragNestPhase() == DRAG_NEST_DRAGGING;
    }

    public boolean isDragNestRecovering() {
        return getDragNestPhase() == DRAG_NEST_RECOVERING;
    }

    public int getDragNestTicks() {
        return this.dragNestTicks;
    }

    public int getDragNestCooldownTicks() {
        return this.dragNestCooldownTicks;
    }

    public BlockPos getDragNestAnchor() {
        return this.dragNestAnchor;
    }

    public boolean hasDragNestMovedTarget() {
        return this.dragNestMovedTarget;
    }

    public boolean hasDragNestReachedAnchor() {
        return this.dragNestReachedAnchor;
    }

    public boolean hasDragNestSeenWindup() {
        return this.dragNestSawWindup;
    }

    public boolean hasDragNestSeenDragging() {
        return this.dragNestSawDragging;
    }

    public boolean hasDragNestSeenRecovery() {
        return this.dragNestSawRecovery;
    }

    public double getDragNestStartAnchorDistance() {
        return this.dragNestStartAnchorDistance;
    }

    public double getDragNestCurrentAnchorDistance() {
        LivingEntity target = this.dragNestTargetId == null ? this.getTarget() : findLivingEntityByUuid(this.dragNestTargetId);
        return target == null ? this.dragNestCurrentAnchorDistance : distanceFromTargetToDragNestAnchor(target);
    }

    public double getDragNestMinAnchorDistance() {
        return this.dragNestMinAnchorDistance;
    }

    public double getDragNestAnchorDistanceReduced() {
        return Math.max(0.0D, this.dragNestStartAnchorDistance - this.dragNestMinAnchorDistance);
    }

    public String getDragNestPhaseName() {
        switch (getDragNestPhase()) {
            case DRAG_NEST_WINDUP:
                return "windup";
            case DRAG_NEST_DRAGGING:
                return "dragging";
            case DRAG_NEST_RECOVERING:
                return "recovering";
            default:
                return "none";
        }
    }

    public boolean isPackCoordinating() {
        return this.packRole != PACK_ROLE_NONE && this.packSize >= PACK_COORDINATION_MIN_SIZE;
    }

    public boolean isPackDirectPressureRole() {
        return this.packRole == PACK_ROLE_DIRECT;
    }

    public boolean isPackAmbushRole() {
        return this.packRole == PACK_ROLE_AMBUSH;
    }

    public boolean isPackFlankRole() {
        return this.packRole == PACK_ROLE_FLANK;
    }

    public String getPackRoleName() {
        switch (this.packRole) {
            case PACK_ROLE_DIRECT:
                return "direct";
            case PACK_ROLE_AMBUSH:
                return "ambush";
            case PACK_ROLE_FLANK:
                return "flank";
            default:
                return "none";
        }
    }

    public int getPackRoleTicks() {
        return this.packRoleTicks;
    }

    public int getPackSize() {
        return this.packSize;
    }

    public int getPackDirectCount() {
        return this.packDirectCount;
    }

    public int getPackAmbushCount() {
        return this.packAmbushCount;
    }

    public int getPackFlankCount() {
        return this.packFlankCount;
    }

    public boolean isThreatDisplaying() {
        return this.threatDisplayTicks > 0 && this.threatDisplayTargetId != null;
    }

    public int getThreatDisplayTicks() {
        return this.threatDisplayTicks;
    }

    public int getThreatDisplayCooldownTicks() {
        return this.threatDisplayCooldownTicks;
    }

    public String getThreatDisplayStatus() {
        return this.threatDisplayStatus;
    }

    public double getThreatDisplayStartDistance() {
        return this.threatDisplayStartDistance;
    }

    public double getThreatDisplayCurrentDistance() {
        return this.threatDisplayCurrentDistance;
    }

    public double getThreatDisplayMaxMovement() {
        return this.threatDisplayMaxMovement;
    }

    public int getThreatDisplayFacingTicks() {
        return this.threatDisplayFacingTicks;
    }

    public boolean hasThreatDisplayFacedTarget() {
        return this.threatDisplayFacingTicks >= 4;
    }

    public boolean hasThreatDisplayHeldStill() {
        return this.threatDisplayMaxMovement <= THREAT_DISPLAY_HELD_STILL_DISTANCE;
    }

    public String getThreatDisplayPoseName() {
        return isThreatDisplaying() ? "raised_front" : "none";
    }

    public boolean isLineOfSightStalking() {
        return this.lineOfSightStalkingTicks > 0 && this.lineOfSightStalkingTargetId != null;
    }

    public int getLineOfSightStalkingTicks() {
        return this.lineOfSightStalkingTicks;
    }

    public int getLineOfSightStalkingCooldownTicks() {
        return this.lineOfSightStalkingCooldownTicks;
    }

    public String getLineOfSightStalkingStatus() {
        return this.lineOfSightStalkingStatus;
    }

    public boolean isLineOfSightStalkingTargetLooking() {
        return this.lineOfSightStalkingTargetLooking;
    }

    public int getLineOfSightStalkingWatchedTicks() {
        return this.lineOfSightStalkingWatchedTicks;
    }

    public int getLineOfSightStalkingUnwatchedTicks() {
        return this.lineOfSightStalkingUnwatchedTicks;
    }

    public boolean hasLineOfSightStalkingSawWatched() {
        return this.lineOfSightStalkingSawWatched;
    }

    public boolean hasLineOfSightStalkingSawUnwatchedAdvance() {
        return this.lineOfSightStalkingSawUnwatchedAdvance;
    }

    public double getLineOfSightStalkingStartDistance() {
        return this.lineOfSightStalkingStartDistance;
    }

    public double getLineOfSightStalkingCurrentDistance() {
        return this.lineOfSightStalkingCurrentDistance;
    }

    public double getLineOfSightStalkingMinDistance() {
        return this.lineOfSightStalkingMinDistance;
    }

    public double getLineOfSightStalkingDistanceClosed() {
        return Math.max(0.0D, this.lineOfSightStalkingStartDistance - this.lineOfSightStalkingMinDistance);
    }

    public double getLineOfSightStalkingTotalMovement() {
        return this.lineOfSightStalkingTotalMovement;
    }

    public double getLineOfSightStalkingMaxWatchedMovement() {
        return this.lineOfSightStalkingMaxWatchedMovement;
    }

    public int getLineOfSightStalkingFacingTicks() {
        return this.lineOfSightStalkingFacingTicks;
    }

    public boolean hasLineOfSightStalkingFacedTarget() {
        return this.lineOfSightStalkingFacingTicks >= 4;
    }

    public boolean hasLineOfSightStalkingHeldStillWhileWatched() {
        return this.lineOfSightStalkingSawWatched
                && this.lineOfSightStalkingMaxWatchedMovement <= LINE_OF_SIGHT_STALKING_HOLD_DISTANCE;
    }

    public boolean isDarknessPreferenceActive() {
        return this.darknessPreference && this.darknessPreferenceTargetId != null;
    }

    public int getDarknessPreferenceTicks() {
        return this.darknessPreferenceTicks;
    }

    public int getDarknessPreferenceCooldownTicks() {
        return this.darknessPreferenceCooldownTicks;
    }

    public String getDarknessPreferenceStatus() {
        return this.darknessPreferenceStatus;
    }

    public BlockPos getDarknessPreferenceAnchor() {
        return this.darknessPreferenceAnchor;
    }

    public String getDarknessPreferenceAttachmentName() {
        return this.darknessPreferenceAttachment == null ? "none" : this.darknessPreferenceAttachment.getName();
    }

    public boolean hasDarknessPreferencePathStarted() {
        return this.darknessPreferencePathStarted;
    }

    public boolean hasDarknessPreferenceReachedAnchor() {
        return this.darknessPreferenceReachedAnchor;
    }

    public boolean hasDarknessPreferenceHeldAnchor() {
        return this.darknessPreferenceHeldAnchor;
    }

    public int getDarknessPreferenceFacingTicks() {
        return this.darknessPreferenceFacingTicks;
    }

    public boolean hasDarknessPreferenceFacedTarget() {
        return this.darknessPreferenceFacingTicks >= 4;
    }

    public int getDarknessPreferenceAnchorLight() {
        return this.darknessPreferenceAnchorLight;
    }

    public int getDarknessPreferenceCurrentLight() {
        return this.darknessPreferenceCurrentLight;
    }

    public int getDarknessPreferenceOpenLight() {
        return this.darknessPreferenceOpenLight;
    }

    public boolean isDarknessPreferenceAnchorDarkerThanOpen() {
        return this.darknessPreferenceAnchorLight < this.darknessPreferenceOpenLight;
    }

    public int getDarknessPreferenceCoverCount() {
        return this.darknessPreferenceCoverCount;
    }

    public int getDarknessPreferenceWallAdjacentCount() {
        return this.darknessPreferenceWallAdjacentCount;
    }

    public boolean isDarknessPreferenceCovered() {
        return this.darknessPreferenceCovered;
    }

    public boolean isDarknessPreferenceCorner() {
        return this.darknessPreferenceCorner;
    }

    public double getDarknessPreferenceAnchorScore() {
        return this.darknessPreferenceAnchorScore;
    }

    public double getDarknessPreferenceOpenScore() {
        return this.darknessPreferenceOpenScore;
    }

    public double getDarknessPreferenceScoreAdvantage() {
        return Math.max(0.0D, this.darknessPreferenceOpenScore - this.darknessPreferenceAnchorScore);
    }

    public double getDarknessPreferenceStartAnchorDistance() {
        return this.darknessPreferenceStartAnchorDistance;
    }

    public double getDarknessPreferenceCurrentAnchorDistance() {
        return this.darknessPreferenceCurrentAnchorDistance;
    }

    public double getDarknessPreferenceMinAnchorDistance() {
        return this.darknessPreferenceMinAnchorDistance;
    }

    public double getDarknessPreferenceAnchorDistanceReduced() {
        return Math.max(0.0D, this.darknessPreferenceStartAnchorDistance - this.darknessPreferenceMinAnchorDistance);
    }

    public boolean isWallPeeking() {
        return this.wallPeekPhase != WALL_PEEK_NONE && this.wallPeekTargetId != null;
    }

    public boolean isWallPeekHolding() {
        return this.wallPeekPhase == WALL_PEEK_HOLDING;
    }

    public String getWallPeekPhaseName() {
        switch (this.wallPeekPhase) {
            case WALL_PEEK_EMERGING:
                return "emerging";
            case WALL_PEEK_HOLDING:
                return "holding";
            case WALL_PEEK_RETREATING:
                return "retreating";
            default:
                return "none";
        }
    }

    public int getWallPeekTicks() {
        return this.wallPeekTicks;
    }

    public int getWallPeekCooldownTicks() {
        return this.wallPeekCooldownTicks;
    }

    public String getWallPeekStatus() {
        return this.wallPeekStatus;
    }

    public BlockPos getWallPeekCoverAnchor() {
        return this.wallPeekCoverAnchor;
    }

    public BlockPos getWallPeekPeekAnchor() {
        return this.wallPeekPeekAnchor;
    }

    public boolean hasWallPeekPathStarted() {
        return this.wallPeekPathStarted;
    }

    public boolean hasWallPeekReachedPeek() {
        return this.wallPeekReachedPeek;
    }

    public boolean hasWallPeekHeldPeek() {
        return this.wallPeekHeldPeek;
    }

    public boolean hasWallPeekRetreated() {
        return this.wallPeekRetreated;
    }

    public boolean hasWallPeekTargetRetained() {
        return this.wallPeekTargetRetained;
    }

    public boolean isWallPeekCoverLineOfSightBlocked() {
        return this.wallPeekCoverLineOfSightBlocked;
    }

    public boolean isWallPeekPeekLineOfSightClear() {
        return this.wallPeekPeekLineOfSightClear;
    }

    public int getWallPeekFacingTicks() {
        return this.wallPeekFacingTicks;
    }

    public boolean hasWallPeekFacedTarget() {
        return this.wallPeekFacingTicks >= 4;
    }

    public double getWallPeekStartPeekDistance() {
        return this.wallPeekStartPeekDistance;
    }

    public double getWallPeekCurrentPeekDistance() {
        return this.wallPeekCurrentPeekDistance;
    }

    public double getWallPeekMinPeekDistance() {
        return this.wallPeekMinPeekDistance;
    }

    public double getWallPeekPeekDistanceReduced() {
        return Math.max(0.0D, this.wallPeekStartPeekDistance - this.wallPeekMinPeekDistance);
    }

    public double getWallPeekStartCoverDistance() {
        return this.wallPeekStartCoverDistance;
    }

    public double getWallPeekCurrentCoverDistance() {
        return this.wallPeekCurrentCoverDistance;
    }

    public double getWallPeekMinCoverDistance() {
        return this.wallPeekMinCoverDistance;
    }

    public double getWallPeekCoverReturnDistanceReduced() {
        return Math.max(0.0D, this.wallPeekStartCoverDistance - this.wallPeekMinCoverDistance);
    }

    public boolean isPreyInteracting() {
        return this.preyInteractionPhase != PREY_INTERACTION_NONE && this.preyInteractionTargetId != null;
    }

    public String getPreyInteractionPhaseName() {
        switch (this.preyInteractionPhase) {
            case PREY_INTERACTION_WEBBING:
                return "webbing";
            case PREY_INTERACTION_GUARDING:
                return "guarding";
            default:
                return "none";
        }
    }

    public int getPreyInteractionTicks() {
        return this.preyInteractionTicks;
    }

    public int getPreyInteractionCooldownTicks() {
        return this.preyInteractionCooldownTicks;
    }

    public String getPreyInteractionStatus() {
        return this.preyInteractionStatus;
    }

    public String getPreyInteractionPreyType() {
        return this.preyInteractionPreyType;
    }

    public BlockPos getPreyInteractionPreyAnchor() {
        return this.preyInteractionPreyAnchor;
    }

    public BlockPos getPreyInteractionGuardAnchor() {
        return this.preyInteractionGuardAnchor;
    }

    public boolean hasPreyInteractionPathStarted() {
        return this.preyInteractionPathStarted;
    }

    public boolean hasPreyInteractionReachedGuard() {
        return this.preyInteractionReachedGuard;
    }

    public boolean hasPreyInteractionHeldGuard() {
        return this.preyInteractionHeldGuard;
    }

    public boolean hasPreyInteractionPlacedWeb() {
        return this.preyInteractionPlacedWeb;
    }

    public int getPreyInteractionPlacedWebCount() {
        return this.preyInteractionPlacedWebCount;
    }

    public boolean hasPreyInteractionTargetKilled() {
        return this.preyInteractionTargetKilled;
    }

    public int getPreyInteractionFacingTicks() {
        return this.preyInteractionFacingTicks;
    }

    public boolean hasPreyInteractionFacedPreyArea() {
        return this.preyInteractionFacingTicks >= 4;
    }

    public double getPreyInteractionStartGuardDistance() {
        return this.preyInteractionStartGuardDistance;
    }

    public double getPreyInteractionCurrentGuardDistance() {
        return this.preyInteractionCurrentGuardDistance;
    }

    public double getPreyInteractionMinGuardDistance() {
        return this.preyInteractionMinGuardDistance;
    }

    public double getPreyInteractionGuardDistanceReduced() {
        return Math.max(0.0D, this.preyInteractionStartGuardDistance - this.preyInteractionMinGuardDistance);
    }

    public boolean isEscapeCutting() {
        return this.escapeCutting;
    }

    public int getEscapeCuttingTicks() {
        return this.escapeCuttingTicks;
    }

    public int getEscapeCuttingCooldownTicks() {
        return this.escapeCuttingCooldownTicks;
    }

    public BlockPos getEscapeCuttingAnchor() {
        return this.escapeCuttingAnchor;
    }

    public String getEscapeCuttingRouteDirectionName() {
        return this.escapeCuttingRouteDirection == null ? "none" : this.escapeCuttingRouteDirection.getName();
    }

    public boolean hasEscapeCuttingPathStarted() {
        return this.escapeCuttingPathStarted;
    }

    public boolean hasEscapeCuttingReachedAnchor() {
        return this.escapeCuttingReachedAnchor;
    }

    public double getEscapeCuttingStartAnchorDistance() {
        return this.escapeCuttingStartAnchorDistance;
    }

    public double getEscapeCuttingCurrentAnchorDistance() {
        return this.escapeCuttingCurrentAnchorDistance;
    }

    public double getEscapeCuttingMinAnchorDistance() {
        return this.escapeCuttingMinAnchorDistance;
    }

    public double getEscapeCuttingAnchorDistanceReduced() {
        return Math.max(0.0D, this.escapeCuttingStartAnchorDistance - this.escapeCuttingMinAnchorDistance);
    }

    public String getEscapeCuttingStatus() {
        return this.escapeCuttingStatus;
    }

    public int getCombatPacingTicks() {
        return this.combatPacingTicks;
    }

    public String getCombatPacingStateName() {
        switch (getCombatPacing()) {
            case COMBAT_PACING_STALK:
                return "stalk";
            case COMBAT_PACING_BURST:
                return "burst";
            default:
                return "none";
        }
    }

    @Override
    public void travel(Vec3 input) {
        if (this.forcedPath != null) {
            // Movement handled manually in tick while on a forced path
            this.setNoGravity(true);
            return;
        }

        if (isWebTrapPlacementActive() || isWallPeekHolding() || isGrabPullActive() || isDragNestActive()) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        Direction attach = getAttachmentDirection();
        if (this.noPhysics && attach != Direction.DOWN) {
            this.setNoGravity(true);
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        boolean attached = (attach != Direction.DOWN);

        this.setNoGravity(attached);

        if (!attached) {
            super.travel(input);
            return;
        }

        // Minimal constraints for all attached states (pathing or idle)
        Vec3 v = this.getDeltaMovement();

        // Drag: horizontal (plane) high, vertical mild
        v = new Vec3(v.x * 0.60D, v.y * 0.90D, v.z * 0.60D);

        float forward = this.zza;
        if (forward > 0.0F) {
            Vec3 look = this.getLookAngle();
            Vec3 n = AttachmentHelper.normal(attach);
            Vec3 tangential = look.subtract(n.scale(look.dot(n)));
            if (tangential.lengthSqr() > 1.0E-6) tangential = tangential.normalize();

            double base = this.getAttributeValue(Attributes.MOVEMENT_SPEED);
            v = v.add(tangential.scale(forward * base * 0.15D));
        }

        // Remove normal drift and clamp plane speed
        Vec3 n = AttachmentHelper.normal(attach);
        double base = this.getAttributeValue(Attributes.MOVEMENT_SPEED);
        Vec3 plane = v.subtract(n.scale(v.dot(n)));
        if (plane.lengthSqr() > base * base) {
            plane = plane.normalize().scale(base);
        }
        double glue = attach == Direction.UP ? 0.0D : 0.04D;
        v = plane.add(n.scale(glue)); // glue toward wall surfaces without drifting through ceilings

        this.setDeltaMovement(v);
        this.move(MoverType.SELF, v);
    }

    private void updateSingleThreadWebTraversalOrientation(Vec3 motion) {
        if (!isOnSingleThreadWeb()) {
            this.entityData.set(WEB_TRAVERSAL_REVERSE, false);
            return;
        }

        Vec3 strand = currentSingleThreadWebTangent();
        Vec3 movement = tangentForCurrentAttachment(motion);
        if (strand == null || strand.lengthSqr() <= WEB_TRAVERSAL_DIRECTION_EPSILON_SQR) {
            strand = movement;
        }
        if (strand == null || strand.lengthSqr() <= WEB_TRAVERSAL_DIRECTION_EPSILON_SQR) {
            return;
        }

        Vec3 strandDirection = strand.normalize();
        boolean reverse = movement != null
                && movement.lengthSqr() > WEB_TRAVERSAL_DIRECTION_EPSILON_SQR
                && strandDirection.dot(movement.normalize()) < -0.05D;
        float yaw = yawForTangent(strandDirection);
        applyWebTraversalBodyYaw(yaw);
        this.webTraversalBodyYaw = yaw;
        this.entityData.set(WEB_TRAVERSAL_REVERSE, reverse);
    }

    private void lookAtWithUnlockedHead(LivingEntity target) {
        if (target == null) {
            return;
        }

        lookAtPointWithUnlockedHead(new Vec3(target.getX(), target.getEyeY(), target.getZ()));
        this.getLookControl().setLookAt(target, WEB_TRAVERSAL_HEAD_YAW_LIMIT, WEB_TRAVERSAL_HEAD_PITCH_LIMIT);
    }

    private void lookAtPointWithUnlockedHead(Vec3 point) {
        Vec3 toTarget = new Vec3(point.x - this.getX(), point.y - this.getEyeY(), point.z - this.getZ());
        Vec3 tangent = tangentForCurrentAttachment(toTarget);
        if (tangent != null && tangent.lengthSqr() > WEB_TRAVERSAL_DIRECTION_EPSILON_SQR) {
            float targetYaw = yawForTangent(tangent);
            float yawDelta = Mth.clamp(Mth.wrapDegrees(targetYaw - this.yBodyRot),
                    -WEB_TRAVERSAL_HEAD_YAW_LIMIT, WEB_TRAVERSAL_HEAD_YAW_LIMIT);
            this.webTraversalHeadYaw = yawDelta;
            this.yHeadRot = this.yBodyRot + yawDelta;
        }

        double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        float targetPitch = (float) (-(Math.atan2(toTarget.y, horizontalDistance) * (180.0D / Math.PI)));
        this.webTraversalHeadPitch = Mth.clamp(targetPitch,
                -WEB_TRAVERSAL_HEAD_PITCH_LIMIT, WEB_TRAVERSAL_HEAD_PITCH_LIMIT);
        this.setXRot(this.webTraversalHeadPitch);
        this.getLookControl().setLookAt(point.x, point.y, point.z,
                WEB_TRAVERSAL_HEAD_YAW_LIMIT, WEB_TRAVERSAL_HEAD_PITCH_LIMIT);
    }

    private Vec3 currentSingleThreadWebTangent() {
        Direction attachment = getAttachmentDirection();
        BlockPos support = this.blockPosition().relative(attachment);
        BlockEntity blockEntity = this.level.getBlockEntity(support);
        if (!(blockEntity instanceof SingleThreadWebBlockEntity strand) || !strand.hasStrand()) {
            return null;
        }

        Vec3 start = blockCenter(strand.getFirstAnchor());
        Vec3 end = blockCenter(strand.getSecondAnchor());
        return tangentForCurrentAttachment(end.subtract(start));
    }

    private Vec3 tangentForCurrentAttachment(Vec3 vector) {
        if (vector == null) {
            return null;
        }
        Direction attachment = getAttachmentDirection();
        if (attachment == Direction.DOWN) {
            return new Vec3(vector.x, 0.0D, vector.z);
        }
        return AttachmentHelper.projectOntoPlane(vector, AttachmentHelper.normal(attachment));
    }

    private static Vec3 blockCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    private static float yawForTangent(Vec3 tangent) {
        return (float) (Math.atan2(tangent.z, tangent.x) * (180.0D / Math.PI)) - 90.0F;
    }

    private void applyWebTraversalBodyYaw(float yaw) {
        this.setYRot(yaw);
        this.yBodyRot = yaw;
    }

    public boolean isWebTraversalReverseAnimation() {
        return this.entityData.get(WEB_TRAVERSAL_REVERSE);
    }

    public float getWebTraversalBodyYawDegrees() {
        return this.webTraversalBodyYaw;
    }

    public float getWebTraversalHeadYawDegrees() {
        return Mth.wrapDegrees(this.yHeadRot - this.yBodyRot);
    }

    public float getWebTraversalHeadPitchDegrees() {
        return this.getXRot();
    }

    public double getWebTraversalBodyAlignmentErrorDegrees() {
        if (!isOnSingleThreadWeb()) {
            return 0.0D;
        }
        Vec3 strand = currentSingleThreadWebTangent();
        if (strand == null || strand.lengthSqr() <= WEB_TRAVERSAL_DIRECTION_EPSILON_SQR) {
            return 0.0D;
        }
        float expectedYaw = yawForTangent(strand);
        return Math.abs(Mth.wrapDegrees(this.yBodyRot - expectedYaw));
    }

    public boolean isWebTraversalBodyAligned() {
        return getWebTraversalBodyAlignmentErrorDegrees() <= WEB_TRAVERSAL_ALIGNMENT_MAX_DEGREES;
    }

    @Override protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, new DenseAwareMeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestReachableAttackableTargetGoal<>(this, Player.class, 10, true, null));
        this.targetSelector.addGoal(3, new NearestReachableAttackableTargetGoal<>(this, IronGolem.class, 10, true, null));
    }

    private static final class DenseAwareMeleeAttackGoal extends MeleeAttackGoal {
        private final GroundSpiderEntity spider;

        private DenseAwareMeleeAttackGoal(GroundSpiderEntity spider, double speedModifier, boolean followingTargetEvenIfNotSeen) {
            super(spider, speedModifier, followingTargetEvenIfNotSeen);
            this.spider = spider;
        }

        @Override
        public boolean canUse() {
            return !this.spider.usesDenseLivePlayerFastCombat()
                    && !this.spider.shouldSuspendMeleeAttackGoal()
                    && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.spider.usesDenseLivePlayerFastCombat()
                    && !this.spider.shouldSuspendMeleeAttackGoal()
                    && super.canContinueToUse();
        }
    }

    @Override public MobType getMobType() { return MobType.ARTHROPOD; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.SPIDER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.SPIDER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SPIDER_DEATH; }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (isThreatDisplaying() || isLineOfSightStalking() || isDarknessPreferenceActive() || isWallPeeking()
                || isPreyInteracting()
                || isWebTrapPlacementActive()
                || isGrabPullActive() || isDragNestActive()) {
            return false;
        }
        if (target instanceof LivingEntity living && !canSpendDenseLivePlayerDamage(living)) {
            return false;
        }
        boolean hurt = super.doHurtTarget(target);
        if (hurt && target instanceof LivingEntity living) {
            recordPlayerExperienceAttack(living);
            maybeStartPreyInteractionAfterDamage(living);
        }
        return hurt;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && amount > 0.0F && !this.level.isClientSide) {
            Entity sourceEntity = source.getEntity();
            if (sourceEntity instanceof LivingEntity attacker && attacker.isAlive()) {
                this.setTarget(attacker);
                queueRetreat(attacker, true, false);
                if (!isDropAttackActive() && !isWebShotActive() && !isWebTrapPlacementActive()
                        && !isWebLowerActive() && !isPounceActive()
                        && !isRetreatActive() && !isFakeRetreatActive() && !isGrabPullActive() && !isDragNestActive()
                        && !isWallPeeking()) {
                    tryStartPendingRetreat(attacker);
                }
            }
        }
        return hurt;
    }

    @Override
    public boolean causeFallDamage(float distance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.SPIDER_STEP, 0.15F, 1.0F);
    }

    // GeckoLib animation methods
    private static boolean isWalkLikeAnimationName(String animation) {
        return ANIMATION_WALK.equals(animation)
                || ANIMATION_ATTACHED_WALK.equals(animation)
                || ANIMATION_CIRCLE_RIGHT.equals(animation)
                || ANIMATION_RAISED_CIRCLE_RIGHT.equals(animation)
                || ANIMATION_RAISED_WALK.equals(animation)
                || ANIMATION_RAISED_WALK_RIGHT.equals(animation)
                || ANIMATION_RAISED_WALK_LEFT.equals(animation);
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        String animation = isAnimationMovementActive() ? movementAnimationName() : ANIMATION_IDLE;
        event.getController().setAnimationSpeed(ANIMATION_ATTACHED_WALK.equals(animation) && isWebTraversalReverseAnimation()
                ? -1.0D : 1.0D);
        event.getController().setAnimation(new AnimationBuilder().addAnimation(animation, ILoopType.EDefaultLoopTypes.LOOP));
        return PlayState.CONTINUE;
    }

    public boolean isAnimationMovementActive() {
        if (isThreatDisplaying()) {
            return true;
        }
        if (isLineOfSightStalking()) {
            return !this.lineOfSightStalkingTargetLooking;
        }
        if (isDarknessPreferenceActive()) {
            return !this.darknessPreferenceHeldAnchor;
        }
        if (isWallPeeking()) {
            return !isWallPeekHolding();
        }
        if (isPreyInteracting()) {
            return !this.preyInteractionHeldGuard;
        }
        if (isWebShotActive()) {
            return false;
        }
        if (isGrabPullWindup()) {
            return false;
        }
        if (isGrabPullPulling() || isGrabPullRecovering()) {
            return true;
        }
        if (isDragNestWindup()) {
            return false;
        }
        if (isDragNestDragging() || isDragNestRecovering()) {
            return true;
        }
        if (isWebLowerWindup()) {
            return false;
        }
        if (isWebLowerLowering() || isWebLowerRecovering()) {
            return true;
        }
        if (isPounceWindup()) {
            return false;
        }
        if (isPounceLeaping() || isPounceRecovering()) {
            return true;
        }
        if (isDropAttackWindup()) {
            return false;
        }
        if (isDropAttackDropping() || isDropAttackRecovering()) {
            return true;
        }
        if (isRetreatActive()) {
            return true;
        }
        if (isFakeRetreatActive()) {
            return true;
        }
        if (isBackpedalingFacingTarget()) {
            return true;
        }
        if (isCeilingStalking()) {
            return !this.getNavigation().isDone() || this.getDeltaMovement().lengthSqr() > ANIMATION_MOVEMENT_EPSILON_SQR;
        }
        if (isCircleStrafing()) {
            return true;
        }
        if (isStalkingPause()) {
            return false;
        }
        Vec3 movement = this.getDeltaMovement();
        Direction attachment = this.getAttachmentDirection();
        if (attachment != Direction.DOWN) {
            Vec3 normal = AttachmentHelper.normal(attachment);
            movement = movement.subtract(normal.scale(movement.dot(normal)));
        }
        if (this.isFollowingForcedPath()) {
            return true;
        }
        return movement.lengthSqr() > ANIMATION_MOVEMENT_EPSILON_SQR;
    }

    public String getAnimationAuditName() {
        return isAnimationMovementActive() ? movementAnimationName() : ANIMATION_IDLE;
    }

    public boolean spinSingleThreadWeb(BlockPos first, BlockPos second) {
        return SingleThreadWebBlock.placeLine(this.level, first, second, BlockRegistry.SINGLE_THREAD_WEB.get());
    }

    public boolean isOnSingleThreadWeb() {
        Direction attachment = getAttachmentDirection();
        BlockPos support = this.blockPosition().relative(attachment);
        return this.level.getBlockState(support).is(BlockRegistry.SINGLE_THREAD_WEB.get());
    }

    private String movementAnimationName() {
        if (isThreatDisplaying()) {
            return ANIMATION_THREAT_DISPLAY;
        }
        if (isLineOfSightStalking()) {
            return raisedGroundOrAttachedWalkAnimationName();
        }
        if (isDarknessPreferenceActive()) {
            return raisedGroundOrAttachedWalkAnimationName();
        }
        if (isWallPeeking()) {
            return raisedGroundOrAttachedWalkAnimationName();
        }
        if (isPreyInteracting()) {
            return raisedGroundOrAttachedWalkAnimationName();
        }
        if (isFakeRetreatActive()) {
            return groundOrAttachedWalkAnimationName();
        }
        if (isDragNestDragging() || isDragNestRecovering()) {
            return groundOrAttachedWalkAnimationName();
        }
        if (isGrabPullPulling() || isGrabPullRecovering()) {
            return groundOrAttachedWalkAnimationName();
        }
        if (isPounceLeaping() || isPounceRecovering()) {
            return ANIMATION_DROP_ATTACK;
        }
        if (isDropAttackDropping() || isDropAttackRecovering()) {
            return ANIMATION_DROP_ATTACK;
        }
        if (isWebLowerLowering() || isWebLowerRecovering()) {
            return ANIMATION_ATTACHED_WALK;
        }
        if (isCircleStrafing() && this.getAttachmentDirection() == Direction.DOWN) {
            return this.circleStrafeClockwise ? ANIMATION_RAISED_CIRCLE_RIGHT : ANIMATION_RAISED_WALK_LEFT;
        }
        return groundOrAttachedWalkAnimationName();
    }

    private String groundOrAttachedWalkAnimationName() {
        return shouldUseWebTraversalAnimation() ? ANIMATION_ATTACHED_WALK : ANIMATION_WALK;
    }

    private String raisedGroundOrAttachedWalkAnimationName() {
        if (shouldUseWebTraversalAnimation()) {
            return ANIMATION_ATTACHED_WALK;
        }
        if (this.xxa > 0.02F) {
            return ANIMATION_RAISED_WALK_RIGHT;
        }
        if (this.xxa < -0.02F) {
            return ANIMATION_RAISED_WALK_LEFT;
        }
        return ANIMATION_RAISED_WALK;
    }

    private boolean shouldUseWebTraversalAnimation() {
        return isWebLowerLowering() || isWebLowerRecovering() || isOnSingleThreadWeb();
    }

    @Override public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 5, this::predicate));
    }
    @Override public AnimationFactory getFactory() { return this.factory; }
}
