package com.horrormods.spiders.command;

import com.horrormods.spiders.Spiders;
import com.horrormods.spiders.block.SingleThreadWebBlock;
import com.horrormods.spiders.entity.GroundSpiderEntity;
import com.horrormods.spiders.entity.util.AttachmentHelper;
import com.horrormods.spiders.registry.BlockRegistry;
import com.horrormods.spiders.registry.EntityRegistry;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = Spiders.ModID)
public final class SpiderCommands {
    private static final double DEFAULT_RANGE = 32.0D;
    private static final double DEFAULT_FORCE_PATH_SPEED = 0.25D;
    private static final int[][] NATURAL_PRESSURE_SITE_OFFSETS = {
            {96, 72},
            {-112, 96},
            {64, 128},
            {-96, -128},
            {128, 128}
    };
    private static final int NATURAL_PRESSURE_MAX_STARTS = 18;
    private static final int NATURAL_PRESSURE_MAX_PATH_ATTEMPTS = 80;
    private static final int NATURAL_PRESSURE_MIN_STABLE_START_CELLS = 7;
    private static final int NATURAL_PRESSURE_PLAYER_FOOD_LEVEL = 16;
    private static final int NATURAL_PRESSURE_CLEAR_FLOOR_RADIUS = 6;
    private static final int NATURAL_PRESSURE_DROP_GUARD_RADIUS = 18;
    private static final int NATURAL_PRESSURE_MAX_SAFE_DROP = 8;
    private static final double NATURAL_PRESSURE_VISUAL_ATTACK_DAMAGE = 0.12D;
    private static final long CONTROLLED_VISUAL_TEST_DAY_TIME = 6000L;
    private static final int CONTROLLED_VISUAL_TEST_CLEAR_WEATHER_TICKS = 1_000_000;
    private static final int CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS = 1_000_000;
    private static final double CONTROLLED_PLAYER_PRESSURE_ATTACK_DAMAGE = 0.55D;
    private static final double CONTROLLED_WALL_SENTRY_ATTACK_DAMAGE = 0.10D;
    private static final double STRESS_PLAYER_PRESSURE_ATTACK_DAMAGE = 0.60D;
    private static final int STRESS_PLAYER_PRESSURE_RESISTANCE_AMPLIFIER = 1;
    private static final int STRESS_PLAYER_PRESSURE_REGENERATION_AMPLIFIER = 1;
    private static final double VISUAL_TEST_ENTITY_CLEAR_RADIUS = 192.0D;
    private static final double BOT_POV_CAMERA_BACK_OFFSET = 0.25D;

    private record NaturalSpawn(BlockPos airPos, Direction attachment) {
    }

    private record NaturalPressureSpawnResult(List<GroundSpiderEntity> spiders, int candidates, int attempts,
                                              int nonFloorStarted) {
        int started() {
            return spiders.size();
        }
    }

    private SpiderCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("spiders")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("audit")
                        .then(Commands.literal("nearest")
                                .executes(context -> auditNearest(context, DEFAULT_RANGE))
                                .then(Commands.argument("range", DoubleArgumentType.doubleArg(1.0D, 256.0D))
                                        .executes(context -> auditNearest(context,
                                                DoubleArgumentType.getDouble(context, "range")))))
                        .then(Commands.literal("arena")
                                .executes(context -> auditArena(context, DEFAULT_RANGE))
                                .then(Commands.argument("range", DoubleArgumentType.doubleArg(1.0D, 256.0D))
                                        .executes(context -> auditArena(context,
                                                DoubleArgumentType.getDouble(context, "range")))))
                        .then(Commands.literal("surfaces")
                                .executes(context -> auditSurfaces(context, DEFAULT_RANGE))
                                .then(Commands.argument("range", DoubleArgumentType.doubleArg(1.0D, 256.0D))
                                        .executes(context -> auditSurfaces(context,
                                                DoubleArgumentType.getDouble(context, "range")))))
                        .then(Commands.literal("swarm")
                                .executes(context -> auditSwarm(context, DEFAULT_RANGE))
                                .then(Commands.argument("range", DoubleArgumentType.doubleArg(1.0D, 256.0D))
                                        .executes(context -> auditSwarm(context,
                                                DoubleArgumentType.getDouble(context, "range")))))
                        .then(Commands.literal("player_pressure")
                                .executes(context -> auditPlayerPressure(context, DEFAULT_RANGE))
                                .then(Commands.argument("range", DoubleArgumentType.doubleArg(1.0D, 256.0D))
                                        .executes(context -> auditPlayerPressure(context,
                                                DoubleArgumentType.getDouble(context, "range")))))
                        .then(Commands.literal("player_pressure_compact")
                                .executes(context -> auditPlayerPressureCompact(context, DEFAULT_RANGE))
                                .then(Commands.argument("range", DoubleArgumentType.doubleArg(1.0D, 256.0D))
                                        .executes(context -> auditPlayerPressureCompact(context,
                                                DoubleArgumentType.getDouble(context, "range")))))
                        .then(Commands.literal("player_pressure_summary")
                                .executes(context -> auditPlayerPressureSummary(context, DEFAULT_RANGE))
                                .then(Commands.argument("range", DoubleArgumentType.doubleArg(1.0D, 256.0D))
                                        .executes(context -> auditPlayerPressureSummary(context,
                                                DoubleArgumentType.getDouble(context, "range")))))
                        .then(Commands.literal("environment")
                                .executes(SpiderCommands::auditEnvironment))
                        .then(Commands.literal("bot_pov_camera")
                                .then(Commands.argument("bot", EntityArgument.player())
                                        .then(Commands.argument("observer", EntityArgument.player())
                                                .executes(SpiderCommands::auditBotPovCamera)))))
                .then(Commands.literal("force_path_nearest")
                        .then(Commands.argument("target", BlockPosArgument.blockPos())
                                .executes(context -> forcePathNearest(context, DEFAULT_FORCE_PATH_SPEED))
                                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.01D, 2.0D))
                                        .executes(context -> forcePathNearest(context,
                                                DoubleArgumentType.getDouble(context, "speed"))))))
                .then(Commands.literal("wall_visual_test")
                        .executes(SpiderCommands::wallVisualTest))
                .then(Commands.literal("wall_combat_visual_test")
                        .executes(SpiderCommands::wallCombatVisualTest))
                .then(Commands.literal("wall_multi_combat_visual_test")
                        .executes(SpiderCommands::wallMultiCombatVisualTest))
                .then(Commands.literal("mixed_surface_combat_visual_test")
                        .executes(context -> mixedSurfaceCombatVisualTest(context, true)))
                .then(Commands.literal("mixed_surface_combat_stage_visual_test")
                        .executes(context -> mixedSurfaceCombatVisualTest(context, false)))
                .then(Commands.literal("mixed_surface_combat_start_nearest")
                        .executes(context -> startMixedSurfaceCombatNearest(context, DEFAULT_RANGE))
                        .then(Commands.argument("range", DoubleArgumentType.doubleArg(1.0D, 256.0D))
                                .executes(context -> startMixedSurfaceCombatNearest(context,
                                        DoubleArgumentType.getDouble(context, "range")))))
                .then(Commands.literal("mixed_surface_swarm_visual_test")
                        .executes(SpiderCommands::mixedSurfaceSwarmVisualTest))
                .then(Commands.literal("player_pressure_visual_test")
                        .executes(SpiderCommands::playerPressureVisualTest))
                .then(Commands.literal("player_pressure_stress_visual_test")
                        .executes(SpiderCommands::playerPressureStressVisualTest))
                .then(Commands.literal("drop_attack_visual_test")
                        .executes(SpiderCommands::dropAttackVisualTest))
                .then(Commands.literal("web_shot_visual_test")
                        .executes(SpiderCommands::webShotVisualTest))
                .then(Commands.literal("web_trap_placement_visual_test")
                        .executes(SpiderCommands::webTrapPlacementVisualTest))
                .then(Commands.literal("single_thread_web_visual_test")
                        .executes(SpiderCommands::singleThreadWebVisualTest))
                .then(Commands.literal("pounce_visual_test")
                        .executes(SpiderCommands::pounceVisualTest))
                .then(Commands.literal("retreat_visual_test")
                        .executes(SpiderCommands::retreatVisualTest))
                .then(Commands.literal("fake_retreat_visual_test")
                        .executes(SpiderCommands::fakeRetreatVisualTest))
                .then(Commands.literal("web_lower_visual_test")
                        .executes(SpiderCommands::webLowerVisualTest))
                .then(Commands.literal("grab_pull_visual_test")
                        .executes(SpiderCommands::grabPullVisualTest))
                .then(Commands.literal("drag_nest_visual_test")
                        .executes(SpiderCommands::dragNestVisualTest))
                .then(Commands.literal("pack_coordination_visual_test")
                        .executes(SpiderCommands::packCoordinationVisualTest))
                .then(Commands.literal("escape_cutting_visual_test")
                        .executes(SpiderCommands::escapeCuttingVisualTest))
                .then(Commands.literal("threat_display_visual_test")
                        .executes(SpiderCommands::threatDisplayVisualTest))
                .then(Commands.literal("line_of_sight_stalking_visual_test")
                        .executes(SpiderCommands::lineOfSightStalkingVisualTest))
                .then(Commands.literal("line_of_sight_stalking_look_away")
                        .executes(SpiderCommands::lineOfSightStalkingLookAway))
                .then(Commands.literal("darkness_preference_visual_test")
                        .executes(SpiderCommands::darknessPreferenceVisualTest))
                .then(Commands.literal("wall_peek_visual_test")
                        .executes(SpiderCommands::wallPeekVisualTest))
                .then(Commands.literal("prey_interaction_visual_test")
                        .executes(SpiderCommands::preyInteractionVisualTest))
                .then(Commands.literal("player_pressure_gauntlet_visual_test")
                        .executes(SpiderCommands::playerPressureGauntletVisualTest))
                .then(Commands.literal("player_pressure_field_visual_test")
                        .executes(SpiderCommands::playerPressureFieldVisualTest))
                .then(Commands.literal("player_pressure_natural_visual_test")
                        .executes(context -> playerPressureNaturalVisualTest(context, 0))
                        .then(Commands.argument("site", IntegerArgumentType.integer(0,
                                        NATURAL_PRESSURE_SITE_OFFSETS.length - 1))
                                .executes(context -> playerPressureNaturalVisualTest(context,
                                        IntegerArgumentType.getInteger(context, "site")))))
                .then(Commands.literal("bot_pov_camera_sync")
                        .then(Commands.argument("bot", EntityArgument.player())
                                .then(Commands.argument("observer", EntityArgument.player())
                                        .executes(context -> auditBotPovCamera(context, true, false)))))
                .then(Commands.literal("bot_pov_camera_spectate")
                        .then(Commands.argument("bot", EntityArgument.player())
                                .then(Commands.argument("observer", EntityArgument.player())
                                        .executes(context -> auditBotPovCamera(context, false, true)))))
                .then(Commands.literal("bot_pov_camera_unspectate")
                        .then(Commands.argument("observer", EntityArgument.player())
                                .executes(SpiderCommands::clearBotPovCamera)))
                .then(Commands.literal("surface_orientation_visual_test")
                        .executes(SpiderCommands::surfaceOrientationVisualTest)));
    }

    private static int auditNearest(CommandContext<CommandSourceStack> context, double range) {
        CommandSourceStack source = context.getSource();
        Optional<GroundSpiderEntity> nearest = SpiderAiAudit.findNearest(source.getLevel(), source.getPosition(), range);
        if (nearest.isEmpty()) {
            source.sendSuccess(Component.literal("spiders_audit found=false range=" + format(range)), false);
            return 0;
        }

        source.sendSuccess(Component.literal(SpiderAiAudit.describe(nearest.get())), false);
        return 1;
    }

    private static int auditArena(CommandContext<CommandSourceStack> context, double range) {
        try {
            CommandSourceStack source = context.getSource();
            ServerLevel level = source.getLevel();
            Optional<GroundSpiderEntity> nearest = SpiderAiAudit.findNearest(level, source.getPosition(), range);
            List<IronGolem> targets = findArenaTargets(level, source.getPosition(), range);
            if (nearest.isEmpty()) {
                source.sendSuccess(Component.literal("spiders_arena_audit spider_found=false targets="
                        + targets.size() + " range=" + format(range)), false);
                return 0;
            }

            GroundSpiderEntity spider = nearest.get();
            targets.sort(Comparator.comparingDouble(target -> target.distanceToSqr(spider)));
            source.sendSuccess(Component.literal(SpiderAiAudit.describeArena(spider, targets)), false);
            return 1;
        } catch (RuntimeException ex) {
            context.getSource().sendFailure(Component.literal("spiders_arena_audit failed=true type="
                    + ex.getClass().getSimpleName()
                    + " message=" + safeMessage(ex.getMessage())));
            return 0;
        }
    }

    private static int auditEnvironment(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();
        MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);

        source.sendSuccess(Component.literal("spiders_environment_audit"
                + " day_time=" + level.getDayTime()
                + " game_time=" + level.getGameTime()
                + " raining=" + level.isRaining()
                + " thundering=" + level.isThundering()
                + " daylight_cycle=" + level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)
                + " weather_cycle=" + level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)
                + " mob_spawning=" + level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                + " difficulty=" + level.getDifficulty().getKey()
                + " player=" + player.getGameProfile().getName()
                + " player_game_mode=" + player.gameMode.getGameModeForPlayer().getName()
                + " night_vision=" + (nightVision != null)
                + " night_vision_duration=" + (nightVision == null ? 0 : nightVision.getDuration())), false);
        return 1;
    }

    private static int auditSurfaces(CommandContext<CommandSourceStack> context, double range) {
        try {
            CommandSourceStack source = context.getSource();
            List<GroundSpiderEntity> spiders = findSurfaceSpiders(source.getLevel(), source.getPosition(), range);
            source.sendSuccess(Component.literal(SpiderAiAudit.describeSurfaces(spiders)), false);
            return spiders.size();
        } catch (RuntimeException ex) {
            context.getSource().sendFailure(Component.literal("spiders_surface_audit failed=true type="
                    + ex.getClass().getSimpleName()
                    + " message=" + safeMessage(ex.getMessage())));
            return 0;
        }
    }

    private static int auditSwarm(CommandContext<CommandSourceStack> context, double range) {
        try {
            CommandSourceStack source = context.getSource();
            ServerLevel level = source.getLevel();
            Vec3 origin = source.getPosition();
            List<GroundSpiderEntity> spiders = findSurfaceSpiders(level, origin, range);
            List<IronGolem> targets = findArenaTargets(level, origin, range);
            source.sendSuccess(Component.literal(SpiderAiAudit.describeSwarm(spiders, targets, origin)), false);
            return spiders.size();
        } catch (RuntimeException ex) {
            context.getSource().sendFailure(Component.literal("spiders_swarm_audit failed=true type="
                    + ex.getClass().getSimpleName()
                    + " message=" + safeMessage(ex.getMessage())));
            return 0;
        }
    }

    private static int auditPlayerPressure(CommandContext<CommandSourceStack> context, double range) {
        try {
            CommandSourceStack source = context.getSource();
            ServerPlayer player = source.getPlayerOrException();
            List<GroundSpiderEntity> spiders = findSurfaceSpiders(source.getLevel(), player.position(), range);
            source.sendSuccess(Component.literal(SpiderAiAudit.describePlayerPressure(spiders, player, player.position())), false);
            return spiders.size();
        } catch (CommandSyntaxException ex) {
            context.getSource().sendFailure(Component.literal("spiders_player_pressure_audit failed=true type=CommandSyntaxException"
                    + " message=" + safeMessage(ex.getMessage())));
            return 0;
        } catch (RuntimeException ex) {
            context.getSource().sendFailure(Component.literal("spiders_player_pressure_audit failed=true type="
                    + ex.getClass().getSimpleName()
                    + " message=" + safeMessage(ex.getMessage())));
            return 0;
        }
    }

    private static int auditPlayerPressureCompact(CommandContext<CommandSourceStack> context, double range) {
        try {
            CommandSourceStack source = context.getSource();
            ServerPlayer player = source.getPlayerOrException();
            List<GroundSpiderEntity> spiders = findSurfaceSpiders(source.getLevel(), player.position(), range);
            source.sendSuccess(Component.literal(SpiderAiAudit.describePlayerPressureCompact(spiders, player, player.position())), false);
            return spiders.size();
        } catch (CommandSyntaxException ex) {
            context.getSource().sendFailure(Component.literal("spiders_player_pressure_audit failed=true type=CommandSyntaxException"
                    + " message=" + safeMessage(ex.getMessage())));
            return 0;
        } catch (RuntimeException ex) {
            context.getSource().sendFailure(Component.literal("spiders_player_pressure_audit failed=true type="
                    + ex.getClass().getSimpleName()
                    + " message=" + safeMessage(ex.getMessage())));
            return 0;
        }
    }

    private static int auditPlayerPressureSummary(CommandContext<CommandSourceStack> context, double range) {
        try {
            CommandSourceStack source = context.getSource();
            ServerPlayer player = source.getPlayerOrException();
            List<GroundSpiderEntity> spiders = findSurfaceSpiders(source.getLevel(), player.position(), range);
            source.sendSuccess(Component.literal(SpiderAiAudit.describePlayerPressureSummary(spiders, player, player.position())), false);
            return spiders.size();
        } catch (CommandSyntaxException ex) {
            context.getSource().sendFailure(Component.literal("spiders_player_pressure_audit failed=true type=CommandSyntaxException"
                    + " message=" + safeMessage(ex.getMessage())));
            return 0;
        } catch (RuntimeException ex) {
            context.getSource().sendFailure(Component.literal("spiders_player_pressure_audit failed=true type="
                    + ex.getClass().getSimpleName()
                    + " message=" + safeMessage(ex.getMessage())));
            return 0;
        }
    }

    private static int auditBotPovCamera(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return auditBotPovCamera(context, false, false);
    }

    private static int auditBotPovCamera(CommandContext<CommandSourceStack> context, boolean syncObserver,
            boolean spectateObserver) throws CommandSyntaxException {
        ServerPlayer bot = EntityArgument.getPlayer(context, "bot");
        ServerPlayer observer = EntityArgument.getPlayer(context, "observer");
        Vec3 botEye = bot.getEyePosition();
        Vec3 expectedObserverEye = botEye.add(bot.getLookAngle().scale(-BOT_POV_CAMERA_BACK_OFFSET));
        if (spectateObserver) {
            observer.setCamera(bot);
        }
        if (syncObserver) {
            Vec3 observerFeet = expectedObserverEye.add(0.0D, -observer.getEyeHeight(), 0.0D);
            observer.connection.teleport(observerFeet.x, observerFeet.y, observerFeet.z, bot.getYRot(), bot.getXRot());
            observer.setYRot(bot.getYRot());
            observer.setXRot(bot.getXRot());
            observer.setYHeadRot(bot.getYRot());
        }
        Vec3 observerEye = observer.getEyePosition();
        Entity camera = observer.getCamera();
        Vec3 cameraEye = camera.getEyePosition();
        Vec3 expectedCameraEye = spectateObserver ? botEye : expectedObserverEye;
        double eyeDistance = Math.sqrt(cameraEye.distanceToSqr(expectedCameraEye));
        double yawDelta = Math.abs(Mth.wrapDegrees(camera.getYRot() - bot.getYRot()));
        double pitchDelta = Math.abs(Mth.wrapDegrees(camera.getXRot() - bot.getXRot()));
        GameType observerGameMode = observer.gameMode.getGameModeForPlayer();

        context.getSource().sendSuccess(Component.literal("spiders_bot_pov_camera_audit"
                + " bot=" + bot.getGameProfile().getName()
                + " observer=" + observer.getGameProfile().getName()
                + " camera=" + camera.getName().getString()
                + " camera_is_bot=" + (camera == bot)
                + " bot_pos=" + vector(bot.position())
                + " bot_eye=" + vector(botEye)
                + " observer_pos=" + vector(observer.position())
                + " observer_eye=" + vector(observerEye)
                + " expected_observer_eye=" + vector(expectedObserverEye)
                + " camera_pos=" + vector(camera.position())
                + " camera_eye=" + vector(cameraEye)
                + " expected_camera_eye=" + vector(expectedCameraEye)
                + " eye_distance=" + format(eyeDistance)
                + " yaw_delta=" + format(yawDelta)
                + " pitch_delta=" + format(pitchDelta)
                + " bot_yaw=" + format(bot.getYRot())
                + " bot_pitch=" + format(bot.getXRot())
                + " observer_yaw=" + format(observer.getYRot())
                + " observer_pitch=" + format(observer.getXRot())
                + " camera_yaw=" + format(camera.getYRot())
                + " camera_pitch=" + format(camera.getXRot())
                + " observer_game_mode=" + observerGameMode.getName()
                + " observer_spectator=" + (observerGameMode == GameType.SPECTATOR)
                + " synced=" + syncObserver
                + " spectating=" + spectateObserver
                + " back_offset=" + format(BOT_POV_CAMERA_BACK_OFFSET)), false);
        return 1;
    }

    private static int clearBotPovCamera(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer observer = EntityArgument.getPlayer(context, "observer");
        observer.setCamera(null);
        context.getSource().sendSuccess(Component.literal("spiders_bot_pov_camera_clear"
                + " observer=" + observer.getGameProfile().getName()
                + " camera=" + observer.getCamera().getName().getString()
                + " camera_is_observer=" + (observer.getCamera() == observer)), false);
        return 1;
    }

    private static int forcePathNearest(CommandContext<CommandSourceStack> context, double speed) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Optional<GroundSpiderEntity> nearest = SpiderAiAudit.findNearest(level, source.getPosition(), DEFAULT_RANGE);
        if (nearest.isEmpty()) {
            source.sendSuccess(Component.literal("spiders_force_path started=false reason=no_spider range="
                    + format(DEFAULT_RANGE)), false);
            return 0;
        }

        GroundSpiderEntity spider = nearest.get();
        BlockPos target = BlockPosArgument.getLoadedBlockPos(context, "target");
        spider.startForcedPath(List.of(spider.blockPosition(), target), speed);

        source.sendSuccess(Component.literal("spiders_force_path started=true"
                + " uuid=" + spider.getUUID()
                + " target=" + target.getX() + "," + target.getY() + "," + target.getZ()
                + " speed=" + format(speed)
                + " path_size=" + spider.getForcedPathSize()), false);
        return 1;
    }

    private static int startMixedSurfaceCombatNearest(CommandContext<CommandSourceStack> context, double range) {
        try {
            CommandSourceStack source = context.getSource();
            ServerLevel level = source.getLevel();
            Optional<GroundSpiderEntity> nearest = SpiderAiAudit.findNearest(level, source.getPosition(), range);
            if (nearest.isEmpty()) {
                source.sendFailure(Component.literal("spiders_mixed_surface_combat_start_nearest started=false reason=no_spider range="
                        + format(range)));
                return 0;
            }

            GroundSpiderEntity spider = nearest.get();
            List<IronGolem> targets = findArenaTargets(level, source.getPosition(), range);
            targets.sort(Comparator.comparingDouble(target -> target.distanceToSqr(spider)));
            if (targets.isEmpty()) {
                source.sendFailure(Component.literal("spiders_mixed_surface_combat_start_nearest started=false reason=no_targets"
                        + " spider=" + spider.getUUID()));
                return 0;
            }

            IronGolem target = targets.get(0);
            spider.setNoAi(false);
            spider.noPhysics = false;
            spider.setInvulnerable(false);
            spider.setNoGravity(true);
            spider.setTarget(target);
            spider.setLastHurtByMob(target);
            boolean pathStarted = spider.getNavigation().moveTo(target, 1.0D);

            source.sendSuccess(Component.literal("spiders_mixed_surface_combat_start_nearest started=true"
                    + " spider=" + spider.getUUID()
                    + " attachment=" + spider.getAttachmentDirection().getName()
                    + " target=" + target.getUUID()
                    + " target_pos=" + format(target.getX()) + "," + format(target.getY()) + "," + format(target.getZ())
                    + " targets=" + targets.size()
                    + " path_started=" + pathStarted), false);
            return 1;
        } catch (RuntimeException ex) {
            context.getSource().sendFailure(Component.literal("spiders_mixed_surface_combat_start_nearest started=false type="
                    + ex.getClass().getSimpleName()
                    + " message=" + safeMessage(ex.getMessage())));
            return 0;
        }
    }

    private static int wallVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        BlockPos airPos = new BlockPos(playerPos.getX() + 4, playerPos.getY(), playerPos.getZ());
        Direction attachment = Direction.EAST;
        BlockPos supportPos = airPos.relative(attachment);

        level.getEntitiesOfClass(GroundSpiderEntity.class, player.getBoundingBox().inflate(64.0D))
                .forEach(GroundSpiderEntity::discard);

        for (int x = playerPos.getX() + 1; x <= playerPos.getX() + 7; x++) {
            for (int y = playerPos.getY() - 3; y <= playerPos.getY() + 4; y++) {
                for (int z = playerPos.getZ() - 4; z <= playerPos.getZ() + 4; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = playerPos.getX() + 1; x <= playerPos.getX() + 7; x++) {
            for (int z = playerPos.getZ() - 4; z <= playerPos.getZ() + 4; z++) {
                level.setBlock(new BlockPos(x, playerPos.getY() - 3, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = playerPos.getY() - 3; y <= playerPos.getY() + 4; y++) {
            for (int z = playerPos.getZ() - 4; z <= playerPos.getZ() + 4; z++) {
                level.setBlock(new BlockPos(supportPos.getX(), y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        GroundSpiderEntity spider = EntityRegistry.GROUND_SPIDER.get().create(level);
        if (spider == null) {
            source.sendFailure(Component.literal("spiders_wall_visual_test created=false reason=entity_create_failed"));
            return 0;
        }

        Vec3 anchor = AttachmentHelper.anchorFor(spider, airPos, attachment);
        spider.setAttachmentDirection(attachment);
        spider.setNoGravity(true);
        spider.setNoAi(true);
        spider.setInvulnerable(true);
        spider.noPhysics = true;
        spider.setPersistenceRequired();
        spider.moveTo(anchor.x, anchor.y, anchor.z, -90.0F, 0.0F);
        level.addFreshEntity(spider);
        spider.moveTo(anchor.x, anchor.y, anchor.z, -90.0F, 0.0F);
        spider.setAttachmentDirection(attachment);
        spider.setNoGravity(true);
        spider.setDeltaMovement(Vec3.ZERO);
        spider.noPhysics = true;

        player.teleportTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);
        player.setYRot(-90.0F);
        player.setXRot(0.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        source.sendSuccess(Component.literal("spiders_wall_visual_test created=true"
                + " uuid=" + spider.getUUID()
                + " attachment=" + attachment.getName()
                + " air=" + airPos.getX() + "," + airPos.getY() + "," + airPos.getZ()
                + " pos=" + format(anchor.x) + "," + format(anchor.y) + "," + format(anchor.z)), false);
        return 1;
    }

    private static int wallCombatVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 3;
        int minX = playerPos.getX() + 1;
        int wallX = playerPos.getX() + 8;
        int airX = wallX - 1;
        int minZ = playerPos.getZ() - 5;
        int maxZ = playerPos.getZ() + 5;

        level.getEntitiesOfClass(GroundSpiderEntity.class, player.getBoundingBox().inflate(64.0D))
                .forEach(GroundSpiderEntity::discard);
        level.getEntitiesOfClass(IronGolem.class, player.getBoundingBox().inflate(64.0D))
                .forEach(IronGolem::discard);

        for (int x = minX; x <= wallX + 1; x++) {
            for (int y = floorY; y <= floorY + 6; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= wallX + 1; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= floorY + 5; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(wallX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        Direction attachment = Direction.EAST;
        BlockPos spiderAir = new BlockPos(airX, playerPos.getY() - 1, playerPos.getZ() - 2);
        BlockPos targetAir = new BlockPos(airX, playerPos.getY() + 1, playerPos.getZ() + 2);

        IronGolem target = EntityType.IRON_GOLEM.create(level);
        if (target == null) {
            source.sendFailure(Component.literal("spiders_wall_combat_visual_test created=false reason=target_create_failed"));
            return 0;
        }
        Vec3 targetAnchor = new Vec3(targetAir.getX() + 0.5D, targetAir.getY(), targetAir.getZ() + 0.5D);
        target.setNoAi(true);
        target.setNoGravity(true);
        target.noPhysics = true;
        target.setPersistenceRequired();
        target.moveTo(targetAnchor.x, targetAnchor.y, targetAnchor.z, -90.0F, 0.0F);
        target.setHealth(24.0F);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        level.addFreshEntity(target);
        target.setNoGravity(true);
        target.noPhysics = true;

        GroundSpiderEntity spider = EntityRegistry.GROUND_SPIDER.get().create(level);
        if (spider == null) {
            target.discard();
            source.sendFailure(Component.literal("spiders_wall_combat_visual_test created=false reason=spider_create_failed"));
            return 0;
        }

        Vec3 spiderAnchor = AttachmentHelper.anchorFor(spider, spiderAir, attachment);
        spider.setAttachmentDirection(attachment);
        spider.setNoGravity(true);
        spider.setPersistenceRequired();
        if (spider.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            spider.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(16.0D);
        }
        spider.moveTo(spiderAnchor.x, spiderAnchor.y, spiderAnchor.z, -90.0F, 0.0F);
        level.addFreshEntity(spider);
        spider.moveTo(spiderAnchor.x, spiderAnchor.y, spiderAnchor.z, -90.0F, 0.0F);
        spider.setAttachmentDirection(attachment);
        spider.setNoGravity(true);
        spider.setTarget(target);
        spider.setLastHurtByMob(target);
        boolean pathStarted = spider.getNavigation().moveTo(target, 1.0D);

        player.teleportTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);
        player.setYRot(-90.0F);
        player.setXRot(0.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        source.sendSuccess(Component.literal("spiders_wall_combat_visual_test created=true"
                + " spider=" + spider.getUUID()
                + " target=" + target.getUUID()
                + " attachment=" + attachment.getName()
                + " spider_start=" + format(spiderAnchor.x) + "," + format(spiderAnchor.y) + "," + format(spiderAnchor.z)
                + " target_pos=" + format(targetAnchor.x) + "," + format(targetAnchor.y) + "," + format(targetAnchor.z)
                + " path_started=" + pathStarted), false);
        return 1;
    }

    private static int wallMultiCombatVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 3;
        int minX = playerPos.getX() + 1;
        int wallX = playerPos.getX() + 9;
        int airX = wallX - 1;
        int minZ = playerPos.getZ() - 7;
        int maxZ = playerPos.getZ() + 7;
        int maxY = floorY + 8;

        clearVisualTestEntities(level, player);

        for (int x = minX; x <= wallX + 1; x++) {
            for (int y = floorY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= wallX + 1; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, maxY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(wallX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        Direction attachment = Direction.EAST;
        BlockPos spiderAir = new BlockPos(airX, playerPos.getY() - 1, playerPos.getZ() - 5);
        BlockPos[] targetAirs = {
                new BlockPos(airX, playerPos.getY() - 1, playerPos.getZ() - 2),
                new BlockPos(airX, playerPos.getY() + 1, playerPos.getZ() + 2),
                new BlockPos(airX, playerPos.getY() + 2, playerPos.getZ() + 5)
        };
        float[] targetHealth = {9.0F, 15.0F, 18.0F};
        List<IronGolem> targets = new ArrayList<>();
        for (int i = 0; i < targetAirs.length; i++) {
            IronGolem target = EntityType.IRON_GOLEM.create(level);
            if (target == null) {
                targets.forEach(IronGolem::discard);
                source.sendFailure(Component.literal("spiders_wall_multi_combat_visual_test created=false reason=target_create_failed index=" + i));
                return 0;
            }

            Vec3 targetAnchor = new Vec3(targetAirs[i].getX() + 0.5D, targetAirs[i].getY(), targetAirs[i].getZ() + 0.5D);
            target.setNoAi(true);
            target.setNoGravity(true);
            target.noPhysics = true;
            target.setPersistenceRequired();
            target.moveTo(targetAnchor.x, targetAnchor.y, targetAnchor.z, -90.0F, 0.0F);
            target.setHealth(targetHealth[i]);
            if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
                target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            }
            level.addFreshEntity(target);
            target.setNoGravity(true);
            target.noPhysics = true;
            targets.add(target);
        }

        GroundSpiderEntity spider = EntityRegistry.GROUND_SPIDER.get().create(level);
        if (spider == null) {
            targets.forEach(IronGolem::discard);
            source.sendFailure(Component.literal("spiders_wall_multi_combat_visual_test created=false reason=spider_create_failed"));
            return 0;
        }

        Vec3 spiderAnchor = AttachmentHelper.anchorFor(spider, spiderAir, attachment);
        spider.setAttachmentDirection(attachment);
        spider.setNoGravity(true);
        spider.setPersistenceRequired();
        if (spider.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            spider.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(28.0D);
        }
        spider.moveTo(spiderAnchor.x, spiderAnchor.y, spiderAnchor.z, -90.0F, 0.0F);
        level.addFreshEntity(spider);
        spider.moveTo(spiderAnchor.x, spiderAnchor.y, spiderAnchor.z, -90.0F, 0.0F);
        spider.setAttachmentDirection(attachment);
        spider.setNoGravity(true);
        IronGolem firstTarget = targets.get(0);
        spider.setTarget(firstTarget);
        spider.setLastHurtByMob(firstTarget);
        boolean pathStarted = spider.getNavigation().moveTo(firstTarget, 1.0D);

        player.teleportTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);
        player.setYRot(-90.0F);
        player.setXRot(0.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        source.sendSuccess(Component.literal("spiders_wall_multi_combat_visual_test created=true"
                + " spider=" + spider.getUUID()
                + " targets=" + targets.size()
                + " attachment=" + attachment.getName()
                + " spider_start=" + format(spiderAnchor.x) + "," + format(spiderAnchor.y) + "," + format(spiderAnchor.z)
                + " target0_health=" + format(targetHealth[0])
                + " target1_health=" + format(targetHealth[1])
                + " target2_health=" + format(targetHealth[2])
                + " path_started=" + pathStarted), false);
        return 1;
    }

    private static int mixedSurfaceCombatVisualTest(CommandContext<CommandSourceStack> context, boolean startCombat) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();
        String commandName = startCombat
                ? "spiders_mixed_surface_combat_visual_test"
                : "spiders_mixed_surface_combat_stage_visual_test";

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 3;
        int ceilingY = floorY + 8;
        int westWallX = playerPos.getX() + 5;
        int eastWallX = playerPos.getX() + 13;
        int minX = playerPos.getX() - 1;
        int maxX = eastWallX + 2;
        int minZ = playerPos.getZ() - 6;
        int maxZ = playerPos.getZ() + 6;

        clearVisualTestEntities(level, player);

        for (int x = minX; x <= maxX; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        for (int x = playerPos.getX() - 1; x <= westWallX - 1; x++) {
            for (int z = playerPos.getZ() - 3; z <= playerPos.getZ() + 3; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = playerPos.getZ() - 3; z <= playerPos.getZ() + 3; z++) {
                level.setBlock(new BlockPos(westWallX, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(eastWallX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int x = westWallX; x <= eastWallX; x++) {
            for (int z = playerPos.getZ() - 3; z <= playerPos.getZ() + 3; z++) {
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        BlockPos[] lights = {
                new BlockPos(westWallX + 1, ceilingY, playerPos.getZ() - 2),
                new BlockPos((westWallX + eastWallX) / 2, ceilingY, playerPos.getZ()),
                new BlockPos(eastWallX - 1, ceilingY, playerPos.getZ() + 2),
                new BlockPos(playerPos.getX() + 1, floorY, playerPos.getZ())
        };
        for (BlockPos light : lights) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        Direction startAttachment = Direction.WEST;
        Direction targetAttachment = Direction.EAST;
        BlockPos spiderAir = new BlockPos(westWallX + 1, floorY + 5, playerPos.getZ());
        BlockPos[] targetAirs = {
                new BlockPos(eastWallX - 1, floorY + 2, playerPos.getZ() - 2),
                new BlockPos(eastWallX - 1, floorY + 3, playerPos.getZ() + 1),
                new BlockPos(eastWallX - 1, floorY + 5, playerPos.getZ() + 3)
        };
        float[] targetHealth = {18.0F, 24.0F, 30.0F};
        List<IronGolem> targets = new ArrayList<>();
        for (int i = 0; i < targetAirs.length; i++) {
            IronGolem target = EntityType.IRON_GOLEM.create(level);
            if (target == null) {
                targets.forEach(IronGolem::discard);
                source.sendFailure(Component.literal(commandName + " created=false reason=target_create_failed index=" + i));
                return 0;
            }

            Vec3 targetAnchor = new Vec3(targetAirs[i].getX() + 0.5D, targetAirs[i].getY(), targetAirs[i].getZ() + 0.5D);
            target.setNoAi(true);
            target.setNoGravity(true);
            target.noPhysics = true;
            target.setPersistenceRequired();
            target.moveTo(targetAnchor.x, targetAnchor.y, targetAnchor.z, -90.0F, 0.0F);
            target.setHealth(targetHealth[i]);
            if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
                target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
            }
            level.addFreshEntity(target);
            target.setNoGravity(true);
            target.noPhysics = true;
            targets.add(target);
        }

        GroundSpiderEntity spider = EntityRegistry.GROUND_SPIDER.get().create(level);
        if (spider == null) {
            targets.forEach(IronGolem::discard);
            source.sendFailure(Component.literal(commandName + " created=false reason=spider_create_failed"));
            return 0;
        }

        Vec3 spiderAnchor = AttachmentHelper.anchorFor(spider, spiderAir, startAttachment);
        spider.setAttachmentDirection(startAttachment);
        spider.setNoGravity(true);
        spider.setNoAi(!startCombat);
        spider.setPersistenceRequired();
        if (spider.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            spider.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(32.0D);
        }
        if (spider.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.12D);
        }
        spider.moveTo(spiderAnchor.x, spiderAnchor.y, spiderAnchor.z, 90.0F, 0.0F);
        level.addFreshEntity(spider);
        spider.moveTo(spiderAnchor.x, spiderAnchor.y, spiderAnchor.z, 90.0F, 0.0F);
        spider.setAttachmentDirection(startAttachment);
        spider.setNoGravity(true);
        spider.setNoAi(!startCombat);
        spider.setDeltaMovement(Vec3.ZERO);
        boolean pathStarted = false;
        if (startCombat) {
            IronGolem firstTarget = targets.get(0);
            spider.setTarget(firstTarget);
            spider.setLastHurtByMob(firstTarget);
            pathStarted = spider.getNavigation().moveTo(firstTarget, 1.0D);
        }

        double observerX = ((double) westWallX + (double) eastWallX) / 2.0D + 0.5D;
        double observerZ = playerPos.getZ() + 7.5D;
        for (int x = westWallX - 1; x <= eastWallX + 1; x++) {
            for (int z = playerPos.getZ() + 4; z <= playerPos.getZ() + 8; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        player.teleportTo(observerX, floorY + 1.0D, observerZ);
        player.setYRot(165.0F);
        player.setXRot(-18.0F);
        player.yHeadRot = 165.0F;
        player.yBodyRot = 165.0F;

        source.sendSuccess(Component.literal(commandName + " created=true"
                + " spider=" + spider.getUUID()
                + " targets=" + targets.size()
                + " start_attachment=" + startAttachment.getName()
                + " target_attachment=" + targetAttachment.getName()
                + " staged=" + !startCombat
                + " spider_start=" + format(spiderAnchor.x) + "," + format(spiderAnchor.y) + "," + format(spiderAnchor.z)
                + " observer=" + format(observerX) + "," + format(floorY + 1.0D) + "," + format(observerZ)
                + " west_wall_x=" + westWallX
                + " east_wall_x=" + eastWallX
                + " ceiling_y=" + ceilingY
                + " target0_health=" + format(targetHealth[0])
                + " target1_health=" + format(targetHealth[1])
                + " target2_health=" + format(targetHealth[2])
                + " path_started=" + pathStarted), false);
        return 1;
    }

    private static int mixedSurfaceSwarmVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 3;
        int ceilingY = floorY + 9;
        int westWallX = playerPos.getX() + 5;
        int eastWallX = playerPos.getX() + 16;
        int northWallZ = playerPos.getZ() - 6;
        int southWallZ = playerPos.getZ() + 6;
        int minX = playerPos.getX() - 1;
        int maxX = eastWallX + 2;

        clearVisualTestEntities(level, player);

        for (int x = minX; x <= maxX; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = northWallZ - 1; z <= southWallZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        for (int x = playerPos.getX() - 1; x <= westWallX - 1; x++) {
            for (int z = playerPos.getZ() - 4; z <= playerPos.getZ() + 4; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = playerPos.getZ() - 4; z <= playerPos.getZ() + 4; z++) {
                level.setBlock(new BlockPos(westWallX, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(eastWallX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int x = westWallX; x <= eastWallX; x++) {
            for (int z = playerPos.getZ() - 4; z <= playerPos.getZ() + 4; z++) {
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        for (int x = westWallX + 3; x <= westWallX + 5; x++) {
            level.setBlock(new BlockPos(x, ceilingY, playerPos.getZ()), Blocks.AIR.defaultBlockState(), 3);
        }

        BlockPos[] lights = {
                new BlockPos(westWallX + 1, ceilingY, playerPos.getZ() - 3),
                new BlockPos((westWallX + eastWallX) / 2, ceilingY, playerPos.getZ() + 3),
                new BlockPos(eastWallX - 1, ceilingY, playerPos.getZ()),
                new BlockPos(playerPos.getX() + 1, floorY, playerPos.getZ())
        };
        for (BlockPos light : lights) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        BlockPos[] targetAirs = {
                new BlockPos(eastWallX - 1, floorY + 2, playerPos.getZ() - 4),
                new BlockPos(eastWallX - 1, floorY + 5, playerPos.getZ()),
                new BlockPos(eastWallX - 1, floorY + 7, playerPos.getZ() + 4),
                new BlockPos(westWallX + 8, ceilingY - 1, playerPos.getZ() + 3),
                new BlockPos(westWallX + 10, ceilingY - 1, playerPos.getZ() - 3)
        };
        Direction[] targetAttachments = {
                Direction.EAST,
                Direction.EAST,
                Direction.EAST,
                Direction.UP,
                Direction.UP
        };
        float[] targetHealth = {36.0F, 42.0F, 42.0F, 36.0F, 36.0F};
        List<IronGolem> targets = new ArrayList<>();
        for (int i = 0; i < targetAirs.length; i++) {
            IronGolem target = spawnPinnedTarget(level, targetAirs[i], targetAttachments[i], targetHealth[i]);
            if (target == null) {
                targets.forEach(IronGolem::discard);
                source.sendFailure(Component.literal("spiders_mixed_surface_swarm_visual_test created=false reason=target_create_failed index=" + i));
                return 0;
            }
            targets.add(target);
        }

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean[] pathStarted = new boolean[4];
        pathStarted[0] = spawnCombatSpider(level, spiders, new BlockPos(westWallX + 1, floorY + 5, playerPos.getZ() - 3),
                Direction.WEST, 90.0F, targets.get(0), 34.0D, 0.11D);
        pathStarted[1] = spawnCombatSpider(level, spiders, new BlockPos(westWallX + 2, ceilingY - 1, playerPos.getZ() + 1),
                Direction.UP, 0.0F, targets.get(1), 34.0D, 0.11D);
        pathStarted[2] = spawnCombatSpider(level, spiders, new BlockPos(westWallX + 1, floorY + 3, playerPos.getZ() + 4),
                Direction.WEST, 90.0F, targets.get(2), 34.0D, 0.11D);
        pathStarted[3] = spawnCombatSpider(level, spiders, new BlockPos(westWallX + 4, ceilingY - 1, playerPos.getZ() - 4),
                Direction.UP, 0.0F, targets.get(4), 34.0D, 0.11D);

        if (spiders.size() != pathStarted.length) {
            spiders.forEach(GroundSpiderEntity::discard);
            targets.forEach(IronGolem::discard);
            source.sendFailure(Component.literal("spiders_mixed_surface_swarm_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        double observerX = ((double) westWallX + (double) eastWallX) / 2.0D + 0.5D;
        double observerZ = playerPos.getZ() + 9.0D;
        for (int x = westWallX - 1; x <= eastWallX + 1; x++) {
            for (int z = playerPos.getZ() + 5; z <= playerPos.getZ() + 9; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        player.teleportTo(observerX, floorY + 1.0D, observerZ);
        player.setYRot(-150.0F);
        player.setXRot(-22.0F);
        player.yHeadRot = -150.0F;
        player.yBodyRot = -150.0F;

        source.sendSuccess(Component.literal("spiders_mixed_surface_swarm_visual_test created=true"
                + " spiders=" + spiders.size()
                + " targets=" + targets.size()
                + " west_wall_x=" + westWallX
                + " east_wall_x=" + eastWallX
                + " ceiling_y=" + ceilingY
                + " observer=" + format(observerX) + "," + format(floorY + 1.0D) + "," + format(observerZ)
                + " path0_started=" + pathStarted[0]
                + " path1_started=" + pathStarted[1]
                + " path2_started=" + pathStarted[2]
                + " path3_started=" + pathStarted[3]
                + " all_paths_started=" + (pathStarted[0] && pathStarted[1] && pathStarted[2] && pathStarted[3])), false);
        return 1;
    }

    private static int playerPressureVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 9;
        int wallX = playerPos.getX() + 9;
        int minX = playerPos.getX() - 3;
        int maxX = wallX + 2;
        int minZ = playerPos.getZ() - 5;
        int maxZ = playerPos.getZ() + 5;
        int clearMinX = minX - 8;
        int clearMaxX = maxX + 8;
        int clearMinZ = minZ - 8;
        int clearMaxZ = maxZ + 8;
        int clearMaxY = ceilingY + 5;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = clearMinX; x <= clearMaxX; x++) {
            for (int y = floorY; y <= clearMaxY; y++) {
                for (int z = clearMinZ; z <= clearMaxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = clearMinX; x <= clearMaxX; x++) {
            for (int z = clearMinZ; z <= clearMaxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.GLASS.defaultBlockState(), 3);
            }
        }
        for (int y = floorY + 1; y <= ceilingY; y++) {
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), Blocks.GLASS.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, maxZ), Blocks.GLASS.defaultBlockState(), 3);
            }
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(minX, y, z), Blocks.GLASS.defaultBlockState(), 3);
                level.setBlock(new BlockPos(maxX, y, z), Blocks.GLASS.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(wallX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        BlockPos[] lights = {
                new BlockPos(wallX - 1, ceilingY, playerPos.getZ() - 3),
                new BlockPos(wallX - 4, ceilingY, playerPos.getZ() + 2),
                new BlockPos(playerPos.getX() + 1, floorY, playerPos.getZ()),
                new BlockPos(wallX, floorY + 3, playerPos.getZ() + 4)
        };
        for (BlockPos light : lights) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(false);
        player.teleportTo(playerPos.getX() + 0.5D, floorY + 1.0D, playerPos.getZ() + 0.5D);
        player.setYRot(-90.0F);
        player.setXRot(-8.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean[] pathStarted = new boolean[5];
        pathStarted[0] = spawnCombatSpider(level, spiders, new BlockPos(wallX - 1, floorY + 2, playerPos.getZ() - 3),
                Direction.EAST, -90.0F, player, 28.0D, 0.14D, CONTROLLED_PLAYER_PRESSURE_ATTACK_DAMAGE);
        pathStarted[1] = spawnCombatSpider(level, spiders, new BlockPos(wallX - 4, ceilingY - 1, playerPos.getZ() + 1),
                Direction.UP, 0.0F, player, 28.0D, 0.14D, CONTROLLED_PLAYER_PRESSURE_ATTACK_DAMAGE);
        pathStarted[2] = spawnCombatSpider(level, spiders, new BlockPos(wallX - 1, floorY + 4, playerPos.getZ() + 3),
                Direction.EAST, -90.0F, player, 28.0D, 0.14D, CONTROLLED_PLAYER_PRESSURE_ATTACK_DAMAGE);
        pathStarted[3] = spawnStationaryWallPressureSpider(level, spiders,
                new BlockPos(playerPos.getX() - 1, floorY + 2, minZ + 1),
                Direction.NORTH, player, 20.0D);
        pathStarted[4] = spawnStationaryWallPressureSpider(level, spiders,
                new BlockPos(minX + 1, floorY + 2, playerPos.getZ() - 1),
                Direction.WEST, player, 20.0D);

        if (spiders.size() != pathStarted.length) {
            spiders.forEach(GroundSpiderEntity::discard);
            source.sendFailure(Component.literal("spiders_player_pressure_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        source.sendSuccess(Component.literal("spiders_player_pressure_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " wall_x=" + wallX
                + " ceiling_y=" + ceilingY
                + " room=minX:" + minX + ",maxX:" + maxX + ",minZ:" + minZ + ",maxZ:" + maxZ
                + " day_time=" + level.getDayTime()
                + " raining=" + level.isRaining()
                + " thundering=" + level.isThundering()
                + " daylight_cycle=" + level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)
                + " weather_cycle=" + level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)
                + " mob_spawning=" + level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                + " night_vision=true"
                + " path0_started=" + pathStarted[0]
                + " path1_started=" + pathStarted[1]
                + " path2_started=" + pathStarted[2]
                + " path3_wall_sentry_started=" + pathStarted[3]
                + " path4_wall_sentry_started=" + pathStarted[4]
                + " all_paths_started=" + (pathStarted[0] && pathStarted[1] && pathStarted[2]
                    && pathStarted[3] && pathStarted[4])), false);
        return 1;
    }

    private static int playerPressureStressVisualTest(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 6;
        int minX = playerPos.getX() - 18;
        int maxX = playerPos.getX() + 18;
        int minZ = playerPos.getZ() - 18;
        int maxZ = playerPos.getZ() + 18;
        int eastWallX = playerPos.getX() + 14;
        int westWallX = playerPos.getX() - 14;
        int northWallZ = playerPos.getZ() - 14;
        int southWallZ = playerPos.getZ() + 14;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX; x <= maxX; x++) {
            for (int y = floorY + 1; y <= ceilingY + 1; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY + 1; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(westWallX, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(eastWallX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            for (int x = westWallX; x <= eastWallX; x++) {
                level.setBlock(new BlockPos(x, y, northWallZ), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, southWallZ), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        int[][] pillars = {
                {playerPos.getX() - 9, playerPos.getZ() - 9},
                {playerPos.getX() + 9, playerPos.getZ() - 9},
                {playerPos.getX() - 9, playerPos.getZ() + 9},
                {playerPos.getX() + 9, playerPos.getZ() + 9},
                {playerPos.getX() - 4, playerPos.getZ() + 11},
                {playerPos.getX() + 5, playerPos.getZ() - 11}
        };
        for (int[] pillar : pillars) {
            for (int y = floorY + 1; y <= floorY + 4; y++) {
                level.setBlock(new BlockPos(pillar[0], y, pillar[1]), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
        }

        BlockPos[] lights = {
                new BlockPos(playerPos.getX(), ceilingY, playerPos.getZ()),
                new BlockPos(playerPos.getX() - 8, ceilingY, playerPos.getZ() - 8),
                new BlockPos(playerPos.getX() + 8, ceilingY, playerPos.getZ() + 8),
                new BlockPos(eastWallX, floorY + 3, playerPos.getZ() - 6),
                new BlockPos(westWallX, floorY + 3, playerPos.getZ() + 6),
                new BlockPos(playerPos.getX(), floorY + 1, northWallZ + 1)
        };
        for (BlockPos light : lights) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, STRESS_PLAYER_PRESSURE_RESISTANCE_AMPLIFIER, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, STRESS_PLAYER_PRESSURE_REGENERATION_AMPLIFIER, false, false, true));
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(false);
        player.teleportTo(playerPos.getX() + 0.5D, floorY + 1.0D, playerPos.getZ() + 0.5D);
        player.setYRot(-90.0F);
        player.setXRot(-8.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        BlockPos[] spiderPositions = new BlockPos[] {
                new BlockPos(playerPos.getX() - 7, floorY + 1, playerPos.getZ()),
                new BlockPos(playerPos.getX() + 7, floorY + 1, playerPos.getZ()),
                new BlockPos(playerPos.getX(), floorY + 1, playerPos.getZ() - 7),
                new BlockPos(playerPos.getX(), floorY + 1, playerPos.getZ() + 7),
                new BlockPos(playerPos.getX() - 5, floorY + 1, playerPos.getZ() - 5),
                new BlockPos(playerPos.getX() + 5, floorY + 1, playerPos.getZ() - 5),
                new BlockPos(playerPos.getX() - 5, floorY + 1, playerPos.getZ() + 5),
                new BlockPos(playerPos.getX() + 5, floorY + 1, playerPos.getZ() + 5),
                new BlockPos(eastWallX - 1, floorY + 2, playerPos.getZ() - 6),
                new BlockPos(eastWallX - 1, floorY + 4, playerPos.getZ() + 2),
                new BlockPos(eastWallX - 1, floorY + 3, playerPos.getZ() + 7),
                new BlockPos(westWallX + 1, floorY + 2, playerPos.getZ() + 6),
                new BlockPos(westWallX + 1, floorY + 4, playerPos.getZ() - 2),
                new BlockPos(westWallX + 1, floorY + 3, playerPos.getZ() - 7),
                new BlockPos(playerPos.getX() - 6, floorY + 2, northWallZ + 1),
                new BlockPos(playerPos.getX() + 6, floorY + 3, southWallZ - 1),
                new BlockPos(playerPos.getX() - 8, ceilingY - 1, playerPos.getZ() - 2),
                new BlockPos(playerPos.getX() - 4, ceilingY - 1, playerPos.getZ() + 6),
                new BlockPos(playerPos.getX(), ceilingY - 1, playerPos.getZ() - 8),
                new BlockPos(playerPos.getX() + 4, ceilingY - 1, playerPos.getZ() - 6),
                new BlockPos(playerPos.getX() + 8, ceilingY - 1, playerPos.getZ() + 2),
                new BlockPos(playerPos.getX() + 6, ceilingY - 1, playerPos.getZ() - 5),
                new BlockPos(playerPos.getX() - 6, ceilingY - 1, playerPos.getZ() + 5),
                new BlockPos(playerPos.getX() + 2, ceilingY - 1, playerPos.getZ() + 8)
        };
        Direction[] attachments = new Direction[] {
                Direction.DOWN, Direction.DOWN, Direction.DOWN, Direction.DOWN,
                Direction.DOWN, Direction.DOWN, Direction.DOWN, Direction.DOWN,
                Direction.EAST, Direction.EAST, Direction.EAST,
                Direction.WEST, Direction.WEST, Direction.WEST,
                Direction.NORTH, Direction.SOUTH,
                Direction.UP, Direction.UP, Direction.UP, Direction.UP,
                Direction.UP, Direction.UP, Direction.UP, Direction.UP
        };

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        int pathStartedCount = 0;
        int floorSpiders = 0;
        int wallSpiders = 0;
        int ceilingSpiders = 0;
        for (int i = 0; i < spiderPositions.length; i++) {
            Direction attachment = attachments[i];
            boolean started = spawnCombatSpiderIfPathStarts(level, spiders, spiderPositions[i], attachment,
                    yawFor(attachment), player, 56.0D, attachment == Direction.DOWN ? 0.18D : 0.16D,
                    STRESS_PLAYER_PRESSURE_ATTACK_DAMAGE);
            if (started) {
                pathStartedCount++;
                if (attachment == Direction.DOWN) {
                    floorSpiders++;
                } else if (attachment == Direction.UP) {
                    ceilingSpiders++;
                } else {
                    wallSpiders++;
                }
            }
        }

        if (spiders.size() != spiderPositions.length || pathStartedCount < 20) {
            spiders.forEach(GroundSpiderEntity::discard);
            source.sendFailure(Component.literal("spiders_player_pressure_stress_visual_test created=false"
                    + " reason=insufficient_pathing_spawns"
                    + " spiders=" + spiders.size()
                    + " expected=" + spiderPositions.length
                    + " path_started_count=" + pathStartedCount
                    + " floor_spiders=" + floorSpiders
                    + " wall_spiders=" + wallSpiders
                    + " ceiling_spiders=" + ceilingSpiders));
            return 0;
        }

        source.sendSuccess(Component.literal("spiders_player_pressure_stress_visual_test created=true"
                + " spiders=" + spiders.size()
                + " path_started_count=" + pathStartedCount
                + " floor_spiders=" + floorSpiders
                + " wall_spiders=" + wallSpiders
                + " ceiling_spiders=" + ceilingSpiders
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " west_wall_x=" + westWallX
                + " east_wall_x=" + eastWallX
                + " north_wall_z=" + northWallZ
                + " south_wall_z=" + southWallZ
                + " ceiling_y=" + ceilingY
                + " stress_attack_damage=" + format(STRESS_PLAYER_PRESSURE_ATTACK_DAMAGE)
                + " resistance_amplifier=" + STRESS_PLAYER_PRESSURE_RESISTANCE_AMPLIFIER
                + " regeneration_amplifier=" + STRESS_PLAYER_PRESSURE_REGENERATION_AMPLIFIER
                + " day_time=" + level.getDayTime()
                + " raining=" + level.isRaining()
                + " thundering=" + level.isThundering()
                + " daylight_cycle=" + level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)
                + " weather_cycle=" + level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)
                + " mob_spawning=" + level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                + " night_vision=true"
                + " all_paths_started=" + (pathStartedCount == spiderPositions.length)), false);
        return 1;
    }

    private static int dropAttackVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 5;
        int minX = playerPos.getX() - 5;
        int maxX = playerPos.getX() + 6;
        int minZ = playerPos.getZ() - 5;
        int maxZ = playerPos.getZ() + 5;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(minX, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(maxX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, maxZ), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        BlockPos[] lights = {
                new BlockPos(playerPos.getX() - 3, ceilingY, playerPos.getZ() - 3),
                new BlockPos(playerPos.getX() + 3, ceilingY, playerPos.getZ() + 3),
                new BlockPos(playerPos.getX(), floorY, playerPos.getZ() + 2)
        };
        for (BlockPos light : lights) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.CREATIVE);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(true);
        player.teleportTo(playerPos.getX() + 0.5D, floorY + 1.0D, playerPos.getZ() + 0.5D);
        player.setYRot(-90.0F);
        player.setXRot(-35.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(playerPos.getX() + 1, ceilingY - 1, playerPos.getZ()),
                Direction.UP, -90.0F, player, 24.0D, 0.14D, CONTROLLED_PLAYER_PRESSURE_ATTACK_DAMAGE);

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            source.sendFailure(Component.literal("spiders_drop_attack_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        source.sendSuccess(Component.literal("spiders_drop_attack_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " ceiling_y=" + ceilingY
                + " spider=" + format(spiders.get(0).getX()) + "," + format(spiders.get(0).getY()) + "," + format(spiders.get(0).getZ())
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int webShotVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 5;
        int minX = playerPos.getX() - 4;
        int maxX = playerPos.getX() + 10;
        int minZ = playerPos.getZ() - 4;
        int maxZ = playerPos.getZ() + 4;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(minX, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(maxX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, maxZ), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        BlockPos[] lights = {
                new BlockPos(playerPos.getX() - 2, ceilingY, playerPos.getZ() - 2),
                new BlockPos(playerPos.getX() + 4, ceilingY, playerPos.getZ()),
                new BlockPos(playerPos.getX() + 8, ceilingY, playerPos.getZ() + 2)
        };
        for (BlockPos light : lights) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(false);
        player.connection.teleport(playerPos.getX() + 0.5D, floorY + 1.0D, playerPos.getZ() + 0.5D,
                -90.0F, 8.0F);
        player.setYRot(-90.0F);
        player.setXRot(8.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(playerPos.getX() + 6, floorY + 1, playerPos.getZ()),
                Direction.DOWN, 90.0F, player, 24.0D, 0.10D, 0.05D);

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            source.sendFailure(Component.literal("spiders_web_shot_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        source.sendSuccess(Component.literal("spiders_web_shot_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " spider=" + format(spiders.get(0).getX()) + "," + format(spiders.get(0).getY()) + "," + format(spiders.get(0).getZ())
                + " room=minX:" + minX + ",maxX:" + maxX + ",minZ:" + minZ + ",maxZ:" + maxZ
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int webTrapPlacementVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 5;
        int minX = playerPos.getX() - 3;
        int maxX = playerPos.getX() + 11;
        int minZ = playerPos.getZ() - 5;
        int maxZ = playerPos.getZ() + 5;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(minX, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(maxX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, maxZ), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (BlockPos light : new BlockPos[] {
                new BlockPos(playerPos.getX() + 2, ceilingY, playerPos.getZ() - 2),
                new BlockPos(playerPos.getX() + 6, ceilingY, playerPos.getZ()),
                new BlockPos(playerPos.getX() + 9, ceilingY, playerPos.getZ() + 2)
        }) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.CREATIVE);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(true);
        player.connection.teleport(playerPos.getX() - 1.5D, floorY + 1.7D, playerPos.getZ() + 4.25D,
                -122.0F, 14.0F);
        player.setYRot(-122.0F);
        player.setXRot(14.0F);
        player.yHeadRot = -122.0F;
        player.yBodyRot = -122.0F;

        BlockPos targetPos = new BlockPos(playerPos.getX() + 7, floorY + 1, playerPos.getZ());
        Sheep target = spawnPinnedSheepTarget(level, targetPos, Direction.DOWN);
        if (target == null) {
            source.sendFailure(Component.literal("spiders_web_trap_placement_visual_test created=false reason=target_create_failed"));
            return 0;
        }
        target.addTag(GroundSpiderEntity.WEB_TRAP_PLACEMENT_TEST_TARGET_TAG);
        target.setGlowingTag(true);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, 0, false, false));
        target.setYRot(-90.0F);
        target.setXRot(0.0F);
        target.yHeadRot = -90.0F;
        target.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(playerPos.getX() + 3, floorY + 1, playerPos.getZ() - 2),
                Direction.DOWN, 135.0F, target, 18.0D, 0.10D, 0.0D);
        if (!spiders.isEmpty()) {
            GroundSpiderEntity spider = spiders.get(0);
            spider.setGlowingTag(true);
            spider.addEffect(new MobEffectInstance(MobEffects.GLOWING, CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, 0, false, false));
        }

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            target.discard();
            source.sendFailure(Component.literal("spiders_web_trap_placement_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        BlockPos expectedBehind = targetPos.west();
        BlockPos expectedBesideNorth = targetPos.north();
        BlockPos expectedBesideSouth = targetPos.south();
        source.sendSuccess(Component.literal("spiders_web_trap_placement_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " target=" + format(target.getX()) + "," + format(target.getY()) + "," + format(target.getZ())
                + " spider=" + format(spiders.get(0).getX()) + "," + format(spiders.get(0).getY()) + "," + format(spiders.get(0).getZ())
                + " expected_behind=" + expectedBehind.getX() + "," + expectedBehind.getY() + "," + expectedBehind.getZ()
                + " expected_beside_north=" + expectedBesideNorth.getX() + "," + expectedBesideNorth.getY() + "," + expectedBesideNorth.getZ()
                + " expected_beside_south=" + expectedBesideSouth.getX() + "," + expectedBesideSouth.getY() + "," + expectedBesideSouth.getZ()
                + " target_route_direction=east"
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int singleThreadWebVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int webY = floorY + 1;
        int minX = playerPos.getX() - 8;
        int maxX = playerPos.getX() + 8;
        int minZ = playerPos.getZ() - 8;
        int maxZ = playerPos.getZ() + 8;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= floorY + 7; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (BlockPos light : new BlockPos[] {
                new BlockPos(playerPos.getX() - 4, floorY + 5, playerPos.getZ() - 4),
                new BlockPos(playerPos.getX() + 4, floorY + 5, playerPos.getZ() - 4),
                new BlockPos(playerPos.getX() - 4, floorY + 5, playerPos.getZ() + 4),
                new BlockPos(playerPos.getX() + 4, floorY + 5, playerPos.getZ() + 4)
        }) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.CREATIVE);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(true);
        player.connection.teleport(playerPos.getX() + 0.5D, floorY + 2.6D, playerPos.getZ() + 0.5D,
                -90.0F, 10.0F);
        player.setYRot(-90.0F);
        player.setXRot(10.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        int lineCount = 0;
        boolean xLine = addSingleThreadWebWalker(level, spiders,
                new BlockPos(playerPos.getX() - 7, webY, playerPos.getZ() - 4),
                new BlockPos(playerPos.getX() - 2, webY, playerPos.getZ() - 4), player, false);
        lineCount += xLine ? 1 : 0;
        boolean zLine = addSingleThreadWebWalker(level, spiders,
                new BlockPos(playerPos.getX() + 4, webY, playerPos.getZ() - 7),
                new BlockPos(playerPos.getX() + 4, webY, playerPos.getZ() - 2), player, false);
        lineCount += zLine ? 1 : 0;
        boolean diagonalA = addSingleThreadWebWalker(level, spiders,
                new BlockPos(playerPos.getX() - 6, webY, playerPos.getZ() + 2),
                new BlockPos(playerPos.getX() - 2, webY, playerPos.getZ() + 6), player, false);
        lineCount += diagonalA ? 1 : 0;
        boolean diagonalB = addSingleThreadWebWalker(level, spiders,
                new BlockPos(playerPos.getX() + 2, webY, playerPos.getZ() + 6),
                new BlockPos(playerPos.getX() + 7, webY, playerPos.getZ() + 1), player, false);
        lineCount += diagonalB ? 1 : 0;
        boolean reverseLine = addSingleThreadWebWalker(level, spiders,
                new BlockPos(playerPos.getX() - 7, webY, playerPos.getZ() + 6),
                new BlockPos(playerPos.getX() - 2, webY, playerPos.getZ() + 6), player, true);
        lineCount += reverseLine ? 1 : 0;
        boolean verticalHanging = SingleThreadWebBlock.placeLine(level,
                new BlockPos(playerPos.getX() + 7, webY, playerPos.getZ() + 6),
                new BlockPos(playerPos.getX() + 7, webY + 5, playerPos.getZ() + 6),
                BlockRegistry.SINGLE_THREAD_WEB.get());
        lineCount += verticalHanging ? 1 : 0;

        if (spiders.size() != 5 || lineCount < 6) {
            spiders.forEach(GroundSpiderEntity::discard);
            source.sendFailure(Component.literal("spiders_single_thread_web_visual_test created=false"
                    + " reason=setup_failed"
                    + " spiders=" + spiders.size()
                    + " line_count=" + lineCount
                    + " x_line=" + xLine
                    + " z_line=" + zLine
                    + " diagonal_a=" + diagonalA
                    + " diagonal_b=" + diagonalB
                    + " reverse_line=" + reverseLine
                    + " vertical_hanging=" + verticalHanging));
            return 0;
        }

        source.sendSuccess(Component.literal("spiders_single_thread_web_visual_test created=true"
                + " spiders=" + spiders.size()
                + " line_count=" + lineCount
                + " single_thread_web=true"
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " x_line=" + xLine
                + " z_line=" + zLine
                + " diagonal_a=" + diagonalA
                + " diagonal_b=" + diagonalB
                + " reverse_line=" + reverseLine
                + " vertical_hanging=" + verticalHanging
                + " moving_spiders=" + spiders.size()), false);
        return 1;
    }

    private static int pounceVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 5;
        int minX = playerPos.getX() - 4;
        int maxX = playerPos.getX() + 8;
        int minZ = playerPos.getZ() - 4;
        int maxZ = playerPos.getZ() + 4;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(minX, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(maxX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, maxZ), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        BlockPos[] lights = {
                new BlockPos(playerPos.getX() - 2, floorY + 3, playerPos.getZ() - 2),
                new BlockPos(playerPos.getX() + 3, floorY + 3, playerPos.getZ()),
                new BlockPos(playerPos.getX() + 6, floorY + 3, playerPos.getZ() + 2)
        };
        for (BlockPos light : lights) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(false);
        player.connection.teleport(playerPos.getX() + 0.5D, floorY + 1.0D, playerPos.getZ() + 0.5D,
                -90.0F, 12.0F);
        player.setYRot(-90.0F);
        player.setXRot(12.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(playerPos.getX() + 3, floorY + 1, playerPos.getZ()),
                Direction.DOWN, 90.0F, player, 18.0D, 0.10D, 0.05D);

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            source.sendFailure(Component.literal("spiders_pounce_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        source.sendSuccess(Component.literal("spiders_pounce_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " spider=" + format(spiders.get(0).getX()) + "," + format(spiders.get(0).getY()) + "," + format(spiders.get(0).getZ())
                + " room=minX:" + minX + ",maxX:" + maxX + ",minZ:" + minZ + ",maxZ:" + maxZ
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int retreatVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 6;
        int minX = playerPos.getX() - 4;
        int maxX = playerPos.getX() + 7;
        int minZ = playerPos.getZ() - 5;
        int maxZ = playerPos.getZ() + 5;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(maxX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (BlockPos light : new BlockPos[] {
                new BlockPos(playerPos.getX() - 2, floorY + 3, playerPos.getZ() - 3),
                new BlockPos(playerPos.getX() + 2, floorY + 4, playerPos.getZ()),
                new BlockPos(playerPos.getX() + 5, floorY + 4, playerPos.getZ() + 3)
        }) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(false);
        player.connection.teleport(playerPos.getX() + 0.5D, floorY + 1.0D, playerPos.getZ() + 0.5D,
                -90.0F, 8.0F);
        player.setYRot(-90.0F);
        player.setXRot(8.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(playerPos.getX() + 3, floorY + 1, playerPos.getZ()),
                Direction.DOWN, 90.0F, player, 18.0D, 0.12D, 0.05D);

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            source.sendFailure(Component.literal("spiders_retreat_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        GroundSpiderEntity spider = spiders.get(0);
        float beforeHealth = spider.getHealth();
        boolean damageApplied = spider.hurt(DamageSource.playerAttack(player), 1.5F);

        source.sendSuccess(Component.literal("spiders_retreat_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " spider=" + format(spider.getX()) + "," + format(spider.getY()) + "," + format(spider.getZ())
                + " spider_health_before=" + format(beforeHealth)
                + " spider_health_after=" + format(spider.getHealth())
                + " damage_applied=" + damageApplied
                + " room=minX:" + minX + ",maxX:" + maxX + ",minZ:" + minZ + ",maxZ:" + maxZ
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int fakeRetreatVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 7;
        int minX = playerPos.getX() - 5;
        int maxX = playerPos.getX() + 9;
        int minZ = playerPos.getZ() - 6;
        int maxZ = playerPos.getZ() + 6;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(maxX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (BlockPos light : new BlockPos[] {
                new BlockPos(playerPos.getX() - 3, floorY + 4, playerPos.getZ() - 4),
                new BlockPos(playerPos.getX() + 3, floorY + 5, playerPos.getZ()),
                new BlockPos(playerPos.getX() + 7, floorY + 5, playerPos.getZ() + 4)
        }) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(false);
        player.connection.teleport(playerPos.getX() + 0.5D, floorY + 1.0D, playerPos.getZ() + 0.5D,
                -90.0F, 8.0F);
        player.setYRot(-90.0F);
        player.setXRot(8.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(playerPos.getX() + 3, floorY + 1, playerPos.getZ()),
                Direction.DOWN, 90.0F, player, 18.0D, 0.12D, 0.05D);

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            source.sendFailure(Component.literal("spiders_fake_retreat_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        GroundSpiderEntity spider = spiders.get(0);
        float beforeHealth = spider.getHealth();
        boolean damageApplied = spider.hurt(DamageSource.playerAttack(player), 1.5F);

        source.sendSuccess(Component.literal("spiders_fake_retreat_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " spider=" + format(spider.getX()) + "," + format(spider.getY()) + "," + format(spider.getZ())
                + " spider_health_before=" + format(beforeHealth)
                + " spider_health_after=" + format(spider.getHealth())
                + " damage_applied=" + damageApplied
                + " room=minX:" + minX + ",maxX:" + maxX + ",minZ:" + minZ + ",maxZ:" + maxZ
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int webLowerVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 9;
        int minX = playerPos.getX() - 3;
        int maxX = playerPos.getX() + 8;
        int minZ = playerPos.getZ() - 4;
        int maxZ = playerPos.getZ() + 4;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(minX, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(maxX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, maxZ), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        BlockPos spiderAir = new BlockPos(playerPos.getX() + 4, ceilingY - 1, playerPos.getZ());
        BlockPos silkMarker = new BlockPos(spiderAir.getX(), ceilingY - 1, spiderAir.getZ() + 3);
        boolean silkMarkerPlaced = SingleThreadWebBlock.placeLine(level, silkMarker, silkMarker.below(),
                BlockRegistry.SINGLE_THREAD_WEB.get());

        for (BlockPos light : new BlockPos[] {
                new BlockPos(playerPos.getX() - 1, floorY + 3, playerPos.getZ() - 3),
                new BlockPos(playerPos.getX() + 3, floorY + 4, playerPos.getZ() + 2),
                new BlockPos(playerPos.getX() + 6, floorY + 4, playerPos.getZ() - 2)
        }) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(false);
        player.connection.teleport(playerPos.getX() + 0.5D, floorY + 1.0D, playerPos.getZ() + 0.5D,
                -90.0F, -12.0F);
        player.setYRot(-90.0F);
        player.setXRot(-12.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders, spiderAir,
                Direction.UP, 90.0F, player, 18.0D, 0.10D, 0.05D);

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            source.sendFailure(Component.literal("spiders_web_lower_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        GroundSpiderEntity spider = spiders.get(0);
        source.sendSuccess(Component.literal("spiders_web_lower_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " spider=" + format(spider.getX()) + "," + format(spider.getY()) + "," + format(spider.getZ())
                + " silk_marker=" + silkMarker.getX() + "," + silkMarker.getY() + "," + silkMarker.getZ()
                + " silk_marker_single_thread=" + silkMarkerPlaced
                + " room=minX:" + minX + ",maxX:" + maxX + ",minZ:" + minZ + ",maxZ:" + maxZ
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int grabPullVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 5;
        int minX = playerPos.getX() - 4;
        int maxX = playerPos.getX() + 7;
        int minZ = playerPos.getZ() - 4;
        int maxZ = playerPos.getZ() + 4;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(maxX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        BlockPos webMarker = new BlockPos(playerPos.getX(), floorY + 1, playerPos.getZ() + 1);
        level.setBlock(webMarker, Blocks.COBWEB.defaultBlockState(), 3);
        for (BlockPos light : new BlockPos[] {
                new BlockPos(playerPos.getX() - 2, floorY + 3, playerPos.getZ() - 2),
                new BlockPos(playerPos.getX() + 3, floorY + 3, playerPos.getZ() + 2),
                new BlockPos(playerPos.getX() + 5, floorY + 4, playerPos.getZ() - 2)
        }) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 4));
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(false);
        double playerX = playerPos.getX() + 0.5D;
        double playerY = floorY + 1.0D;
        double playerZ = playerPos.getZ() + 0.5D;
        player.connection.teleport(playerX, playerY, playerZ, -90.0F, 5.0F);
        player.setYRot(-90.0F);
        player.setXRot(5.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(playerPos.getX() + 2, floorY + 1, playerPos.getZ()),
                Direction.DOWN, 90.0F, player, 12.0D, 0.08D, 0.0D);

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            source.sendFailure(Component.literal("spiders_grab_pull_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        GroundSpiderEntity spider = spiders.get(0);
        source.sendSuccess(Component.literal("spiders_grab_pull_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " spider=" + format(spider.getX()) + "," + format(spider.getY()) + "," + format(spider.getZ())
                + " web_marker=" + webMarker.getX() + "," + webMarker.getY() + "," + webMarker.getZ()
                + " room=minX:" + minX + ",maxX:" + maxX + ",minZ:" + minZ + ",maxZ:" + maxZ
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int dragNestVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 5;
        int minX = playerPos.getX() - 4;
        int maxX = playerPos.getX() + 9;
        int minZ = playerPos.getZ() - 5;
        int maxZ = playerPos.getZ() + 5;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(maxX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, maxZ), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        BlockPos nestAnchor = new BlockPos(playerPos.getX() + 6, floorY + 1, playerPos.getZ() + 1);
        for (BlockPos web : new BlockPos[] {
                nestAnchor,
                nestAnchor.above(),
                nestAnchor.south(),
                nestAnchor.south().above()
        }) {
            level.setBlock(web, Blocks.COBWEB.defaultBlockState(), 3);
        }
        for (BlockPos light : new BlockPos[] {
                new BlockPos(playerPos.getX() - 2, floorY + 3, playerPos.getZ() - 2),
                new BlockPos(playerPos.getX() + 3, floorY + 3, playerPos.getZ() + 2),
                new BlockPos(playerPos.getX() + 7, floorY + 4, playerPos.getZ() - 2)
        }) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 360, 4));
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(false);
        double playerX = playerPos.getX() + 0.5D;
        double playerY = floorY + 1.0D;
        double playerZ = playerPos.getZ() + 0.5D;
        player.connection.teleport(playerX, playerY, playerZ, -90.0F, 4.0F);
        player.setYRot(-90.0F);
        player.setXRot(4.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(playerPos.getX() + 2, floorY + 1, playerPos.getZ()),
                Direction.DOWN, 90.0F, player, 12.0D, 0.08D, 0.0D);

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            source.sendFailure(Component.literal("spiders_drag_nest_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        GroundSpiderEntity spider = spiders.get(0);
        source.sendSuccess(Component.literal("spiders_drag_nest_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " spider=" + format(spider.getX()) + "," + format(spider.getY()) + "," + format(spider.getZ())
                + " nest_anchor=" + nestAnchor.getX() + "," + nestAnchor.getY() + "," + nestAnchor.getZ()
                + " room=minX:" + minX + ",maxX:" + maxX + ",minZ:" + minZ + ",maxZ:" + maxZ
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int packCoordinationVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 6;
        int minX = playerPos.getX() - 4;
        int maxX = playerPos.getX() + 12;
        int minZ = playerPos.getZ() - 6;
        int maxZ = playerPos.getZ() + 6;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(minX, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(maxX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, maxZ), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (BlockPos light : new BlockPos[] {
                new BlockPos(playerPos.getX() - 1, floorY + 3, playerPos.getZ() - 4),
                new BlockPos(playerPos.getX() + 5, floorY + 4, playerPos.getZ()),
                new BlockPos(playerPos.getX() + 9, floorY + 4, playerPos.getZ() + 4)
        }) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.CREATIVE);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(true);
        player.connection.teleport(playerPos.getX() + 0.5D, floorY + 1.0D, playerPos.getZ() + 0.5D,
                -90.0F, -4.0F);
        player.setYRot(-90.0F);
        player.setXRot(-4.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        BlockPos targetPos = new BlockPos(playerPos.getX() + 5, floorY + 1, playerPos.getZ());
        IronGolem target = spawnPinnedTarget(level, targetPos, Direction.DOWN, 80.0F);
        if (target == null) {
            source.sendFailure(Component.literal("spiders_pack_coordination_visual_test created=false reason=target_create_failed"));
            return 0;
        }
        target.addTag(GroundSpiderEntity.PACK_COORDINATION_TEST_TARGET_TAG);

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean directPathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(targetPos.getX() - 2, floorY + 1, targetPos.getZ()),
                Direction.DOWN, 90.0F, target, 24.0D, 0.12D, 0.0D);
        boolean ambushPathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(targetPos.getX() + 3, ceilingY - 1, targetPos.getZ()),
                Direction.UP, 90.0F, target, 24.0D, 0.12D, 0.0D);
        boolean flankPathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(targetPos.getX(), floorY + 1, targetPos.getZ() + 4),
                Direction.DOWN, 180.0F, target, 24.0D, 0.12D, 0.0D);

        if (spiders.size() != 3) {
            spiders.forEach(GroundSpiderEntity::discard);
            target.discard();
            source.sendFailure(Component.literal("spiders_pack_coordination_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        boolean allPathsStarted = directPathStarted && ambushPathStarted && flankPathStarted;
        source.sendSuccess(Component.literal("spiders_pack_coordination_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " target=" + format(target.getX()) + "," + format(target.getY()) + "," + format(target.getZ())
                + " direct_spider=" + format(spiders.get(0).getX()) + "," + format(spiders.get(0).getY()) + "," + format(spiders.get(0).getZ())
                + " ambush_spider=" + format(spiders.get(1).getX()) + "," + format(spiders.get(1).getY()) + "," + format(spiders.get(1).getZ())
                + " flank_spider=" + format(spiders.get(2).getX()) + "," + format(spiders.get(2).getY()) + "," + format(spiders.get(2).getZ())
                + " room=minX:" + minX + ",maxX:" + maxX + ",minZ:" + minZ + ",maxZ:" + maxZ
                + " path0_started=" + directPathStarted
                + " path1_started=" + ambushPathStarted
                + " path2_started=" + flankPathStarted
                + " all_paths_started=" + allPathsStarted), false);
        return 1;
    }

    private static int escapeCuttingVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 5;
        int minX = playerPos.getX() - 3;
        int maxX = playerPos.getX() + 13;
        int minZ = playerPos.getZ() - 6;
        int maxZ = playerPos.getZ() + 6;
        int doorwayX = playerPos.getX() + 10;
        int doorwayZ = playerPos.getZ();

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 2; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX + 2; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
                if (x <= maxX) {
                    level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        for (int y = floorY + 1; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(minX, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(maxX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, maxZ), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY + 1; y <= ceilingY; y++) {
            level.setBlock(new BlockPos(doorwayX, y, doorwayZ - 2), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(doorwayX, y, doorwayZ + 2), Blocks.STONE.defaultBlockState(), 3);
        }
        for (int z = doorwayZ - 1; z <= doorwayZ + 1; z++) {
            for (int y = floorY + 1; y <= floorY + 2; y++) {
                level.setBlock(new BlockPos(doorwayX, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        for (int x = doorwayX + 1; x <= maxX + 2; x++) {
            level.setBlock(new BlockPos(x, floorY, doorwayZ), Blocks.STONE.defaultBlockState(), 3);
        }
        for (BlockPos light : new BlockPos[] {
                new BlockPos(playerPos.getX() - 1, floorY + 3, playerPos.getZ() - 4),
                new BlockPos(playerPos.getX() + 4, floorY + 4, playerPos.getZ() + 3),
                new BlockPos(doorwayX - 1, floorY + 4, doorwayZ)
        }) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.CREATIVE);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(true);
        player.connection.teleport(playerPos.getX() - 1.5D, floorY + 1.0D, playerPos.getZ() + 4.5D,
                -55.0F, 2.0F);
        player.setYRot(-55.0F);
        player.setXRot(2.0F);
        player.yHeadRot = -55.0F;
        player.yBodyRot = -55.0F;

        BlockPos targetPos = new BlockPos(playerPos.getX() + 3, floorY + 1, playerPos.getZ());
        IronGolem target = spawnPinnedTarget(level, targetPos, Direction.DOWN, 80.0F);
        if (target == null) {
            source.sendFailure(Component.literal("spiders_escape_cutting_visual_test created=false reason=target_create_failed"));
            return 0;
        }
        target.addTag(GroundSpiderEntity.ESCAPE_CUTTING_TEST_TARGET_TAG);
        target.setYRot(-90.0F);
        target.setXRot(0.0F);
        target.yHeadRot = -90.0F;
        target.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(playerPos.getX() - 1, floorY + 1, playerPos.getZ() + 4),
                Direction.DOWN, 140.0F, target, 24.0D, 0.13D, 0.0D);

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            target.discard();
            source.sendFailure(Component.literal("spiders_escape_cutting_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        source.sendSuccess(Component.literal("spiders_escape_cutting_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " target=" + format(target.getX()) + "," + format(target.getY()) + "," + format(target.getZ())
                + " spider=" + format(spiders.get(0).getX()) + "," + format(spiders.get(0).getY()) + "," + format(spiders.get(0).getZ())
                + " doorway_anchor=" + doorwayX + "," + (floorY + 1) + "," + doorwayZ
                + " doorway_direction=east"
                + " room=minX:" + minX + ",maxX:" + maxX + ",minZ:" + minZ + ",maxZ:" + maxZ
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int threatDisplayVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 5;
        int minX = playerPos.getX() - 4;
        int maxX = playerPos.getX() + 9;
        int minZ = playerPos.getZ() - 5;
        int maxZ = playerPos.getZ() + 5;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY + 1; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(minX, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(maxX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, maxZ), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (BlockPos light : new BlockPos[] {
                new BlockPos(playerPos.getX() - 2, floorY + 4, playerPos.getZ() - 2),
                new BlockPos(playerPos.getX() + 2, floorY + 4, playerPos.getZ() + 2),
                new BlockPos(playerPos.getX() + 6, floorY + 4, playerPos.getZ())
        }) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.CREATIVE);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(true);
        player.connection.teleport(playerPos.getX() - 1.5D, floorY + 1.6D, playerPos.getZ() + 3.6D,
                -128.0F, 15.0F);
        player.setYRot(-128.0F);
        player.setXRot(15.0F);
        player.yHeadRot = -128.0F;
        player.yBodyRot = -128.0F;

        BlockPos targetPos = new BlockPos(playerPos.getX() + 5, floorY + 1, playerPos.getZ());
        IronGolem target = spawnPinnedTarget(level, targetPos, Direction.DOWN, 80.0F);
        if (target == null) {
            source.sendFailure(Component.literal("spiders_threat_display_visual_test created=false reason=target_create_failed"));
            return 0;
        }
        target.addTag(GroundSpiderEntity.THREAT_DISPLAY_TEST_TARGET_TAG);
        target.setGlowingTag(true);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, 0, false, false));
        target.setYRot(90.0F);
        target.setXRot(0.0F);
        target.yHeadRot = 90.0F;
        target.yBodyRot = 90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(playerPos.getX() + 2, floorY + 1, playerPos.getZ()),
                Direction.DOWN, -90.0F, target, 18.0D, 0.10D, 0.0D);
        if (!spiders.isEmpty()) {
            GroundSpiderEntity spider = spiders.get(0);
            spider.setGlowingTag(true);
            spider.addEffect(new MobEffectInstance(MobEffects.GLOWING, CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, 0, false, false));
        }

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            target.discard();
            source.sendFailure(Component.literal("spiders_threat_display_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        source.sendSuccess(Component.literal("spiders_threat_display_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " target=" + format(target.getX()) + "," + format(target.getY()) + "," + format(target.getZ())
                + " spider=" + format(spiders.get(0).getX()) + "," + format(spiders.get(0).getY()) + "," + format(spiders.get(0).getZ())
                + " room=minX:" + minX + ",maxX:" + maxX + ",minZ:" + minZ + ",maxZ:" + maxZ
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int lineOfSightStalkingVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 6;
        int minX = playerPos.getX() - 3;
        int maxX = playerPos.getX() + 10;
        int minZ = playerPos.getZ() - 5;
        int maxZ = playerPos.getZ() + 5;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY + 1; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(minX, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(maxX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            for (int x = minX; x <= maxX; x++) {
                level.setBlock(new BlockPos(x, y, minZ), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, maxZ), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (BlockPos light : new BlockPos[] {
                new BlockPos(playerPos.getX() - 2, floorY + 4, playerPos.getZ() - 2),
                new BlockPos(playerPos.getX() + 3, floorY + 4, playerPos.getZ() + 2),
                new BlockPos(playerPos.getX() + 8, floorY + 4, playerPos.getZ())
        }) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.CREATIVE);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(true);
        player.connection.teleport(playerPos.getX() - 1.5D, floorY + 1.6D, playerPos.getZ() + 3.6D,
                -126.0F, 15.0F);
        player.setYRot(-126.0F);
        player.setXRot(15.0F);
        player.yHeadRot = -126.0F;
        player.yBodyRot = -126.0F;

        BlockPos targetPos = new BlockPos(playerPos.getX() + 7, floorY + 1, playerPos.getZ());
        IronGolem target = spawnPinnedTarget(level, targetPos, Direction.DOWN, 80.0F);
        if (target == null) {
            source.sendFailure(Component.literal("spiders_line_of_sight_stalking_visual_test created=false reason=target_create_failed"));
            return 0;
        }
        target.addTag(GroundSpiderEntity.LINE_OF_SIGHT_STALKING_TEST_TARGET_TAG);
        target.setGlowingTag(true);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, 0, false, false));
        target.setYRot(90.0F);
        target.setXRot(0.0F);
        target.yHeadRot = 90.0F;
        target.yBodyRot = 90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(playerPos.getX() + 2, floorY + 1, playerPos.getZ()),
                Direction.DOWN, -90.0F, target, 18.0D, 0.10D, 0.0D);
        if (!spiders.isEmpty()) {
            GroundSpiderEntity spider = spiders.get(0);
            spider.setGlowingTag(true);
            spider.addEffect(new MobEffectInstance(MobEffects.GLOWING, CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, 0, false, false));
        }

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            target.discard();
            source.sendFailure(Component.literal("spiders_line_of_sight_stalking_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        GroundSpiderEntity spider = spiders.get(0);
        target.lookAt(EntityAnchorArgument.Anchor.EYES, spider.getEyePosition());
        target.yHeadRot = target.getYRot();
        target.yBodyRot = target.getYRot();

        source.sendSuccess(Component.literal("spiders_line_of_sight_stalking_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " target=" + format(target.getX()) + "," + format(target.getY()) + "," + format(target.getZ())
                + " target_yaw=" + format(target.getYRot())
                + " spider=" + format(spider.getX()) + "," + format(spider.getY()) + "," + format(spider.getZ())
                + " room=minX:" + minX + ",maxX:" + maxX + ",minZ:" + minZ + ",maxZ:" + maxZ
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int lineOfSightStalkingLookAway(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();
        AABB bounds = player.getBoundingBox().inflate(64.0D);
        List<IronGolem> targets = new ArrayList<>(level.getEntitiesOfClass(IronGolem.class, bounds,
                target -> target.isAlive()
                        && target.getTags().contains(GroundSpiderEntity.LINE_OF_SIGHT_STALKING_TEST_TARGET_TAG)));
        targets.sort(Comparator.comparingDouble(target -> target.distanceToSqr(player)));
        for (IronGolem target : targets) {
            target.setDeltaMovement(Vec3.ZERO);
            target.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition().add(0.0D, 0.0D, 6.0D));
            target.yHeadRot = target.getYRot();
            target.yBodyRot = target.getYRot();
        }

        source.sendSuccess(Component.literal("spiders_line_of_sight_stalking_look_away applied=" + !targets.isEmpty()
                + " targets=" + targets.size()), false);
        return targets.isEmpty() ? 0 : 1;
    }

    private static int darknessPreferenceVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int roofY = floorY + 4;
        int minX = playerPos.getX() - 3;
        int maxX = playerPos.getX() + 12;
        int minZ = playerPos.getZ() - 7;
        int maxZ = playerPos.getZ() + 6;
        int darkMinX = playerPos.getX() + 1;
        int darkMaxX = playerPos.getX() + 5;
        int darkMinZ = playerPos.getZ() - 6;
        int darkMaxZ = playerPos.getZ() - 2;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= roofY + 3; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            level.setBlock(new BlockPos(x, floorY + 1, minZ), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, floorY + 2, minZ), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, floorY + 1, maxZ), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x, floorY + 2, maxZ), Blocks.STONE.defaultBlockState(), 3);
        }
        for (int z = minZ; z <= maxZ; z++) {
            level.setBlock(new BlockPos(minX, floorY + 1, z), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(minX, floorY + 2, z), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(maxX, floorY + 1, z), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(maxX, floorY + 2, z), Blocks.STONE.defaultBlockState(), 3);
        }

        for (int x = darkMinX; x <= darkMaxX; x++) {
            for (int z = darkMinZ; z <= darkMaxZ; z++) {
                level.setBlock(new BlockPos(x, roofY, z), Blocks.DEEPSLATE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY + 1; y <= roofY; y++) {
            for (int x = darkMinX; x <= darkMaxX; x++) {
                level.setBlock(new BlockPos(x, y, darkMinZ), Blocks.DEEPSLATE.defaultBlockState(), 3);
            }
            for (int z = darkMinZ; z <= darkMaxZ; z++) {
                level.setBlock(new BlockPos(darkMinX, y, z), Blocks.DEEPSLATE.defaultBlockState(), 3);
            }
        }
        for (BlockPos light : new BlockPos[] {
                new BlockPos(playerPos.getX() + 5, floorY + 3, playerPos.getZ() + 2),
                new BlockPos(playerPos.getX() + 8, floorY + 3, playerPos.getZ() + 1),
                new BlockPos(playerPos.getX() + 9, floorY + 3, playerPos.getZ() - 2)
        }) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.CREATIVE);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(true);
        player.connection.teleport(playerPos.getX() - 1.5D, floorY + 1.6D, playerPos.getZ() + 4.2D,
                -126.0F, 14.0F);
        player.setYRot(-126.0F);
        player.setXRot(14.0F);
        player.yHeadRot = -126.0F;
        player.yBodyRot = -126.0F;

        BlockPos targetPos = new BlockPos(playerPos.getX() + 8, floorY + 1, playerPos.getZ() + 1);
        IronGolem target = spawnPinnedTarget(level, targetPos, Direction.DOWN, 80.0F);
        if (target == null) {
            source.sendFailure(Component.literal("spiders_darkness_preference_visual_test created=false reason=target_create_failed"));
            return 0;
        }
        target.addTag(GroundSpiderEntity.DARKNESS_PREFERENCE_TEST_TARGET_TAG);
        target.setGlowingTag(true);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, 0, false, false));
        target.setYRot(90.0F);
        target.setXRot(0.0F);
        target.yHeadRot = 90.0F;
        target.yBodyRot = 90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(playerPos.getX() + 5, floorY + 1, playerPos.getZ() + 1),
                Direction.DOWN, -90.0F, target, 18.0D, 0.12D, 0.0D);
        if (!spiders.isEmpty()) {
            GroundSpiderEntity spider = spiders.get(0);
            spider.setGlowingTag(true);
            spider.addEffect(new MobEffectInstance(MobEffects.GLOWING, CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, 0, false, false));
        }

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            target.discard();
            source.sendFailure(Component.literal("spiders_darkness_preference_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        BlockPos openSample = targetPos;
        BlockPos darkSample = new BlockPos(darkMinX + 1, floorY + 1, darkMinZ + 1);
        source.sendSuccess(Component.literal("spiders_darkness_preference_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " target=" + format(target.getX()) + "," + format(target.getY()) + "," + format(target.getZ())
                + " spider=" + format(spiders.get(0).getX()) + "," + format(spiders.get(0).getY()) + "," + format(spiders.get(0).getZ())
                + " open_sample=" + openSample.getX() + "," + openSample.getY() + "," + openSample.getZ()
                + " open_light=" + level.getMaxLocalRawBrightness(openSample)
                + " dark_sample=" + darkSample.getX() + "," + darkSample.getY() + "," + darkSample.getZ()
                + " dark_light=" + level.getMaxLocalRawBrightness(darkSample)
                + " dark_corner=minX:" + darkMinX + ",maxX:" + darkMaxX + ",minZ:" + darkMinZ + ",maxZ:" + darkMaxZ
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int wallPeekVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 5;
        int minX = playerPos.getX() - 4;
        int maxX = playerPos.getX() + 10;
        int minZ = playerPos.getZ() - 5;
        int maxZ = playerPos.getZ() + 5;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY + 1; y <= floorY + 3; y++) {
            level.setBlock(new BlockPos(playerPos.getX() + 4, y, playerPos.getZ() - 1),
                    Blocks.DEEPSLATE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(playerPos.getX() + 4, y, playerPos.getZ() - 2),
                    Blocks.DEEPSLATE.defaultBlockState(), 3);
        }
        for (BlockPos light : new BlockPos[] {
                new BlockPos(playerPos.getX() + 1, floorY + 4, playerPos.getZ() + 3),
                new BlockPos(playerPos.getX() + 6, floorY + 4, playerPos.getZ() + 2),
                new BlockPos(playerPos.getX() + 8, floorY + 4, playerPos.getZ() - 2)
        }) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.CREATIVE);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(true);
        player.connection.teleport(playerPos.getX() - 1.75D, floorY + 1.7D, playerPos.getZ() + 4.35D,
                -122.0F, 12.0F);
        player.setYRot(-122.0F);
        player.setXRot(12.0F);
        player.yHeadRot = -122.0F;
        player.yBodyRot = -122.0F;

        BlockPos coverPos = new BlockPos(playerPos.getX() + 3, floorY + 1, playerPos.getZ() - 1);
        BlockPos peekPos = coverPos.south();
        BlockPos targetPos = new BlockPos(playerPos.getX() + 8, floorY + 1, playerPos.getZ() + 1);
        IronGolem target = spawnPinnedTarget(level, targetPos, Direction.DOWN, 80.0F);
        if (target == null) {
            source.sendFailure(Component.literal("spiders_wall_peek_visual_test created=false reason=target_create_failed"));
            return 0;
        }
        target.addTag(GroundSpiderEntity.WALL_PEEK_TEST_TARGET_TAG);
        target.setGlowingTag(true);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, 0, false, false));
        target.setYRot(90.0F);
        target.setXRot(0.0F);
        target.yHeadRot = 90.0F;
        target.yBodyRot = 90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders, coverPos, Direction.DOWN, 135.0F,
                target, 18.0D, 0.10D, 0.0D);
        if (!spiders.isEmpty()) {
            GroundSpiderEntity spider = spiders.get(0);
            spider.setGlowingTag(true);
            spider.addEffect(new MobEffectInstance(MobEffects.GLOWING, CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, 0, false, false));
        }

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            target.discard();
            source.sendFailure(Component.literal("spiders_wall_peek_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        source.sendSuccess(Component.literal("spiders_wall_peek_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " target=" + format(target.getX()) + "," + format(target.getY()) + "," + format(target.getZ())
                + " spider=" + format(spiders.get(0).getX()) + "," + format(spiders.get(0).getY()) + "," + format(spiders.get(0).getZ())
                + " expected_cover=" + coverPos.getX() + "," + coverPos.getY() + "," + coverPos.getZ()
                + " expected_peek=" + peekPos.getX() + "," + peekPos.getY() + "," + peekPos.getZ()
                + " wall_block=" + (playerPos.getX() + 4) + "," + (floorY + 1) + "," + (playerPos.getZ() - 1)
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int preyInteractionVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 5;
        int minX = playerPos.getX() - 4;
        int maxX = playerPos.getX() + 10;
        int minZ = playerPos.getZ() - 5;
        int maxZ = playerPos.getZ() + 5;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);

        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.PODZOL.defaultBlockState(), 3);
            }
        }
        for (int y = floorY + 1; y <= floorY + 2; y++) {
            level.setBlock(new BlockPos(playerPos.getX() + 8, y, playerPos.getZ() - 3),
                    Blocks.DEEPSLATE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(playerPos.getX() + 8, y, playerPos.getZ() + 3),
                    Blocks.DEEPSLATE.defaultBlockState(), 3);
        }
        for (BlockPos light : new BlockPos[] {
                new BlockPos(playerPos.getX() + 1, floorY + 4, playerPos.getZ() + 3),
                new BlockPos(playerPos.getX() + 5, floorY + 4, playerPos.getZ()),
                new BlockPos(playerPos.getX() + 8, floorY + 4, playerPos.getZ() - 2)
        }) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.CREATIVE);
        player.removeAllEffects();
        giveControlledVisualTestVision(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(true);
        player.connection.teleport(playerPos.getX() - 1.5D, floorY + 1.7D, playerPos.getZ() + 4.25D,
                -121.0F, 13.0F);
        player.setYRot(-121.0F);
        player.setXRot(13.0F);
        player.yHeadRot = -121.0F;
        player.yBodyRot = -121.0F;

        BlockPos preyPos = new BlockPos(playerPos.getX() + 6, floorY + 1, playerPos.getZ());
        Sheep prey = spawnPinnedSheepTarget(level, preyPos, Direction.DOWN);
        if (prey == null) {
            source.sendFailure(Component.literal("spiders_prey_interaction_visual_test created=false reason=prey_create_failed"));
            return 0;
        }
        prey.addTag(GroundSpiderEntity.PREY_INTERACTION_TEST_TARGET_TAG);
        prey.setGlowingTag(true);
        prey.addEffect(new MobEffectInstance(MobEffects.GLOWING, CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, 0, false, false));
        prey.setHealth(Math.min(prey.getMaxHealth(), 2.0F));
        prey.setYRot(-90.0F);
        prey.setXRot(0.0F);
        prey.yHeadRot = -90.0F;
        prey.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean pathStarted = spawnCombatSpider(level, spiders,
                new BlockPos(playerPos.getX() + 3, floorY + 1, playerPos.getZ() - 1),
                Direction.DOWN, 95.0F, prey, 18.0D, 0.16D, 6.0D);
        if (!spiders.isEmpty()) {
            GroundSpiderEntity spider = spiders.get(0);
            spider.setGlowingTag(true);
            spider.addEffect(new MobEffectInstance(MobEffects.GLOWING, CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, 0, false, false));
        }

        if (spiders.size() != 1) {
            spiders.forEach(GroundSpiderEntity::discard);
            prey.discard();
            source.sendFailure(Component.literal("spiders_prey_interaction_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        source.sendSuccess(Component.literal("spiders_prey_interaction_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " prey=" + format(prey.getX()) + "," + format(prey.getY()) + "," + format(prey.getZ())
                + " prey_health=" + format(prey.getHealth())
                + " spider=" + format(spiders.get(0).getX()) + "," + format(spiders.get(0).getY()) + "," + format(spiders.get(0).getZ())
                + " expected_prey_anchor=" + preyPos.getX() + "," + preyPos.getY() + "," + preyPos.getZ()
                + " path0_started=" + pathStarted
                + " all_paths_started=" + pathStarted), false);
        return 1;
    }

    private static int playerPressureGauntletVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int ceilingY = floorY + 7;
        int westWallX = playerPos.getX() - 7;
        int eastWallX = playerPos.getX() + 11;
        int minZ = playerPos.getZ() - 7;
        int maxZ = playerPos.getZ() + 7;

        clearVisualTestEntities(level, player);
        level.getServer().setDifficulty(Difficulty.NORMAL, true);

        for (int x = westWallX - 1; x <= eastWallX + 1; x++) {
            for (int y = floorY; y <= ceilingY + 1; y++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = westWallX; x <= eastWallX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(westWallX, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(eastWallX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int x = westWallX; x <= eastWallX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int x = westWallX; x <= eastWallX; x++) {
            for (int y = floorY + 1; y <= floorY + 3; y++) {
                level.setBlock(new BlockPos(x, y, minZ), Blocks.GLASS.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, maxZ), Blocks.GLASS.defaultBlockState(), 3);
            }
        }

        BlockPos[] pillars = {
                new BlockPos(playerPos.getX() + 2, floorY + 1, playerPos.getZ() - 4),
                new BlockPos(playerPos.getX() + 4, floorY + 1, playerPos.getZ() + 3),
                new BlockPos(playerPos.getX() + 7, floorY + 1, playerPos.getZ() - 1)
        };
        for (BlockPos pillar : pillars) {
            for (int y = pillar.getY(); y <= floorY + 3; y++) {
                level.setBlock(new BlockPos(pillar.getX(), y, pillar.getZ()), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
        }

        BlockPos[] lights = {
                new BlockPos(playerPos.getX() - 3, ceilingY, playerPos.getZ() - 4),
                new BlockPos(playerPos.getX() + 3, ceilingY, playerPos.getZ() + 4),
                new BlockPos(playerPos.getX() + 8, ceilingY, playerPos.getZ()),
                new BlockPos(westWallX, floorY + 3, playerPos.getZ() + 5),
                new BlockPos(eastWallX, floorY + 4, playerPos.getZ() - 5)
        };
        for (BlockPos light : lights) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(false);
        player.teleportTo(playerPos.getX() + 0.5D, floorY + 1.0D, playerPos.getZ() + 0.5D);
        player.setYRot(-90.0F);
        player.setXRot(-10.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean[] pathStarted = new boolean[5];
        pathStarted[0] = spawnCombatSpider(level, spiders, new BlockPos(eastWallX - 1, floorY + 2, playerPos.getZ() - 5),
                Direction.EAST, -90.0F, player, 40.0D, 0.16D, 0.35D);
        pathStarted[1] = spawnCombatSpider(level, spiders, new BlockPos(eastWallX - 1, floorY + 5, playerPos.getZ() + 4),
                Direction.EAST, -90.0F, player, 40.0D, 0.16D, 0.35D);
        pathStarted[2] = spawnCombatSpider(level, spiders, new BlockPos(playerPos.getX() + 3, ceilingY - 1, playerPos.getZ() + 1),
                Direction.UP, 0.0F, player, 40.0D, 0.16D, 0.35D);
        pathStarted[3] = spawnCombatSpider(level, spiders, new BlockPos(westWallX + 1, floorY + 3, playerPos.getZ() + 5),
                Direction.WEST, 90.0F, player, 40.0D, 0.16D, 0.35D);
        pathStarted[4] = spawnCombatSpider(level, spiders, new BlockPos(playerPos.getX() + 5, floorY + 1, playerPos.getZ() - 5),
                Direction.DOWN, -90.0F, player, 40.0D, 0.18D, 0.35D);

        if (spiders.size() != pathStarted.length) {
            spiders.forEach(GroundSpiderEntity::discard);
            source.sendFailure(Component.literal("spiders_player_pressure_gauntlet_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        source.sendSuccess(Component.literal("spiders_player_pressure_gauntlet_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " west_wall_x=" + westWallX
                + " east_wall_x=" + eastWallX
                + " ceiling_y=" + ceilingY
                + " path0_started=" + pathStarted[0]
                + " path1_started=" + pathStarted[1]
                + " path2_started=" + pathStarted[2]
                + " path3_started=" + pathStarted[3]
                + " path4_started=" + pathStarted[4]
                + " all_paths_started=" + (pathStarted[0] && pathStarted[1] && pathStarted[2]
                        && pathStarted[3] && pathStarted[4])), false);
        return 1;
    }

    private static int playerPressureFieldVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerPos = player.blockPosition();
        int floorY = playerPos.getY() - 1;
        int minX = playerPos.getX() - 11;
        int maxX = playerPos.getX() + 17;
        int minZ = playerPos.getZ() - 12;
        int maxZ = playerPos.getZ() + 12;
        int eastWallX = playerPos.getX() + 13;
        int westWallX = playerPos.getX() - 7;
        int northWallZ = playerPos.getZ() - 9;
        int overhangY = floorY + 5;

        clearVisualTestEntities(level, player);
        level.getServer().setDifficulty(Difficulty.NORMAL, true);

        for (int x = minX; x <= maxX; x++) {
            for (int y = floorY + 1; y <= floorY + 8; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean stonePatch = Math.floorMod((x * 3) + z, 11) == 0;
                level.setBlock(new BlockPos(x, floorY, z),
                        (stonePatch ? Blocks.COBBLESTONE : Blocks.GRASS_BLOCK).defaultBlockState(), 3);
            }
        }

        for (int z = playerPos.getZ() - 6; z <= playerPos.getZ() + 5; z++) {
            for (int y = floorY + 1; y <= floorY + 5; y++) {
                level.setBlock(new BlockPos(eastWallX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int z = playerPos.getZ() + 4; z <= playerPos.getZ() + 8; z++) {
            for (int y = floorY + 1; y <= floorY + 4; y++) {
                level.setBlock(new BlockPos(westWallX, y, z), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
        }
        for (int x = playerPos.getX() + 1; x <= playerPos.getX() + 7; x++) {
            for (int z = playerPos.getZ() + 3; z <= playerPos.getZ() + 8; z++) {
                level.setBlock(new BlockPos(x, overhangY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        int[][] columns = {
                {playerPos.getX() + 1, playerPos.getZ() + 3},
                {playerPos.getX() + 7, playerPos.getZ() + 3},
                {playerPos.getX() + 1, playerPos.getZ() + 8},
                {playerPos.getX() + 7, playerPos.getZ() + 8},
                {playerPos.getX() + 5, playerPos.getZ() - 7},
                {playerPos.getX() + 9, playerPos.getZ() - 2}
        };
        for (int[] column : columns) {
            for (int y = floorY + 1; y <= floorY + 4; y++) {
                level.setBlock(new BlockPos(column[0], y, column[1]), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
        }
        for (int x = playerPos.getX() + 2; x <= playerPos.getX() + 8; x++) {
            for (int y = floorY + 1; y <= floorY + 3; y++) {
                level.setBlock(new BlockPos(x, y, northWallZ), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
        }

        BlockPos[] lights = {
                new BlockPos(playerPos.getX() - 3, floorY + 1, playerPos.getZ() - 6),
                new BlockPos(playerPos.getX() + 4, overhangY, playerPos.getZ() + 5),
                new BlockPos(eastWallX, floorY + 4, playerPos.getZ() - 4),
                new BlockPos(westWallX, floorY + 3, playerPos.getZ() + 7),
                new BlockPos(playerPos.getX() + 11, floorY + 1, playerPos.getZ() + 2)
        };
        for (BlockPos light : lights) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.setInvulnerable(false);
        player.teleportTo(playerPos.getX() + 0.5D, floorY + 1.0D, playerPos.getZ() + 0.5D);
        player.setYRot(-90.0F);
        player.setXRot(-8.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        boolean[] pathStarted = new boolean[5];
        pathStarted[0] = spawnCombatSpider(level, spiders, new BlockPos(eastWallX - 1, floorY + 2, playerPos.getZ() - 4),
                Direction.EAST, -90.0F, player, 48.0D, 0.16D, 0.35D);
        pathStarted[1] = spawnCombatSpider(level, spiders, new BlockPos(playerPos.getX() + 4, overhangY - 1, playerPos.getZ() + 5),
                Direction.UP, 0.0F, player, 48.0D, 0.16D, 0.35D);
        pathStarted[2] = spawnCombatSpider(level, spiders, new BlockPos(westWallX + 1, floorY + 2, playerPos.getZ() + 6),
                Direction.WEST, 90.0F, player, 48.0D, 0.16D, 0.35D);
        pathStarted[3] = spawnCombatSpider(level, spiders, new BlockPos(playerPos.getX() + 5, floorY + 1, playerPos.getZ() - 6),
                Direction.DOWN, -90.0F, player, 48.0D, 0.18D, 0.35D);
        pathStarted[4] = spawnCombatSpider(level, spiders, new BlockPos(playerPos.getX() + 7, floorY + 2, northWallZ + 1),
                Direction.NORTH, 180.0F, player, 48.0D, 0.16D, 0.35D);

        if (spiders.size() != pathStarted.length) {
            spiders.forEach(GroundSpiderEntity::discard);
            source.sendFailure(Component.literal("spiders_player_pressure_field_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        source.sendSuccess(Component.literal("spiders_player_pressure_field_visual_test created=true"
                + " spiders=" + spiders.size()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " east_wall_x=" + eastWallX
                + " west_wall_x=" + westWallX
                + " north_wall_z=" + northWallZ
                + " overhang_y=" + overhangY
                + " path0_started=" + pathStarted[0]
                + " path1_started=" + pathStarted[1]
                + " path2_started=" + pathStarted[2]
                + " path3_started=" + pathStarted[3]
                + " path4_started=" + pathStarted[4]
                + " all_paths_started=" + (pathStarted[0] && pathStarted[1] && pathStarted[2]
                        && pathStarted[3] && pathStarted[4])), false);
        return 1;
    }

    private static int playerPressureNaturalVisualTest(CommandContext<CommandSourceStack> context, int siteIndex)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos playerOrigin = player.blockPosition();
        BlockPos current = level.getSharedSpawnPos();
        discardGroundSpidersNear(level, playerOrigin, 512.0D);
        discardGroundSpidersNear(level, current, 512.0D);
        clearVisualTestEntities(level, player);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(NATURAL_PRESSURE_PLAYER_FOOD_LEVEL);
        player.getFoodData().setSaturation(0.0F);
        int[] offset = NATURAL_PRESSURE_SITE_OFFSETS[siteIndex];
        List<BlockPos> starts = findNaturalFloors(level, current.getX() + offset[0], current.getZ() + offset[1],
                24, NATURAL_PRESSURE_MAX_STARTS);
        if (starts.isEmpty()) {
            source.sendFailure(Component.literal("spiders_player_pressure_natural_visual_test created=false"
                    + " reason=no_safe_natural_start site=" + siteIndex
                    + " offset=" + offset[0] + "," + offset[1]
                    + " starts_examined=0"
                    + " blocks_modified=false"));
            return 0;
        }

        level.getServer().setDifficulty(Difficulty.NORMAL, true);

        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(NATURAL_PRESSURE_PLAYER_FOOD_LEVEL);
        player.getFoodData().setSaturation(0.0F);
        player.setInvulnerable(false);
        GroundSpiderEntity probe = EntityRegistry.GROUND_SPIDER.get().create(level);
        if (probe == null) {
            source.sendFailure(Component.literal("spiders_player_pressure_natural_visual_test created=false"
                    + " reason=probe_create_failed site=" + siteIndex
                    + " offset=" + offset[0] + "," + offset[1]
                    + " starts_examined=0"
                    + " blocks_modified=false"));
            return 0;
        }

        int startsExamined = 0;
        int bestStarted = 0;
        int bestCandidates = 0;
        int bestAttempts = 0;
        int bestNonFloorStarted = 0;
        BlockPos bestStart = starts.get(0);
        NaturalPressureSpawnResult accepted = null;
        BlockPos acceptedStart = null;
        for (BlockPos start : starts) {
            startsExamined++;
            player.teleportTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D);
            player.setYRot(-90.0F);
            player.setXRot(-7.0F);
            player.yHeadRot = -90.0F;
            player.yBodyRot = -90.0F;

            NaturalPressureSpawnResult result = tryNaturalPressureSpawns(level, probe, start, player);
            if (result.started() > bestStarted
                    || (result.started() == bestStarted && result.nonFloorStarted() > bestNonFloorStarted)) {
                bestStarted = result.started();
                bestCandidates = result.candidates();
                bestAttempts = result.attempts();
                bestNonFloorStarted = result.nonFloorStarted();
                bestStart = start;
            }

            if (result.started() >= 5) {
                accepted = result;
                acceptedStart = start;
                break;
            }
            result.spiders().forEach(GroundSpiderEntity::discard);
        }

        if (accepted == null) {
            source.sendFailure(Component.literal("spiders_player_pressure_natural_visual_test created=false"
                    + " reason=insufficient_pathing_spawns"
                    + " site=" + siteIndex
                    + " offset=" + offset[0] + "," + offset[1]
                    + " starts_examined=" + startsExamined
                    + " max_starts=" + NATURAL_PRESSURE_MAX_STARTS
                    + " started=" + bestStarted
                    + " candidates=" + bestCandidates
                    + " attempts=" + bestAttempts
                    + " max_attempts=" + NATURAL_PRESSURE_MAX_PATH_ATTEMPTS
                    + " non_floor_started=" + bestNonFloorStarted
                    + " best_origin=" + bestStart.getX() + "," + bestStart.getY() + "," + bestStart.getZ()
                    + " blocks_modified=false"));
            return 0;
        }

        source.sendSuccess(Component.literal("spiders_player_pressure_natural_visual_test created=true"
                + " spiders=" + accepted.spiders().size()
                + " site=" + siteIndex
                + " offset=" + offset[0] + "," + offset[1]
                + " starts_examined=" + startsExamined
                + " max_starts=" + NATURAL_PRESSURE_MAX_STARTS
                + " candidates=" + accepted.candidates()
                + " attempts=" + accepted.attempts()
                + " max_attempts=" + NATURAL_PRESSURE_MAX_PATH_ATTEMPTS
                + " non_floor_started=" + accepted.nonFloorStarted()
                + " player_health=" + format(player.getHealth())
                + " player=" + format(player.getX()) + "," + format(player.getY()) + "," + format(player.getZ())
                + " origin=" + acceptedStart.getX() + "," + acceptedStart.getY() + "," + acceptedStart.getZ()
                + " blocks_modified=false"
                + " path0_started=true"
                + " path1_started=true"
                + " path2_started=true"
                + " path3_started=true"
                + " path4_started=true"
                + " all_paths_started=true"), false);
        return 1;
    }

    private static int surfaceOrientationVisualTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayerOrException();

        BlockPos center = player.blockPosition();
        int floorY = center.getY() - 1;
        int ceilingY = floorY + 5;
        int westWallX = center.getX() - 6;
        int eastWallX = center.getX() + 6;
        int northWallZ = center.getZ() - 6;
        int southWallZ = center.getZ() + 6;

        clearVisualTestEntities(level, player);
        applyControlledVisualTestEnvironment(level);
        giveControlledVisualTestVision(player);

        for (int x = westWallX - 1; x <= eastWallX + 1; x++) {
            for (int y = floorY; y <= ceilingY; y++) {
                for (int z = northWallZ - 1; z <= southWallZ + 1; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        for (int x = westWallX; x <= eastWallX; x++) {
            for (int z = northWallZ; z <= southWallZ; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, ceilingY, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int y = floorY; y <= ceilingY; y++) {
            for (int z = northWallZ; z <= southWallZ; z++) {
                level.setBlock(new BlockPos(westWallX, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(eastWallX, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            for (int x = westWallX; x <= eastWallX; x++) {
                level.setBlock(new BlockPos(x, y, northWallZ), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, y, southWallZ), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        BlockPos[] lights = {
                new BlockPos(center.getX() - 3, ceilingY, center.getZ() - 3),
                new BlockPos(center.getX() + 3, ceilingY, center.getZ() - 3),
                new BlockPos(center.getX() - 3, ceilingY, center.getZ() + 3),
                new BlockPos(center.getX() + 3, ceilingY, center.getZ() + 3),
                new BlockPos(center.getX(), floorY, center.getZ())
        };
        for (BlockPos light : lights) {
            level.setBlock(light, Blocks.GLOWSTONE.defaultBlockState(), 3);
        }

        List<GroundSpiderEntity> spiders = new ArrayList<>();
        spawnSurfaceSpider(level, spiders, new BlockPos(center.getX(), floorY + 1, center.getZ() + 4),
                Direction.DOWN, 180.0F);
        spawnSurfaceSpider(level, spiders, new BlockPos(center.getX(), ceilingY - 1, center.getZ() - 4),
                Direction.UP, 0.0F);
        spawnSurfaceSpider(level, spiders, new BlockPos(center.getX(), floorY + 2, northWallZ + 1),
                Direction.NORTH, 180.0F);
        spawnSurfaceSpider(level, spiders, new BlockPos(center.getX(), floorY + 2, southWallZ - 1),
                Direction.SOUTH, 0.0F);
        spawnSurfaceSpider(level, spiders, new BlockPos(westWallX + 1, floorY + 2, center.getZ()),
                Direction.WEST, 90.0F);
        spawnSurfaceSpider(level, spiders, new BlockPos(eastWallX - 1, floorY + 2, center.getZ()),
                Direction.EAST, -90.0F);

        if (spiders.size() != Direction.values().length) {
            spiders.forEach(GroundSpiderEntity::discard);
            source.sendFailure(Component.literal("spiders_surface_orientation_visual_test created=false reason=spider_create_failed"
                    + " spiders=" + spiders.size()));
            return 0;
        }

        player.teleportTo(center.getX() + 0.5D, floorY + 1.0D, center.getZ() + 0.5D);
        player.setYRot(-90.0F);
        player.setXRot(0.0F);
        player.yHeadRot = -90.0F;
        player.yBodyRot = -90.0F;

        source.sendSuccess(Component.literal("spiders_surface_orientation_visual_test created=true"
                + " spiders=" + spiders.size()
                + " center=" + center.getX() + "," + (floorY + 1) + "," + center.getZ()
                + " floor_y=" + floorY
                + " ceiling_y=" + ceilingY
                + " west_wall_x=" + westWallX
                + " east_wall_x=" + eastWallX
                + " north_wall_z=" + northWallZ
                + " south_wall_z=" + southWallZ
                + " day_time=" + level.getDayTime()
                + " raining=" + level.isRaining()
                + " thundering=" + level.isThundering()
                + " daylight_cycle=" + level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)
                + " weather_cycle=" + level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)
                + " mob_spawning=" + level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                + " night_vision=true"), false);
        return 1;
    }

    private static void applyControlledVisualTestEnvironment(ServerLevel level) {
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.setDayTime(CONTROLLED_VISUAL_TEST_DAY_TIME);
        level.setWeatherParameters(0, CONTROLLED_VISUAL_TEST_CLEAR_WEATHER_TICKS, false, false);
    }

    private static void giveControlledVisualTestVision(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, 0, false, false, true));
    }

    private static List<IronGolem> findArenaTargets(ServerLevel level, Vec3 origin, double range) {
        List<IronGolem> targets = new ArrayList<>(level.getEntitiesOfClass(IronGolem.class,
                new net.minecraft.world.phys.AABB(origin, origin).inflate(range), IronGolem::isAlive));
        targets.sort(Comparator.comparingDouble(target -> target.distanceToSqr(origin)));
        return targets;
    }

    private static void clearVisualTestEntities(ServerLevel level, ServerPlayer player) {
        AABB cleanupBounds = player.getBoundingBox().inflate(VISUAL_TEST_ENTITY_CLEAR_RADIUS);
        level.getEntitiesOfClass(LivingEntity.class, cleanupBounds, entity -> !(entity instanceof ServerPlayer))
                .forEach(LivingEntity::discard);
        level.getEntitiesOfClass(ItemEntity.class, cleanupBounds)
                .forEach(ItemEntity::discard);
    }

    private static IronGolem spawnPinnedTarget(ServerLevel level, BlockPos airPos, Direction attachment, float health) {
        IronGolem target = EntityType.IRON_GOLEM.create(level);
        if (target == null) {
            return null;
        }

        Vec3 anchor = new Vec3(airPos.getX() + 0.5D, airPos.getY(), airPos.getZ() + 0.5D);
        target.setNoAi(true);
        target.setNoGravity(true);
        target.noPhysics = true;
        target.setPersistenceRequired();
        target.moveTo(anchor.x, anchor.y, anchor.z, yawFor(attachment), 0.0F);
        target.setHealth(health);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        level.addFreshEntity(target);
        target.setNoGravity(true);
        target.noPhysics = true;
        return target;
    }

    private static Sheep spawnPinnedSheepTarget(ServerLevel level, BlockPos airPos, Direction attachment) {
        Sheep target = EntityType.SHEEP.create(level);
        if (target == null) {
            return null;
        }

        Vec3 anchor = new Vec3(airPos.getX() + 0.5D, airPos.getY(), airPos.getZ() + 0.5D);
        target.setNoAi(true);
        target.setNoGravity(true);
        target.noPhysics = true;
        target.setPersistenceRequired();
        target.moveTo(anchor.x, anchor.y, anchor.z, yawFor(attachment), 0.0F);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        level.addFreshEntity(target);
        target.setNoGravity(true);
        target.noPhysics = true;
        return target;
    }

    private static boolean spawnCombatSpider(ServerLevel level, List<GroundSpiderEntity> spiders, BlockPos airPos,
            Direction attachment, float yaw, LivingEntity target, double followRange, double movementSpeed) {
        return spawnCombatSpider(level, spiders, airPos, attachment, yaw, target, followRange, movementSpeed, Double.NaN);
    }

    private static boolean spawnStationaryWallPressureSpider(ServerLevel level, List<GroundSpiderEntity> spiders,
            BlockPos airPos, Direction attachment, LivingEntity target, double followRange) {
        int before = spiders.size();
        boolean started = spawnCombatSpider(level, spiders, airPos, attachment, yawFor(attachment), target,
                followRange, 0.0D, CONTROLLED_WALL_SENTRY_ATTACK_DAMAGE);
        if (spiders.size() > before) {
            GroundSpiderEntity spider = spiders.get(spiders.size() - 1);
            spider.getNavigation().stop();
            spider.setAttachmentDirection(attachment);
            spider.setNoGravity(true);
            spider.setTarget(target);
            return true;
        }
        return started;
    }

    private static boolean spawnCombatSpider(ServerLevel level, List<GroundSpiderEntity> spiders, BlockPos airPos,
            Direction attachment, float yaw, LivingEntity target, double followRange, double movementSpeed, double attackDamage) {
        GroundSpiderEntity spider = EntityRegistry.GROUND_SPIDER.get().create(level);
        if (spider == null) {
            return false;
        }

        Vec3 anchor = AttachmentHelper.anchorFor(spider, airPos, attachment);
        spider.setAttachmentDirection(attachment);
        spider.setNoGravity(attachment != Direction.DOWN);
        spider.setPersistenceRequired();
        if (spider.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            spider.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(followRange);
        }
        if (spider.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(movementSpeed);
        }
        if (!Double.isNaN(attackDamage) && spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attackDamage);
        }
        spider.moveTo(anchor.x, anchor.y, anchor.z, yaw, 0.0F);
        level.addFreshEntity(spider);
        spider.moveTo(anchor.x, anchor.y, anchor.z, yaw, 0.0F);
        spider.setAttachmentDirection(attachment);
        spider.setNoGravity(attachment != Direction.DOWN);
        spider.setTarget(target);
        spider.setLastHurtByMob(target);
        spiders.add(spider);
        return spider.getNavigation().moveTo(target, 1.0D);
    }

    private static boolean addSingleThreadWebWalker(ServerLevel level, List<GroundSpiderEntity> spiders,
            BlockPos webStart, BlockPos webEnd, LivingEntity lookTarget, boolean reverse) {
        boolean placed = SingleThreadWebBlock.placeLine(level, webStart, webEnd, BlockRegistry.SINGLE_THREAD_WEB.get());
        if (!placed) {
            return false;
        }

        GroundSpiderEntity spider = EntityRegistry.GROUND_SPIDER.get().create(level);
        if (spider == null) {
            return false;
        }

        BlockPos airStart = (reverse ? webEnd : webStart).above();
        BlockPos airEnd = (reverse ? webStart : webEnd).above();
        Vec3 anchor = AttachmentHelper.anchorFor(spider, airStart, Direction.DOWN);
        float yaw = yawForDelta(webEnd.getX() - webStart.getX(), webEnd.getZ() - webStart.getZ());
        spider.setAttachmentDirection(Direction.DOWN);
        spider.setNoGravity(false);
        spider.setNoAi(false);
        spider.setInvulnerable(true);
        spider.setGlowingTag(true);
        spider.addEffect(new MobEffectInstance(MobEffects.GLOWING, CONTROLLED_VISUAL_TEST_NIGHT_VISION_TICKS, 0, false, false));
        spider.moveTo(anchor.x, anchor.y, anchor.z, yaw, 0.0F);
        spider.yHeadRot = yaw;
        spider.yBodyRot = yaw;
        level.addFreshEntity(spider);
        if (lookTarget != null) {
            spider.setTarget(lookTarget);
            spider.setLastHurtByMob(lookTarget);
        }
        spider.startForcedPath(List.of(airStart, airEnd), 0.035D);
        spiders.add(spider);
        return true;
    }

    private static boolean spawnCombatSpiderIfPathStarts(ServerLevel level, List<GroundSpiderEntity> spiders,
            BlockPos airPos, Direction attachment, float yaw, LivingEntity target, double followRange,
            double movementSpeed, double attackDamage) {
        int before = spiders.size();
        boolean started = spawnCombatSpider(level, spiders, airPos, attachment, yaw, target,
                followRange, movementSpeed, attackDamage);
        if (!started && spiders.size() > before) {
            GroundSpiderEntity failed = spiders.remove(spiders.size() - 1);
            failed.discard();
        }
        return started;
    }

    private static NaturalPressureSpawnResult tryNaturalPressureSpawns(ServerLevel level, GroundSpiderEntity probe,
            BlockPos start, ServerPlayer player) {
        List<NaturalSpawn> candidates = findNaturalSpawnCandidates(level, probe, start, 20);
        List<GroundSpiderEntity> spiders = new ArrayList<>();
        int nonFloorStarted = 0;
        int attempts = 0;
        for (NaturalSpawn candidate : candidates) {
            if (spiders.size() >= 5) {
                break;
            }
            if (attempts >= NATURAL_PRESSURE_MAX_PATH_ATTEMPTS) {
                break;
            }
            attempts++;
            boolean started = spawnCombatSpiderIfPathStarts(level, spiders, candidate.airPos(), candidate.attachment(),
                    yawFor(candidate.attachment()), player, 48.0D,
                    candidate.attachment() == Direction.DOWN ? 0.18D : 0.16D, NATURAL_PRESSURE_VISUAL_ATTACK_DAMAGE);
            if (started && candidate.attachment() != Direction.DOWN) {
                nonFloorStarted++;
            }
        }
        return new NaturalPressureSpawnResult(spiders, candidates.size(), attempts, nonFloorStarted);
    }

    private static List<BlockPos> findNaturalFloors(ServerLevel level, int centerX, int centerZ, int radius,
            int maxStarts) {
        List<BlockPos> starts = new ArrayList<>();
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    BlockPos candidate = naturalFloorAt(level, centerX + dx, centerZ + dz);
                    if (candidate == null || isTooCloseToExistingStart(candidate, starts)) {
                        continue;
                    }
                    starts.add(candidate);
                    if (starts.size() >= maxStarts) {
                        return starts;
                    }
                }
            }
        }
        return starts;
    }

    private static boolean isTooCloseToExistingStart(BlockPos candidate, List<BlockPos> starts) {
        for (BlockPos start : starts) {
            if (candidate.distSqr(start) < 36.0D) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos naturalFloorAt(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 3) {
            return null;
        }

        BlockPos air = new BlockPos(x, y, z);
        if (!isNaturalFloorCell(level, air) || !isStableNaturalPlayerStart(level, air)) {
            return null;
        }
        return air;
    }

    private static boolean isStableNaturalPlayerStart(ServerLevel level, BlockPos center) {
        if (!level.canSeeSky(center.above(2))) {
            return false;
        }

        int stableCells = 0;
        int cardinalCells = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!isNaturalFloorCell(level, center.offset(dx, 0, dz))) {
                    continue;
                }
                stableCells++;
                if (Math.abs(dx) + Math.abs(dz) == 1) {
                    cardinalCells++;
                }
            }
        }
        if (stableCells < NATURAL_PRESSURE_MIN_STABLE_START_CELLS || cardinalCells != 4) {
            return false;
        }
        return hasOpenNaturalFightingSpace(level, center)
                && hasLevelNaturalFightingFloor(level, center)
                && hasNoDangerousDropsNear(level, center);
    }

    private static boolean hasOpenNaturalFightingSpace(ServerLevel level, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 4; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    var state = level.getBlockState(pos);
                    if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                        return false;
                    }
                    if (!state.getCollisionShape(level, pos).isEmpty()) {
                        return false;
                    }
                    if (!level.getFluidState(pos).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean hasLevelNaturalFightingFloor(ServerLevel level, BlockPos center) {
        for (int dx = -NATURAL_PRESSURE_CLEAR_FLOOR_RADIUS; dx <= NATURAL_PRESSURE_CLEAR_FLOOR_RADIUS; dx++) {
            for (int dz = -NATURAL_PRESSURE_CLEAR_FLOOR_RADIUS; dz <= NATURAL_PRESSURE_CLEAR_FLOOR_RADIUS; dz++) {
                if ((dx * dx) + (dz * dz) > NATURAL_PRESSURE_CLEAR_FLOOR_RADIUS * NATURAL_PRESSURE_CLEAR_FLOOR_RADIUS) {
                    continue;
                }

                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(y - center.getY()) > 1) {
                    return false;
                }
                if (!isNaturalFloorCell(level, new BlockPos(x, y, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasNoDangerousDropsNear(ServerLevel level, BlockPos center) {
        for (int dx = -NATURAL_PRESSURE_DROP_GUARD_RADIUS; dx <= NATURAL_PRESSURE_DROP_GUARD_RADIUS; dx++) {
            for (int dz = -NATURAL_PRESSURE_DROP_GUARD_RADIUS; dz <= NATURAL_PRESSURE_DROP_GUARD_RADIUS; dz++) {
                if ((dx * dx) + (dz * dz) > NATURAL_PRESSURE_DROP_GUARD_RADIUS * NATURAL_PRESSURE_DROP_GUARD_RADIUS) {
                    continue;
                }

                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (center.getY() - y > NATURAL_PRESSURE_MAX_SAFE_DROP) {
                    return false;
                }

                BlockPos sameLevelAir = center.offset(dx, 0, dz);
                if (level.getBlockState(sameLevelAir).isAir()
                        && level.getBlockState(sameLevelAir.above()).isAir()
                        && !hasGroundWithinSafeDrop(level, sameLevelAir)) {
                    return false;
                }
                if (!level.getFluidState(sameLevelAir).isEmpty() || !level.getFluidState(sameLevelAir.below()).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasGroundWithinSafeDrop(ServerLevel level, BlockPos air) {
        for (int dy = 1; dy <= NATURAL_PRESSURE_MAX_SAFE_DROP; dy++) {
            BlockPos support = air.offset(0, -dy, 0);
            if (!level.getBlockState(support).getCollisionShape(level, support).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNaturalFloorCell(ServerLevel level, BlockPos air) {
        BlockPos support = air.below();
        if (level.getBlockState(support).getCollisionShape(level, support).isEmpty()) {
            return false;
        }
        if (!isNaturalBodySpaceClear(level, air)) {
            return false;
        }
        BlockPos head = air.above();
        if (!isNaturalBodySpaceClear(level, head)) {
            return false;
        }
        return true;
    }

    private static boolean isNaturalBodySpaceClear(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }

    private static List<NaturalSpawn> findNaturalSpawnCandidates(ServerLevel level, GroundSpiderEntity probe,
            BlockPos origin, int radius) {
        List<NaturalSpawn> nonFloor = new ArrayList<>();
        List<NaturalSpawn> floor = new ArrayList<>();
        Direction[] directions = {
                Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN
        };

        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int minY = Math.max(level.getMinBuildHeight() + 1, surfaceY - 6);
                int maxY = Math.min(level.getMaxBuildHeight() - 2, surfaceY + 8);
                for (int y = minY; y <= maxY; y++) {
                    BlockPos air = new BlockPos(x, y, z);
                    if (!isNaturalBodySpaceClear(level, air)) {
                        continue;
                    }
                    double distanceSqr = air.distSqr(origin);
                    if (distanceSqr < 24.0D || distanceSqr > (radius * radius)) {
                        continue;
                    }
                    for (Direction direction : directions) {
                        BlockPos back = air.relative(direction.getOpposite());
                        if (!AttachmentHelper.hasSupport(level, air, direction)
                                || !isNaturalBodySpaceClear(level, back)
                                || !AttachmentHelper.aabbFitsOnSurface(level, probe, air, direction)) {
                            continue;
                        }
                        NaturalSpawn spawn = new NaturalSpawn(air, direction);
                        if (direction == Direction.DOWN) {
                            floor.add(spawn);
                        } else {
                            nonFloor.add(spawn);
                        }
                    }
                }
            }
        }

        Comparator<NaturalSpawn> byDistance = Comparator.comparingDouble(spawn -> spawn.airPos().distSqr(origin));
        nonFloor.sort(byDistance);
        floor.sort(byDistance);

        List<NaturalSpawn> result = new ArrayList<>();
        result.addAll(nonFloor);
        result.addAll(floor);
        return result;
    }

    private static List<GroundSpiderEntity> findSurfaceSpiders(ServerLevel level, Vec3 origin, double range) {
        List<GroundSpiderEntity> spiders = new ArrayList<>(level.getEntitiesOfClass(GroundSpiderEntity.class,
                new net.minecraft.world.phys.AABB(origin, origin).inflate(range), GroundSpiderEntity::isAlive));
        spiders.sort(Comparator
                .comparing((GroundSpiderEntity spider) -> spider.getAttachmentDirection().ordinal())
                .thenComparingDouble(spider -> spider.distanceToSqr(origin)));
        return spiders;
    }

    private static float yawFor(Direction direction) {
        switch (direction) {
            case EAST:
                return -90.0F;
            case WEST:
                return 90.0F;
            case NORTH:
                return 180.0F;
            default:
                return 0.0F;
        }
    }

    private static float yawForDelta(int dx, int dz) {
        return (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
    }

    private static void spawnSurfaceSpider(ServerLevel level, List<GroundSpiderEntity> spiders,
            BlockPos airPos, Direction attachment, float yaw) {
        GroundSpiderEntity spider = EntityRegistry.GROUND_SPIDER.get().create(level);
        if (spider == null) {
            return;
        }

        Vec3 anchor = AttachmentHelper.anchorFor(spider, airPos, attachment);
        spider.setAttachmentDirection(attachment);
        spider.setNoGravity(true);
        spider.setNoAi(true);
        spider.setInvulnerable(true);
        spider.noPhysics = true;
        spider.setPersistenceRequired();
        spider.moveTo(anchor.x, anchor.y, anchor.z, yaw, 0.0F);
        level.addFreshEntity(spider);
        spider.moveTo(anchor.x, anchor.y, anchor.z, yaw, 0.0F);
        spider.setAttachmentDirection(attachment);
        spider.setNoGravity(true);
        spider.setDeltaMovement(Vec3.ZERO);
        spider.noPhysics = true;
        spiders.add(spider);
    }

    private static int discardGroundSpidersNear(ServerLevel level, BlockPos center, double radius) {
        AABB bounds = new AABB(center).inflate(radius);
        List<GroundSpiderEntity> existingSpiders = level.getEntitiesOfClass(GroundSpiderEntity.class, bounds);
        existingSpiders.forEach(GroundSpiderEntity::discard);
        return existingSpiders.size();
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static String vector(Vec3 vector) {
        return format(vector.x) + "," + format(vector.y) + "," + format(vector.z);
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "none";
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }
}
