package com.horrormods.spiders.gametest;

import com.horrormods.spiders.Spiders;
import com.horrormods.spiders.command.SpiderAiAudit;
import com.horrormods.spiders.block.SingleThreadWebBlock;
import com.horrormods.spiders.block.entity.SingleThreadWebBlockEntity;
import com.horrormods.spiders.entity.GroundSpiderEntity;
import com.horrormods.spiders.entity.WebShotEntity;
import com.horrormods.spiders.entity.ai.ClimberPathNavigator;
import com.horrormods.spiders.entity.util.AttachmentHelper;
import com.horrormods.spiders.registry.BlockRegistry;
import com.horrormods.spiders.registry.EntityRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(Spiders.ModID)
@PrefixGameTestTemplate(false)
public final class GroundSpiderGameTests {
    private GroundSpiderGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void groundSpiderSpawnsWithClimberRuntime(GameTestHelper helper) {
        BlockPos spawnPos = new BlockPos(1, 2, 1);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spawnPos);

        helper.runAfterDelay(5, () -> {
            helper.assertEntityPresent(EntityRegistry.GROUND_SPIDER.get(), spawnPos, 2.0D);
            helper.assertEntityProperty(spider, entity -> entity.getAttachmentDirection() == Direction.DOWN,
                    "Ground spider should start attached to the floor");
            helper.assertEntityProperty(spider,
                    entity -> entity.getNavigation().getClass().getSimpleName().equals("ClimberPathNavigator"),
                    "Ground spider should use climber navigation");
            helper.assertEntityProperty(spider, entity -> entity.getAttributeValue(Attributes.MAX_HEALTH) == 16.0D,
                    "Ground spider max health should match its registered attributes");
            succeedAndDiscard(helper, spider);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void auditFindsNearestGroundSpider(GameTestHelper helper) {
        BlockPos spawnPos = new BlockPos(1, 2, 1);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spawnPos);

        helper.runAfterDelay(2, () -> {
            Vec3 origin = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
            GroundSpiderEntity found = SpiderAiAudit.findNearest(helper.getLevel(), origin, 8.0D).orElse(null);
            if (found == null) {
                failAndDiscard(helper, "Expected audit to find spawned ground spider", spider);
                return;
            }
            helper.assertEntityProperty(found, entity -> entity == spider,
                    "Audit should return the nearest spawned ground spider");
            helper.assertEntityProperty(found, entity -> SpiderAiAudit.describe(entity).contains("navigation=ClimberPathNavigator"),
                    "Audit description should include navigation state");
            succeedAndDiscard(helper, spider);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void forcePathCommandMovesNearestGroundSpider(GameTestHelper helper) {
        BlockPos spawnPos = new BlockPos(1, 2, 1);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spawnPos);
        spider.setNoAi(true);

        helper.runAfterDelay(2, () -> {
            double startX = spider.getX();
            BlockPos target = spider.blockPosition().east(2);
            CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                    .withLevel(helper.getLevel())
                    .withPosition(spider.position())
                    .withPermission(2)
                    .withSuppressedOutput();

            int result = helper.getLevel().getServer().getCommands().performPrefixedCommand(source,
                    "spiders force_path_nearest "
                            + target.getX() + " " + target.getY() + " " + target.getZ() + " 0.25");
            helper.assertEntityProperty(spider, entity -> result == 1,
                    "Force-path command should return success");
            helper.assertEntityProperty(spider, GroundSpiderEntity::isFollowingForcedPath,
                    "Force-path command should put the spider on a forced path");

            helper.runAfterDelay(12, () -> {
                helper.assertEntityProperty(spider, entity -> entity.getX() > startX + 0.5D,
                        "Force-path command should move the spider toward the target");
                succeedAndDiscard(helper, spider);
            });
        });
    }

    @GameTest(template = "arena", timeoutTicks = 80, batch = "singleThreadWebs")
    public static void singleThreadWebLineBreaksOnPlayerFallAndUsesWebWalkAnimation(GameTestHelper helper) {
        helper.setBlock(0, 0, 0, Blocks.STONE);

        GroundSpiderEntity spinner = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), new BlockPos(0, 1, 0));
        BlockPos start = helper.absolutePos(new BlockPos(1, 0, 2));
        BlockPos middle = helper.absolutePos(new BlockPos(3, 0, 2));
        BlockPos end = helper.absolutePos(new BlockPos(5, 0, 2));
        boolean spun = spinner.spinSingleThreadWeb(start, end);

        GroundSpiderEntity walker = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), new BlockPos(3, 1, 2));
        AtomicBoolean sawWebWalkAnimation = new AtomicBoolean(false);

        helper.onEachTick(() -> {
            walker.setDeltaMovement(new Vec3(0.12D, 0.0D, 0.0D));
            walker.setZza(0.4F);
            if (walker.isOnSingleThreadWeb()
                    && "walk_forward_on_web".equals(walker.getAnimationAuditName())) {
                sawWebWalkAnimation.set(true);
            }
        });

        helper.runAfterDelay(8, () -> {
            boolean startPresent = helper.getLevel().getBlockState(start).is(BlockRegistry.SINGLE_THREAD_WEB.get());
            boolean middlePresent = helper.getLevel().getBlockState(middle).is(BlockRegistry.SINGLE_THREAD_WEB.get());
            boolean endPresent = helper.getLevel().getBlockState(end).is(BlockRegistry.SINGLE_THREAD_WEB.get());
            boolean axisX = helper.getLevel().getBlockState(middle).getValue(SingleThreadWebBlock.AXIS) == Direction.Axis.X;

            var fakePlayer = FakePlayerFactory.getMinecraft(helper.getLevel());
            var state = helper.getLevel().getBlockState(middle);
            state.getBlock().fallOn(helper.getLevel(), state, middle, fakePlayer, 3.0F);
            boolean brokeOnFall = helper.getLevel().isEmptyBlock(middle);
            boolean wholeStrandBrokeOnFall = helper.getLevel().isEmptyBlock(start)
                    && helper.getLevel().isEmptyBlock(middle)
                    && helper.getLevel().isEmptyBlock(end);

            if (!spun
                    || !startPresent
                    || !middlePresent
                    || !endPresent
                    || !axisX
                    || !sawWebWalkAnimation.get()
                    || !brokeOnFall
                    || !wholeStrandBrokeOnFall) {
                failAndDiscard(helper,
                        "Single-thread web should be spun as a line, drive web-walk animation, and break on player fall; spun="
                        + spun
                        + " startPresent=" + startPresent
                        + " middlePresent=" + middlePresent
                        + " endPresent=" + endPresent
                        + " axisX=" + axisX
                        + " sawWebWalkAnimation=" + sawWebWalkAnimation.get()
                        + " brokeOnFall=" + brokeOnFall
                        + " wholeStrandBrokeOnFall=" + wholeStrandBrokeOnFall
                        + " animation=" + walker.getAnimationAuditName()
                        + " walkerOnWeb=" + walker.isOnSingleThreadWeb(),
                        spinner, walker);
                return;
            }
            succeedAndDiscard(helper, spinner, walker);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100, batch = "singleThreadWebs")
    public static void singleThreadWebBodyStaysOnStrandWhileHeadTracksTarget(GameTestHelper helper) {
        GroundSpiderEntity spinner = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), new BlockPos(0, 2, 0));
        BlockPos start = helper.absolutePos(new BlockPos(1, 1, 2));
        BlockPos end = helper.absolutePos(new BlockPos(5, 1, 2));
        boolean spun = spinner.spinSingleThreadWeb(start, end);

        GroundSpiderEntity walker = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), new BlockPos(1, 2, 2));
        BlockPos airStart = helper.absolutePos(new BlockPos(1, 2, 2));
        BlockPos airEnd = helper.absolutePos(new BlockPos(5, 2, 2));
        Vec3 walkerAnchor = AttachmentHelper.anchorFor(walker, airStart, Direction.DOWN);
        walker.teleportTo(walkerAnchor.x, walkerAnchor.y, walkerAnchor.z);
        walker.setAttachmentDirection(Direction.DOWN);
        walker.setYRot(0.0F);
        walker.yBodyRot = 0.0F;
        walker.yHeadRot = 0.0F;

        var target = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(3, 2, 5));
        target.setNoGravity(true);
        target.setInvulnerable(true);
        walker.setTarget(target);
        walker.startForcedPath(List.of(airStart, airEnd), 0.05D);

        helper.onEachTick(() -> {
            if (target.isAlive()) {
                walker.setTarget(target);
            }
        });

        helper.runAfterDelay(12, () -> {
            double bodyError = walker.getWebTraversalBodyAlignmentErrorDegrees();
            double headYaw = Math.abs(walker.getWebTraversalHeadYawDegrees());
            double headPitch = Math.abs(walker.getWebTraversalHeadPitchDegrees());
            boolean bodyAligned = walker.isWebTraversalBodyAligned();
            boolean headUnlocked = headYaw >= 15.0D && headYaw <= 65.5D && headPitch <= 35.5D;
            boolean webWalkAnimation = "walk_forward_on_web".equals(walker.getAnimationAuditName());
            if (!spun || !walker.isOnSingleThreadWeb() || !bodyAligned || !headUnlocked
                    || !webWalkAnimation || walker.isWebTraversalReverseAnimation()) {
                failAndDiscard(helper,
                        "Single-thread web walker should keep body aligned to strand while head tracks target; spun="
                        + spun
                        + " onWeb=" + walker.isOnSingleThreadWeb()
                        + " bodyAligned=" + bodyAligned
                        + " bodyError=" + bodyError
                        + " headYaw=" + headYaw
                        + " headPitch=" + headPitch
                        + " animation=" + walker.getAnimationAuditName()
                        + " reverse=" + walker.isWebTraversalReverseAnimation(),
                        spinner, walker, target);
                return;
            }
            succeedAndDiscard(helper, spinner, walker, target);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100, batch = "singleThreadWebs")
    public static void singleThreadWebReverseMovementKeepsBodyOnStrandAndReversesAnimation(GameTestHelper helper) {
        GroundSpiderEntity spinner = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), new BlockPos(0, 2, 0));
        BlockPos start = helper.absolutePos(new BlockPos(1, 1, 2));
        BlockPos end = helper.absolutePos(new BlockPos(5, 1, 2));
        boolean spun = spinner.spinSingleThreadWeb(start, end);

        GroundSpiderEntity walker = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), new BlockPos(5, 2, 2));
        BlockPos airStart = helper.absolutePos(new BlockPos(1, 2, 2));
        BlockPos airEnd = helper.absolutePos(new BlockPos(5, 2, 2));
        Vec3 walkerAnchor = AttachmentHelper.anchorFor(walker, airEnd, Direction.DOWN);
        walker.teleportTo(walkerAnchor.x, walkerAnchor.y, walkerAnchor.z);
        walker.setAttachmentDirection(Direction.DOWN);
        walker.setYRot(0.0F);
        walker.yBodyRot = 0.0F;
        walker.yHeadRot = 0.0F;
        double startX = walker.getX();

        walker.startForcedPath(List.of(airEnd, airStart), 0.05D);

        helper.runAfterDelay(12, () -> {
            double bodyError = walker.getWebTraversalBodyAlignmentErrorDegrees();
            boolean bodyAligned = walker.isWebTraversalBodyAligned();
            boolean movedReverse = walker.getX() < startX - 0.25D;
            boolean webWalkAnimation = "walk_forward_on_web".equals(walker.getAnimationAuditName());
            if (!spun || !walker.isOnSingleThreadWeb() || !bodyAligned || !movedReverse
                    || !webWalkAnimation || !walker.isWebTraversalReverseAnimation()) {
                failAndDiscard(helper,
                        "Reverse single-thread web walker should move backward, keep body on strand, and reverse animation; spun="
                        + spun
                        + " onWeb=" + walker.isOnSingleThreadWeb()
                        + " bodyAligned=" + bodyAligned
                        + " bodyError=" + bodyError
                        + " startX=" + startX
                        + " currentX=" + walker.getX()
                        + " animation=" + walker.getAnimationAuditName()
                        + " reverse=" + walker.isWebTraversalReverseAnimation(),
                        spinner, walker);
                return;
            }
            succeedAndDiscard(helper, spinner, walker);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 80, batch = "singleThreadWebs")
    public static void angledSingleThreadWebStoresEndpointsAndBreaksWholeStrand(GameTestHelper helper) {
        GroundSpiderEntity spinner = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), new BlockPos(0, 1, 0));
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos end = helper.absolutePos(new BlockPos(5, 4, 3));
        boolean spun = spinner.spinSingleThreadWeb(start, end);
        List<BlockPos> strandPositions = SingleThreadWebBlock.positionsBetween(start, end);

        int webBlockCount = 0;
        int hostCount = 0;
        boolean endpointsStored = true;
        for (BlockPos pos : strandPositions) {
            if (helper.getLevel().getBlockState(pos).is(BlockRegistry.SINGLE_THREAD_WEB.get())) {
                webBlockCount++;
            }
            if (helper.getLevel().getBlockEntity(pos) instanceof SingleThreadWebBlockEntity strand) {
                endpointsStored &= strand.hasStrand()
                        && start.equals(strand.getFirstAnchor())
                        && end.equals(strand.getSecondAnchor());
                if (strand.isRenderHost()) {
                    hostCount++;
                }
            } else {
                endpointsStored = false;
            }
        }

        BlockPos breakPos = strandPositions.get(strandPositions.size() / 2);
        helper.getLevel().destroyBlock(breakPos, false);
        boolean wholeStrandBroke = true;
        for (BlockPos pos : strandPositions) {
            wholeStrandBroke &= helper.getLevel().isEmptyBlock(pos);
        }

        if (!spun
                || strandPositions.size() < 4
                || webBlockCount != strandPositions.size()
                || hostCount != 1
                || !endpointsStored
                || !wholeStrandBroke) {
            failAndDiscard(helper,
                    "Angled single-thread web should store endpoint metadata for one render host and break as one strand; spun="
                    + spun
                    + " strandPositions=" + strandPositions.size()
                    + " webBlockCount=" + webBlockCount
                    + " hostCount=" + hostCount
                    + " endpointsStored=" + endpointsStored
                    + " breakPos=" + breakPos
                    + " wholeStrandBroke=" + wholeStrandBroke,
                    spinner);
            return;
        }
        succeedAndDiscard(helper, spinner);
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "singleThreadWebs")
    public static void normalSolidSurfaceMovementDoesNotUseWebWalkAnimation(GameTestHelper helper) {
        fillFloor(helper, 6, 5);
        fillWall(helper, 0, 1, 4, 0, 4);

        GroundSpiderEntity floorSpider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), new BlockPos(3, 1, 2));
        GroundSpiderEntity wallSpider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), new BlockPos(1, 2, 2));
        placeAttached(helper, wallSpider, new BlockPos(1, 2, 2), Direction.WEST);

        helper.onEachTick(() -> {
            floorSpider.setDeltaMovement(new Vec3(0.12D, 0.0D, 0.0D));
            floorSpider.setZza(0.4F);
            wallSpider.setDeltaMovement(new Vec3(0.0D, 0.12D, 0.0D));
            wallSpider.setZza(0.4F);
        });

        helper.runAfterDelay(8, () -> {
            boolean floorUsesNormalWalk = "walk_forward".equals(floorSpider.getAnimationAuditName());
            boolean wallUsesNormalWalk = "walk_forward".equals(wallSpider.getAnimationAuditName());
            if (!floorUsesNormalWalk || !wallUsesNormalWalk) {
                failAndDiscard(helper,
                        "Normal floor/wall movement should not use tight-rope web walk animation; floorAnimation="
                        + floorSpider.getAnimationAuditName()
                        + " wallAnimation=" + wallSpider.getAnimationAuditName()
                        + " wallAttachment=" + wallSpider.getAttachmentDirection(),
                        floorSpider, wallSpider);
                return;
            }
            succeedAndDiscard(helper, floorSpider, wallSpider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200, batch = "naturalPursuitBasic")
    public static void naturalTargetPursuitMovesWithoutForcedPath(GameTestHelper helper) {
        fillFloor(helper, 9, 5);

        BlockPos spiderPos = new BlockPos(1, 1, 2);
        BlockPos targetPos = new BlockPos(7, 1, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 7.5D);
        var target = helper.spawnWithNoFreeWill(EntityType.SHEEP, targetPos);
        double startDistance = spider.distanceToSqr(target);

        spider.setTarget(target);
        spider.getNavigation().moveTo(target, 1.0D);
        AtomicBoolean usedAssignedTarget = new AtomicBoolean(spider.getTarget() == target);
        double[] bestDistance = { startDistance };
        helper.onEachTick(() -> {
            if (target.isAlive() && spider.getTarget() != target) {
                spider.setTarget(target);
            }
            if (spider.getTarget() == target) {
                usedAssignedTarget.set(true);
            }
            bestDistance[0] = Math.min(bestDistance[0], spider.distanceToSqr(target));
        });

        helper.runAfterDelay(100, () -> {
            double endDistance = spider.distanceToSqr(target);
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Natural pursuit should not use forced-path mode");
            helper.assertEntityProperty(spider, entity -> usedAssignedTarget.get(),
                    "Ground spider should use the assigned target during pursuit");
            if (bestDistance[0] >= startDistance - 4.0D) {
                failAndDiscard(helper,
                        "Ground spider should naturally move closer to its target; startDistance="
                        + startDistance
                        + " bestDistance=" + bestDistance[0]
                        + " endDistance=" + endDistance
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " navDone=" + spider.getNavigation().isDone()
                        + " path=" + describePath(spider.getNavigation().getPath()),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 240, batch = "sprintBurst")
    public static void sprintBurstAlternatesStalkPauseAndFastClosure(GameTestHelper helper) {
        fillFloor(helper, 10, 5);

        BlockPos spiderPos = new BlockPos(1, 1, 2);
        BlockPos targetPos = new BlockPos(8, 1, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 10.0D);
        var target = helper.spawnWithNoFreeWill(EntityType.SHEEP, targetPos);
        target.setInvulnerable(true);
        target.setNoGravity(true);
        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));

        double startDistance = spider.distanceToSqr(target);
        double[] bestBurstDistance = { startDistance };
        double[] maxStalkStep = { 0.0D };
        double[] maxBurstStep = { 0.0D };
        Vec3[] previousPos = { spider.position() };
        AtomicBoolean sawStalk = new AtomicBoolean(false);
        AtomicBoolean sawBurst = new AtomicBoolean(false);
        AtomicBoolean sawBurstAfterStalk = new AtomicBoolean(false);

        spider.setTarget(target);
        spider.getNavigation().moveTo(target, 1.0D);

        helper.onEachTick(() -> {
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
            }
            if (spider.getTarget() != target) {
                spider.setTarget(target);
            }

            double step = Math.sqrt(spider.position().distanceToSqr(previousPos[0]));
            previousPos[0] = spider.position();

            if (spider.isStalkingPause()) {
                sawStalk.set(true);
                maxStalkStep[0] = Math.max(maxStalkStep[0], horizontal(spider.getDeltaMovement()).length());
            }
            if (spider.isSprintBurstActive()) {
                sawBurst.set(true);
                if (sawStalk.get()) {
                    sawBurstAfterStalk.set(true);
                }
                maxBurstStep[0] = Math.max(maxBurstStep[0], step);
                bestBurstDistance[0] = Math.min(bestBurstDistance[0], spider.distanceToSqr(target));
            }
        });

        helper.runAfterDelay(190, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Sprint burst pacing should remain natural AI movement, not forced-path mode");
            if (!sawStalk.get() || !sawBurst.get() || !sawBurstAfterStalk.get()
                    || maxBurstStep[0] <= Math.max(0.08D, maxStalkStep[0] * 2.0D)
                    || bestBurstDistance[0] >= startDistance - 6.0D) {
                failAndDiscard(helper,
                        "Ground spider should visibly pause/stalk and then close distance in a faster sprint burst; sawStalk="
                        + sawStalk.get()
                        + " sawBurst=" + sawBurst.get()
                        + " sawBurstAfterStalk=" + sawBurstAfterStalk.get()
                        + " maxStalkStep=" + maxStalkStep[0]
                        + " maxBurstStep=" + maxBurstStep[0]
                        + " startDistance=" + startDistance
                        + " bestBurstDistance=" + bestBurstDistance[0]
                        + " state=" + spider.getCombatPacingStateName()
                        + " pacingTicks=" + spider.getCombatPacingTicks()
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " navDone=" + spider.getNavigation().isDone(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 600, batch = "zzzzzzzzPreyInteraction")
    public static void preyInteractionWebsAndGuardsKillSite(GameTestHelper helper) {
        fillFloor(helper, 12, 8);

        BlockPos spiderPos = new BlockPos(3, 1, 3);
        BlockPos preyPos = new BlockPos(6, 1, 3);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 18.0D);
        if (spider.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.16D);
        }
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6.0D);
        }

        var prey = helper.spawnWithNoFreeWill(EntityType.SHEEP, preyPos);
        prey.setNoGravity(true);
        prey.setHealth(2.0F);
        prey.addTag(GroundSpiderEntity.PREY_INTERACTION_TEST_TARGET_TAG);
        if (prey.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            prey.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        Vec3 preyAnchor = helper.absoluteVec(new Vec3(
                preyPos.getX() + 0.5D,
                preyPos.getY(),
                preyPos.getZ() + 0.5D));

        spider.setTarget(prey);

        AtomicBoolean sawPreyKilled = new AtomicBoolean(false);
        AtomicBoolean sawPreyInteraction = new AtomicBoolean(false);
        AtomicBoolean sawWebbing = new AtomicBoolean(false);
        AtomicBoolean sawGuarding = new AtomicBoolean(false);
        AtomicBoolean sawPreyAnchor = new AtomicBoolean(false);
        AtomicBoolean sawGuardAnchor = new AtomicBoolean(false);
        AtomicBoolean sawPathStarted = new AtomicBoolean(false);
        AtomicBoolean sawReachedGuard = new AtomicBoolean(false);
        AtomicBoolean sawHeldGuard = new AtomicBoolean(false);
        AtomicBoolean sawPlacedWeb = new AtomicBoolean(false);
        AtomicBoolean sawTargetKilledFlag = new AtomicBoolean(false);
        AtomicBoolean sawFacingPreyArea = new AtomicBoolean(false);
        AtomicBoolean sawCooldown = new AtomicBoolean(false);
        AtomicBoolean usedForcedPath = new AtomicBoolean(false);
        AtomicBoolean sawUnexpectedAttack = new AtomicBoolean(false);
        AtomicReference<BlockPos> lastPreyAnchor = new AtomicReference<>();
        AtomicReference<BlockPos> lastGuardAnchor = new AtomicReference<>();
        int[] firstPreyInteractionTick = { -1 };
        int[] firstPreyInteractionEntityTick = { -1 };
        int[] firstGuardingTick = { -1 };
        int[] firstGuardingEntityTick = { -1 };
        int[] firstCooldownTick = { -1 };
        int[] firstCooldownEntityTick = { -1 };
        int[] lastObservedEntityTick = { spider.tickCount };
        int[] ticks = { 0 };
        int[] maxWebCount = { 0 };
        double[] maxGuardReduced = { 0.0D };
        AtomicBoolean completed = new AtomicBoolean(false);

        helper.onEachTick(() -> {
            ticks[0]++;
            lastObservedEntityTick[0] = spider.tickCount;
            if (prey.isAlive()) {
                prey.setDeltaMovement(Vec3.ZERO);
                prey.setPos(preyAnchor.x, preyAnchor.y, preyAnchor.z);
                prey.setYRot(-90.0F);
                prey.setXRot(0.0F);
                prey.yHeadRot = -90.0F;
                prey.yBodyRot = -90.0F;
                if (spider.getTarget() != prey) {
                    spider.setTarget(prey);
                }
            } else {
                sawPreyKilled.set(true);
            }

            if (spider.isFollowingForcedPath()) {
                usedForcedPath.set(true);
            }
            if (!"none".equals(spider.getDropAttackPhaseName())
                    || !"none".equals(spider.getWebShotPhaseName())
                    || !"none".equals(spider.getWebLowerPhaseName())
                    || !"none".equals(spider.getPouncePhaseName())
                    || !"none".equals(spider.getGrabPullPhaseName())
                    || !"none".equals(spider.getDragNestPhaseName())
                    || spider.isThreatDisplaying()
                    || spider.isLineOfSightStalking()
                    || spider.isDarknessPreferenceActive()
                    || spider.isWallPeeking()
                    || spider.isRetreatActive()
                    || spider.isFakeRetreatActive()
                    || spider.isEscapeCutting()
                    || spider.isPackCoordinating()) {
                sawUnexpectedAttack.set(true);
            }

            if (spider.isPreyInteracting()) {
                sawPreyInteraction.set(true);
                if (firstPreyInteractionTick[0] < 0) {
                    firstPreyInteractionTick[0] = ticks[0];
                    firstPreyInteractionEntityTick[0] = spider.tickCount;
                }
                if ("webbing".equals(spider.getPreyInteractionPhaseName())) {
                    sawWebbing.set(true);
                }
                if ("guarding".equals(spider.getPreyInteractionPhaseName())) {
                    sawGuarding.set(true);
                    if (firstGuardingTick[0] < 0) {
                        firstGuardingTick[0] = ticks[0];
                        firstGuardingEntityTick[0] = spider.tickCount;
                    }
                }
                if (spider.getPreyInteractionPreyAnchor() != null) {
                    sawPreyAnchor.set(true);
                    lastPreyAnchor.set(spider.getPreyInteractionPreyAnchor());
                }
                if (spider.getPreyInteractionGuardAnchor() != null) {
                    sawGuardAnchor.set(true);
                    lastGuardAnchor.set(spider.getPreyInteractionGuardAnchor());
                }
                if (spider.hasPreyInteractionPathStarted()) {
                    sawPathStarted.set(true);
                }
                if (spider.hasPreyInteractionReachedGuard()) {
                    sawReachedGuard.set(true);
                }
                if (spider.hasPreyInteractionHeldGuard()) {
                    sawHeldGuard.set(true);
                }
                if (spider.hasPreyInteractionPlacedWeb()) {
                    sawPlacedWeb.set(true);
                }
                if (spider.hasPreyInteractionTargetKilled()) {
                    sawTargetKilledFlag.set(true);
                }
                if (spider.hasPreyInteractionFacedPreyArea()) {
                    sawFacingPreyArea.set(true);
                }
                maxWebCount[0] = Math.max(maxWebCount[0], spider.getPreyInteractionPlacedWebCount());
                maxGuardReduced[0] = Math.max(maxGuardReduced[0], spider.getPreyInteractionGuardDistanceReduced());
            }

            if (sawPreyInteraction.get() && !spider.isPreyInteracting()
                    && spider.getPreyInteractionCooldownTicks() > 0) {
                sawCooldown.set(true);
                if (firstCooldownTick[0] < 0) {
                    firstCooldownTick[0] = ticks[0];
                    firstCooldownEntityTick[0] = spider.tickCount;
                }
                if (spider.hasPreyInteractionReachedGuard()) {
                    sawReachedGuard.set(true);
                }
                if (spider.hasPreyInteractionHeldGuard()) {
                    sawHeldGuard.set(true);
                }
                if (spider.hasPreyInteractionFacedPreyArea()) {
                    sawFacingPreyArea.set(true);
                }
                maxWebCount[0] = Math.max(maxWebCount[0], spider.getPreyInteractionPlacedWebCount());
                maxGuardReduced[0] = Math.max(maxGuardReduced[0], spider.getPreyInteractionGuardDistanceReduced());
            }

            boolean guardMovementEnough = sawReachedGuard.get() || maxGuardReduced[0] >= 0.35D;
            if (!completed.get()
                    && sawPreyKilled.get()
                    && sawPreyInteraction.get()
                    && sawWebbing.get()
                    && sawGuarding.get()
                    && sawPreyAnchor.get()
                    && sawGuardAnchor.get()
                    && sawPathStarted.get()
                    && guardMovementEnough
                    && sawHeldGuard.get()
                    && sawPlacedWeb.get()
                    && maxWebCount[0] >= 1
                    && sawTargetKilledFlag.get()
                    && sawFacingPreyArea.get()
                    && sawCooldown.get()
                    && !usedForcedPath.get()
                    && !sawUnexpectedAttack.get()
                    && firstPreyInteractionTick[0] >= 0
                    // Generated-world placement can add a short, bounded approach delay; keep
                    // the contract strict enough to catch a stalled interaction without
                    // rejecting the otherwise complete web-and-guard sequence.
                    && firstPreyInteractionTick[0] <= 400
                    && completed.compareAndSet(false, true)) {
                succeedAndDiscard(helper, prey, spider);
            }
        });

        helper.runAfterDelay(560, () -> {
            if (completed.get()) {
                return;
            }
            boolean guardMovementEnough = sawReachedGuard.get() || maxGuardReduced[0] >= 0.35D;
            if (!sawPreyKilled.get()
                    || !sawPreyInteraction.get()
                    || !sawWebbing.get()
                    || !sawGuarding.get()
                    || !sawPreyAnchor.get()
                    || !sawGuardAnchor.get()
                    || !sawPathStarted.get()
                    || !guardMovementEnough
                    || !sawHeldGuard.get()
                    || !sawPlacedWeb.get()
                    || maxWebCount[0] < 1
                    || !sawTargetKilledFlag.get()
                    || !sawFacingPreyArea.get()
                    || !sawCooldown.get()
                    || usedForcedPath.get()
                    || sawUnexpectedAttack.get()
                    || firstPreyInteractionTick[0] < 0
                    || firstPreyInteractionTick[0] > 70) {
                failAndDiscard(helper,
                        "Ground spider should kill prey, web the kill area, and hold guard; sawPreyKilled="
                        + sawPreyKilled.get()
                        + " sawPreyInteraction=" + sawPreyInteraction.get()
                        + " sawWebbing=" + sawWebbing.get()
                        + " sawGuarding=" + sawGuarding.get()
                        + " sawPreyAnchor=" + sawPreyAnchor.get()
                        + " sawGuardAnchor=" + sawGuardAnchor.get()
                        + " sawPathStarted=" + sawPathStarted.get()
                        + " guardMovementEnough=" + guardMovementEnough
                        + " sawHeldGuard=" + sawHeldGuard.get()
                        + " sawPlacedWeb=" + sawPlacedWeb.get()
                        + " maxWebCount=" + maxWebCount[0]
                        + " sawTargetKilledFlag=" + sawTargetKilledFlag.get()
                        + " sawFacingPreyArea=" + sawFacingPreyArea.get()
                        + " sawCooldown=" + sawCooldown.get()
                        + " usedForcedPath=" + usedForcedPath.get()
                        + " sawUnexpectedAttack=" + sawUnexpectedAttack.get()
                        + " firstPreyInteractionTick=" + firstPreyInteractionTick[0]
                        + " firstPreyInteractionEntityTick=" + firstPreyInteractionEntityTick[0]
                        + " firstGuardingTick=" + firstGuardingTick[0]
                        + " firstGuardingEntityTick=" + firstGuardingEntityTick[0]
                        + " firstCooldownTick=" + firstCooldownTick[0]
                        + " firstCooldownEntityTick=" + firstCooldownEntityTick[0]
                        + " lastObservedEntityTick=" + lastObservedEntityTick[0]
                        + " maxGuardReduced=" + maxGuardReduced[0]
                        + " preyInteractionPhase=" + spider.getPreyInteractionPhaseName()
                        + " preyInteractionStatus=" + spider.getPreyInteractionStatus()
                        + " preyInteractionTicks=" + spider.getPreyInteractionTicks()
                        + " preyInteractionCooldown=" + spider.getPreyInteractionCooldownTicks()
                        + " lastPreyAnchor=" + lastPreyAnchor.get()
                        + " lastGuardAnchor=" + lastGuardAnchor.get()
                        + " combatState=" + spider.getCombatPacingStateName()
                        + " animation=" + spider.getAnimationAuditName()
                        + " spiderPos=" + spider.position()
                        + " preyAlive=" + prey.isAlive()
                        + " preyRemoved=" + prey.isRemoved(),
                        prey, spider);
                return;
            }
            succeedAndDiscard(helper, prey, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 180, batch = "backpedal")
    public static void backpedalFacesAdvancingTarget(GameTestHelper helper) {
        fillFloor(helper, 11, 5);

        BlockPos spiderPos = new BlockPos(4, 1, 2);
        BlockPos targetPos = new BlockPos(8, 1, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 10.0D);
        var target = helper.spawnWithNoFreeWill(EntityType.SHEEP, targetPos);
        target.setInvulnerable(true);
        target.setNoGravity(true);
        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));

        spider.setTarget(target);
        spider.getNavigation().moveTo(target, 1.0D);

        int[] ticks = { 0 };
        double[] heldTargetX = { targetAnchor.x };
        double[] firstBackpedalDistance = { Double.NaN };
        double[] bestBackpedalDistance = { 0.0D };
        double[] firstBackpedalX = { Double.NaN };
        double[] lowestBackpedalX = { Double.POSITIVE_INFINITY };
        AtomicBoolean sawBackpedal = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(spider.getTarget() == target);

        helper.onEachTick(() -> {
            ticks[0]++;
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                if (!sawBackpedal.get()) {
                    heldTargetX[0] = targetAnchor.x - Math.min(ticks[0] * 0.12D, 1.4D);
                }
                target.setPos(heldTargetX[0], targetAnchor.y, targetAnchor.z);
            }
            if (spider.getTarget() != target) {
                spider.setTarget(target);
            }
            keptTarget.compareAndSet(false, spider.getTarget() == target);

            if (spider.isBackpedalingFacingTarget()) {
                sawBackpedal.set(true);
                double currentDistance = Math.sqrt(spider.distanceToSqr(target));
                if (Double.isNaN(firstBackpedalDistance[0])) {
                    firstBackpedalDistance[0] = currentDistance;
                    firstBackpedalX[0] = spider.getX();
                }
                bestBackpedalDistance[0] = Math.max(bestBackpedalDistance[0], currentDistance);
                lowestBackpedalX[0] = Math.min(lowestBackpedalX[0], spider.getX());
                if (facesTargetOnFloor(spider, target, 45.0D)) {
                    sawFacingTarget.set(true);
                }
            }
        });

        helper.runAfterDelay(130, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Backpedal should remain natural AI movement, not forced-path mode");
            if (!sawBackpedal.get()
                    || !sawFacingTarget.get()
                    || !keptTarget.get()
                    || Double.isNaN(firstBackpedalDistance[0])
                    || bestBackpedalDistance[0] <= firstBackpedalDistance[0] + 0.25D
                    || lowestBackpedalX[0] >= firstBackpedalX[0] - 0.25D) {
                failAndDiscard(helper,
                        "Ground spider should backpedal away from an advancing target while facing it; sawBackpedal="
                        + sawBackpedal.get()
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " keptTarget=" + keptTarget.get()
                        + " firstBackpedalDistance=" + firstBackpedalDistance[0]
                        + " bestBackpedalDistance=" + bestBackpedalDistance[0]
                        + " firstBackpedalX=" + firstBackpedalX[0]
                        + " lowestBackpedalX=" + lowestBackpedalX[0]
                        + " state=" + spider.getCombatPacingStateName()
                        + " backpedalTicks=" + spider.getBackpedalTicks()
                        + " pacingTicks=" + spider.getCombatPacingTicks()
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " navDone=" + spider.getNavigation().isDone(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 240, batch = "ceilingStalk")
    public static void ceilingStalksAboveOrBehindTargetDuringStalkPhase(GameTestHelper helper) {
        fillFloor(helper, 11, 7);
        fillCeiling(helper, 0, 10, 5, 0, 6);

        BlockPos spiderPos = new BlockPos(1, 4, 3);
        BlockPos targetPos = new BlockPos(6, 1, 3);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 12.0D);
        var target = helper.spawnWithNoFreeWill(EntityType.SHEEP, targetPos);
        target.setInvulnerable(true);
        target.setNoGravity(true);
        target.addTag(GroundSpiderEntity.CEILING_STALK_TEST_TARGET_TAG);
        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));
        placeAttached(helper, spider, spiderPos, Direction.UP);

        spider.setTarget(target);

        AtomicBoolean sawCeilingStalk = new AtomicBoolean(false);
        AtomicBoolean sawCeiling = new AtomicBoolean(spider.getAttachmentDirection() == Direction.UP);
        AtomicBoolean keptTarget = new AtomicBoolean(spider.getTarget() == target);
        AtomicBoolean anchorAboveOrBehind = new AtomicBoolean(false);
        double[] startAnchorDistance = { Double.NaN };
        double[] bestAnchorDistance = { Double.POSITIVE_INFINITY };
        BlockPos[] firstAnchor = { null };
        BlockPos[] lastAnchor = { null };

        helper.onEachTick(() -> {
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
            }
            if (spider.getTarget() != target) {
                spider.setTarget(target);
            }
            keptTarget.compareAndSet(false, spider.getTarget() == target);
            if (spider.getAttachmentDirection() == Direction.UP) {
                sawCeiling.set(true);
            }

            if (spider.isCeilingStalking()) {
                sawCeilingStalk.set(true);
                BlockPos anchor = spider.getCeilingStalkAnchor();
                if (anchor != null) {
                    if (firstAnchor[0] == null) {
                        firstAnchor[0] = anchor;
                    }
                    lastAnchor[0] = anchor;
                    Vec3 anchorPos = AttachmentHelper.anchorFor(spider, anchor, Direction.UP);
                    double anchorDistance = spider.position().distanceToSqr(anchorPos);
                    if (Double.isNaN(startAnchorDistance[0])) {
                        startAnchorDistance[0] = anchorDistance;
                    }
                    bestAnchorDistance[0] = Math.min(bestAnchorDistance[0], anchorDistance);
                    BlockPos absoluteTarget = helper.absolutePos(targetPos);
                    if (anchor.getY() >= absoluteTarget.getY() + 3
                            && Math.abs(anchor.getX() - absoluteTarget.getX()) <= 3
                            && Math.abs(anchor.getZ() - absoluteTarget.getZ()) <= 3) {
                        anchorAboveOrBehind.set(true);
                    }
                }
            }
        });

        helper.runAfterDelay(170, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Ceiling stalk should remain natural AI movement, not forced-path mode");
            helper.assertEntityProperty(spider, entity -> entity.getTarget() == target,
                    "Ground spider should keep the target while ceiling-stalking");
            boolean approachedAnchor = !Double.isNaN(startAnchorDistance[0])
                    && (bestAnchorDistance[0] <= 1.75D
                    || bestAnchorDistance[0] <= startAnchorDistance[0] - 1.0D);
            if (!sawCeilingStalk.get()
                    || !sawCeiling.get()
                    || !keptTarget.get()
                    || !anchorAboveOrBehind.get()
                    || !approachedAnchor) {
                failAndDiscard(helper,
                        "Ground spider should enter ceiling-stalk mode and approach an above/behind ceiling anchor; sawCeilingStalk="
                        + sawCeilingStalk.get()
                        + " sawCeiling=" + sawCeiling.get()
                        + " keptTarget=" + keptTarget.get()
                        + " anchorAboveOrBehind=" + anchorAboveOrBehind.get()
                        + " startAnchorDistance=" + startAnchorDistance[0]
                        + " bestAnchorDistance=" + bestAnchorDistance[0]
                        + " firstAnchor=" + firstAnchor[0]
                        + " lastAnchor=" + lastAnchor[0]
                        + " state=" + spider.getCombatPacingStateName()
                        + " pacingTicks=" + spider.getCombatPacingTicks()
                        + " ceilingStalking=" + spider.isCeilingStalking()
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " navDone=" + spider.getNavigation().isDone(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 240, batch = "dropAttack")
    public static void ceilingDropAttackWindsUpThenDropsOntoTarget(GameTestHelper helper) {
        fillFloor(helper, 9, 7);
        fillCeiling(helper, 0, 8, 5, 0, 6);

        BlockPos spiderPos = new BlockPos(4, 4, 3);
        BlockPos targetPos = new BlockPos(5, 1, 3);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 12.0D);
        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        target.setNoGravity(true);
        target.setHealth(24.0F);
        target.addTag(GroundSpiderEntity.DROP_ATTACK_TEST_TARGET_TAG);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));
        placeAttached(helper, spider, spiderPos, Direction.UP);

        spider.setTarget(target);

        AtomicBoolean sawWindup = new AtomicBoolean(false);
        AtomicBoolean sawDrop = new AtomicBoolean(false);
        AtomicBoolean sawRecovery = new AtomicBoolean(false);
        AtomicBoolean windupBeforeDrop = new AtomicBoolean(false);
        AtomicBoolean windupHeldCeilingPosition = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean sawDropAnimation = new AtomicBoolean(false);
        AtomicBoolean sawDownwardMovement = new AtomicBoolean(false);
        AtomicBoolean sawDamageSpend = new AtomicBoolean(false);
        AtomicBoolean sawCooldown = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        int[] ticks = { 0 };
        int[] firstWindupTick = { -1 };
        int[] firstDropTick = { -1 };
        int[] firstRecoveryTick = { -1 };
        int[] damageSpendTransitions = { 0 };
        boolean[] previousDamageSpent = { false };
        double startY = spider.getY();
        double startHealth = target.getHealth();
        double[] lowestHealth = { startHealth };
        double[] lowestSpiderY = { startY };

        helper.onEachTick(() -> {
            ticks[0]++;
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
            }
            if (spider.getTarget() != target) {
                keptTarget.set(false);
                spider.setTarget(target);
            }
            lowestHealth[0] = Math.min(lowestHealth[0], target.getHealth());
            lowestSpiderY[0] = Math.min(lowestSpiderY[0], spider.getY());

            if (spider.isDropAttackWindup()) {
                sawWindup.set(true);
                if (firstWindupTick[0] < 0) {
                    firstWindupTick[0] = ticks[0];
                }
                if (spider.getAttachmentDirection() == Direction.UP && Math.abs(spider.getY() - startY) < 0.08D) {
                    windupHeldCeilingPosition.set(true);
                }
                if (facesTargetOnFloor(spider, target, 55.0D)) {
                    sawFacingTarget.set(true);
                }
            }

            if (spider.isDropAttackDropping()) {
                sawDrop.set(true);
                if (firstDropTick[0] < 0) {
                    firstDropTick[0] = ticks[0];
                }
                if (firstWindupTick[0] >= 0 && ticks[0] > firstWindupTick[0]) {
                    windupBeforeDrop.set(true);
                }
                if ("ground_spider_jump_forward".equals(spider.getAnimationAuditName())) {
                    sawDropAnimation.set(true);
                }
                if (spider.getY() < startY - 0.45D || spider.getDeltaMovement().y < -0.20D) {
                    sawDownwardMovement.set(true);
                }
                if (facesTargetOnFloor(spider, target, 65.0D)) {
                    sawFacingTarget.set(true);
                }
            }

            if (spider.isDropAttackRecovering()) {
                sawRecovery.set(true);
                if (firstRecoveryTick[0] < 0) {
                    firstRecoveryTick[0] = ticks[0];
                }
                if ("ground_spider_jump_forward".equals(spider.getAnimationAuditName())) {
                    sawDropAnimation.set(true);
                }
                if (spider.getDropAttackCooldownTicks() > 0) {
                    sawCooldown.set(true);
                }
            }

            if (spider.isDropAttackDamageSpent()) {
                sawDamageSpend.set(true);
            }
            if (!previousDamageSpent[0] && spider.isDropAttackDamageSpent()) {
                damageSpendTransitions[0]++;
            }
            previousDamageSpent[0] = spider.isDropAttackDamageSpent();
        });

        helper.runAfterDelay(180, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Drop attack should remain natural AI movement, not forced-path mode");
            helper.assertEntityProperty(spider, entity -> entity.getTarget() == target,
                    "Ground spider should keep the target through drop attack");
            if (!sawWindup.get()
                    || !sawDrop.get()
                    || !sawRecovery.get()
                    || !windupBeforeDrop.get()
                    || !windupHeldCeilingPosition.get()
                    || !sawFacingTarget.get()
                    || !sawDropAnimation.get()
                    || !sawDownwardMovement.get()
                    || !sawDamageSpend.get()
                    || !sawCooldown.get()
                    || !keptTarget.get()
                    || damageSpendTransitions[0] != 1
                    || lowestHealth[0] >= startHealth
                    || lowestSpiderY[0] >= startY - 1.0D
                    || firstRecoveryTick[0] <= firstDropTick[0]) {
                failAndDiscard(helper,
                        "Ground spider should wind up on ceiling, drop downward, spend one damage window, and recover; sawWindup="
                        + sawWindup.get()
                        + " sawDrop=" + sawDrop.get()
                        + " sawRecovery=" + sawRecovery.get()
                        + " windupBeforeDrop=" + windupBeforeDrop.get()
                        + " windupHeldCeilingPosition=" + windupHeldCeilingPosition.get()
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " sawDropAnimation=" + sawDropAnimation.get()
                        + " sawDownwardMovement=" + sawDownwardMovement.get()
                        + " sawDamageSpend=" + sawDamageSpend.get()
                        + " sawCooldown=" + sawCooldown.get()
                        + " keptTarget=" + keptTarget.get()
                        + " damageSpendTransitions=" + damageSpendTransitions[0]
                        + " firstWindupTick=" + firstWindupTick[0]
                        + " firstDropTick=" + firstDropTick[0]
                        + " firstRecoveryTick=" + firstRecoveryTick[0]
                        + " startHealth=" + startHealth
                        + " lowestHealth=" + lowestHealth[0]
                        + " startY=" + startY
                        + " lowestSpiderY=" + lowestSpiderY[0]
                        + " dropPhase=" + spider.getDropAttackPhaseName()
                        + " dropTicks=" + spider.getDropAttackTicks()
                        + " cooldownTicks=" + spider.getDropAttackCooldownTicks()
                        + " damageSpent=" + spider.isDropAttackDamageSpent()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " state=" + spider.getCombatPacingStateName()
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " navDone=" + spider.getNavigation().isDone(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 260, batch = "zzzzzzMixedSurfaceChangingThreats")
    public static void webShotWindsUpThenTrapsTargetWithoutDamage(GameTestHelper helper) {
        fillFloor(helper, 11, 5);

        BlockPos spiderPos = new BlockPos(1, 1, 2);
        BlockPos targetPos = new BlockPos(8, 1, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 12.0D);
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }

        var target = helper.spawnWithNoFreeWill(EntityType.SHEEP, targetPos);
        target.setNoGravity(true);
        target.noPhysics = true;
        target.setHealth(target.getMaxHealth());
        target.removeAllEffects();
        target.addTag(GroundSpiderEntity.WEB_SHOT_TEST_TARGET_TAG);

        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));
        spider.setTarget(target);

        AtomicBoolean sawWindup = new AtomicBoolean(false);
        AtomicBoolean sawProjectile = new AtomicBoolean(false);
        AtomicBoolean sawRecovery = new AtomicBoolean(false);
        AtomicBoolean sawCooldown = new AtomicBoolean(false);
        AtomicBoolean sawResetWithCooldown = new AtomicBoolean(false);
        AtomicBoolean windupBeforeProjectile = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean lostTargetDuringWebShot = new AtomicBoolean(false);
        AtomicBoolean sawEffects = new AtomicBoolean(false);
        AtomicBoolean sawWeb = new AtomicBoolean(false);
        int[] ticks = { 0 };
        int[] firstWindupTick = { -1 };
        int[] firstProjectileTick = { -1 };
        int[] firstRecoveryTick = { -1 };
        double startHealth = target.getHealth();
        double[] lowestHealth = { startHealth };

        helper.onEachTick(() -> {
            ticks[0]++;
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
            }
            if (spider.isWebShotActive() && spider.getTarget() != target) {
                lostTargetDuringWebShot.set(true);
            }
            if (spider.getTarget() != target) {
                spider.setTarget(target);
            }
            lowestHealth[0] = Math.min(lowestHealth[0], target.getHealth());

            if (spider.isWebShotWindup()) {
                sawWindup.set(true);
                if (firstWindupTick[0] < 0) {
                    firstWindupTick[0] = ticks[0];
                }
                if (facesTargetOnFloor(spider, target, 55.0D)) {
                    sawFacingTarget.set(true);
                }
            }

            if (!helper.getLevel().getEntitiesOfClass(WebShotEntity.class,
                    new AABB(spider.blockPosition()).inflate(16.0D)).isEmpty()) {
                sawProjectile.set(true);
                if (firstProjectileTick[0] < 0) {
                    firstProjectileTick[0] = ticks[0];
                }
                if (firstWindupTick[0] >= 0 && ticks[0] > firstWindupTick[0]) {
                    windupBeforeProjectile.set(true);
                }
            }

            if (spider.isWebShotRecovering()) {
                sawRecovery.set(true);
                if (firstRecoveryTick[0] < 0) {
                    firstRecoveryTick[0] = ticks[0];
                }
                if (spider.getWebShotCooldownTicks() > 0 && spider.isWebShotFired()) {
                    sawCooldown.set(true);
                }
                if (facesTargetOnFloor(spider, target, 65.0D)) {
                    sawFacingTarget.set(true);
                }
            }

            if (sawRecovery.get() && !spider.isWebShotActive() && spider.getWebShotCooldownTicks() > 0) {
                sawResetWithCooldown.set(true);
            }
            if (target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && target.hasEffect(MobEffects.BLINDNESS)) {
                sawEffects.set(true);
            }
            if (hasCobwebNear(helper, target.blockPosition())) {
                sawWeb.set(true);
            }
        });

        helper.runAfterDelay(170, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Web shot should remain natural AI movement, not forced-path mode");
            helper.assertEntityProperty(spider, entity -> entity.getTarget() == target,
                    "Ground spider should keep the target through web shot");
            if (!sawWindup.get()
                    || !sawProjectile.get()
                    || !sawRecovery.get()
                    || !sawCooldown.get()
                    || !sawResetWithCooldown.get()
                    || !windupBeforeProjectile.get()
                    || !sawFacingTarget.get()
                    || lostTargetDuringWebShot.get()
                    || !sawEffects.get()
                    || !sawWeb.get()
                    || lowestHealth[0] < startHealth - 0.01D
                    || firstRecoveryTick[0] < firstProjectileTick[0]) {
                failAndDiscard(helper,
                        "Ground spider should wind up, fire a visible web shot, trap/control target, recover, and deal no damage; sawWindup="
                        + sawWindup.get()
                        + " sawProjectile=" + sawProjectile.get()
                        + " sawRecovery=" + sawRecovery.get()
                        + " sawCooldown=" + sawCooldown.get()
                        + " sawResetWithCooldown=" + sawResetWithCooldown.get()
                        + " windupBeforeProjectile=" + windupBeforeProjectile.get()
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " lostTargetDuringWebShot=" + lostTargetDuringWebShot.get()
                        + " sawEffects=" + sawEffects.get()
                        + " sawWeb=" + sawWeb.get()
                        + " firstWindupTick=" + firstWindupTick[0]
                        + " firstProjectileTick=" + firstProjectileTick[0]
                        + " firstRecoveryTick=" + firstRecoveryTick[0]
                        + " startHealth=" + startHealth
                        + " lowestHealth=" + lowestHealth[0]
                        + " webPhase=" + spider.getWebShotPhaseName()
                        + " webTicks=" + spider.getWebShotTicks()
                        + " cooldownTicks=" + spider.getWebShotCooldownTicks()
                        + " webFired=" + spider.isWebShotFired()
                        + " state=" + spider.getCombatPacingStateName()
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " navDone=" + spider.getNavigation().isDone(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 150, batch = "zzzzzzzzWebTrapPlacement")
    public static void webTrapPlacementNarrowsEscapeLaneBehindOrBesideTarget(GameTestHelper helper) {
        fillFloor(helper, 12, 7);

        BlockPos spiderPos = new BlockPos(2, 1, 2);
        BlockPos targetPos = new BlockPos(7, 1, 3);
        BlockPos expectedBehind = targetPos.west();
        BlockPos expectedBesideNorth = targetPos.north();
        BlockPos expectedBesideSouth = targetPos.south();

        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 14.0D);
        if (spider.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.10D);
        }
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }

        var target = helper.spawnWithNoFreeWill(EntityType.SHEEP, targetPos);
        target.setNoGravity(true);
        target.setHealth(target.getMaxHealth());
        target.addTag(GroundSpiderEntity.WEB_TRAP_PLACEMENT_TEST_TARGET_TAG);
        target.setYRot(-90.0F);
        target.setXRot(0.0F);
        target.yHeadRot = -90.0F;
        target.yBodyRot = -90.0F;
        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));

        spider.setTarget(target);

        AtomicBoolean sawWebTrap = new AtomicBoolean(false);
        AtomicBoolean sawAnchor = new AtomicBoolean(false);
        AtomicBoolean sawBehind = new AtomicBoolean(false);
        AtomicBoolean sawBeside = new AtomicBoolean(false);
        AtomicBoolean sawPlacedCount = new AtomicBoolean(false);
        AtomicBoolean sawTargetRetained = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean sawCooldown = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        AtomicBoolean usedForcedPath = new AtomicBoolean(false);
        AtomicBoolean sawUnexpectedAttack = new AtomicBoolean(false);
        AtomicBoolean sawWebShotProjectile = new AtomicBoolean(false);
        AtomicBoolean frozeAfterCooldown = new AtomicBoolean(false);
        AtomicReference<BlockPos> lastAnchor = new AtomicReference<>();
        int[] firstTrapTick = { -1 };
        int[] ticks = { 0 };
        double startHealth = target.getHealth();
        double[] lowestHealth = { startHealth };

        helper.onEachTick(() -> {
            ticks[0]++;
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
                target.setYRot(-90.0F);
                target.setXRot(0.0F);
                target.yHeadRot = -90.0F;
                target.yBodyRot = -90.0F;
            }

            if (!frozeAfterCooldown.get() && spider.getTarget() != target) {
                keptTarget.set(false);
                spider.setTarget(target);
            }
            if (spider.isFollowingForcedPath()) {
                usedForcedPath.set(true);
            }
            if (!helper.getLevel().getEntitiesOfClass(WebShotEntity.class,
                    new AABB(spider.blockPosition()).inflate(16.0D)).isEmpty()) {
                sawWebShotProjectile.set(true);
            }
            if (!"none".equals(spider.getDropAttackPhaseName())
                    || !"none".equals(spider.getWebShotPhaseName())
                    || !"none".equals(spider.getWebLowerPhaseName())
                    || !"none".equals(spider.getPouncePhaseName())
                    || !"none".equals(spider.getGrabPullPhaseName())
                    || !"none".equals(spider.getDragNestPhaseName())
                    || spider.isThreatDisplaying()
                    || spider.isLineOfSightStalking()
                    || spider.isDarknessPreferenceActive()
                    || spider.isRetreatActive()
                    || spider.isFakeRetreatActive()
                    || spider.isEscapeCutting()
                    || spider.isPackCoordinating()) {
                sawUnexpectedAttack.set(true);
            }
            lowestHealth[0] = Math.min(lowestHealth[0], target.getHealth());

            if (spider.isWebTrapPlacementActive()) {
                sawWebTrap.set(true);
                if (firstTrapTick[0] < 0) {
                    firstTrapTick[0] = ticks[0];
                }
                if (spider.getWebTrapPlacementAnchor() != null) {
                    sawAnchor.set(true);
                    lastAnchor.set(spider.getWebTrapPlacementAnchor());
                }
                if (spider.getWebTrapPlacementPlacedCount() >= 1) {
                    sawPlacedCount.set(true);
                }
                if (spider.hasWebTrapPlacementPlacedBehind()) {
                    sawBehind.set(true);
                }
                if (spider.hasWebTrapPlacementPlacedBeside()) {
                    sawBeside.set(true);
                }
                if (spider.hasWebTrapPlacementTargetRetained()) {
                    sawTargetRetained.set(true);
                }
                if (spider.hasWebTrapPlacementFacedTarget() || facesTargetOnFloor(spider, target, 65.0D)) {
                    sawFacingTarget.set(true);
                }
            }
            if (sawWebTrap.get()
                    && !spider.isWebTrapPlacementActive()
                    && spider.getWebTrapPlacementCooldownTicks() > 0) {
                sawCooldown.set(true);
                if (frozeAfterCooldown.compareAndSet(false, true)) {
                    spider.getNavigation().stop();
                    spider.setTarget(null);
                    spider.setNoAi(true);
                }
            }
        });

        helper.runAfterDelay(100, () -> {
            boolean behindWebPresent = hasWebTrapWebAt(helper, helper.absolutePos(expectedBehind));
            boolean besideWebPresent = hasWebTrapWebAt(helper, helper.absolutePos(expectedBesideNorth))
                    || hasWebTrapWebAt(helper, helper.absolutePos(expectedBesideSouth));
            boolean targetBlockClear = !hasWebTrapWebAt(helper, target.blockPosition());
            boolean usedSingleThreadWeb = hasSingleThreadWebAt(helper, helper.absolutePos(expectedBehind))
                    && (hasSingleThreadWebAt(helper, helper.absolutePos(expectedBesideNorth))
                    || hasSingleThreadWebAt(helper, helper.absolutePos(expectedBesideSouth)));
            if (!sawWebTrap.get()
                    || !sawAnchor.get()
                    || !sawPlacedCount.get()
                    || !sawBehind.get()
                    || !sawBeside.get()
                    || !behindWebPresent
                    || !besideWebPresent
                    || !targetBlockClear
                    || !usedSingleThreadWeb
                    || !sawTargetRetained.get()
                    || !sawFacingTarget.get()
                    || !sawCooldown.get()
                    || !keptTarget.get()
                    || usedForcedPath.get()
                    || sawUnexpectedAttack.get()
                    || sawWebShotProjectile.get()
                    || lowestHealth[0] < startHealth - 0.01D
                    || firstTrapTick[0] < 0
                    || firstTrapTick[0] > 45) {
                failAndDiscard(helper,
                        "Ground spider should place proactive behind/beside web traps without web-shot or target damage; sawWebTrap="
                        + sawWebTrap.get()
                        + " sawAnchor=" + sawAnchor.get()
                        + " sawPlacedCount=" + sawPlacedCount.get()
                        + " sawBehind=" + sawBehind.get()
                        + " sawBeside=" + sawBeside.get()
                        + " behindWebPresent=" + behindWebPresent
                        + " besideWebPresent=" + besideWebPresent
                        + " targetBlockClear=" + targetBlockClear
                        + " usedSingleThreadWeb=" + usedSingleThreadWeb
                        + " sawTargetRetained=" + sawTargetRetained.get()
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " sawCooldown=" + sawCooldown.get()
                        + " keptTarget=" + keptTarget.get()
                        + " usedForcedPath=" + usedForcedPath.get()
                        + " sawUnexpectedAttack=" + sawUnexpectedAttack.get()
                        + " sawWebShotProjectile=" + sawWebShotProjectile.get()
                        + " firstTrapTick=" + firstTrapTick[0]
                        + " startHealth=" + startHealth
                        + " lowestHealth=" + lowestHealth[0]
                        + " webTrapStatus=" + spider.getWebTrapPlacementStatus()
                        + " webTrapTicks=" + spider.getWebTrapPlacementTicks()
                        + " webTrapCooldown=" + spider.getWebTrapPlacementCooldownTicks()
                        + " placedCount=" + spider.getWebTrapPlacementPlacedCount()
                        + " lastAnchor=" + lastAnchor.get()
                        + " route=" + spider.getWebTrapPlacementRouteDirectionName()
                        + " webShotPhase=" + spider.getWebShotPhaseName()
                        + " combatState=" + spider.getCombatPacingStateName()
                        + " spiderPos=" + spider.position()
                        + " targetPos=" + target.position(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 440, batch = "zzzzzWebLower")
    public static void webLowerWindsUpThenDescendsOnSilkWithoutDamage(GameTestHelper helper) {
        fillFloor(helper, 11, 7);
        fillCeiling(helper, 0, 10, 5, 0, 6);

        BlockPos spiderPos = new BlockPos(4, 4, 3);
        BlockPos targetPos = new BlockPos(7, 1, 3);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 12.0D);
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }
        placeAttached(helper, spider, spiderPos, Direction.UP);

        var target = helper.spawnWithNoFreeWill(EntityType.SHEEP, targetPos);
        target.setNoGravity(true);
        target.setHealth(target.getMaxHealth());
        target.addTag(GroundSpiderEntity.WEB_LOWER_TEST_TARGET_TAG);

        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));
        spider.setTarget(target);

        AtomicBoolean sawWindup = new AtomicBoolean(false);
        AtomicBoolean sawLowering = new AtomicBoolean(false);
        AtomicBoolean sawRecovery = new AtomicBoolean(false);
        AtomicBoolean sawCooldown = new AtomicBoolean(false);
        AtomicBoolean sawResetWithCooldown = new AtomicBoolean(false);
        AtomicBoolean windupBeforeLowering = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        AtomicBoolean lostTargetDuringWebLower = new AtomicBoolean(false);
        AtomicBoolean sawNoGravityLowering = new AtomicBoolean(false);
        AtomicBoolean sawStrandAnchor = new AtomicBoolean(false);
        int[] ticks = { 0 };
        int[] firstWindupTick = { -1 };
        int[] firstLoweringTick = { -1 };
        int[] firstRecoveryTick = { -1 };
        double startHealth = target.getHealth();
        double[] lowestHealth = { startHealth };
        double startSpiderY = spider.getY();
        double[] previousY = { spider.getY() };
        double[] lowestSpiderY = { spider.getY() };
        double[] maxDownStep = { 0.0D };

        helper.onEachTick(() -> {
            ticks[0]++;
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
            }
            if (spider.isWebLowerActive() && spider.getTarget() != target) {
                lostTargetDuringWebLower.set(true);
            }
            if (spider.getTarget() != target) {
                keptTarget.set(false);
                spider.setTarget(target);
            }
            lowestHealth[0] = Math.min(lowestHealth[0], target.getHealth());
            lowestSpiderY[0] = Math.min(lowestSpiderY[0], spider.getY());

            if (spider.isWebLowerWindup()) {
                sawWindup.set(true);
                if (firstWindupTick[0] < 0) {
                    firstWindupTick[0] = ticks[0];
                }
                if (spider.getWebLowerStrandAnchor() != null) {
                    sawStrandAnchor.set(true);
                }
                if (spider.getAttachmentDirection() == Direction.UP && facesTargetOnFloor(spider, target, 60.0D)) {
                    sawFacingTarget.set(true);
                }
            }

            if (spider.isWebLowerLowering()) {
                sawLowering.set(true);
                if (firstLoweringTick[0] < 0) {
                    firstLoweringTick[0] = ticks[0];
                }
                if (firstWindupTick[0] >= 0 && ticks[0] > firstWindupTick[0]) {
                    windupBeforeLowering.set(true);
                }
                if (spider.isNoGravity()) {
                    sawNoGravityLowering.set(true);
                }
                if (spider.getWebLowerStrandAnchor() != null) {
                    sawStrandAnchor.set(true);
                }
                if (facesTargetOnFloor(spider, target, 70.0D)) {
                    sawFacingTarget.set(true);
                }
                double downStep = previousY[0] - spider.getY();
                if (downStep > 0.0D) {
                    maxDownStep[0] = Math.max(maxDownStep[0], downStep);
                }
            }

            if (spider.isWebLowerRecovering()) {
                sawRecovery.set(true);
                if (firstRecoveryTick[0] < 0) {
                    firstRecoveryTick[0] = ticks[0];
                }
                if (spider.getWebLowerCooldownTicks() > 0) {
                    sawCooldown.set(true);
                }
                if (facesTargetOnFloor(spider, target, 70.0D)) {
                    sawFacingTarget.set(true);
                }
            }

            if (sawRecovery.get() && !spider.isWebLowerActive() && spider.getWebLowerCooldownTicks() > 0) {
                sawResetWithCooldown.set(true);
            }
            previousY[0] = spider.getY();
        });

        helper.runAfterDelay(310, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Web lower should remain natural AI movement, not forced-path mode");
            helper.assertEntityProperty(spider, entity -> entity.getTarget() == target,
                    "Ground spider should keep the target through web lower");
            double descent = Math.max(spider.getWebLowerDescentDistance(), startSpiderY - lowestSpiderY[0]);
            if (!sawWindup.get()
                    || !sawLowering.get()
                    || !sawRecovery.get()
                    || !sawCooldown.get()
                    || !sawResetWithCooldown.get()
                    || !windupBeforeLowering.get()
                    || !sawFacingTarget.get()
                    || !keptTarget.get()
                    || lostTargetDuringWebLower.get()
                    || !sawNoGravityLowering.get()
                    || !sawStrandAnchor.get()
                    || descent < 1.2D
                    || maxDownStep[0] <= 0.01D
                    || maxDownStep[0] > 0.08D
                    || lowestHealth[0] < startHealth - 0.01D
                    || firstLoweringTick[0] <= firstWindupTick[0]
                    || firstRecoveryTick[0] <= firstLoweringTick[0]) {
                failAndDiscard(helper,
                        "Ground spider should wind up on ceiling, lower slowly on silk, recover, and deal no damage; sawWindup="
                        + sawWindup.get()
                        + " sawLowering=" + sawLowering.get()
                        + " sawRecovery=" + sawRecovery.get()
                        + " sawCooldown=" + sawCooldown.get()
                        + " sawResetWithCooldown=" + sawResetWithCooldown.get()
                        + " windupBeforeLowering=" + windupBeforeLowering.get()
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " keptTarget=" + keptTarget.get()
                        + " lostTargetDuringWebLower=" + lostTargetDuringWebLower.get()
                        + " sawNoGravityLowering=" + sawNoGravityLowering.get()
                        + " sawStrandAnchor=" + sawStrandAnchor.get()
                        + " firstWindupTick=" + firstWindupTick[0]
                        + " firstLoweringTick=" + firstLoweringTick[0]
                        + " firstRecoveryTick=" + firstRecoveryTick[0]
                        + " startHealth=" + startHealth
                        + " lowestHealth=" + lowestHealth[0]
                        + " descent=" + descent
                        + " lowestSpiderY=" + lowestSpiderY[0]
                        + " maxDownStep=" + maxDownStep[0]
                        + " webLowerPhase=" + spider.getWebLowerPhaseName()
                        + " webLowerTicks=" + spider.getWebLowerTicks()
                        + " cooldownTicks=" + spider.getWebLowerCooldownTicks()
                        + " strandAnchor=" + spider.getWebLowerStrandAnchor()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " state=" + spider.getCombatPacingStateName()
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " navDone=" + spider.getNavigation().isDone(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 180, batch = "zzzzzzGrabPull")
    public static void grabPullWindsUpThenPullsWebControlledTargetWithoutDamage(GameTestHelper helper) {
        fillFloor(helper, 8, 7);

        BlockPos spiderPos = new BlockPos(3, 1, 3);
        BlockPos targetPos = new BlockPos(1, 1, 3);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 10.0D);
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }

        var target = helper.spawn(EntityType.ARMOR_STAND, targetPos);
        target.setNoGravity(true);
        target.setHealth(target.getMaxHealth());
        target.addTag(GroundSpiderEntity.GRAB_PULL_TEST_TARGET_TAG);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 4));

        spider.setTarget(target);

        AtomicBoolean sawWindup = new AtomicBoolean(false);
        AtomicBoolean sawPulling = new AtomicBoolean(false);
        AtomicBoolean sawRecovery = new AtomicBoolean(false);
        AtomicBoolean sawCooldown = new AtomicBoolean(false);
        AtomicBoolean sawResetWithCooldown = new AtomicBoolean(false);
        AtomicBoolean windupBeforePulling = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        AtomicBoolean lostTargetDuringGrabPull = new AtomicBoolean(false);
        AtomicBoolean sawWebTrigger = new AtomicBoolean(false);
        AtomicBoolean sawMovedTarget = new AtomicBoolean(false);
        int[] ticks = { 0 };
        int[] firstWindupTick = { -1 };
        int[] firstPullingTick = { -1 };
        int[] firstRecoveryTick = { -1 };
        double startHealth = target.getHealth();
        double[] lowestHealth = { startHealth };
        double[] startDistance = { Math.sqrt(spider.distanceToSqr(target)) };
        double[] minDistance = { startDistance[0] };
        double[] startY = { target.getY() };
        double[] maxY = { startY[0] };

        helper.onEachTick(() -> {
            ticks[0]++;
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
            }
            if (spider.isGrabPullActive() && spider.getTarget() != target) {
                lostTargetDuringGrabPull.set(true);
                keptTarget.set(false);
            }
            if (spider.getTarget() != target) {
                spider.setTarget(target);
            }
            lowestHealth[0] = Math.min(lowestHealth[0], target.getHealth());
            double distance = Math.sqrt(spider.distanceToSqr(target));
            minDistance[0] = Math.min(minDistance[0], distance);
            maxY[0] = Math.max(maxY[0], target.getY());

            if (spider.isGrabPullWindup()) {
                sawWindup.set(true);
                if (firstWindupTick[0] < 0) {
                    firstWindupTick[0] = ticks[0];
                }
                if (spider.isGrabPullTriggeredByWeb()) {
                    sawWebTrigger.set(true);
                }
                if (facesTargetOnFloor(spider, target, 60.0D)) {
                    sawFacingTarget.set(true);
                }
            }

            if (spider.isGrabPullPulling()) {
                sawPulling.set(true);
                if (firstPullingTick[0] < 0) {
                    firstPullingTick[0] = ticks[0];
                }
                if (firstWindupTick[0] >= 0 && ticks[0] > firstWindupTick[0]) {
                    windupBeforePulling.set(true);
                }
                if (spider.hasGrabPullMovedTarget()) {
                    sawMovedTarget.set(true);
                }
                if (facesTargetOnFloor(spider, target, 70.0D)) {
                    sawFacingTarget.set(true);
                }
            }

            if (spider.isGrabPullRecovering()) {
                sawRecovery.set(true);
                if (firstRecoveryTick[0] < 0) {
                    firstRecoveryTick[0] = ticks[0];
                }
                if (spider.getGrabPullCooldownTicks() > 0) {
                    sawCooldown.set(true);
                }
                if (facesTargetOnFloor(spider, target, 70.0D)) {
                    sawFacingTarget.set(true);
                }
            }

            if (sawRecovery.get() && !spider.isGrabPullActive() && spider.getGrabPullCooldownTicks() > 0) {
                sawResetWithCooldown.set(true);
            }
        });

        helper.runAfterDelay(135, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Grab/pull should remain natural AI movement, not forced-path mode");
            double reduced = startDistance[0] - minDistance[0];
            double lift = maxY[0] - startY[0];
            double auditedReduced = spider.getGrabPullDistanceReduced();
            double auditedLift = spider.getGrabPullTargetLift();
            if (!sawWindup.get()
                    || !sawPulling.get()
                    || !sawRecovery.get()
                    || !sawCooldown.get()
                    || !sawResetWithCooldown.get()
                    || !windupBeforePulling.get()
                    || !sawFacingTarget.get()
                    || !keptTarget.get()
                    || lostTargetDuringGrabPull.get()
                    || !sawWebTrigger.get()
                    || !sawMovedTarget.get()
                    || (reduced < 0.35D && lift < 0.18D)
                    || (auditedReduced < 0.35D && auditedLift < 0.18D)
                    || lowestHealth[0] < startHealth - 0.01D
                    || firstPullingTick[0] <= firstWindupTick[0]
                    || firstRecoveryTick[0] <= firstPullingTick[0]) {
                failAndDiscard(helper,
                        "Ground spider should wind up, pull a web-controlled target, recover, and deal no damage; sawWindup="
                        + sawWindup.get()
                        + " sawPulling=" + sawPulling.get()
                        + " sawRecovery=" + sawRecovery.get()
                        + " sawCooldown=" + sawCooldown.get()
                        + " sawResetWithCooldown=" + sawResetWithCooldown.get()
                        + " windupBeforePulling=" + windupBeforePulling.get()
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " keptTarget=" + keptTarget.get()
                        + " lostTargetDuringGrabPull=" + lostTargetDuringGrabPull.get()
                        + " sawWebTrigger=" + sawWebTrigger.get()
                        + " sawMovedTarget=" + sawMovedTarget.get()
                        + " firstWindupTick=" + firstWindupTick[0]
                        + " firstPullingTick=" + firstPullingTick[0]
                        + " firstRecoveryTick=" + firstRecoveryTick[0]
                        + " startHealth=" + startHealth
                        + " lowestHealth=" + lowestHealth[0]
                        + " startDistance=" + startDistance[0]
                        + " minDistance=" + minDistance[0]
                        + " reduced=" + reduced
                        + " auditedReduced=" + auditedReduced
                        + " lift=" + lift
                        + " auditedLift=" + auditedLift
                        + " grabPullPhase=" + spider.getGrabPullPhaseName()
                        + " grabPullTicks=" + spider.getGrabPullTicks()
                        + " cooldownTicks=" + spider.getGrabPullCooldownTicks()
                        + " triggerWeb=" + spider.isGrabPullTriggeredByWeb()
                        + " movedTarget=" + spider.hasGrabPullMovedTarget()
                        + " state=" + spider.getCombatPacingStateName()
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " navDone=" + spider.getNavigation().isDone(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 390, batch = "zzzzzzzDragNest")
    public static void dragNestFollowsGrabPullTowardWebAnchorWithoutDamage(GameTestHelper helper) {
        fillFloor(helper, 9, 7);

        BlockPos spiderPos = new BlockPos(3, 1, 3);
        BlockPos targetPos = new BlockPos(1, 1, 3);
        BlockPos anchorPos = new BlockPos(7, 1, 3);
        BlockPos absoluteAnchorPos = helper.absolutePos(anchorPos);
        helper.setBlock(anchorPos, Blocks.COBWEB);
        helper.setBlock(anchorPos.above(), Blocks.COBWEB);

        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 10.0D);
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }

        var target = helper.spawn(EntityType.ARMOR_STAND, targetPos);
        target.setNoGravity(true);
        target.setHealth(target.getMaxHealth());
        target.addTag(GroundSpiderEntity.GRAB_PULL_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.DRAG_NEST_TEST_TARGET_TAG);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 260, 4));

        spider.setTarget(target);

        AtomicBoolean sawGrabPull = new AtomicBoolean(false);
        AtomicBoolean sawWindup = new AtomicBoolean(false);
        AtomicBoolean sawDragging = new AtomicBoolean(false);
        AtomicBoolean sawRecovery = new AtomicBoolean(false);
        AtomicBoolean sawCooldown = new AtomicBoolean(false);
        AtomicBoolean sawResetWithCooldown = new AtomicBoolean(false);
        AtomicBoolean sawMovedTarget = new AtomicBoolean(false);
        AtomicBoolean sawAnchor = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        AtomicBoolean lostTargetDuringDrag = new AtomicBoolean(false);
        AtomicBoolean sequenceAfterGrab = new AtomicBoolean(false);
        int[] ticks = { 0 };
        int[] firstGrabTick = { -1 };
        int[] firstWindupTick = { -1 };
        int[] firstDraggingTick = { -1 };
        int[] firstRecoveryTick = { -1 };
        double startHealth = target.getHealth();
        double[] lowestHealth = { startHealth };
        double[] startAnchorDistance = { target.position().distanceTo(new Vec3(
                absoluteAnchorPos.getX() + 0.5D,
                target.getY(),
                absoluteAnchorPos.getZ() + 0.5D)) };
        double[] minAnchorDistance = { startAnchorDistance[0] };

        helper.onEachTick(() -> {
            ticks[0]++;
            if (target.isAlive() && !spider.isGrabPullPulling() && !spider.isDragNestDragging()) {
                target.setDeltaMovement(Vec3.ZERO);
            }
            if ((spider.isDragNestActive() || spider.isGrabPullActive()) && spider.getTarget() != target) {
                if (spider.isDragNestActive()) {
                    lostTargetDuringDrag.set(true);
                }
                keptTarget.set(false);
            }
            if (spider.getTarget() != target) {
                spider.setTarget(target);
            }

            lowestHealth[0] = Math.min(lowestHealth[0], target.getHealth());
            double anchorDistance = target.position().distanceTo(new Vec3(
                    absoluteAnchorPos.getX() + 0.5D,
                    target.getY(),
                    absoluteAnchorPos.getZ() + 0.5D));
            minAnchorDistance[0] = Math.min(minAnchorDistance[0], anchorDistance);

            if (spider.isGrabPullPulling()) {
                sawGrabPull.set(true);
                if (firstGrabTick[0] < 0) {
                    firstGrabTick[0] = ticks[0];
                }
            }

            if (spider.isDragNestWindup()) {
                sawWindup.set(true);
                if (firstWindupTick[0] < 0) {
                    firstWindupTick[0] = ticks[0];
                }
                if (firstGrabTick[0] >= 0 && ticks[0] > firstGrabTick[0]) {
                    sequenceAfterGrab.set(true);
                }
                if (spider.getDragNestAnchor() != null) {
                    sawAnchor.set(true);
                }
                if (facesTargetOnFloor(spider, target, 70.0D)) {
                    sawFacingTarget.set(true);
                }
            }

            if (spider.isDragNestDragging()) {
                sawDragging.set(true);
                if (firstDraggingTick[0] < 0) {
                    firstDraggingTick[0] = ticks[0];
                }
                if (spider.hasDragNestMovedTarget()) {
                    sawMovedTarget.set(true);
                }
                if (spider.getDragNestAnchor() != null) {
                    sawAnchor.set(true);
                }
                if (facesTargetOnFloor(spider, target, 75.0D)) {
                    sawFacingTarget.set(true);
                }
            }

            if (spider.isDragNestRecovering()) {
                sawRecovery.set(true);
                if (firstRecoveryTick[0] < 0) {
                    firstRecoveryTick[0] = ticks[0];
                }
                if (spider.getDragNestCooldownTicks() > 0) {
                    sawCooldown.set(true);
                }
                if (facesTargetOnFloor(spider, target, 75.0D)) {
                    sawFacingTarget.set(true);
                }
            }

            if (sawRecovery.get() && !spider.isDragNestActive() && spider.getDragNestCooldownTicks() > 0) {
                sawResetWithCooldown.set(true);
            }
        });

        helper.runAfterDelay(300, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Drag-to-nest should remain natural AI movement, not forced-path mode");
            double reduced = startAnchorDistance[0] - minAnchorDistance[0];
            double auditedReduced = spider.getDragNestAnchorDistanceReduced();
            if (!sawGrabPull.get()
                    || !sawWindup.get()
                    || !sawDragging.get()
                    || !sawRecovery.get()
                    || !sawCooldown.get()
                    || !sawResetWithCooldown.get()
                    || !sawAnchor.get()
                    || !sawMovedTarget.get()
                    || !sawFacingTarget.get()
                    || !keptTarget.get()
                    || lostTargetDuringDrag.get()
                    || !sequenceAfterGrab.get()
                    || reduced < 0.45D
                    || auditedReduced < 0.45D
                    || lowestHealth[0] < startHealth - 0.01D
                    || firstWindupTick[0] <= firstGrabTick[0]
                    || firstDraggingTick[0] <= firstWindupTick[0]
                    || firstRecoveryTick[0] <= firstDraggingTick[0]) {
                failAndDiscard(helper,
                        "Ground spider should follow grab/pull with anchor-directed drag, recover, and deal no damage; sawGrabPull="
                        + sawGrabPull.get()
                        + " sawWindup=" + sawWindup.get()
                        + " sawDragging=" + sawDragging.get()
                        + " sawRecovery=" + sawRecovery.get()
                        + " sawCooldown=" + sawCooldown.get()
                        + " sawResetWithCooldown=" + sawResetWithCooldown.get()
                        + " sawAnchor=" + sawAnchor.get()
                        + " sawMovedTarget=" + sawMovedTarget.get()
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " keptTarget=" + keptTarget.get()
                        + " lostTargetDuringDrag=" + lostTargetDuringDrag.get()
                        + " sequenceAfterGrab=" + sequenceAfterGrab.get()
                        + " firstGrabTick=" + firstGrabTick[0]
                        + " firstWindupTick=" + firstWindupTick[0]
                        + " firstDraggingTick=" + firstDraggingTick[0]
                        + " firstRecoveryTick=" + firstRecoveryTick[0]
                        + " startHealth=" + startHealth
                        + " lowestHealth=" + lowestHealth[0]
                        + " startAnchorDistance=" + startAnchorDistance[0]
                        + " minAnchorDistance=" + minAnchorDistance[0]
                        + " reduced=" + reduced
                        + " auditedReduced=" + auditedReduced
                        + " dragNestPhase=" + spider.getDragNestPhaseName()
                        + " dragNestTicks=" + spider.getDragNestTicks()
                        + " cooldownTicks=" + spider.getDragNestCooldownTicks()
                        + " anchor=" + spider.getDragNestAnchor()
                        + " movedTarget=" + spider.hasDragNestMovedTarget()
                        + " reachedAnchor=" + spider.hasDragNestReachedAnchor()
                        + " state=" + spider.getCombatPacingStateName()
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " navDone=" + spider.getNavigation().isDone(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 260, batch = "zzzzzzPackCoordination")
    public static void packCoordinationAssignsDirectAmbushAndFlankRoles(GameTestHelper helper) {
        fillFloor(helper, 13, 13);
        fillCeiling(helper, 0, 12, 6, 0, 12);
        fillWall(helper, 0, 1, 6, 0, 12);
        fillWall(helper, 12, 1, 6, 0, 12);
        for (int x = 0; x <= 12; x++) {
            for (int y = 1; y <= 6; y++) {
                helper.setBlock(x, y, 0, Blocks.STONE);
                helper.setBlock(x, y, 12, Blocks.STONE);
            }
        }

        BlockPos targetPos = new BlockPos(5, 1, 6);
        BlockPos directPos = new BlockPos(3, 1, 6);
        BlockPos ambushPos = new BlockPos(8, 5, 6);
        BlockPos flankPos = new BlockPos(5, 1, 10);

        GroundSpiderEntity directSpider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), directPos);
        GroundSpiderEntity ambushSpider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), ambushPos);
        GroundSpiderEntity flankSpider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), flankPos);
        placeAttached(helper, ambushSpider, ambushPos, Direction.UP);

        for (GroundSpiderEntity spider : new GroundSpiderEntity[] { directSpider, ambushSpider, flankSpider }) {
            setFollowRange(spider, 24.0D);
            if (spider.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.12D);
            }
            if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
            }
        }

        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        target.setNoGravity(true);
        target.setHealth(80.0F);
        target.addTag(GroundSpiderEntity.PACK_COORDINATION_TEST_TARGET_TAG);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));

        directSpider.setTarget(target);
        ambushSpider.setTarget(target);
        flankSpider.setTarget(target);

        AtomicBoolean sawCoordination = new AtomicBoolean(false);
        AtomicBoolean sawDirectRole = new AtomicBoolean(false);
        AtomicBoolean sawAmbushRole = new AtomicBoolean(false);
        AtomicBoolean sawFlankRole = new AtomicBoolean(false);
        AtomicBoolean sawRoleDiversity = new AtomicBoolean(false);
        AtomicBoolean sawStableRoleTicks = new AtomicBoolean(false);
        AtomicBoolean sawAmbushMovement = new AtomicBoolean(false);
        AtomicBoolean sawFlankOffset = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        AtomicBoolean usedForcedPath = new AtomicBoolean(false);
        int[] maxPackSize = { 0 };
        int[] maxDirectRoleTicks = { 0 };
        int[] maxAmbushRoleTicks = { 0 };
        int[] maxFlankRoleTicks = { 0 };

        helper.onEachTick(() -> {
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
            }

            for (GroundSpiderEntity spider : new GroundSpiderEntity[] { directSpider, ambushSpider, flankSpider }) {
                if (spider.getTarget() != target) {
                    keptTarget.set(false);
                    spider.setTarget(target);
                }
                if (spider.isFollowingForcedPath()) {
                    usedForcedPath.set(true);
                }
                if (spider.isPackCoordinating()) {
                    sawCoordination.set(true);
                    maxPackSize[0] = Math.max(maxPackSize[0], spider.getPackSize());
                    if (spider.getPackDirectCount() >= 1
                            && spider.getPackAmbushCount() >= 1
                            && spider.getPackFlankCount() >= 1) {
                        sawRoleDiversity.set(true);
                    }
                    if (spider.getPackRoleTicks() >= 6) {
                        sawStableRoleTicks.set(true);
                    }
                }
            }

            if (directSpider.isPackDirectPressureRole()) {
                sawDirectRole.set(true);
                maxDirectRoleTicks[0] = Math.max(maxDirectRoleTicks[0], directSpider.getPackRoleTicks());
            }
            if (ambushSpider.isPackAmbushRole()) {
                sawAmbushRole.set(true);
                maxAmbushRoleTicks[0] = Math.max(maxAmbushRoleTicks[0], ambushSpider.getPackRoleTicks());
                if (ambushSpider.isCeilingStalking()) {
                    sawAmbushMovement.set(true);
                }
            }
            if (flankSpider.isPackFlankRole()) {
                sawFlankRole.set(true);
                maxFlankRoleTicks[0] = Math.max(maxFlankRoleTicks[0], flankSpider.getPackRoleTicks());
                if (Math.abs(flankSpider.getZ() - target.getZ()) >= 2.5D) {
                    sawFlankOffset.set(true);
                }
            }
        });

        helper.runAfterDelay(190, () -> {
            if (!sawCoordination.get()
                    || !sawDirectRole.get()
                    || !sawAmbushRole.get()
                    || !sawFlankRole.get()
                    || !sawRoleDiversity.get()
                    || !sawStableRoleTicks.get()
                    || !sawAmbushMovement.get()
                    || !sawFlankOffset.get()
                    || !keptTarget.get()
                    || usedForcedPath.get()
                    || maxPackSize[0] < 3) {
                failAndDiscard(helper,
                        "Ground spiders should coordinate around the same target with direct, ambush, and flank roles; sawCoordination="
                        + sawCoordination.get()
                        + " sawDirectRole=" + sawDirectRole.get()
                        + " sawAmbushRole=" + sawAmbushRole.get()
                        + " sawFlankRole=" + sawFlankRole.get()
                        + " sawRoleDiversity=" + sawRoleDiversity.get()
                        + " sawStableRoleTicks=" + sawStableRoleTicks.get()
                        + " sawAmbushMovement=" + sawAmbushMovement.get()
                        + " sawFlankOffset=" + sawFlankOffset.get()
                        + " keptTarget=" + keptTarget.get()
                        + " usedForcedPath=" + usedForcedPath.get()
                        + " maxPackSize=" + maxPackSize[0]
                        + " maxDirectRoleTicks=" + maxDirectRoleTicks[0]
                        + " maxAmbushRoleTicks=" + maxAmbushRoleTicks[0]
                        + " maxFlankRoleTicks=" + maxFlankRoleTicks[0]
                        + " directRole=" + directSpider.getPackRoleName()
                        + " ambushRole=" + ambushSpider.getPackRoleName()
                        + " flankRole=" + flankSpider.getPackRoleName()
                        + " directPos=" + directSpider.position()
                        + " ambushPos=" + ambushSpider.position()
                        + " flankPos=" + flankSpider.position()
                        + " targetPos=" + target.position(),
                        target, directSpider, ambushSpider, flankSpider);
                return;
            }
            succeedAndDiscard(helper, target, directSpider, ambushSpider, flankSpider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 260, batch = "zzzzzzPerformance")
    public static void twentyFourSpiderMixedAiStressStaysWithinTickBudget(GameTestHelper helper) {
        fillFloor(helper, 21, 21);
        fillCeiling(helper, 0, 20, 6, 0, 20);
        fillWall(helper, 0, 1, 6, 0, 20);
        fillWall(helper, 20, 1, 6, 0, 20);
        for (int x = 0; x <= 20; x++) {
            for (int y = 1; y <= 6; y++) {
                helper.setBlock(x, y, 0, Blocks.STONE);
                helper.setBlock(x, y, 20, Blocks.STONE);
            }
        }
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(10, y, 4, Blocks.STONE);
            helper.setBlock(10, y, 16, Blocks.STONE);
            helper.setBlock(4, y, 10, Blocks.STONE);
            helper.setBlock(16, y, 10, Blocks.STONE);
        }

        IronGolem[] targets = new IronGolem[] {
                stressTarget(helper, new BlockPos(10, 1, 10)),
                stressTarget(helper, new BlockPos(5, 1, 5)),
                stressTarget(helper, new BlockPos(15, 1, 15))
        };
        Vec3[] targetAnchors = new Vec3[] {
                helper.absoluteVec(new Vec3(10.5D, 1.0D, 10.5D)),
                helper.absoluteVec(new Vec3(5.5D, 1.0D, 5.5D)),
                helper.absoluteVec(new Vec3(15.5D, 1.0D, 15.5D))
        };

        BlockPos[] spiderPositions = new BlockPos[] {
                new BlockPos(4, 1, 10), new BlockPos(16, 1, 10), new BlockPos(10, 1, 4), new BlockPos(10, 1, 16),
                new BlockPos(6, 5, 10), new BlockPos(14, 5, 10), new BlockPos(1, 3, 10), new BlockPos(19, 3, 10),
                new BlockPos(2, 1, 5), new BlockPos(8, 1, 5), new BlockPos(5, 1, 2), new BlockPos(5, 1, 8),
                new BlockPos(3, 5, 5), new BlockPos(7, 5, 5), new BlockPos(1, 3, 5), new BlockPos(5, 3, 1),
                new BlockPos(12, 1, 15), new BlockPos(18, 1, 15), new BlockPos(15, 1, 12), new BlockPos(15, 1, 18),
                new BlockPos(13, 5, 15), new BlockPos(17, 5, 15), new BlockPos(19, 3, 15), new BlockPos(15, 3, 19)
        };
        Direction[] attachments = new Direction[] {
                Direction.DOWN, Direction.DOWN, Direction.DOWN, Direction.DOWN,
                Direction.UP, Direction.UP, Direction.WEST, Direction.EAST,
                Direction.DOWN, Direction.DOWN, Direction.DOWN, Direction.DOWN,
                Direction.UP, Direction.UP, Direction.WEST, Direction.NORTH,
                Direction.DOWN, Direction.DOWN, Direction.DOWN, Direction.DOWN,
                Direction.UP, Direction.UP, Direction.EAST, Direction.SOUTH
        };
        GroundSpiderEntity[] spiders = new GroundSpiderEntity[spiderPositions.length];
        for (int i = 0; i < spiderPositions.length; i++) {
            GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPositions[i]);
            if (attachments[i] != Direction.DOWN) {
                placeAttached(helper, spider, spiderPositions[i], attachments[i]);
            }
            setFollowRange(spider, 36.0D);
            if (spider.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.15D);
            }
            if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
            }
            LivingEntity target = targets[i / 8];
            spider.setTarget(target);
            spider.getNavigation().moveTo(target, 1.0D);
            spiders[i] = spider;
        }

        HashSet<String> observedStates = new HashSet<>();
        AtomicBoolean droppedTarget = new AtomicBoolean(false);
        AtomicBoolean sawPathing = new AtomicBoolean(false);
        AtomicBoolean sawCeilingAttachment = new AtomicBoolean(false);
        AtomicBoolean sawPackDiversity = new AtomicBoolean(false);
        int[] maxActiveSpiders = { 0 };
        int[] maxPackSize = { 0 };
        int[] tick = { 0 };
        long[] previousTickNanos = { System.nanoTime() };
        long[] sampledTotalNanos = { 0L };
        long[] sampledMaxNanos = { 0L };
        int[] sampledTicks = { 0 };

        helper.onEachTick(() -> {
            tick[0]++;
            long now = System.nanoTime();
            long elapsed = now - previousTickNanos[0];
            previousTickNanos[0] = now;
            if (tick[0] > 35) {
                sampledTicks[0]++;
                sampledTotalNanos[0] += elapsed;
                sampledMaxNanos[0] = Math.max(sampledMaxNanos[0], elapsed);
            }

            for (int i = 0; i < targets.length; i++) {
                IronGolem target = targets[i];
                if (target.isAlive()) {
                    double offset = Math.sin((tick[0] + (i * 9)) * 0.13D) * 0.55D;
                    target.setDeltaMovement(Vec3.ZERO);
                    target.setPos(targetAnchors[i].x + offset, targetAnchors[i].y, targetAnchors[i].z - offset);
                }
            }

            int active = 0;
            for (int i = 0; i < spiders.length; i++) {
                GroundSpiderEntity spider = spiders[i];
                LivingEntity expectedTarget = targets[i / 8];
                if (spider != null && spider.isAlive() && !spider.isRemoved()) {
                    active++;
                    if (spider.getTarget() != expectedTarget) {
                        droppedTarget.set(true);
                        spider.setTarget(expectedTarget);
                    }
                    if (!spider.getNavigation().isDone()) {
                        sawPathing.set(true);
                    }
                    if (spider.getAttachmentDirection() == Direction.UP
                            || spider.getAttachmentDirection() == Direction.NORTH
                            || spider.getAttachmentDirection() == Direction.SOUTH
                            || spider.getAttachmentDirection() == Direction.EAST
                            || spider.getAttachmentDirection() == Direction.WEST) {
                        sawCeilingAttachment.set(true);
                    }
                    recordStressState(observedStates, spider);
                    if (spider.isPackCoordinating()) {
                        maxPackSize[0] = Math.max(maxPackSize[0], spider.getPackSize());
                        if (spider.getPackDirectCount() >= 1
                                && spider.getPackAmbushCount() >= 1
                                && spider.getPackFlankCount() >= 1) {
                            sawPackDiversity.set(true);
                        }
                    }
                }
            }
            maxActiveSpiders[0] = Math.max(maxActiveSpiders[0], active);
        });

        helper.runAfterDelay(210, () -> {
            double averageTickMs = sampledTicks[0] == 0
                    ? 0.0D
                    : sampledTotalNanos[0] / 1_000_000.0D / sampledTicks[0];
            double maxTickMs = sampledMaxNanos[0] / 1_000_000.0D;
            int stateCount = observedStates.size();
            if (maxActiveSpiders[0] < 24
                    || droppedTarget.get()
                    || !sawPathing.get()
                    || !sawCeilingAttachment.get()
                    || !sawPackDiversity.get()
                    || maxPackSize[0] < 8
                    || stateCount < 5
                    || averageTickMs > 75.0D
                    || maxTickMs > 250.0D) {
                failAndDiscard(helper,
                        "Twenty-four-spider mixed AI stress should retain all spiders, show varied active goals, and stay within tick budget; active="
                        + maxActiveSpiders[0]
                        + " droppedTarget=" + droppedTarget.get()
                        + " sawPathing=" + sawPathing.get()
                        + " sawSurfaceAttachment=" + sawCeilingAttachment.get()
                        + " sawPackDiversity=" + sawPackDiversity.get()
                        + " maxPackSize=" + maxPackSize[0]
                        + " states=" + observedStates
                        + " averageTickMs=" + averageTickMs
                        + " maxTickMs=" + maxTickMs,
                        combineEntities(targets, spiders));
                return;
            }
            Spiders.LOGGER.info("spiders_mixed_ai_stress_result spiders={} states={} max_pack_size={} average_tick_ms={} max_tick_ms={}",
                    maxActiveSpiders[0],
                    observedStates,
                    maxPackSize[0],
                    averageTickMs,
                    maxTickMs);
            succeedAndDiscard(helper, combineEntities(targets, spiders));
        });
    }

    @GameTest(template = "arena", timeoutTicks = 280, batch = "zzzzzzzEscapeCutting")
    public static void escapeCuttingMovesToDoorwayCutoffInsteadOfTarget(GameTestHelper helper) {
        for (int y = 1; y <= 5; y++) {
            clearLayer(helper, 0, 15, y, 0, 10);
        }
        fillFloor(helper, 16, 11);
        fillCeiling(helper, 0, 15, 5, 0, 10);
        for (int z = 0; z <= 10; z++) {
            for (int y = 1; y <= 5; y++) {
                helper.setBlock(11, y, z, Blocks.STONE);
            }
        }
        for (int z = 4; z <= 6; z++) {
            for (int y = 1; y <= 2; y++) {
                helper.setBlock(11, y, z, Blocks.AIR);
            }
        }

        BlockPos targetPos = new BlockPos(5, 1, 5);
        BlockPos spiderPos = new BlockPos(2, 1, 8);
        BlockPos doorwayAnchor = new BlockPos(11, 1, 5);
        BlockPos absoluteDoorwayAnchor = helper.absolutePos(doorwayAnchor);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 24.0D);
        if (spider.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.13D);
        }
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }

        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        target.setNoGravity(true);
        target.setHealth(80.0F);
        target.addTag(GroundSpiderEntity.ESCAPE_CUTTING_TEST_TARGET_TAG);
        target.setYRot(-90.0F);
        target.setXRot(0.0F);
        target.yHeadRot = -90.0F;
        target.yBodyRot = -90.0F;
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));

        spider.setTarget(target);

        AtomicBoolean sawEscapeCutting = new AtomicBoolean(false);
        AtomicBoolean sawDoorwayAnchor = new AtomicBoolean(false);
        AtomicBoolean sawPathStarted = new AtomicBoolean(false);
        AtomicBoolean sawReachedOrReduced = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        AtomicBoolean usedForcedPath = new AtomicBoolean(false);
        AtomicBoolean sawUnexpectedAttack = new AtomicBoolean(false);
        AtomicReference<BlockPos> lastAnchor = new AtomicReference<>();
        double[] bestDistanceReduced = { 0.0D };

        helper.onEachTick(() -> {
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
                target.setYRot(-90.0F);
                target.yHeadRot = -90.0F;
                target.yBodyRot = -90.0F;
            }

            if (spider.getTarget() != target) {
                keptTarget.set(false);
                spider.setTarget(target);
            }
            if (spider.isFollowingForcedPath()) {
                usedForcedPath.set(true);
            }
            if (!"none".equals(spider.getDropAttackPhaseName())
                    || !"none".equals(spider.getWebShotPhaseName())
                    || !"none".equals(spider.getWebLowerPhaseName())
                    || !"none".equals(spider.getPouncePhaseName())
                    || !"none".equals(spider.getGrabPullPhaseName())
                    || !"none".equals(spider.getDragNestPhaseName())) {
                sawUnexpectedAttack.set(true);
            }

            if (spider.isEscapeCutting()) {
                sawEscapeCutting.set(true);
                BlockPos anchor = spider.getEscapeCuttingAnchor();
                lastAnchor.set(anchor);
                if (anchor != null
                        && anchor.distSqr(absoluteDoorwayAnchor) <= 1.0D
                        && anchor.distSqr(target.blockPosition()) >= 16.0D
                        && "east".equals(spider.getEscapeCuttingRouteDirectionName())) {
                    sawDoorwayAnchor.set(true);
                }
                if (spider.hasEscapeCuttingPathStarted()) {
                    sawPathStarted.set(true);
                }
                bestDistanceReduced[0] = Math.max(bestDistanceReduced[0], spider.getEscapeCuttingAnchorDistanceReduced());
                if (spider.hasEscapeCuttingReachedAnchor() || spider.getEscapeCuttingAnchorDistanceReduced() >= 0.55D) {
                    sawReachedOrReduced.set(true);
                }
            }
        });

        helper.runAfterDelay(210, () -> {
            if (!sawEscapeCutting.get()
                    || !sawDoorwayAnchor.get()
                    || !sawPathStarted.get()
                    || !sawReachedOrReduced.get()
                    || !keptTarget.get()
                    || usedForcedPath.get()
                    || sawUnexpectedAttack.get()) {
                failAndDiscard(helper,
                        "Ground spider should cut off the doorway instead of only chasing the target; sawEscapeCutting="
                        + sawEscapeCutting.get()
                        + " sawDoorwayAnchor=" + sawDoorwayAnchor.get()
                        + " sawPathStarted=" + sawPathStarted.get()
                        + " sawReachedOrReduced=" + sawReachedOrReduced.get()
                        + " keptTarget=" + keptTarget.get()
                        + " usedForcedPath=" + usedForcedPath.get()
                        + " sawUnexpectedAttack=" + sawUnexpectedAttack.get()
                        + " escapeStatus=" + spider.getEscapeCuttingStatus()
                        + " bestDistanceReduced=" + bestDistanceReduced[0]
                        + " lastAnchor=" + lastAnchor.get()
                        + " route=" + spider.getEscapeCuttingRouteDirectionName()
                        + " combatState=" + spider.getCombatPacingStateName()
                        + " combatTicks=" + spider.getCombatPacingTicks()
                        + " targetTags=" + target.getTags()
                        + " spiderPos=" + spider.position()
                        + " targetPos=" + target.position(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 190, batch = "zzzzzzzzLineOfSightStalking")
    public static void lineOfSightStalkingFreezesWhenWatchedThenClosesWhenUnwatched(GameTestHelper helper) {
        fillFloor(helper, 10, 7);

        BlockPos spiderPos = new BlockPos(2, 1, 3);
        BlockPos targetPos = new BlockPos(7, 1, 3);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 14.0D);
        if (spider.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.10D);
        }
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }

        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        target.setNoGravity(true);
        target.setHealth(80.0F);
        target.addTag(GroundSpiderEntity.LINE_OF_SIGHT_STALKING_TEST_TARGET_TAG);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));

        spider.setTarget(target);

        AtomicBoolean sawStalking = new AtomicBoolean(false);
        AtomicBoolean sawWatched = new AtomicBoolean(false);
        AtomicBoolean sawUnwatchedAdvance = new AtomicBoolean(false);
        AtomicBoolean sawTargetLooking = new AtomicBoolean(false);
        AtomicBoolean sawTargetLookAway = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean sawHeldStill = new AtomicBoolean(false);
        AtomicBoolean sawMovingAnimation = new AtomicBoolean(false);
        AtomicBoolean sawCooldown = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        AtomicBoolean usedForcedPath = new AtomicBoolean(false);
        AtomicBoolean sawUnexpectedAttack = new AtomicBoolean(false);
        double[] maxDistanceClosed = { 0.0D };
        double[] maxWatchedMovement = { 0.0D };
        double[] maxTotalMovement = { 0.0D };
        int[] firstStalkTick = { -1 };
        int[] ticks = { 0 };

        helper.onEachTick(() -> {
            ticks[0]++;
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
                Vec3 lookPoint = ticks[0] < 24
                        ? spider.getEyePosition()
                        : target.getEyePosition().add(0.0D, 0.0D, 6.0D);
                target.lookAt(EntityAnchorArgument.Anchor.EYES, lookPoint);
                target.yHeadRot = target.getYRot();
                target.yBodyRot = target.getYRot();
            }

            if (spider.getTarget() != target) {
                keptTarget.set(false);
                spider.setTarget(target);
            }
            if (spider.isFollowingForcedPath()) {
                usedForcedPath.set(true);
            }
            if (!"none".equals(spider.getDropAttackPhaseName())
                    || !"none".equals(spider.getWebShotPhaseName())
                    || !"none".equals(spider.getWebLowerPhaseName())
                    || !"none".equals(spider.getPouncePhaseName())
                    || !"none".equals(spider.getGrabPullPhaseName())
                    || !"none".equals(spider.getDragNestPhaseName())
                    || spider.isThreatDisplaying()
                    || spider.isRetreatActive()
                    || spider.isFakeRetreatActive()
                    || spider.isEscapeCutting()
                    || spider.isPackCoordinating()) {
                sawUnexpectedAttack.set(true);
            }

            if (spider.isLineOfSightStalking()) {
                sawStalking.set(true);
                if (firstStalkTick[0] < 0) {
                    firstStalkTick[0] = ticks[0];
                }
                if (spider.isLineOfSightStalkingTargetLooking()) {
                    sawTargetLooking.set(true);
                } else {
                    sawTargetLookAway.set(true);
                }
                if (spider.hasLineOfSightStalkingSawWatched()) {
                    sawWatched.set(true);
                }
                if (spider.hasLineOfSightStalkingSawUnwatchedAdvance()) {
                    sawUnwatchedAdvance.set(true);
                }
                if (spider.hasLineOfSightStalkingFacedTarget() || facesTargetOnFloor(spider, target, 45.0D)) {
                    sawFacingTarget.set(true);
                }
                if (spider.hasLineOfSightStalkingHeldStillWhileWatched()) {
                    sawHeldStill.set(true);
                }
                if (!spider.isLineOfSightStalkingTargetLooking()
                        && isRaisedWalkAnimation(spider.getAnimationAuditName())) {
                    sawMovingAnimation.set(true);
                }
                maxDistanceClosed[0] = Math.max(maxDistanceClosed[0], spider.getLineOfSightStalkingDistanceClosed());
                maxWatchedMovement[0] = Math.max(maxWatchedMovement[0], spider.getLineOfSightStalkingMaxWatchedMovement());
                maxTotalMovement[0] = Math.max(maxTotalMovement[0], spider.getLineOfSightStalkingTotalMovement());
            }
            if (sawStalking.get() && !spider.isLineOfSightStalking() && spider.getLineOfSightStalkingCooldownTicks() > 0) {
                sawCooldown.set(true);
            }
        });

        helper.runAfterDelay(145, () -> {
            if (!sawStalking.get()
                    || !sawWatched.get()
                    || !sawUnwatchedAdvance.get()
                    || !sawTargetLooking.get()
                    || !sawTargetLookAway.get()
                    || !sawFacingTarget.get()
                    || !sawHeldStill.get()
                    || !sawMovingAnimation.get()
                    || !sawCooldown.get()
                    || !keptTarget.get()
                    || usedForcedPath.get()
                    || sawUnexpectedAttack.get()
                    || maxWatchedMovement[0] > 0.35D
                    || maxDistanceClosed[0] < 0.35D
                    || firstStalkTick[0] < 0
                    || firstStalkTick[0] > 35) {
                failAndDiscard(helper,
                        "Ground spider should freeze under line of sight, then close after the watcher turns away; sawStalking="
                        + sawStalking.get()
                        + " sawWatched=" + sawWatched.get()
                        + " sawUnwatchedAdvance=" + sawUnwatchedAdvance.get()
                        + " sawTargetLooking=" + sawTargetLooking.get()
                        + " sawTargetLookAway=" + sawTargetLookAway.get()
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " sawHeldStill=" + sawHeldStill.get()
                        + " sawMovingAnimation=" + sawMovingAnimation.get()
                        + " sawCooldown=" + sawCooldown.get()
                        + " keptTarget=" + keptTarget.get()
                        + " usedForcedPath=" + usedForcedPath.get()
                        + " sawUnexpectedAttack=" + sawUnexpectedAttack.get()
                        + " firstStalkTick=" + firstStalkTick[0]
                        + " maxDistanceClosed=" + maxDistanceClosed[0]
                        + " maxWatchedMovement=" + maxWatchedMovement[0]
                        + " maxTotalMovement=" + maxTotalMovement[0]
                        + " stalkingStatus=" + spider.getLineOfSightStalkingStatus()
                        + " stalkingTicks=" + spider.getLineOfSightStalkingTicks()
                        + " stalkingCooldown=" + spider.getLineOfSightStalkingCooldownTicks()
                        + " targetLooking=" + spider.isLineOfSightStalkingTargetLooking()
                        + " watchedTicks=" + spider.getLineOfSightStalkingWatchedTicks()
                        + " unwatchedTicks=" + spider.getLineOfSightStalkingUnwatchedTicks()
                        + " animation=" + spider.getAnimationAuditName()
                        + " combatState=" + spider.getCombatPacingStateName()
                        + " spiderPos=" + spider.position()
                        + " targetPos=" + target.position(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 210, batch = "zzzzzzzzDarknessPreference")
    public static void darknessPreferenceChoosesCoveredShadowOverOpenLane(GameTestHelper helper) {
        fillFloor(helper, 13, 11);
        for (int x = 0; x <= 12; x++) {
            helper.setBlock(x, 1, 0, Blocks.STONE);
            helper.setBlock(x, 2, 0, Blocks.STONE);
            helper.setBlock(x, 1, 10, Blocks.STONE);
            helper.setBlock(x, 2, 10, Blocks.STONE);
        }
        for (int z = 0; z <= 10; z++) {
            helper.setBlock(0, 1, z, Blocks.STONE);
            helper.setBlock(0, 2, z, Blocks.STONE);
            helper.setBlock(12, 1, z, Blocks.STONE);
            helper.setBlock(12, 2, z, Blocks.STONE);
        }
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 4; z++) {
                helper.setBlock(x, 4, z, Blocks.DEEPSLATE);
            }
        }
        for (int y = 1; y <= 4; y++) {
            for (int x = 1; x <= 5; x++) {
                helper.setBlock(x, y, 1, Blocks.DEEPSLATE);
            }
            for (int z = 1; z <= 4; z++) {
                helper.setBlock(1, y, z, Blocks.DEEPSLATE);
            }
        }
        helper.setBlock(7, 3, 6, Blocks.GLOWSTONE);
        helper.setBlock(9, 3, 6, Blocks.GLOWSTONE);
        helper.setBlock(10, 3, 4, Blocks.GLOWSTONE);

        BlockPos spiderPos = new BlockPos(6, 1, 6);
        BlockPos targetPos = new BlockPos(10, 1, 6);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 18.0D);
        if (spider.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.12D);
        }
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }

        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        target.setNoGravity(true);
        target.setHealth(80.0F);
        target.addTag(GroundSpiderEntity.DARKNESS_PREFERENCE_TEST_TARGET_TAG);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));

        spider.setTarget(target);

        AtomicBoolean sawDarknessPreference = new AtomicBoolean(false);
        AtomicBoolean sawAnchor = new AtomicBoolean(false);
        AtomicBoolean sawBetterScore = new AtomicBoolean(false);
        AtomicBoolean sawDarkerAnchor = new AtomicBoolean(false);
        AtomicBoolean sawCoveredOrCorner = new AtomicBoolean(false);
        AtomicBoolean sawPathStarted = new AtomicBoolean(false);
        AtomicBoolean sawReachedOrHeld = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean sawCooldown = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        AtomicBoolean usedForcedPath = new AtomicBoolean(false);
        AtomicBoolean sawUnexpectedAttack = new AtomicBoolean(false);
        AtomicReference<BlockPos> lastAnchor = new AtomicReference<>();
        double[] maxDistanceReduced = { 0.0D };
        double[] maxScoreAdvantage = { 0.0D };
        int[] bestAnchorLight = { 99 };
        int[] openLight = { -1 };
        int[] firstDarknessTick = { -1 };
        int[] ticks = { 0 };

        helper.onEachTick(() -> {
            ticks[0]++;
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
                target.setYRot(90.0F);
                target.setXRot(0.0F);
                target.yHeadRot = 90.0F;
                target.yBodyRot = 90.0F;
            }

            if (spider.getTarget() != target) {
                keptTarget.set(false);
                spider.setTarget(target);
            }
            if (spider.isFollowingForcedPath()) {
                usedForcedPath.set(true);
            }
            if (!"none".equals(spider.getDropAttackPhaseName())
                    || !"none".equals(spider.getWebShotPhaseName())
                    || !"none".equals(spider.getWebLowerPhaseName())
                    || !"none".equals(spider.getPouncePhaseName())
                    || !"none".equals(spider.getGrabPullPhaseName())
                    || !"none".equals(spider.getDragNestPhaseName())
                    || spider.isThreatDisplaying()
                    || spider.isLineOfSightStalking()
                    || spider.isRetreatActive()
                    || spider.isFakeRetreatActive()
                    || spider.isEscapeCutting()
                    || spider.isPackCoordinating()) {
                sawUnexpectedAttack.set(true);
            }

            if (spider.isDarknessPreferenceActive()) {
                sawDarknessPreference.set(true);
                if (firstDarknessTick[0] < 0) {
                    firstDarknessTick[0] = ticks[0];
                }
                if (spider.getDarknessPreferenceAnchor() != null) {
                    sawAnchor.set(true);
                    lastAnchor.set(spider.getDarknessPreferenceAnchor());
                }
                if (spider.getDarknessPreferenceScoreAdvantage() >= 4.0D) {
                    sawBetterScore.set(true);
                }
                if (spider.isDarknessPreferenceAnchorDarkerThanOpen()) {
                    sawDarkerAnchor.set(true);
                }
                if (spider.isDarknessPreferenceCovered() || spider.isDarknessPreferenceCorner()) {
                    sawCoveredOrCorner.set(true);
                }
                if (spider.hasDarknessPreferencePathStarted()) {
                    sawPathStarted.set(true);
                }
                if (spider.hasDarknessPreferenceReachedAnchor() || spider.hasDarknessPreferenceHeldAnchor()) {
                    sawReachedOrHeld.set(true);
                }
                if (spider.hasDarknessPreferenceFacedTarget() || facesTargetOnFloor(spider, target, 55.0D)) {
                    sawFacingTarget.set(true);
                }
                maxDistanceReduced[0] = Math.max(maxDistanceReduced[0], spider.getDarknessPreferenceAnchorDistanceReduced());
                maxScoreAdvantage[0] = Math.max(maxScoreAdvantage[0], spider.getDarknessPreferenceScoreAdvantage());
                bestAnchorLight[0] = Math.min(bestAnchorLight[0], spider.getDarknessPreferenceAnchorLight());
                openLight[0] = Math.max(openLight[0], spider.getDarknessPreferenceOpenLight());
            }
            if (sawDarknessPreference.get()
                    && !spider.isDarknessPreferenceActive()
                    && spider.getDarknessPreferenceCooldownTicks() > 0) {
                sawCooldown.set(true);
            }
        });

        helper.runAfterDelay(155, () -> {
            boolean movedEnough = sawReachedOrHeld.get() || maxDistanceReduced[0] >= 0.55D;
            if (!sawDarknessPreference.get()
                    || !sawAnchor.get()
                    || !sawBetterScore.get()
                    || !sawDarkerAnchor.get()
                    || !sawCoveredOrCorner.get()
                    || !sawPathStarted.get()
                    || !movedEnough
                    || !sawFacingTarget.get()
                    || !sawCooldown.get()
                    || !keptTarget.get()
                    || usedForcedPath.get()
                    || sawUnexpectedAttack.get()
                    || firstDarknessTick[0] < 0
                    || firstDarknessTick[0] > 45) {
                failAndDiscard(helper,
                        "Ground spider should prefer a covered dark anchor over the lit open lane; sawDarknessPreference="
                        + sawDarknessPreference.get()
                        + " sawAnchor=" + sawAnchor.get()
                        + " sawBetterScore=" + sawBetterScore.get()
                        + " sawDarkerAnchor=" + sawDarkerAnchor.get()
                        + " sawCoveredOrCorner=" + sawCoveredOrCorner.get()
                        + " sawPathStarted=" + sawPathStarted.get()
                        + " sawReachedOrHeld=" + sawReachedOrHeld.get()
                        + " movedEnough=" + movedEnough
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " sawCooldown=" + sawCooldown.get()
                        + " keptTarget=" + keptTarget.get()
                        + " usedForcedPath=" + usedForcedPath.get()
                        + " sawUnexpectedAttack=" + sawUnexpectedAttack.get()
                        + " firstDarknessTick=" + firstDarknessTick[0]
                        + " maxDistanceReduced=" + maxDistanceReduced[0]
                        + " maxScoreAdvantage=" + maxScoreAdvantage[0]
                        + " bestAnchorLight=" + bestAnchorLight[0]
                        + " openLight=" + openLight[0]
                        + " darknessStatus=" + spider.getDarknessPreferenceStatus()
                        + " darknessTicks=" + spider.getDarknessPreferenceTicks()
                        + " darknessCooldown=" + spider.getDarknessPreferenceCooldownTicks()
                        + " lastAnchor=" + lastAnchor.get()
                        + " combatState=" + spider.getCombatPacingStateName()
                        + " animation=" + spider.getAnimationAuditName()
                        + " spiderPos=" + spider.position()
                        + " targetPos=" + target.position(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 360, batch = "zzzzzzzzWallPeek")
    public static void wallPeekEmergesFromCoverThenRetreats(GameTestHelper helper) {
        fillFloor(helper, 12, 8);
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(4, y, 2, Blocks.DEEPSLATE);
            helper.setBlock(4, y, 1, Blocks.DEEPSLATE);
        }

        BlockPos spiderPos = new BlockPos(3, 1, 2);
        BlockPos targetPos = new BlockPos(8, 1, 4);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 18.0D);
        if (spider.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.10D);
        }
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }

        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        target.setNoGravity(true);
        target.setHealth(80.0F);
        target.addTag(GroundSpiderEntity.WALL_PEEK_TEST_TARGET_TAG);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));

        spider.setTarget(target);

        AtomicBoolean sawWallPeek = new AtomicBoolean(false);
        AtomicBoolean sawEmerging = new AtomicBoolean(false);
        AtomicBoolean sawHolding = new AtomicBoolean(false);
        AtomicBoolean sawRetreating = new AtomicBoolean(false);
        AtomicBoolean sawCoverAnchor = new AtomicBoolean(false);
        AtomicBoolean sawPeekAnchor = new AtomicBoolean(false);
        AtomicBoolean sawPathStarted = new AtomicBoolean(false);
        AtomicBoolean sawReachedPeek = new AtomicBoolean(false);
        AtomicBoolean sawHeldPeek = new AtomicBoolean(false);
        AtomicBoolean sawRetreated = new AtomicBoolean(false);
        AtomicBoolean sawTargetRetained = new AtomicBoolean(false);
        AtomicBoolean sawCoverBlocked = new AtomicBoolean(false);
        AtomicBoolean sawPeekClear = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean sawCooldown = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        AtomicBoolean usedForcedPath = new AtomicBoolean(false);
        AtomicBoolean sawUnexpectedAttack = new AtomicBoolean(false);
        AtomicReference<BlockPos> lastCover = new AtomicReference<>();
        AtomicReference<BlockPos> lastPeek = new AtomicReference<>();
        double[] maxPeekReduced = { 0.0D };
        double[] maxCoverReturnReduced = { 0.0D };
        int[] firstWallPeekTick = { -1 };
        int[] ticks = { 0 };
        double startHealth = target.getHealth();
        double[] lowestHealth = { startHealth };

        helper.onEachTick(() -> {
            ticks[0]++;
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
                target.setYRot(90.0F);
                target.setXRot(0.0F);
                target.yHeadRot = 90.0F;
                target.yBodyRot = 90.0F;
            }

            if (spider.getTarget() != target) {
                keptTarget.set(false);
                spider.setTarget(target);
            }
            if (spider.isFollowingForcedPath()) {
                usedForcedPath.set(true);
            }
            if (!"none".equals(spider.getDropAttackPhaseName())
                    || !"none".equals(spider.getWebShotPhaseName())
                    || !"none".equals(spider.getWebLowerPhaseName())
                    || !"none".equals(spider.getPouncePhaseName())
                    || !"none".equals(spider.getGrabPullPhaseName())
                    || !"none".equals(spider.getDragNestPhaseName())
                    || spider.isThreatDisplaying()
                    || spider.isLineOfSightStalking()
                    || spider.isDarknessPreferenceActive()
                    || spider.isRetreatActive()
                    || spider.isFakeRetreatActive()
                    || spider.isEscapeCutting()
                    || spider.isPackCoordinating()) {
                sawUnexpectedAttack.set(true);
            }
            lowestHealth[0] = Math.min(lowestHealth[0], target.getHealth());

            if (spider.isWallPeeking()) {
                sawWallPeek.set(true);
                if (firstWallPeekTick[0] < 0) {
                    firstWallPeekTick[0] = ticks[0];
                }
                if ("emerging".equals(spider.getWallPeekPhaseName())) {
                    sawEmerging.set(true);
                }
                if ("holding".equals(spider.getWallPeekPhaseName())) {
                    sawHolding.set(true);
                }
                if ("retreating".equals(spider.getWallPeekPhaseName())) {
                    sawRetreating.set(true);
                }
                if (spider.getWallPeekCoverAnchor() != null) {
                    sawCoverAnchor.set(true);
                    lastCover.set(spider.getWallPeekCoverAnchor());
                }
                if (spider.getWallPeekPeekAnchor() != null) {
                    sawPeekAnchor.set(true);
                    lastPeek.set(spider.getWallPeekPeekAnchor());
                }
                if (spider.hasWallPeekPathStarted()) {
                    sawPathStarted.set(true);
                }
                if (spider.hasWallPeekReachedPeek()) {
                    sawReachedPeek.set(true);
                }
                if (spider.hasWallPeekHeldPeek()) {
                    sawHeldPeek.set(true);
                }
                if (spider.hasWallPeekRetreated()) {
                    sawRetreated.set(true);
                }
                if (spider.hasWallPeekTargetRetained()) {
                    sawTargetRetained.set(true);
                }
                if (spider.isWallPeekCoverLineOfSightBlocked()) {
                    sawCoverBlocked.set(true);
                }
                if (spider.isWallPeekPeekLineOfSightClear()) {
                    sawPeekClear.set(true);
                }
                if (spider.hasWallPeekFacedTarget() || facesTargetOnFloor(spider, target, 55.0D)) {
                    sawFacingTarget.set(true);
                }
                maxPeekReduced[0] = Math.max(maxPeekReduced[0], spider.getWallPeekPeekDistanceReduced());
                maxCoverReturnReduced[0] = Math.max(maxCoverReturnReduced[0],
                        spider.getWallPeekCoverReturnDistanceReduced());
            }
            if (sawWallPeek.get() && !spider.isWallPeeking() && spider.getWallPeekCooldownTicks() > 0) {
                sawCooldown.set(true);
                if (spider.hasWallPeekRetreated()) {
                    sawRetreated.set(true);
                }
            }
        });

        helper.runAfterDelay(300, () -> {
            boolean targetUndamaged = lowestHealth[0] >= startHealth;
            boolean peekMovementEnough = sawReachedPeek.get() || maxPeekReduced[0] >= 0.45D;
            boolean retreatEvidence = sawRetreated.get() || maxCoverReturnReduced[0] >= 0.35D;
            if (!sawWallPeek.get()
                    || !sawEmerging.get()
                    || !sawHolding.get()
                    || !sawRetreating.get()
                    || !sawCoverAnchor.get()
                    || !sawPeekAnchor.get()
                    || !sawPathStarted.get()
                    || !peekMovementEnough
                    || !sawHeldPeek.get()
                    || !retreatEvidence
                    || !sawTargetRetained.get()
                    || !sawCoverBlocked.get()
                    || !sawPeekClear.get()
                    || !sawFacingTarget.get()
                    || !sawCooldown.get()
                    || !keptTarget.get()
                    || usedForcedPath.get()
                    || sawUnexpectedAttack.get()
                    || !targetUndamaged
                    || firstWallPeekTick[0] < 0
                    || firstWallPeekTick[0] > 50) {
                failAndDiscard(helper,
                        "Ground spider should peek from covered wall/corner, hold target view, and retreat; sawWallPeek="
                        + sawWallPeek.get()
                        + " sawEmerging=" + sawEmerging.get()
                        + " sawHolding=" + sawHolding.get()
                        + " sawRetreating=" + sawRetreating.get()
                        + " sawCoverAnchor=" + sawCoverAnchor.get()
                        + " sawPeekAnchor=" + sawPeekAnchor.get()
                        + " sawPathStarted=" + sawPathStarted.get()
                        + " peekMovementEnough=" + peekMovementEnough
                        + " sawHeldPeek=" + sawHeldPeek.get()
                        + " retreatEvidence=" + retreatEvidence
                        + " sawTargetRetained=" + sawTargetRetained.get()
                        + " sawCoverBlocked=" + sawCoverBlocked.get()
                        + " sawPeekClear=" + sawPeekClear.get()
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " sawCooldown=" + sawCooldown.get()
                        + " keptTarget=" + keptTarget.get()
                        + " usedForcedPath=" + usedForcedPath.get()
                        + " sawUnexpectedAttack=" + sawUnexpectedAttack.get()
                        + " targetUndamaged=" + targetUndamaged
                        + " firstWallPeekTick=" + firstWallPeekTick[0]
                        + " maxPeekReduced=" + maxPeekReduced[0]
                        + " maxCoverReturnReduced=" + maxCoverReturnReduced[0]
                        + " wallPeekPhase=" + spider.getWallPeekPhaseName()
                        + " wallPeekStatus=" + spider.getWallPeekStatus()
                        + " wallPeekCooldown=" + spider.getWallPeekCooldownTicks()
                        + " lastCover=" + lastCover.get()
                        + " lastPeek=" + lastPeek.get()
                        + " combatState=" + spider.getCombatPacingStateName()
                        + " animation=" + spider.getAnimationAuditName()
                        + " spiderPos=" + spider.position()
                        + " targetPos=" + target.position(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 180, batch = "zzzzzzzzThreatDisplay")
    public static void threatDisplayWarnsBeforeCloseAttack(GameTestHelper helper) {
        fillFloor(helper, 9, 7);

        BlockPos spiderPos = new BlockPos(2, 1, 3);
        BlockPos targetPos = new BlockPos(5, 1, 3);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 12.0D);
        if (spider.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            spider.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.10D);
        }
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }

        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        target.setNoGravity(true);
        target.setHealth(80.0F);
        target.addTag(GroundSpiderEntity.THREAT_DISPLAY_TEST_TARGET_TAG);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));

        spider.setTarget(target);

        AtomicBoolean sawThreatDisplay = new AtomicBoolean(false);
        AtomicBoolean sawRaisedPose = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean sawHeldStill = new AtomicBoolean(false);
        AtomicBoolean sawThreatAnimation = new AtomicBoolean(false);
        AtomicBoolean sawCooldown = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        AtomicBoolean usedForcedPath = new AtomicBoolean(false);
        AtomicBoolean sawUnexpectedAttack = new AtomicBoolean(false);
        double[] maxMovement = { 0.0D };
        int[] firstThreatTick = { -1 };
        int[] ticks = { 0 };

        helper.onEachTick(() -> {
            ticks[0]++;
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
                target.setYRot(90.0F);
                target.setXRot(0.0F);
                target.yHeadRot = 90.0F;
                target.yBodyRot = 90.0F;
            }

            if (spider.getTarget() != target) {
                keptTarget.set(false);
                spider.setTarget(target);
            }
            if (spider.isFollowingForcedPath()) {
                usedForcedPath.set(true);
            }
            if (!"none".equals(spider.getDropAttackPhaseName())
                    || !"none".equals(spider.getWebShotPhaseName())
                    || !"none".equals(spider.getWebLowerPhaseName())
                    || !"none".equals(spider.getPouncePhaseName())
                    || !"none".equals(spider.getGrabPullPhaseName())
                    || !"none".equals(spider.getDragNestPhaseName())
                    || spider.isRetreatActive()
                    || spider.isFakeRetreatActive()
                    || spider.isEscapeCutting()
                    || spider.isPackCoordinating()) {
                sawUnexpectedAttack.set(true);
            }

            if (spider.isThreatDisplaying()) {
                sawThreatDisplay.set(true);
                if (firstThreatTick[0] < 0) {
                    firstThreatTick[0] = ticks[0];
                }
                if ("raised_front".equals(spider.getThreatDisplayPoseName())) {
                    sawRaisedPose.set(true);
                }
                if (spider.hasThreatDisplayFacedTarget() || facesTargetOnFloor(spider, target, 40.0D)) {
                    sawFacingTarget.set(true);
                }
                if (spider.hasThreatDisplayHeldStill()) {
                    sawHeldStill.set(true);
                }
                if ("threat_display".equals(spider.getAnimationAuditName())) {
                    sawThreatAnimation.set(true);
                }
                maxMovement[0] = Math.max(maxMovement[0], spider.getThreatDisplayMaxMovement());
            }
            if (sawThreatDisplay.get() && !spider.isThreatDisplaying() && spider.getThreatDisplayCooldownTicks() > 0) {
                sawCooldown.set(true);
            }
        });

        helper.runAfterDelay(125, () -> {
            if (!sawThreatDisplay.get()
                    || !sawRaisedPose.get()
                    || !sawFacingTarget.get()
                    || !sawHeldStill.get()
                    || !sawThreatAnimation.get()
                    || !sawCooldown.get()
                    || !keptTarget.get()
                    || usedForcedPath.get()
                    || sawUnexpectedAttack.get()
                    || maxMovement[0] > 0.34D
                    || firstThreatTick[0] < 0
                    || firstThreatTick[0] > 30) {
                failAndDiscard(helper,
                        "Ground spider should warn from close range before attacking; sawThreatDisplay="
                        + sawThreatDisplay.get()
                        + " sawRaisedPose=" + sawRaisedPose.get()
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " sawHeldStill=" + sawHeldStill.get()
                        + " sawThreatAnimation=" + sawThreatAnimation.get()
                        + " sawCooldown=" + sawCooldown.get()
                        + " keptTarget=" + keptTarget.get()
                        + " usedForcedPath=" + usedForcedPath.get()
                        + " sawUnexpectedAttack=" + sawUnexpectedAttack.get()
                        + " firstThreatTick=" + firstThreatTick[0]
                        + " maxMovement=" + maxMovement[0]
                        + " threatStatus=" + spider.getThreatDisplayStatus()
                        + " threatTicks=" + spider.getThreatDisplayTicks()
                        + " threatCooldown=" + spider.getThreatDisplayCooldownTicks()
                        + " threatPose=" + spider.getThreatDisplayPoseName()
                        + " threatFacingTicks=" + spider.getThreatDisplayFacingTicks()
                        + " threatHeldStill=" + spider.hasThreatDisplayHeldStill()
                        + " animation=" + spider.getAnimationAuditName()
                        + " combatState=" + spider.getCombatPacingStateName()
                        + " spiderPos=" + spider.position()
                        + " targetPos=" + target.position(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 240, batch = "zzzzzzMixedSurfaceChangingThreats")
    public static void pounceWindsUpThenCommitsLeapAndRecovers(GameTestHelper helper) {
        fillFloor(helper, 9, 7);

        BlockPos spiderPos = new BlockPos(2, 1, 3);
        BlockPos targetPos = new BlockPos(5, 1, 3);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 10.0D);
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }

        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        target.setNoGravity(true);
        target.setHealth(24.0F);
        target.addTag(GroundSpiderEntity.POUNCE_TEST_TARGET_TAG);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }

        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));
        spider.setTarget(target);

        AtomicBoolean sawWindup = new AtomicBoolean(false);
        AtomicBoolean sawLaunch = new AtomicBoolean(false);
        AtomicBoolean sawRecovery = new AtomicBoolean(false);
        AtomicBoolean sawCooldown = new AtomicBoolean(false);
        AtomicBoolean sawResetWithCooldown = new AtomicBoolean(false);
        AtomicBoolean windupBeforeLaunch = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean sawPounceAnimation = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        AtomicBoolean lostTargetDuringPounce = new AtomicBoolean(false);
        AtomicBoolean sawDamageSpend = new AtomicBoolean(false);
        int[] ticks = { 0 };
        int[] firstWindupTick = { -1 };
        int[] firstLaunchTick = { -1 };
        int[] firstRecoveryTick = { -1 };
        int[] damageSpendTransitions = { 0 };
        boolean[] previousDamageSpent = { false };
        double startHealth = target.getHealth();
        double[] lowestHealth = { startHealth };
        Vec3 startPosition = spider.position();
        Vec3 targetDirection = horizontal(targetAnchor.subtract(startPosition));
        double[] maxForwardDisplacement = { 0.0D };
        double[] maxStepSpeed = { 0.0D };

        helper.onEachTick(() -> {
            ticks[0]++;
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
            }
            if (spider.isPounceActive() && spider.getTarget() != target) {
                lostTargetDuringPounce.set(true);
            }
            if (spider.getTarget() != target) {
                keptTarget.set(false);
                spider.setTarget(target);
            }
            lowestHealth[0] = Math.min(lowestHealth[0], target.getHealth());

            Vec3 displacement = horizontal(spider.position().subtract(startPosition));
            if (targetDirection.lengthSqr() > 1.0E-6D) {
                maxForwardDisplacement[0] = Math.max(maxForwardDisplacement[0],
                        displacement.dot(targetDirection.normalize()));
            }
            maxStepSpeed[0] = Math.max(maxStepSpeed[0], horizontal(spider.getDeltaMovement()).length());

            if (spider.isPounceWindup()) {
                sawWindup.set(true);
                if (firstWindupTick[0] < 0) {
                    firstWindupTick[0] = ticks[0];
                }
                if (facesTargetOnFloor(spider, target, 55.0D)) {
                    sawFacingTarget.set(true);
                }
            }

            if (spider.isPounceLeaping()) {
                sawLaunch.set(true);
                if (firstLaunchTick[0] < 0) {
                    firstLaunchTick[0] = ticks[0];
                }
                if (firstWindupTick[0] >= 0 && ticks[0] > firstWindupTick[0]) {
                    windupBeforeLaunch.set(true);
                }
                if ("ground_spider_jump_forward".equals(spider.getAnimationAuditName())) {
                    sawPounceAnimation.set(true);
                }
                if (facesTargetOnFloor(spider, target, 65.0D)) {
                    sawFacingTarget.set(true);
                }
            }

            if (spider.isPounceRecovering()) {
                sawRecovery.set(true);
                if (firstRecoveryTick[0] < 0) {
                    firstRecoveryTick[0] = ticks[0];
                }
                if (spider.getPounceCooldownTicks() > 0) {
                    sawCooldown.set(true);
                }
                if ("ground_spider_jump_forward".equals(spider.getAnimationAuditName())) {
                    sawPounceAnimation.set(true);
                }
            }

            if (sawRecovery.get() && !spider.isPounceActive() && spider.getPounceCooldownTicks() > 0) {
                sawResetWithCooldown.set(true);
            }
            if (spider.isPounceDamageSpent()) {
                sawDamageSpend.set(true);
            }
            if (!previousDamageSpent[0] && spider.isPounceDamageSpent()) {
                damageSpendTransitions[0]++;
            }
            previousDamageSpent[0] = spider.isPounceDamageSpent();
        });

        helper.runAfterDelay(130, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Pounce should remain natural AI movement, not forced-path mode");
            helper.assertEntityProperty(spider, entity -> entity.getTarget() == target,
                    "Ground spider should keep the target through pounce");
            if (!sawWindup.get()
                    || !sawLaunch.get()
                    || !sawRecovery.get()
                    || !sawCooldown.get()
                    || !sawResetWithCooldown.get()
                    || !windupBeforeLaunch.get()
                    || !sawFacingTarget.get()
                    || !sawPounceAnimation.get()
                    || !sawDamageSpend.get()
                    || !keptTarget.get()
                    || lostTargetDuringPounce.get()
                    || damageSpendTransitions[0] != 1
                    || lowestHealth[0] >= startHealth
                    || maxForwardDisplacement[0] < 1.6D
                    || maxStepSpeed[0] < 0.35D
                    || firstRecoveryTick[0] <= firstLaunchTick[0]) {
                failAndDiscard(helper,
                        "Ground spider should wind up, commit a fast pounce, spend one damage window, and recover; sawWindup="
                        + sawWindup.get()
                        + " sawLaunch=" + sawLaunch.get()
                        + " sawRecovery=" + sawRecovery.get()
                        + " sawCooldown=" + sawCooldown.get()
                        + " sawResetWithCooldown=" + sawResetWithCooldown.get()
                        + " windupBeforeLaunch=" + windupBeforeLaunch.get()
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " sawPounceAnimation=" + sawPounceAnimation.get()
                        + " sawDamageSpend=" + sawDamageSpend.get()
                        + " keptTarget=" + keptTarget.get()
                        + " lostTargetDuringPounce=" + lostTargetDuringPounce.get()
                        + " damageSpendTransitions=" + damageSpendTransitions[0]
                        + " firstWindupTick=" + firstWindupTick[0]
                        + " firstLaunchTick=" + firstLaunchTick[0]
                        + " firstRecoveryTick=" + firstRecoveryTick[0]
                        + " startHealth=" + startHealth
                        + " lowestHealth=" + lowestHealth[0]
                        + " maxForwardDisplacement=" + maxForwardDisplacement[0]
                        + " maxStepSpeed=" + maxStepSpeed[0]
                        + " pouncePhase=" + spider.getPouncePhaseName()
                        + " pounceTicks=" + spider.getPounceTicks()
                        + " cooldownTicks=" + spider.getPounceCooldownTicks()
                        + " launched=" + spider.isPounceLaunched()
                        + " damageSpent=" + spider.isPounceDamageSpent()
                        + " state=" + spider.getCombatPacingStateName()
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " navDone=" + spider.getNavigation().isDone(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 260, batch = "zzzzzzMixedSurfaceChangingThreats")
    public static void retreatRepositionsAfterDamageWhileFacingTarget(GameTestHelper helper) {
        fillFloor(helper, 10, 7);
        fillWall(helper, 0, 1, 5, 0, 6);

        BlockPos spiderPos = new BlockPos(3, 1, 3);
        BlockPos targetPos = new BlockPos(6, 1, 3);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 12.0D);
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }

        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        target.setNoGravity(true);
        target.addTag(GroundSpiderEntity.RETREAT_TEST_TARGET_TAG);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }

        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));
        spider.setTarget(target);
        double startX = spider.getX();

        AtomicBoolean damageApplied = new AtomicBoolean(false);
        AtomicBoolean sawRetreatMoving = new AtomicBoolean(false);
        AtomicBoolean sawRetreatRecovery = new AtomicBoolean(false);
        AtomicBoolean sawCooldown = new AtomicBoolean(false);
        AtomicBoolean sawDamageTrigger = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        AtomicBoolean sawResetWithCooldown = new AtomicBoolean(false);
        double[] distanceAtDamage = { Math.sqrt(spider.distanceToSqr(target)) };
        double[] maxDistanceAfterDamage = { distanceAtDamage[0] };
        double[] yAtDamage = { spider.getY() };
        double[] maxYAfterDamage = { spider.getY() };
        int[] retreatMovingTicks = { 0 };

        helper.onEachTick(() -> {
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
            }
            if (damageApplied.get()) {
                if (spider.getTarget() != target) {
                    keptTarget.set(false);
                    spider.setTarget(target);
                }
                double distance = Math.sqrt(spider.distanceToSqr(target));
                maxDistanceAfterDamage[0] = Math.max(maxDistanceAfterDamage[0], distance);
                maxYAfterDamage[0] = Math.max(maxYAfterDamage[0], spider.getY());
            }
            if (spider.isRetreatMoving()) {
                retreatMovingTicks[0]++;
                sawRetreatMoving.set(true);
                if (spider.isRetreatTriggeredByDamage()) {
                    sawDamageTrigger.set(true);
                }
                if (facesTargetOnFloor(spider, target, 65.0D)) {
                    sawFacingTarget.set(true);
                }
            }
            if (spider.isRetreatRecovering()) {
                sawRetreatRecovery.set(true);
                if (spider.getRetreatCooldownTicks() > 0) {
                    sawCooldown.set(true);
                }
                if (facesTargetOnFloor(spider, target, 65.0D)) {
                    sawFacingTarget.set(true);
                }
            }
            if (sawRetreatRecovery.get() && !spider.isRetreatActive() && spider.getRetreatCooldownTicks() > 0) {
                sawResetWithCooldown.set(true);
            }
        });

        helper.runAfterDelay(12, () -> {
            distanceAtDamage[0] = Math.sqrt(spider.distanceToSqr(target));
            maxDistanceAfterDamage[0] = distanceAtDamage[0];
            yAtDamage[0] = spider.getY();
            maxYAfterDamage[0] = spider.getY();
            damageApplied.set(spider.hurt(DamageSource.mobAttack(target), 2.0F));
            if (!damageApplied.get()) {
                failAndDiscard(helper, "Retreat test damage did not apply", target, spider);
            }
        });

        helper.runAfterDelay(160, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Retreat/reposition should remain natural movement, not forced-path mode");
            boolean gainedDistance = maxDistanceAfterDamage[0] >= distanceAtDamage[0] + 0.20D;
            boolean gainedHeight = maxYAfterDamage[0] >= yAtDamage[0] + 0.35D;
            if (!damageApplied.get()
                    || !sawRetreatMoving.get()
                    || !sawRetreatRecovery.get()
                    || !sawCooldown.get()
                    || !sawDamageTrigger.get()
                    || !sawFacingTarget.get()
                    || !keptTarget.get()
                    || !sawResetWithCooldown.get()
                    || retreatMovingTicks[0] < 6
                    || (!gainedDistance && !gainedHeight)) {
                failAndDiscard(helper,
                        "Ground spider should retreat/reposition after damage while keeping target pressure; damageApplied="
                        + damageApplied.get()
                        + " sawRetreatMoving=" + sawRetreatMoving.get()
                        + " sawRetreatRecovery=" + sawRetreatRecovery.get()
                        + " sawCooldown=" + sawCooldown.get()
                        + " sawDamageTrigger=" + sawDamageTrigger.get()
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " keptTarget=" + keptTarget.get()
                        + " sawResetWithCooldown=" + sawResetWithCooldown.get()
                        + " retreatMovingTicks=" + retreatMovingTicks[0]
                        + " distanceAtDamage=" + distanceAtDamage[0]
                        + " maxDistanceAfterDamage=" + maxDistanceAfterDamage[0]
                        + " yAtDamage=" + yAtDamage[0]
                        + " maxYAfterDamage=" + maxYAfterDamage[0]
                        + " startX=" + startX
                        + " retreatPhase=" + spider.getRetreatPhaseName()
                        + " retreatTicks=" + spider.getRetreatTicks()
                        + " retreatCooldownTicks=" + spider.getRetreatCooldownTicks()
                        + " retreatAnchor=" + spider.getRetreatAnchor()
                        + " retreatStartDistance=" + spider.getRetreatStartDistance()
                        + " retreatMaxDistance=" + spider.getRetreatMaxDistance()
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " navDone=" + spider.getNavigation().isDone(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 300, batch = "zzzzzzMixedSurfaceChangingThreats")
    public static void failedPounceTriggersRetreatReposition(GameTestHelper helper) {
        fillFloor(helper, 12, 9);
        fillWall(helper, 0, 1, 5, 0, 8);

        BlockPos spiderPos = new BlockPos(2, 1, 4);
        BlockPos targetPos = new BlockPos(5, 1, 4);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 16.0D);
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }

        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        target.setNoGravity(true);
        target.setInvulnerable(true);
        target.setHealth(24.0F);
        target.addTag(GroundSpiderEntity.POUNCE_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.RETREAT_TEST_TARGET_TAG);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }

        Vec3 initialAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));
        Vec3 evasionAnchor = helper.absoluteVec(new Vec3(9.5D, 1.0D, 7.5D));
        AtomicReference<Vec3> targetAnchor = new AtomicReference<>(initialAnchor);
        spider.setTarget(target);

        AtomicBoolean movedTargetForMiss = new AtomicBoolean(false);
        AtomicBoolean sawPounceWindup = new AtomicBoolean(false);
        AtomicBoolean sawPounceLaunch = new AtomicBoolean(false);
        AtomicBoolean sawPounceRecovery = new AtomicBoolean(false);
        AtomicBoolean sawRetreatMoving = new AtomicBoolean(false);
        AtomicBoolean sawMissTrigger = new AtomicBoolean(false);
        AtomicBoolean sawRetreatCooldown = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        AtomicBoolean pounceSpentDamage = new AtomicBoolean(false);
        double startHealth = target.getHealth();
        double[] lowestHealth = { startHealth };

        helper.onEachTick(() -> {
            if (target.isAlive()) {
                Vec3 anchor = targetAnchor.get();
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(anchor.x, anchor.y, anchor.z);
            }
            if (spider.getTarget() != target) {
                keptTarget.set(false);
                spider.setTarget(target);
            }
            lowestHealth[0] = Math.min(lowestHealth[0], target.getHealth());

            if (spider.isPounceWindup()) {
                sawPounceWindup.set(true);
                if (spider.getPounceTicks() <= 35 && movedTargetForMiss.compareAndSet(false, true)) {
                    targetAnchor.set(evasionAnchor);
                    target.setPos(evasionAnchor.x, evasionAnchor.y, evasionAnchor.z);
                }
            }
            if (spider.isPounceLeaping()) {
                sawPounceLaunch.set(true);
            }
            if (spider.isPounceRecovering()) {
                sawPounceRecovery.set(true);
            }
            if (spider.isPounceDamageSpent()) {
                pounceSpentDamage.set(true);
            }
            if (spider.isRetreatMoving()) {
                sawRetreatMoving.set(true);
                if (spider.isRetreatTriggeredByMiss()) {
                    sawMissTrigger.set(true);
                }
                if (facesTargetOnFloor(spider, target, 70.0D)) {
                    sawFacingTarget.set(true);
                }
            }
            if (spider.isRetreatActive() && spider.getRetreatCooldownTicks() > 0) {
                sawRetreatCooldown.set(true);
            }
        });

        helper.runAfterDelay(190, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Miss-triggered retreat should remain natural movement, not forced-path mode");
            if (!sawPounceWindup.get()
                    || !movedTargetForMiss.get()
                    || !sawPounceLaunch.get()
                    || !sawPounceRecovery.get()
                    || !sawRetreatMoving.get()
                    || !sawMissTrigger.get()
                    || !sawRetreatCooldown.get()
                    || !sawFacingTarget.get()
                    || pounceSpentDamage.get()
                    || lowestHealth[0] < startHealth) {
                failAndDiscard(helper,
                        "Failed no-damage pounce should queue retreat/reposition; sawPounceWindup="
                        + sawPounceWindup.get()
                        + " movedTargetForMiss=" + movedTargetForMiss.get()
                        + " sawPounceLaunch=" + sawPounceLaunch.get()
                        + " sawPounceRecovery=" + sawPounceRecovery.get()
                        + " sawRetreatMoving=" + sawRetreatMoving.get()
                        + " sawMissTrigger=" + sawMissTrigger.get()
                        + " sawRetreatCooldown=" + sawRetreatCooldown.get()
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " keptTarget=" + keptTarget.get()
                        + " pounceSpentDamage=" + pounceSpentDamage.get()
                        + " startHealth=" + startHealth
                        + " lowestHealth=" + lowestHealth[0]
                        + " pouncePhase=" + spider.getPouncePhaseName()
                        + " retreatPhase=" + spider.getRetreatPhaseName()
                        + " retreatTicks=" + spider.getRetreatTicks()
                        + " retreatCooldownTicks=" + spider.getRetreatCooldownTicks()
                        + " retreatTriggerMiss=" + spider.isRetreatTriggeredByMiss()
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 340, batch = "zzzzzzzFakeRetreat")
    public static void fakeRetreatReengagesAfterDamageFromNewAngle(GameTestHelper helper) {
        fillFloor(helper, 14, 11);
        fillWall(helper, 0, 1, 6, 0, 10);
        fillWall(helper, 0, 1, 13, 0, 6);

        BlockPos spiderPos = new BlockPos(4, 1, 5);
        BlockPos targetPos = new BlockPos(8, 1, 5);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 18.0D);
        if (spider.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            spider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }

        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        target.setNoGravity(true);
        target.addTag(GroundSpiderEntity.RETREAT_TEST_TARGET_TAG);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }

        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));
        spider.setTarget(target);

        AtomicBoolean damageApplied = new AtomicBoolean(false);
        AtomicBoolean sawRetreatMoving = new AtomicBoolean(false);
        AtomicBoolean sawFakeFleeing = new AtomicBoolean(false);
        AtomicBoolean sawFakeRepositioning = new AtomicBoolean(false);
        AtomicBoolean sawFakeReengaging = new AtomicBoolean(false);
        AtomicBoolean sawFakeRecovery = new AtomicBoolean(false);
        AtomicBoolean sawFakeTrigger = new AtomicBoolean(false);
        AtomicBoolean sawFakeAnchor = new AtomicBoolean(false);
        AtomicBoolean sawReengageStarted = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(true);
        double[] maxDistanceGained = { 0.0D };
        double[] maxReturnClosed = { 0.0D };

        helper.onEachTick(() -> {
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
            }
            if (spider.getTarget() != target) {
                keptTarget.set(false);
                spider.setTarget(target);
            }
            if (spider.isRetreatMoving()) {
                sawRetreatMoving.set(true);
            }
            if (spider.isFakeRetreatFleeing()) {
                sawFakeFleeing.set(true);
            }
            if (spider.isFakeRetreatRepositioning()) {
                sawFakeRepositioning.set(true);
            }
            if (spider.isFakeRetreatReengaging()) {
                sawFakeReengaging.set(true);
            }
            if (spider.isFakeRetreatRecovering()) {
                sawFakeRecovery.set(true);
            }
            if (spider.isFakeRetreatTriggeredByDamage() || spider.isFakeRetreatTriggeredByMiss()) {
                sawFakeTrigger.set(true);
            }
            if (spider.getFakeRetreatAnchor() != null) {
                sawFakeAnchor.set(true);
            }
            if (spider.hasFakeRetreatReengageStarted()) {
                sawReengageStarted.set(true);
            }
            maxDistanceGained[0] = Math.max(maxDistanceGained[0], spider.getFakeRetreatDistanceGained());
            maxReturnClosed[0] = Math.max(maxReturnClosed[0], spider.getFakeRetreatReturnClosedDistance());
        });

        helper.runAfterDelay(12, () -> {
            damageApplied.set(spider.hurt(DamageSource.mobAttack(target), 2.0F));
            if (!damageApplied.get()) {
                failAndDiscard(helper, "Fake-retreat test damage did not apply", target, spider);
            }
        });

        helper.runAfterDelay(260, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Fake retreat should remain natural movement, not forced-path mode");
            if (!damageApplied.get()
                    || !sawRetreatMoving.get()
                    || !sawFakeFleeing.get()
                    || !sawFakeRepositioning.get()
                    || !sawFakeReengaging.get()
                    || !sawFakeRecovery.get()
                    || !sawFakeTrigger.get()
                    || !sawFakeAnchor.get()
                    || !sawReengageStarted.get()
                    || !keptTarget.get()
                    || maxDistanceGained[0] < 0.25D
                    || maxReturnClosed[0] < 0.35D) {
                failAndDiscard(helper,
                        "Ground spider should fake retreat after damage, then re-engage from the retreat angle; damageApplied="
                        + damageApplied.get()
                        + " sawRetreatMoving=" + sawRetreatMoving.get()
                        + " sawFakeFleeing=" + sawFakeFleeing.get()
                        + " sawFakeRepositioning=" + sawFakeRepositioning.get()
                        + " sawFakeReengaging=" + sawFakeReengaging.get()
                        + " sawFakeRecovery=" + sawFakeRecovery.get()
                        + " sawFakeTrigger=" + sawFakeTrigger.get()
                        + " sawFakeAnchor=" + sawFakeAnchor.get()
                        + " sawReengageStarted=" + sawReengageStarted.get()
                        + " keptTarget=" + keptTarget.get()
                        + " maxDistanceGained=" + maxDistanceGained[0]
                        + " maxReturnClosed=" + maxReturnClosed[0]
                        + " fakeRetreatPhase=" + spider.getFakeRetreatPhaseName()
                        + " fakeRetreatTicks=" + spider.getFakeRetreatTicks()
                        + " fakeRetreatCooldownTicks=" + spider.getFakeRetreatCooldownTicks()
                        + " fakeRetreatAnchor=" + spider.getFakeRetreatAnchor()
                        + " fakeRetreatDistanceGained=" + spider.getFakeRetreatDistanceGained()
                        + " fakeRetreatReturnClosed=" + spider.getFakeRetreatReturnClosedDistance()
                        + " retreatPhase=" + spider.getRetreatPhaseName()
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 260, batch = "circleStrafe")
    public static void circleStrafesAroundTargetWhileFacing(GameTestHelper helper) {
        fillFloor(helper, 10, 7);

        BlockPos spiderPos = new BlockPos(2, 1, 3);
        BlockPos targetPos = new BlockPos(6, 1, 3);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 10.0D);
        var target = helper.spawnWithNoFreeWill(EntityType.SHEEP, targetPos);
        target.setInvulnerable(true);
        target.setNoGravity(true);
        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));

        spider.setTarget(target);

        Vec3 initialRadial = horizontal(spider.position().subtract(targetAnchor));
        Vec3[] previousPos = { spider.position() };
        AtomicBoolean sawCircleStrafe = new AtomicBoolean(false);
        AtomicBoolean sawFacingTarget = new AtomicBoolean(false);
        AtomicBoolean sawLeft = new AtomicBoolean(false);
        AtomicBoolean sawRight = new AtomicBoolean(false);
        AtomicBoolean sawRightCircleAnimation = new AtomicBoolean(false);
        AtomicBoolean sawLeftCircleAnimation = new AtomicBoolean(false);
        AtomicBoolean sawMeleeConvergence = new AtomicBoolean(false);
        AtomicBoolean keptTarget = new AtomicBoolean(spider.getTarget() == target);
        double[] maxAngleChange = { 0.0D };
        double[] minCircleRadius = { Double.POSITIVE_INFINITY };
        double[] maxCircleRadius = { 0.0D };
        double[] maxCircleStep = { 0.0D };

        helper.onEachTick(() -> {
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
            }
            if (spider.getTarget() != target) {
                spider.setTarget(target);
            }
            keptTarget.compareAndSet(false, spider.getTarget() == target);

            if (spider.isCircleStrafing()) {
                sawCircleStrafe.set(true);
                if ("left".equals(spider.getCircleStrafeDirectionName())) {
                    sawLeft.set(true);
                }
                if ("right".equals(spider.getCircleStrafeDirectionName())) {
                    sawRight.set(true);
                }
                if ("raised_circle_right".equals(spider.getAnimationAuditName())) {
                    sawRightCircleAnimation.set(true);
                }
                if ("raised_walk_forward_left".equals(spider.getAnimationAuditName())) {
                    sawLeftCircleAnimation.set(true);
                }
                if (facesTargetOnFloor(spider, target, 50.0D)) {
                    sawFacingTarget.set(true);
                }

                Vec3 currentRadial = horizontal(spider.position().subtract(targetAnchor));
                maxAngleChange[0] = Math.max(maxAngleChange[0],
                        Math.abs(signedHorizontalAngleDegrees(initialRadial, currentRadial)));
                double radius = currentRadial.length();
                minCircleRadius[0] = Math.min(minCircleRadius[0], radius);
                maxCircleRadius[0] = Math.max(maxCircleRadius[0], radius);
                maxCircleStep[0] = Math.max(maxCircleStep[0],
                        Math.sqrt(spider.position().distanceToSqr(previousPos[0])));
            }
            if (sawCircleStrafe.get() && spider.isWithinMeleeAttackRange(target)) {
                sawMeleeConvergence.set(true);
            }
            previousPos[0] = spider.position();
        });

        helper.runAfterDelay(190, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Circle strafe should remain natural AI movement, not forced-path mode");
            helper.assertEntityProperty(spider, entity -> entity.getTarget() == target,
                    "Ground spider should keep the target while circle-strafing");
            boolean radiusMaintained = minCircleRadius[0] >= 2.0D && maxCircleRadius[0] <= 6.25D;
            if (!sawCircleStrafe.get()
                    || !sawFacingTarget.get()
                    || !keptTarget.get()
                    || !sawMeleeConvergence.get()
                    || !((sawLeft.get() && sawLeftCircleAnimation.get())
                            || (sawRight.get() && sawRightCircleAnimation.get()))
                    || maxAngleChange[0] < 18.0D
                    || maxCircleStep[0] <= 0.04D
                    || !radiusMaintained) {
                failAndDiscard(helper,
                        "Ground spider should circle strafe around a target while facing it; sawCircleStrafe="
                        + sawCircleStrafe.get()
                        + " sawFacingTarget=" + sawFacingTarget.get()
                        + " keptTarget=" + keptTarget.get()
                        + " sawLeft=" + sawLeft.get()
                        + " sawRight=" + sawRight.get()
                        + " sawRightCircleAnimation=" + sawRightCircleAnimation.get()
                        + " sawLeftCircleAnimation=" + sawLeftCircleAnimation.get()
                        + " sawMeleeConvergence=" + sawMeleeConvergence.get()
                        + " maxAngleChange=" + maxAngleChange[0]
                        + " minCircleRadius=" + minCircleRadius[0]
                        + " maxCircleRadius=" + maxCircleRadius[0]
                        + " maxCircleStep=" + maxCircleStep[0]
                        + " state=" + spider.getCombatPacingStateName()
                        + " pacingTicks=" + spider.getCombatPacingTicks()
                        + " circleStrafing=" + spider.isCircleStrafing()
                        + " circleDirection=" + spider.getCircleStrafeDirectionName()
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " navDone=" + spider.getNavigation().isDone(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 260, batch = "naturalMelee")
    public static void naturalTargetSelectionAndMeleeDamagesNearestGolem(GameTestHelper helper) {
        fillFloor(helper, 9, 5);

        BlockPos spiderPos = new BlockPos(1, 1, 2);
        BlockPos closeTargetPos = new BlockPos(3, 1, 2);
        BlockPos farTargetPos = new BlockPos(7, 1, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 8.0D);
        var closeTarget = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, closeTargetPos);
        var farTarget = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, farTargetPos);
        double closeStartHealth = closeTarget.getHealth();
        double farStartHealth = farTarget.getHealth();

        AtomicBoolean selectedCloseTarget = new AtomicBoolean(false);
        double[] closeLowestHealth = { closeStartHealth };
        double[] farLowestHealth = { farStartHealth };
        helper.onEachTick(() -> {
            selectedCloseTarget.compareAndSet(false, spider.getTarget() == closeTarget);
            closeLowestHealth[0] = Math.min(closeLowestHealth[0], closeTarget.getHealth());
            farLowestHealth[0] = Math.min(farLowestHealth[0], farTarget.getHealth());
        });

        helper.runAfterDelay(180, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Natural target selection and melee should not use forced-path mode");
            if (!selectedCloseTarget.get() || closeLowestHealth[0] >= closeStartHealth) {
                failAndDiscard(helper,
                        "Ground spider should naturally select and damage the nearest iron golem; selectedClose="
                        + selectedCloseTarget.get()
                        + " closeStartHealth=" + closeStartHealth
                        + " closeLowestHealth=" + closeLowestHealth[0]
                        + " farStartHealth=" + farStartHealth
                        + " farLowestHealth=" + farLowestHealth[0]
                        + " currentTarget=" + spider.getTarget()
                        + " pos=" + spider.position()
                        + " navDone=" + spider.getNavigation().isDone(),
                        closeTarget, farTarget, spider);
                return;
            }
            succeedAndDiscard(helper, closeTarget, farTarget, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 520, batch = "naturalMeleeSustained")
    public static void naturalMeleeSustainsDamageAgainstLowHealthGolem(GameTestHelper helper) {
        fillFloor(helper, 9, 5);

        BlockPos spiderPos = new BlockPos(1, 1, 2);
        BlockPos targetPos = new BlockPos(4, 1, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 6.0D);
        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        target.setHealth(9.0F);
        target.addTag(GroundSpiderEntity.BASIC_MELEE_TEST_TARGET_TAG);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }

        AtomicBoolean selectedTarget = new AtomicBoolean(false);
        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));
        double startHealth = target.getHealth();
        double[] lowestHealth = { startHealth };
        float[] lastHealth = { target.getHealth() };
        int[] damageEvents = { 0 };
        helper.onEachTick(() -> {
            selectedTarget.compareAndSet(false, spider.getTarget() == target);
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
                float health = target.getHealth();
                if (health < lastHealth[0] - 0.1F) {
                    damageEvents[0]++;
                }
                lastHealth[0] = health;
                lowestHealth[0] = Math.min(lowestHealth[0], health);
            } else {
                lowestHealth[0] = 0.0D;
            }
        });

        helper.runAfterDelay(420, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Sustained natural melee should not use forced-path mode");
            if (!selectedTarget.get() || damageEvents[0] < 2 || lowestHealth[0] > startHealth - 5.5D) {
                failAndDiscard(helper,
                        "Ground spider should naturally keep attacking a low-health golem; selectedTarget="
                        + selectedTarget.get()
                        + " startHealth=" + startHealth
                        + " lowestHealth=" + lowestHealth[0]
                        + " damageEvents=" + damageEvents[0]
                        + " targetAlive=" + target.isAlive()
                        + " currentTarget=" + spider.getTarget()
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " navDone=" + spider.getNavigation().isDone(),
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 420, batch = "naturalRetargeting")
    public static void naturalTargetingRetargetsAfterFirstGolemFalls(GameTestHelper helper) {
        fillFloor(helper, 10, 5);

        BlockPos spiderPos = new BlockPos(1, 1, 2);
        BlockPos firstTargetPos = new BlockPos(3, 1, 2);
        BlockPos secondTargetPos = new BlockPos(7, 1, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 8.0D);
        var firstTarget = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, firstTargetPos);
        var secondTarget = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, secondTargetPos);
        firstTarget.setHealth(2.0F);
        secondTarget.setHealth(9.0F);
        if (firstTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            firstTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        if (secondTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            secondTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }

        AtomicBoolean selectedFirstTarget = new AtomicBoolean(false);
        AtomicBoolean firstTargetFell = new AtomicBoolean(false);
        AtomicBoolean selectedSecondTarget = new AtomicBoolean(false);
        double firstStartHealth = firstTarget.getHealth();
        double[] firstLowestHealth = { firstStartHealth };
        double secondStartHealth = secondTarget.getHealth();
        double[] secondLowestHealth = { secondStartHealth };
        helper.onEachTick(() -> {
            if (firstTarget.isAlive()) {
                firstLowestHealth[0] = Math.min(firstLowestHealth[0], firstTarget.getHealth());
            } else {
                firstLowestHealth[0] = 0.0D;
            }
            selectedFirstTarget.compareAndSet(false,
                    spider.getTarget() == firstTarget || firstLowestHealth[0] < firstStartHealth);
            if (!firstTarget.isAlive()) {
                firstTargetFell.set(true);
            }
            selectedSecondTarget.compareAndSet(false, spider.getTarget() == secondTarget);
            if (secondTarget.isAlive()) {
                secondLowestHealth[0] = Math.min(secondLowestHealth[0], secondTarget.getHealth());
            }
        });

        helper.runAfterDelay(340, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Natural retargeting should not use forced-path mode");
            if (!selectedFirstTarget.get() || !firstTargetFell.get()
                    || !selectedSecondTarget.get() || secondLowestHealth[0] >= secondStartHealth) {
                String message = "Ground spider should retarget after the first golem falls and damage the second golem; selectedFirst="
                        + selectedFirstTarget.get()
                        + " firstFell=" + firstTargetFell.get()
                        + " firstStartHealth=" + firstStartHealth
                        + " firstLowestHealth=" + firstLowestHealth[0]
                        + " selectedSecond=" + selectedSecondTarget.get()
                        + " secondStartHealth=" + secondStartHealth
                        + " secondLowestHealth=" + secondLowestHealth[0]
                        + " currentTarget=" + spider.getTarget()
                        + " pos=" + spider.position()
                        + " firstPos=" + firstTarget.position()
                        + " secondPos=" + secondTarget.position()
                        + " navDone=" + spider.getNavigation().isDone();
                failAndDiscard(helper, message, firstTarget, secondTarget, spider);
                return;
            }
            succeedAndDiscard(helper, firstTarget, secondTarget, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 420, batch = "reachabilitySelection")
    public static void naturalTargetSelectionSkipsUnreachableNearGolem(GameTestHelper helper) {
        fillFloor(helper, 10, 7);
        clearLayer(helper, 3, 5, 0, 1, 5);
        clearLayer(helper, 3, 5, -1, 1, 5);

        BlockPos spiderPos = new BlockPos(1, 1, 3);
        BlockPos unreachableTargetPos = new BlockPos(4, 1, 3);
        BlockPos reachableTargetPos = new BlockPos(8, 1, 3);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 7.5D);
        var unreachableTarget = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, unreachableTargetPos);
        var reachableTarget = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, reachableTargetPos);
        unreachableTarget.setNoGravity(true);
        reachableTarget.setNoGravity(true);

        Vec3 unreachableAnchor = helper.absoluteVec(new Vec3(
                unreachableTargetPos.getX() + 0.5D,
                unreachableTargetPos.getY(),
                unreachableTargetPos.getZ() + 0.5D));
        Vec3 reachableAnchor = helper.absoluteVec(new Vec3(
                reachableTargetPos.getX() + 0.5D,
                reachableTargetPos.getY(),
                reachableTargetPos.getZ() + 0.5D));
        AtomicBoolean selectedUnreachable = new AtomicBoolean(false);
        AtomicBoolean selectedReachable = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        double startReachableDistance = spider.distanceToSqr(reachableTarget);
        double[] bestReachableDistance = { startReachableDistance };
        helper.onEachTick(() -> {
            if (unreachableTarget.isAlive()) {
                unreachableTarget.setDeltaMovement(Vec3.ZERO);
                unreachableTarget.setPos(unreachableAnchor.x, unreachableAnchor.y, unreachableAnchor.z);
            }
            if (reachableTarget.isAlive()) {
                reachableTarget.setDeltaMovement(Vec3.ZERO);
                reachableTarget.setPos(reachableAnchor.x, reachableAnchor.y, reachableAnchor.z);
            }
            selectedUnreachable.compareAndSet(false, spider.getTarget() == unreachableTarget);
            selectedReachable.compareAndSet(false, spider.getTarget() == reachableTarget);
            bestReachableDistance[0] = Math.min(bestReachableDistance[0], spider.distanceToSqr(reachableTarget));

            if (!completed.get()
                    && !selectedUnreachable.get()
                    && selectedReachable.get()
                    && bestReachableDistance[0] < startReachableDistance) {
                completed.set(true);
                succeedAndDiscard(helper, unreachableTarget, reachableTarget, spider);
            }
        });

        helper.runAfterDelay(340, () -> {
            if (completed.get()) {
                return;
            }
            if (spider.isFollowingForcedPath()) {
                failAndDiscard(helper,
                        "Reachability-aware target selection should not use forced-path mode",
                        unreachableTarget, reachableTarget, spider);
                return;
            }
            if (selectedUnreachable.get() || !selectedReachable.get() || bestReachableDistance[0] >= startReachableDistance) {
                String unreachablePath = describePath(spider.getNavigation().createPath(unreachableTarget, 0));
                String reachablePath = describePath(spider.getNavigation().createPath(reachableTarget, 0));
                failAndDiscard(helper,
                        "Ground spider should skip the unreachable nearer golem and pursue the reachable farther golem; selectedUnreachable="
                        + selectedUnreachable.get()
                        + " selectedReachable=" + selectedReachable.get()
                        + " startReachableDistance=" + startReachableDistance
                        + " bestReachableDistance=" + bestReachableDistance[0]
                        + " currentTarget=" + spider.getTarget()
                        + " pos=" + spider.position()
                        + " unreachablePos=" + unreachableTarget.position()
                        + " reachablePos=" + reachableTarget.position()
                        + " unreachablePath=" + unreachablePath
                        + " reachablePath=" + reachablePath
                        + " navDone=" + spider.getNavigation().isDone(),
                        unreachableTarget, reachableTarget, spider);
                return;
            }
            succeedAndDiscard(helper, unreachableTarget, reachableTarget, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120, batch = "pathfinding")
    public static void multiTargetPathChoosesShorterRouteOverNearestDetour(GameTestHelper helper) {
        clearFloor(helper, 0, 10, 0, 6);

        BlockPos spiderPos = new BlockPos(1, 1, 3);
        BlockPos nearDetourTarget = new BlockPos(4, 1, 3);
        BlockPos fartherShortTarget = new BlockPos(7, 1, 1);
        setFloorSupports(helper,
                new BlockPos(1, 0, 3),
                new BlockPos(1, 0, 2),
                new BlockPos(1, 0, 1),
                new BlockPos(2, 0, 1),
                new BlockPos(3, 0, 1),
                new BlockPos(4, 0, 1),
                new BlockPos(5, 0, 1),
                new BlockPos(6, 0, 1),
                new BlockPos(7, 0, 1),
                new BlockPos(1, 0, 4),
                new BlockPos(1, 0, 5),
                new BlockPos(2, 0, 5),
                new BlockPos(3, 0, 5),
                new BlockPos(4, 0, 5),
                new BlockPos(5, 0, 5),
                new BlockPos(6, 0, 5),
                new BlockPos(7, 0, 5),
                new BlockPos(8, 0, 5),
                new BlockPos(9, 0, 5),
                new BlockPos(9, 0, 4),
                new BlockPos(9, 0, 3),
                new BlockPos(8, 0, 3),
                new BlockPos(7, 0, 3),
                new BlockPos(6, 0, 3),
                new BlockPos(5, 0, 3),
                new BlockPos(4, 0, 3));

        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        ExposedClimberPathNavigator navigator = new ExposedClimberPathNavigator(spider, helper.getLevel());

        BlockPos absoluteNear = helper.absolutePos(nearDetourTarget);
        BlockPos absoluteFar = helper.absolutePos(fartherShortTarget);
        Set<BlockPos> candidates = new java.util.LinkedHashSet<>();
        candidates.add(absoluteNear);
        candidates.add(absoluteFar);

        Path chosenPath = navigator.createBestPath(candidates);
        Path nearPath = spider.getNavigation().createPath(absoluteNear, 0);
        Path farPath = spider.getNavigation().createPath(absoluteFar, 0);
        if (chosenPath == null || nearPath == null || farPath == null) {
            failAndDiscard(helper, "Expected near and far target routes to be reachable; chosenPath="
                    + describePath(chosenPath)
                    + " nearPath=" + describePath(nearPath)
                    + " farPath=" + describePath(farPath),
                    spider);
            return;
        }

        BlockPos chosenEnd = pathEndPos(chosenPath);
        double chosenCost = pathSegmentLength(spider, spider.position(), chosenPath);
        double nearCost = pathSegmentLength(spider, spider.position(), nearPath);
        double farCost = pathSegmentLength(spider, spider.position(), farPath);

        if (!absoluteFar.equals(chosenEnd)
                || chosenCost + 1.0E-4D >= nearCost
                || Math.abs(chosenCost - farCost) > 1.0E-4D) {
            failAndDiscard(helper,
                    "Multi-target path should choose the lower-cost route instead of the nearest detour; chosenEnd="
                            + chosenEnd
                            + " nearTarget=" + absoluteNear
                            + " farTarget=" + absoluteFar
                            + " chosenCost=" + chosenCost
                            + " nearCost=" + nearCost
                            + " farCost=" + farCost
                            + " chosenPath=" + describePath(chosenPath)
                            + " nearPath=" + describePath(nearPath)
                            + " farPath=" + describePath(farPath),
                    spider);
            return;
        }

        succeedAndDiscard(helper, spider);
    }

    @GameTest(template = "arena", timeoutTicks = 120, batch = "pathfinding")
    public static void dummyPlayerPathCutsCeilingCornerShortcut(GameTestHelper helper) {
        buildSurfaceCornerShortcutFixture(helper);

        BlockPos spiderPos = new BlockPos(1, 1, 5);
        BlockPos dummyPlayerPos = new BlockPos(8, 1, 1);
        BlockPos expectedMeleeEnd = helper.absolutePos(new BlockPos(7, 1, 1));
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        placeAttached(helper, spider, spiderPos, Direction.DOWN);
        FakePlayer dummyPlayer = dummyPlayerAt(helper, dummyPlayerPos);

        Path path = spider.getNavigation().createPath(dummyPlayer, 0);
        double shortcutCost = pathSegmentLength(spider, spider.position(), path);
        double floorDetourCost = anchoredPolylineLength(spider, helper, Direction.DOWN,
                new BlockPos(1, 1, 5),
                new BlockPos(9, 1, 5),
                new BlockPos(9, 1, 1),
                new BlockPos(7, 1, 1));
        int transitionCount = pathAttachmentTransitionCount(path);
        boolean usedCeilingShortcut = pathUsesAttachment(path, Direction.UP);
        BlockPos chosenEnd = pathEndPos(path);

        if (path == null
                || !expectedMeleeEnd.equals(chosenEnd)
                || !usedCeilingShortcut
                || transitionCount < 2
                || shortcutCost >= floorDetourCost - 2.0D) {
            failAndDiscard(helper,
                    "Path to dummy player should transition onto the ceiling and cut the inside corner instead of taking the longer floor detour; path="
                            + describePath(path)
                            + " chosenEnd=" + chosenEnd
                            + " expectedMeleeEnd=" + expectedMeleeEnd
                            + " usedCeilingShortcut=" + usedCeilingShortcut
                            + " transitionCount=" + transitionCount
                            + " shortcutCost=" + shortcutCost
                            + " floorDetourCost=" + floorDetourCost
                            + " spiderPos=" + spider.position()
                            + " dummyPlayerPos=" + dummyPlayer.position(),
                    spider);
            return;
        }

        succeedAndDiscard(helper, spider);
    }

    @GameTest(template = "arena", timeoutTicks = 220, batch = "pathfinding")
    public static void dummyPlayerPursuitTransitionsAndCutsCorner(GameTestHelper helper) {
        buildSurfaceCornerShortcutFixture(helper);

        BlockPos spiderPos = new BlockPos(1, 1, 5);
        BlockPos dummyPlayerPos = new BlockPos(8, 1, 1);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        placeAttached(helper, spider, spiderPos, Direction.DOWN);
        FakePlayer dummyPlayer = dummyPlayerAt(helper, dummyPlayerPos);

        Path initialPath = spider.getNavigation().createPath(dummyPlayer, 0);
        String initialPathDescription = describePath(initialPath);
        boolean initialUsesCeiling = pathUsesAttachment(initialPath, Direction.UP);
        double floorDetourCost = anchoredPolylineLength(spider, helper, Direction.DOWN,
                new BlockPos(1, 1, 5),
                new BlockPos(9, 1, 5),
                new BlockPos(9, 1, 1),
                new BlockPos(7, 1, 1));
        double initialPathCost = pathSegmentLength(spider, spider.position(), initialPath);
        if (initialPath == null || !initialUsesCeiling || initialPathCost >= floorDetourCost - 2.0D) {
            failAndDiscard(helper,
                    "Initial dummy-player pursuit path should prefer the ceiling corner shortcut; initialPath="
                            + initialPathDescription
                            + " initialUsesCeiling=" + initialUsesCeiling
                            + " initialPathCost=" + initialPathCost
                            + " floorDetourCost=" + floorDetourCost
                            + " dummyPlayerPos=" + dummyPlayer.position(),
                    spider);
            return;
        }

        double startDistance = spider.distanceToSqr(dummyPlayer);
        double[] bestDistance = { startDistance };
        AtomicBoolean sawCeiling = new AtomicBoolean(spider.getAttachmentDirection() == Direction.UP);
        AtomicBoolean enteredShortcutVolume = new AtomicBoolean(false);
        AtomicBoolean touchedOuterFloorDetour = new AtomicBoolean(false);
        String[] lastPath = { initialPathDescription };
        Vec3 shortcutMin = helper.absoluteVec(new Vec3(2.0D, 0.0D, 1.0D));
        Vec3 shortcutMax = helper.absoluteVec(new Vec3(7.99D, 2.99D, 5.99D));
        Vec3 outerCorner = AttachmentHelper.anchorFor(spider, helper.absolutePos(new BlockPos(9, 1, 5)), Direction.DOWN);

        spider.setTarget(dummyPlayer);
        boolean pathStarted = spider.getNavigation().moveTo(dummyPlayer, 1.0D);
        if (!pathStarted) {
            failAndDiscard(helper,
                    "Ground spider should start natural pursuit toward the dummy player through the corner shortcut; initialPath="
                            + initialPathDescription
                            + " dummyPlayerPos=" + dummyPlayer.position(),
                    spider);
            return;
        }

        helper.onEachTick(() -> {
            positionDummyPlayer(helper, dummyPlayer, dummyPlayerPos);
            if (spider.getTarget() != dummyPlayer) {
                spider.setTarget(dummyPlayer);
            }
            Path currentPath = spider.getNavigation().getPath();
            if (currentPath != null) {
                lastPath[0] = describePath(currentPath);
            }
            if (spider.getAttachmentDirection() == Direction.UP) {
                sawCeiling.set(true);
            }
            bestDistance[0] = Math.min(bestDistance[0], spider.distanceToSqr(dummyPlayer));

            Vec3 pos = spider.position();
            if (pos.x >= shortcutMin.x && pos.x <= shortcutMax.x
                    && pos.y >= shortcutMin.y && pos.y <= shortcutMax.y
                    && pos.z >= shortcutMin.z && pos.z <= shortcutMax.z
                    && spider.getAttachmentDirection() == Direction.UP) {
                enteredShortcutVolume.set(true);
            }
            if (pos.distanceToSqr(outerCorner) < 1.0D) {
                touchedOuterFloorDetour.set(true);
            }
        });

        helper.runAfterDelay(140, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Dummy-player corner pursuit should remain natural AI movement, not forced-path mode");
            if (!sawCeiling.get()
                    || !enteredShortcutVolume.get()
                    || touchedOuterFloorDetour.get()
                    || bestDistance[0] >= startDistance - 4.0D
                    || spider.getTarget() != dummyPlayer) {
                failAndDiscard(helper,
                        "Ground spider should naturally transition surfaces and cut the corner toward a dummy player; sawCeiling="
                                + sawCeiling.get()
                                + " enteredShortcutVolume=" + enteredShortcutVolume.get()
                                + " touchedOuterFloorDetour=" + touchedOuterFloorDetour.get()
                                + " startDistance=" + startDistance
                                + " bestDistance=" + bestDistance[0]
                                + " endDistance=" + spider.distanceToSqr(dummyPlayer)
                                + " pos=" + spider.position()
                                + " attachment=" + spider.getAttachmentDirection()
                                + " targetKept=" + (spider.getTarget() == dummyPlayer)
                                + " navDone=" + spider.getNavigation().isDone()
                                + " initialPath=" + initialPathDescription
                                + " lastPath=" + lastPath[0]
                                + " dummyPlayerPos=" + dummyPlayer.position(),
                        spider);
                return;
            }
            succeedAndDiscard(helper, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 220, batch = "naturalWallPursuit")
    public static void naturalTargetPursuitClimbsWallTowardElevatedTarget(GameTestHelper helper) {
        fillWall(helper, 0, 1, 7, 0, 4);
        helper.setBlock(1, 4, 2, Blocks.STONE);

        BlockPos spiderPos = new BlockPos(1, 2, 2);
        BlockPos targetPos = new BlockPos(1, 5, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        Direction wallDirection = directionBetween(
                helper.absolutePos(spiderPos),
                helper.absolutePos(spiderPos.west()));
        spider.setAttachmentDirection(wallDirection);
        spider.setNoGravity(true);

        double startY = spider.getY();
        double startDistance = spider.distanceToSqr(target);
        double[] highestY = { startY };
        double[] bestDistance = { startDistance };
        spider.setTarget(target);
        boolean pathStarted = spider.getNavigation().moveTo(target, 1.0D);
        if (!pathStarted) {
            failAndDiscard(helper, "Ground spider should create an initial wall path; pos="
                    + spider.position() + " target=" + target.position()
                    + " attachment=" + spider.getAttachmentDirection(), target, spider);
            return;
        }

        helper.onEachTick(() -> {
            if (target.isAlive() && spider.getTarget() != target) {
                spider.setTarget(target);
            }
            highestY[0] = Math.max(highestY[0], spider.getY());
            bestDistance[0] = Math.min(bestDistance[0], spider.distanceToSqr(target));
        });
        helper.runAfterDelay(120, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Natural wall pursuit should not use forced-path mode");
            helper.assertEntityProperty(spider, entity -> entity.getTarget() == target,
                    "Ground spider should keep the elevated target");
            double endDistance = spider.distanceToSqr(target);
            if (highestY[0] <= startY + 0.75D || bestDistance[0] >= startDistance) {
                failAndDiscard(helper, "Ground spider should climb upward toward the elevated target; startY="
                        + startY + " highestY=" + highestY[0] + " endY=" + spider.getY()
                        + " startDistance=" + startDistance + " bestDistance=" + bestDistance[0]
                        + " endDistance=" + endDistance
                        + " pos=" + spider.position()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " navDone=" + spider.getNavigation().isDone(), target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 260, batch = "naturalWallCeilingTransition")
    public static void naturalTargetPursuitTransitionsFromWallToCeiling(GameTestHelper helper) {
        fillWall(helper, 0, 1, 4, 0, 4);
        fillCeiling(helper, 1, 4, 5, 0, 4);
        helper.setBlock(5, 3, 2, Blocks.STONE);

        BlockPos spiderPos = new BlockPos(1, 4, 2);
        BlockPos targetPos = new BlockPos(5, 4, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        Direction wallDirection = directionBetween(
                helper.absolutePos(spiderPos),
                helper.absolutePos(spiderPos.west()));
        spider.setAttachmentDirection(wallDirection);
        spider.setNoGravity(true);

        double startDistance = spider.distanceToSqr(target);
        spider.setTarget(target);
        boolean pathStarted = spider.getNavigation().moveTo(target, 1.0D);
        if (!pathStarted) {
            failAndDiscard(helper, "Ground spider should create an initial wall-to-ceiling path; pos="
                    + spider.position() + " target=" + target.position()
                    + " attachment=" + spider.getAttachmentDirection(), target, spider);
            return;
        }

        String initialPath = describePath(spider.getNavigation().getPath());
        AtomicBoolean sawCeiling = new AtomicBoolean(spider.getAttachmentDirection() == Direction.UP);
        double[] bestDistance = { startDistance };
        double[] bestCeilingDistance = { Double.POSITIVE_INFINITY };
        Runnable sampleTransition = () -> {
            if (target.isAlive() && spider.getTarget() != target) {
                spider.setTarget(target);
            }
            if (spider.getAttachmentDirection() == Direction.UP) {
                sawCeiling.set(true);
                bestCeilingDistance[0] = Math.min(bestCeilingDistance[0], spider.distanceToSqr(target));
            }
            bestDistance[0] = Math.min(bestDistance[0], spider.distanceToSqr(target));
        };
        helper.onEachTick(sampleTransition);
        helper.runAfterDelay(140, () -> {
            sampleTransition.run();
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Natural wall-to-ceiling pursuit should not use forced-path mode");
            helper.assertEntityProperty(spider, entity -> entity.getTarget() == target,
                    "Ground spider should keep the ceiling-side target");
            double endDistance = spider.distanceToSqr(target);
            if (!sawCeiling.get() || bestDistance[0] >= startDistance) {
                failAndDiscard(helper,
                        "Ground spider should transition onto the ceiling during pursuit and close distance; sawCeiling="
                        + sawCeiling.get()
                        + " startDistance="
                        + startDistance + " bestDistance=" + bestDistance[0]
                        + " bestCeilingDistance=" + bestCeilingDistance[0]
                        + " endDistance=" + endDistance
                        + " pos=" + spider.position()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " navDone=" + spider.getNavigation().isDone()
                        + " initialPath=" + initialPath,
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 260, batch = "naturalCeilingPursuit")
    public static void naturalTargetPursuitTraversesCeilingTowardTarget(GameTestHelper helper) {
        fillCeiling(helper, 0, 8, 5, 0, 4);
        helper.setBlock(7, 3, 2, Blocks.STONE);

        BlockPos spiderPos = new BlockPos(1, 4, 2);
        BlockPos targetPos = new BlockPos(7, 4, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        placeAttached(helper, spider, spiderPos, Direction.UP);

        double startDistance = spider.distanceToSqr(target);
        spider.setTarget(target);
        boolean pathStarted = spider.getNavigation().moveTo(target, 1.0D);
        if (!pathStarted) {
            failAndDiscard(helper, "Ground spider should create an initial ceiling pursuit path; pos="
                    + spider.position() + " target=" + target.position()
                    + " attachment=" + spider.getAttachmentDirection(), target, spider);
            return;
        }

        String initialPath = describePath(spider.getNavigation().getPath());
        AtomicBoolean sawCeiling = new AtomicBoolean(spider.getAttachmentDirection() == Direction.UP);
        double[] bestCeilingDistance = { startDistance };
        helper.onEachTick(() -> {
            if (target.isAlive() && spider.getTarget() != target) {
                spider.setTarget(target);
            }
            if (spider.getAttachmentDirection() == Direction.UP) {
                sawCeiling.set(true);
                bestCeilingDistance[0] = Math.min(bestCeilingDistance[0], spider.distanceToSqr(target));
            }
        });

        helper.runAfterDelay(140, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Natural ceiling pursuit should not use forced-path mode");
            helper.assertEntityProperty(spider, entity -> entity.getTarget() == target,
                    "Ground spider should keep the ceiling-side target");
            double endDistance = spider.distanceToSqr(target);
            if (!sawCeiling.get() || bestCeilingDistance[0] >= startDistance) {
                failAndDiscard(helper,
                        "Ground spider should traverse the ceiling during pursuit and close distance; sawCeiling="
                        + sawCeiling.get()
                        + " startDistance=" + startDistance
                        + " bestCeilingDistance=" + bestCeilingDistance[0]
                        + " endDistance=" + endDistance
                        + " pos=" + spider.position()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " navDone=" + spider.getNavigation().isDone()
                        + " initialPath=" + initialPath,
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 320, batch = "naturalChainedPursuit")
    public static void naturalTargetPursuitChainsWallCeilingWallTowardTarget(GameTestHelper helper) {
        fillWall(helper, 0, 1, 5, 0, 4);
        fillCeiling(helper, 1, 7, 5, 0, 4);
        fillWall(helper, 8, 1, 5, 0, 4);

        BlockPos spiderPos = new BlockPos(1, 4, 2);
        BlockPos targetPos = new BlockPos(6, 2, 2);
        BlockPos rightWallAir = new BlockPos(7, 4, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        var target = helper.spawnWithNoFreeWill(EntityType.SHEEP, targetPos);
        target.setNoGravity(true);
        target.setInvulnerable(true);

        Direction leftWallDirection = directionBetween(
                helper.absolutePos(spiderPos),
                helper.absolutePos(spiderPos.west()));
        Direction rightWallDirection = directionBetween(
                helper.absolutePos(rightWallAir),
                helper.absolutePos(rightWallAir.east()));
        placeAttached(helper, spider, spiderPos, leftWallDirection);

        double startDistance = spider.distanceToSqr(target);
        spider.setTarget(target);
        boolean pathStarted = spider.getNavigation().moveTo(target, 1.0D);
        if (!pathStarted) {
            failAndDiscard(helper,
                    "Ground spider should create an initial wall-ceiling-wall pursuit path; pos="
                    + spider.position() + " target=" + target.position()
                    + " attachment=" + spider.getAttachmentDirection()
                    + " leftWall=" + leftWallDirection + " rightWall=" + rightWallDirection,
                    target, spider);
            return;
        }

        String initialPath = describePath(spider.getNavigation().getPath());
        String rightWallPathToken = ":" + rightWallDirection.getName();
        boolean initialPathIncludesCeiling = initialPath.contains(":up");
        boolean initialPathIncludesTargetWall = initialPath.contains(rightWallPathToken);
        if (!initialPathIncludesCeiling || !initialPathIncludesTargetWall) {
            failAndDiscard(helper,
                    "Initial chained path should include ceiling and target-wall attachment nodes; rightWall="
                    + rightWallDirection + " initialPath=" + initialPath,
                    target, spider);
            return;
        }

        AtomicBoolean sawCeiling = new AtomicBoolean(spider.getAttachmentDirection() == Direction.UP);
        AtomicBoolean sawTargetWall = new AtomicBoolean(spider.getAttachmentDirection() == rightWallDirection);
        AtomicBoolean sawNextTargetWall = new AtomicBoolean(false);
        double[] bestDistance = { startDistance };
        double[] bestTargetWallDistance = { Double.POSITIVE_INFINITY };
        double[] lowestTargetWallY = { Double.POSITIVE_INFINITY };
        String[] lastPath = { initialPath };
        helper.onEachTick(() -> {
            if (target.isAlive() && spider.getTarget() != target) {
                spider.setTarget(target);
            }
            Path currentPath = spider.getNavigation().getPath();
            if (currentPath != null) {
                lastPath[0] = describePath(currentPath);
                if (currentPath.getNextNodeIndex() < currentPath.getNodeCount()) {
                    Node nextNode = currentPath.getNextNode();
                    if (nextNode instanceof com.horrormods.spiders.entity.ai.ClimberNodeEvaluator.CustomNode customNode
                            && customNode.attachment == rightWallDirection) {
                        sawNextTargetWall.set(true);
                    }
                }
            }
            Direction attachment = spider.getAttachmentDirection();
            bestDistance[0] = Math.min(bestDistance[0], spider.distanceToSqr(target));
            if (attachment == Direction.UP) {
                sawCeiling.set(true);
            }
            if (attachment == rightWallDirection) {
                sawTargetWall.set(true);
                bestTargetWallDistance[0] = Math.min(bestTargetWallDistance[0], spider.distanceToSqr(target));
                lowestTargetWallY[0] = Math.min(lowestTargetWallY[0], spider.getY());
            }
        });

        helper.runAfterDelay(210, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Natural chained pursuit should not use forced-path mode");
            boolean provedCeilingTraversal = sawCeiling.get() || initialPathIncludesCeiling;
            boolean provedTargetWallTraversal = sawTargetWall.get() || sawNextTargetWall.get()
                    || initialPathIncludesTargetWall;
            double bestProvedDistance = sawTargetWall.get() ? bestTargetWallDistance[0] : bestDistance[0];
            if (!provedCeilingTraversal || !provedTargetWallTraversal || bestProvedDistance >= startDistance) {
                failAndDiscard(helper,
                        "Ground spider should chain wall-to-ceiling-to-wall pursuit and close distance on the target wall; sawCeiling="
                        + sawCeiling.get()
                        + " initialPathIncludesCeiling=" + initialPathIncludesCeiling
                        + " initialPathIncludesTargetWall=" + initialPathIncludesTargetWall
                        + " sawTargetWall=" + sawTargetWall.get()
                        + " sawNextTargetWall=" + sawNextTargetWall.get()
                        + " startDistance=" + startDistance
                        + " bestDistance=" + bestDistance[0]
                        + " bestTargetWallDistance=" + bestTargetWallDistance[0]
                        + " bestProvedDistance=" + bestProvedDistance
                        + " lowestTargetWallY=" + lowestTargetWallY[0]
                        + " endDistance=" + spider.distanceToSqr(target)
                        + " pos=" + spider.position()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " navDone=" + spider.getNavigation().isDone()
                        + " initialPath=" + initialPath
                        + " lastPath=" + lastPath[0],
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 520, batch = "naturalChainedDamage")
    public static void naturalChainedSurfacePursuitDamagesTarget(GameTestHelper helper) {
        fillWall(helper, 0, 1, 5, 0, 4);
        fillCeiling(helper, 1, 7, 5, 0, 4);
        fillWall(helper, 8, 1, 5, 0, 4);
        clearFloor(helper, 0, 8, 0, 4);
        clearLayer(helper, 0, 8, -1, 0, 4);

        BlockPos spiderPos = new BlockPos(1, 4, 2);
        BlockPos targetPos = new BlockPos(6, 1, 2);
        BlockPos rightWallAir = new BlockPos(7, 2, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        target.setNoGravity(true);

        Direction leftWallDirection = directionBetween(
                helper.absolutePos(spiderPos),
                helper.absolutePos(spiderPos.west()));
        Direction rightWallDirection = directionBetween(
                helper.absolutePos(rightWallAir),
                helper.absolutePos(rightWallAir.east()));
        placeAttached(helper, spider, spiderPos, leftWallDirection);

        double startHealth = target.getHealth();
        spider.setTarget(target);
        boolean pathStarted = spider.getNavigation().moveTo(target, 1.0D);
        if (!pathStarted) {
            failAndDiscard(helper, "Ground spider should create an initial chained combat path; pos="
                    + spider.position() + " target=" + target.position()
                    + " attachment=" + spider.getAttachmentDirection()
                    + " leftWall=" + leftWallDirection + " rightWall=" + rightWallDirection,
                    target, spider);
            return;
        }

        String initialPath = describePath(spider.getNavigation().getPath());
        String rightWallPathToken = ":" + rightWallDirection.getName();
        if (!initialPath.contains(":up") || !initialPath.contains(rightWallPathToken)) {
            failAndDiscard(helper, "Initial chained combat path should include ceiling and target-wall nodes; rightWall="
                    + rightWallDirection + " initialPath=" + initialPath, target, spider);
            return;
        }

        AtomicBoolean sawCeiling = new AtomicBoolean(spider.getAttachmentDirection() == Direction.UP);
        AtomicBoolean sawTargetWall = new AtomicBoolean(spider.getAttachmentDirection() == rightWallDirection);
        double[] lowestHealth = { startHealth };
        double[] bestDistance = { spider.distanceToSqr(target) };
        String[] lastPath = { initialPath };
        helper.onEachTick(() -> {
            if (target.isAlive() && spider.getTarget() != target) {
                spider.setTarget(target);
            }
            Path currentPath = spider.getNavigation().getPath();
            if (currentPath != null) {
                lastPath[0] = describePath(currentPath);
            }
            Direction attachment = spider.getAttachmentDirection();
            if (attachment == Direction.UP) {
                sawCeiling.set(true);
            }
            if (attachment == rightWallDirection) {
                sawTargetWall.set(true);
            }
            lowestHealth[0] = Math.min(lowestHealth[0], target.getHealth());
            bestDistance[0] = Math.min(bestDistance[0], spider.distanceToSqr(target));
        });

        helper.runAfterDelay(360, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Natural chained combat pursuit should not use forced-path mode");
            if (!sawCeiling.get() || !sawTargetWall.get() || lowestHealth[0] >= startHealth) {
                failAndDiscard(helper, "Ground spider should chain surfaces and damage the target; sawCeiling="
                        + sawCeiling.get()
                        + " sawTargetWall=" + sawTargetWall.get()
                        + " startHealth=" + startHealth
                        + " lowestHealth=" + lowestHealth[0]
                        + " targetHealth=" + target.getHealth()
                        + " bestDistance=" + bestDistance[0]
                        + " endDistance=" + spider.distanceToSqr(target)
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " navDone=" + spider.getNavigation().isDone()
                        + " initialPath=" + initialPath
                        + " lastPath=" + lastPath[0],
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 1200, batch = "mixedSurfaceTargeting")
    public static void naturalTargetSelectionChainsSurfacesAndDamagesGolem(GameTestHelper helper) {
        fillWall(helper, 0, 1, 5, 0, 4);
        fillCeiling(helper, 1, 7, 5, 0, 4);
        fillWall(helper, 8, 1, 5, 0, 4);
        clearFloor(helper, 0, 8, 0, 4);
        clearLayer(helper, 0, 8, -1, 0, 4);

        BlockPos spiderPos = new BlockPos(1, 4, 2);
        BlockPos targetPos = new BlockPos(6, 1, 2);
        BlockPos rightWallAir = new BlockPos(7, 2, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 8.0D);
        var target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, targetPos);
        target.setNoGravity(true);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }

        Direction leftWallDirection = directionBetween(
                helper.absolutePos(spiderPos),
                helper.absolutePos(spiderPos.west()));
        Direction rightWallDirection = directionBetween(
                helper.absolutePos(rightWallAir),
                helper.absolutePos(rightWallAir.east()));
        placeAttached(helper, spider, spiderPos, leftWallDirection);

        Vec3 targetAnchor = helper.absoluteVec(new Vec3(
                targetPos.getX() + 0.5D,
                targetPos.getY(),
                targetPos.getZ() + 0.5D));
        double startHealth = target.getHealth();
        AtomicBoolean selectedTarget = new AtomicBoolean(false);
        AtomicBoolean sawCeiling = new AtomicBoolean(spider.getAttachmentDirection() == Direction.UP);
        AtomicBoolean sawTargetWall = new AtomicBoolean(spider.getAttachmentDirection() == rightWallDirection);
        AtomicBoolean sawChainedPath = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        double[] lowestHealth = { startHealth };
        double[] bestDistance = { spider.distanceToSqr(target) };
        String[] lastPath = { "null" };
        String rightWallPathToken = ":" + rightWallDirection.getName();
        helper.onEachTick(() -> {
            if (target.isAlive()) {
                target.setDeltaMovement(Vec3.ZERO);
                target.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
            }
            selectedTarget.compareAndSet(false, spider.getTarget() == target);
            Path currentPath = spider.getNavigation().getPath();
            if (currentPath != null) {
                String pathDescription = describePath(currentPath);
                lastPath[0] = pathDescription;
                if ((pathDescription.contains(":up") && pathDescription.contains(rightWallPathToken))
                        || (sawCeiling.get() && pathDescription.contains(rightWallPathToken))
                        || (sawTargetWall.get() && pathDescription.contains(":up"))) {
                    sawChainedPath.set(true);
                }
            }
            Direction attachment = spider.getAttachmentDirection();
            if (attachment == Direction.UP) {
                sawCeiling.set(true);
            }
            if (attachment == rightWallDirection) {
                sawTargetWall.set(true);
            }
            if (sawCeiling.get() && sawTargetWall.get()) {
                sawChainedPath.set(true);
            }
            lowestHealth[0] = Math.min(lowestHealth[0], target.getHealth());
            bestDistance[0] = Math.min(bestDistance[0], spider.distanceToSqr(target));
            if (!completed.get()
                    && selectedTarget.get()
                    && sawChainedPath.get()
                    && sawCeiling.get()
                    && sawTargetWall.get()
                    && lowestHealth[0] < startHealth) {
                helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                        "Natural chained target selection should not use forced-path mode");
                completed.set(true);
                succeedAndDiscard(helper, target, spider);
            }
        });

        helper.runAfterDelay(950, () -> {
            if (completed.get()) {
                return;
            }
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Natural chained target selection should not use forced-path mode");
            if (!selectedTarget.get() || !sawChainedPath.get() || !sawCeiling.get()
                    || !sawTargetWall.get() || lowestHealth[0] >= startHealth) {
                failAndDiscard(helper,
                        "Ground spider should naturally select a golem, chain wall-to-ceiling-to-wall, and damage it; selectedTarget="
                        + selectedTarget.get()
                        + " sawChainedPath=" + sawChainedPath.get()
                        + " sawCeiling=" + sawCeiling.get()
                        + " sawTargetWall=" + sawTargetWall.get()
                        + " startHealth=" + startHealth
                        + " lowestHealth=" + lowestHealth[0]
                        + " targetHealth=" + target.getHealth()
                        + " bestDistance=" + bestDistance[0]
                        + " endDistance=" + spider.distanceToSqr(target)
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " currentTarget=" + spider.getTarget()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " navDone=" + spider.getNavigation().isDone()
                        + " lastPath=" + lastPath[0],
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 1200, batch = "zzMixedSurfaceRetargeting")
    public static void mixedSurfaceCombatRetargetsAfterFirstGolemFalls(GameTestHelper helper) {
        fillWall(helper, 0, 1, 5, 0, 4);
        fillCeiling(helper, 1, 7, 5, 0, 4);
        fillWall(helper, 8, 1, 5, 0, 4);
        clearFloor(helper, 0, 8, 0, 4);
        clearLayer(helper, 0, 8, -1, 0, 4);

        BlockPos spiderPos = new BlockPos(1, 4, 2);
        BlockPos firstTargetPos = new BlockPos(6, 1, 2);
        BlockPos secondTargetPos = new BlockPos(6, 1, 0);
        BlockPos rightWallAir = new BlockPos(7, 2, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 8.0D);
        var firstTarget = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, firstTargetPos);
        var secondTarget = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, secondTargetPos);
        firstTarget.setNoGravity(true);
        secondTarget.setNoGravity(true);
        firstTarget.setHealth(2.0F);
        secondTarget.setHealth(9.0F);
        if (firstTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            firstTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        if (secondTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            secondTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }

        Direction leftWallDirection = directionBetween(
                helper.absolutePos(spiderPos),
                helper.absolutePos(spiderPos.west()));
        Direction rightWallDirection = directionBetween(
                helper.absolutePos(rightWallAir),
                helper.absolutePos(rightWallAir.east()));
        placeAttached(helper, spider, spiderPos, leftWallDirection);
        // Natural acquisition on this mixed-surface fixture is covered above; this isolates multi-target combat.
        spider.setTarget(firstTarget);

        Vec3 firstTargetAnchor = helper.absoluteVec(new Vec3(
                firstTargetPos.getX() + 0.5D,
                firstTargetPos.getY(),
                firstTargetPos.getZ() + 0.5D));
        AtomicBoolean selectedFirstTarget = new AtomicBoolean(false);
        AtomicBoolean firstTargetFell = new AtomicBoolean(false);
        AtomicBoolean selectedSecondTarget = new AtomicBoolean(false);
        AtomicBoolean sawCeiling = new AtomicBoolean(spider.getAttachmentDirection() == Direction.UP);
        AtomicBoolean sawTargetWall = new AtomicBoolean(spider.getAttachmentDirection() == rightWallDirection);
        AtomicBoolean sawChainedPath = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        double secondStartHealth = secondTarget.getHealth();
        Vec3 secondTargetAnchor = helper.absoluteVec(new Vec3(
                secondTargetPos.getX() + 0.5D,
                secondTargetPos.getY(),
                secondTargetPos.getZ() + 0.5D));
        double[] secondLowestHealth = { secondStartHealth };
        double[] bestFirstDistance = { spider.distanceToSqr(firstTarget) };
        double[] bestSecondDistance = { spider.distanceToSqr(secondTarget) };
        String[] lastPath = { "null" };
        String rightWallPathToken = ":" + rightWallDirection.getName();
        helper.onEachTick(() -> {
            if (firstTarget.isAlive()) {
                firstTarget.setDeltaMovement(Vec3.ZERO);
                firstTarget.setPos(firstTargetAnchor.x, firstTargetAnchor.y, firstTargetAnchor.z);
            }
            if (secondTarget.isAlive()) {
                secondTarget.setDeltaMovement(Vec3.ZERO);
                secondTarget.setPos(secondTargetAnchor.x, secondTargetAnchor.y, secondTargetAnchor.z);
            }
            selectedFirstTarget.compareAndSet(false, spider.getTarget() == firstTarget);
            if (!firstTarget.isAlive()) {
                firstTargetFell.set(true);
            }
            selectedSecondTarget.compareAndSet(false, spider.getTarget() == secondTarget);
            Path currentPath = spider.getNavigation().getPath();
            if (currentPath != null) {
                String pathDescription = describePath(currentPath);
                lastPath[0] = pathDescription;
                if (pathDescription.contains(":up") && pathDescription.contains(rightWallPathToken)) {
                    sawChainedPath.set(true);
                }
            }
            Direction attachment = spider.getAttachmentDirection();
            if (attachment == Direction.UP) {
                sawCeiling.set(true);
            }
            if (attachment == rightWallDirection) {
                sawTargetWall.set(true);
            }
            if (firstTarget.isAlive()) {
                bestFirstDistance[0] = Math.min(bestFirstDistance[0], spider.distanceToSqr(firstTarget));
            }
            if (secondTarget.isAlive()) {
                bestSecondDistance[0] = Math.min(bestSecondDistance[0], spider.distanceToSqr(secondTarget));
                secondLowestHealth[0] = Math.min(secondLowestHealth[0], secondTarget.getHealth());
            }
            if (!completed.get()
                    && selectedFirstTarget.get() && firstTargetFell.get() && selectedSecondTarget.get()
                    && secondLowestHealth[0] < secondStartHealth
                    && sawChainedPath.get() && sawCeiling.get() && sawTargetWall.get()) {
                helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                        "Mixed-surface retargeting should not use forced-path mode");
                completed.set(true);
                succeedAndDiscard(helper, firstTarget, secondTarget, spider);
            }
        });

        helper.runAfterDelay(900, () -> {
            if (completed.get()) {
                return;
            }
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Mixed-surface retargeting should not use forced-path mode");
            if (!selectedFirstTarget.get() || !firstTargetFell.get() || !selectedSecondTarget.get()
                    || secondLowestHealth[0] >= secondStartHealth
                    || !sawChainedPath.get() || !sawCeiling.get() || !sawTargetWall.get()) {
                failAndDiscard(helper,
                        "Ground spider should chain surfaces toward a seeded first golem, kill it, retarget, and damage the second golem; selectedFirst="
                        + selectedFirstTarget.get()
                        + " firstFell=" + firstTargetFell.get()
                        + " selectedSecond=" + selectedSecondTarget.get()
                        + " sawChainedPath=" + sawChainedPath.get()
                        + " sawCeiling=" + sawCeiling.get()
                        + " sawTargetWall=" + sawTargetWall.get()
                        + " secondStartHealth=" + secondStartHealth
                        + " secondLowestHealth=" + secondLowestHealth[0]
                        + " secondHealth=" + secondTarget.getHealth()
                        + " bestFirstDistance=" + bestFirstDistance[0]
                        + " bestSecondDistance=" + bestSecondDistance[0]
                        + " pos=" + spider.position()
                        + " firstPos=" + firstTarget.position()
                        + " secondPos=" + secondTarget.position()
                        + " currentTarget=" + spider.getTarget()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " navDone=" + spider.getNavigation().isDone()
                        + " lastPath=" + lastPath[0],
                        firstTarget, secondTarget, spider);
                return;
            }
            succeedAndDiscard(helper, firstTarget, secondTarget, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 1600, batch = "zzzNaturalMixedSurfaceRetargeting")
    public static void naturalMixedSurfaceCombatRetargetsSpawnedSecondGolemAfterFirstFalls(GameTestHelper helper) {
        fillWall(helper, 0, 1, 5, 0, 4);
        fillCeiling(helper, 1, 7, 5, 0, 4);
        fillWall(helper, 8, 1, 5, 0, 4);
        clearFloor(helper, 0, 8, 0, 4);
        clearLayer(helper, 0, 8, -1, 0, 4);

        BlockPos spiderPos = new BlockPos(1, 4, 2);
        BlockPos firstTargetPos = new BlockPos(6, 1, 2);
        BlockPos secondTargetPos = new BlockPos(6, 1, 3);
        BlockPos rightWallAir = new BlockPos(7, 2, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 12.0D);
        var firstTarget = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, firstTargetPos);
        firstTarget.setNoGravity(true);
        firstTarget.setHealth(2.0F);
        if (firstTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            firstTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }

        Direction leftWallDirection = directionBetween(
                helper.absolutePos(spiderPos),
                helper.absolutePos(spiderPos.west()));
        Direction rightWallDirection = directionBetween(
                helper.absolutePos(rightWallAir),
                helper.absolutePos(rightWallAir.east()));
        placeAttached(helper, spider, spiderPos, leftWallDirection);

        AtomicBoolean selectedFirstTarget = new AtomicBoolean(false);
        AtomicBoolean firstTargetFell = new AtomicBoolean(false);
        AtomicBoolean spawnedSecondTarget = new AtomicBoolean(false);
        AtomicBoolean selectedSecondTarget = new AtomicBoolean(false);
        AtomicBoolean sawCeiling = new AtomicBoolean(spider.getAttachmentDirection() == Direction.UP);
        AtomicBoolean sawTargetWall = new AtomicBoolean(spider.getAttachmentDirection() == rightWallDirection);
        AtomicBoolean sawChainedPath = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<IronGolem> secondTarget = new AtomicReference<>();
        Vec3 firstTargetAnchor = helper.absoluteVec(new Vec3(
                firstTargetPos.getX() + 0.5D,
                firstTargetPos.getY(),
                firstTargetPos.getZ() + 0.5D));
        Vec3 secondTargetAnchor = helper.absoluteVec(new Vec3(
                secondTargetPos.getX() + 0.5D,
                secondTargetPos.getY(),
                secondTargetPos.getZ() + 0.5D));
        double[] secondStartHealth = { Double.NaN };
        double[] secondLowestHealth = { Double.NaN };
        double[] bestFirstDistance = { spider.distanceToSqr(firstTarget) };
        double[] bestSecondDistance = { Double.POSITIVE_INFINITY };
        String[] lastPath = { "null" };
        String rightWallPathToken = ":" + rightWallDirection.getName();
        helper.onEachTick(() -> {
            selectedFirstTarget.compareAndSet(false, spider.getTarget() == firstTarget);
            if (firstTarget.isAlive()) {
                firstTarget.setDeltaMovement(Vec3.ZERO);
                firstTarget.setPos(firstTargetAnchor.x, firstTargetAnchor.y, firstTargetAnchor.z);
            }
            if (!firstTarget.isAlive()) {
                firstTargetFell.set(true);
                if (spawnedSecondTarget.compareAndSet(false, true)) {
                    IronGolem spawned = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, secondTargetPos);
                    spawned.setNoGravity(true);
                    spawned.setHealth(9.0F);
                    if (spawned.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
                        spawned.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
                    }
                    secondStartHealth[0] = spawned.getHealth();
                    secondLowestHealth[0] = spawned.getHealth();
                    secondTarget.set(spawned);
                    spider.setLastHurtByMob(spawned);
                }
            }

            IronGolem currentSecondTarget = secondTarget.get();
            if (currentSecondTarget != null && currentSecondTarget.isAlive()) {
                currentSecondTarget.setDeltaMovement(Vec3.ZERO);
                currentSecondTarget.setPos(secondTargetAnchor.x, secondTargetAnchor.y, secondTargetAnchor.z);
                selectedSecondTarget.compareAndSet(false, spider.getTarget() == currentSecondTarget);
                secondLowestHealth[0] = Math.min(secondLowestHealth[0], currentSecondTarget.getHealth());
                bestSecondDistance[0] = Math.min(bestSecondDistance[0], spider.distanceToSqr(currentSecondTarget));
            }

            Path currentPath = spider.getNavigation().getPath();
            if (currentPath != null) {
                String pathDescription = describePath(currentPath);
                lastPath[0] = pathDescription;
                if (pathDescription.contains(":up") && pathDescription.contains(rightWallPathToken)) {
                    sawChainedPath.set(true);
                }
            }
            Direction attachment = spider.getAttachmentDirection();
            if (attachment == Direction.UP) {
                sawCeiling.set(true);
            }
            if (attachment == rightWallDirection) {
                sawTargetWall.set(true);
            }
            if (firstTarget.isAlive()) {
                bestFirstDistance[0] = Math.min(bestFirstDistance[0], spider.distanceToSqr(firstTarget));
            }
            if (!completed.get()
                    && selectedFirstTarget.get() && firstTargetFell.get() && spawnedSecondTarget.get()
                    && selectedSecondTarget.get()
                    && secondLowestHealth[0] < secondStartHealth[0]
                    && sawChainedPath.get() && sawCeiling.get() && sawTargetWall.get()) {
                helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                        "Natural mixed-surface retargeting should not use forced-path mode");
                completed.set(true);
                succeedAndDiscard(helper, firstTarget, currentSecondTarget, spider);
            }
        });

        helper.runAfterDelay(1300, () -> {
            if (completed.get()) {
                return;
            }
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Natural mixed-surface retargeting should not use forced-path mode");
            IronGolem currentSecondTarget = secondTarget.get();
            if (!selectedFirstTarget.get() || !firstTargetFell.get() || !spawnedSecondTarget.get()
                    || !selectedSecondTarget.get()
                    || !(secondLowestHealth[0] < secondStartHealth[0])
                    || !sawChainedPath.get() || !sawCeiling.get() || !sawTargetWall.get()) {
                String message = "Ground spider should naturally select the first golem, chain surfaces, kill it, retarget to a spawned second combat threat, and damage it; selectedFirst="
                        + selectedFirstTarget.get()
                        + " firstFell=" + firstTargetFell.get()
                        + " spawnedSecond=" + spawnedSecondTarget.get()
                        + " selectedSecond=" + selectedSecondTarget.get()
                        + " sawChainedPath=" + sawChainedPath.get()
                        + " sawCeiling=" + sawCeiling.get()
                        + " sawTargetWall=" + sawTargetWall.get()
                        + " secondStartHealth=" + secondStartHealth[0]
                        + " secondLowestHealth=" + secondLowestHealth[0]
                        + " secondHealth=" + (currentSecondTarget == null ? "null" : currentSecondTarget.getHealth())
                        + " bestFirstDistance=" + bestFirstDistance[0]
                        + " bestSecondDistance=" + bestSecondDistance[0]
                        + " pos=" + spider.position()
                        + " firstPos=" + firstTarget.position()
                        + " secondPos=" + (currentSecondTarget == null ? "null" : currentSecondTarget.position())
                        + " currentTarget=" + spider.getTarget()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " navDone=" + spider.getNavigation().isDone()
                        + " lastPath=" + lastPath[0];
                failAndDiscard(helper, message, firstTarget, currentSecondTarget, spider);
                return;
            }
            succeedAndDiscard(helper, firstTarget, currentSecondTarget, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 820, batch = "zzzzDenseMixedSurfaceObstacles")
    public static void naturalMixedSurfacePursuitDetoursAroundCeilingBarrier(GameTestHelper helper) {
        fillWall(helper, 0, 1, 5, 0, 6);
        fillCeiling(helper, 1, 7, 5, 0, 6);
        fillWall(helper, 8, 1, 5, 0, 6);
        clearFloor(helper, 0, 8, 0, 6);
        clearLayer(helper, 0, 8, -1, 0, 6);
        helper.setBlock(3, 5, 2, Blocks.AIR);
        helper.setBlock(4, 5, 2, Blocks.AIR);
        helper.setBlock(5, 5, 2, Blocks.AIR);

        BlockPos spiderPos = new BlockPos(1, 4, 2);
        BlockPos targetPos = new BlockPos(6, 1, 2);
        BlockPos rightWallAir = new BlockPos(7, 2, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 12.0D);
        var target = helper.spawnWithNoFreeWill(EntityType.SHEEP, targetPos);
        target.setNoGravity(true);
        target.setInvulnerable(true);

        Direction leftWallDirection = directionBetween(
                helper.absolutePos(spiderPos),
                helper.absolutePos(spiderPos.west()));
        Direction rightWallDirection = directionBetween(
                helper.absolutePos(rightWallAir),
                helper.absolutePos(rightWallAir.east()));
        placeAttached(helper, spider, spiderPos, leftWallDirection);

        double startDistance = spider.distanceToSqr(target);
        spider.setTarget(target);
        boolean pathStarted = spider.getNavigation().moveTo(target, 1.0D);
        if (!pathStarted) {
            failAndDiscard(helper,
                    "Ground spider should create an initial dense mixed-surface pursuit path; pos="
                    + spider.position() + " target=" + target.position()
                    + " attachment=" + spider.getAttachmentDirection(),
                    target, spider);
            return;
        }

        Path path = spider.getNavigation().getPath();
        String initialPath = describePath(path);
        BlockPos absoluteStart = helper.absolutePos(spiderPos);
        BlockPos absoluteTarget = helper.absolutePos(targetPos);
        if (!pathDetoursFromStraightLane(path, absoluteStart, absoluteTarget)) {
            failAndDiscard(helper,
                    "Initial dense mixed-surface path should detour around the missing ceiling lane; initialPath="
                    + initialPath
                    + " gapA=" + helper.absolutePos(new BlockPos(3, 5, 2))
                    + " gapB=" + helper.absolutePos(new BlockPos(5, 5, 2)),
                    target, spider);
            return;
        }

        AtomicBoolean sawCeiling = new AtomicBoolean(spider.getAttachmentDirection() == Direction.UP);
        AtomicBoolean sawTargetWall = new AtomicBoolean(spider.getAttachmentDirection() == rightWallDirection);
        AtomicBoolean sawTargetWallPath = new AtomicBoolean(initialPath.contains(":" + rightWallDirection.getName()));
        AtomicBoolean lostBellySupport = new AtomicBoolean(false);
        AtomicReference<String> lostBellySupportState = new AtomicReference<>("");
        double[] bestDistance = { startDistance };
        String[] lastPath = { initialPath };
        helper.onEachTick(() -> {
            if (target.isAlive() && spider.getTarget() != target) {
                spider.setTarget(target);
            }
            Path currentPath = spider.getNavigation().getPath();
            if (currentPath != null) {
                lastPath[0] = describePath(currentPath);
                if (lastPath[0].contains(":" + rightWallDirection.getName())) {
                    sawTargetWallPath.set(true);
                }
            }
            Direction attachment = spider.getAttachmentDirection();
            if (attachment == Direction.UP) {
                sawCeiling.set(true);
            }
            if (attachment == rightWallDirection) {
                sawTargetWall.set(true);
            }
            if (attachment != Direction.DOWN) {
                BlockPos spiderBlock = spider.blockPosition();
                boolean bellySupported = AttachmentHelper.hasSupport(helper.getLevel(), spiderBlock, attachment);
                BlockPos backBlock = spiderBlock.relative(attachment.getOpposite());
                boolean backAir = helper.getLevel().getBlockState(backBlock)
                        .getCollisionShape(helper.getLevel(), backBlock)
                        .isEmpty();
                if (!bellySupported || !backAir) {
                    lostBellySupport.set(true);
                    lostBellySupportState.compareAndSet("", "attachment=" + attachment
                            + " block=" + spiderBlock
                            + " pos=" + spider.position()
                            + " bellySupported=" + bellySupported
                            + " backAir=" + backAir);
                }
            }
            bestDistance[0] = Math.min(bestDistance[0], spider.distanceToSqr(target));
        });

        helper.runAfterDelay(560, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Dense mixed-surface pursuit should not use forced-path mode");
            boolean provedTargetWallRoute = sawTargetWall.get() || sawTargetWallPath.get();
            if (lostBellySupport.get() || !sawCeiling.get() || !provedTargetWallRoute || bestDistance[0] >= startDistance) {
                failAndDiscard(helper,
                        "Ground spider should detour around dense ceiling obstacles, keep a target-wall route, and close distance; sawCeiling="
                        + sawCeiling.get()
                        + " sawTargetWall=" + sawTargetWall.get()
                        + " sawTargetWallPath=" + sawTargetWallPath.get()
                        + " lostBellySupport=" + lostBellySupport.get()
                        + " lostBellySupportState=" + lostBellySupportState.get()
                        + " startDistance=" + startDistance
                        + " bestDistance=" + bestDistance[0]
                        + " endDistance=" + spider.distanceToSqr(target)
                        + " pos=" + spider.position()
                        + " targetPos=" + target.position()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " navDone=" + spider.getNavigation().isDone()
                        + " initialPath=" + initialPath
                        + " lastPath=" + lastPath[0],
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 1600, batch = "zzzzzSimultaneousMixedSurfaceThreats")
    public static void naturalMixedSurfaceCombatDamagesBothSimultaneousGolems(GameTestHelper helper) {
        fillWall(helper, 0, 1, 5, 0, 4);
        fillCeiling(helper, 1, 7, 5, 0, 4);
        fillWall(helper, 8, 1, 5, 0, 4);
        clearFloor(helper, 0, 8, 0, 4);
        clearLayer(helper, 0, 8, -1, 0, 4);

        BlockPos spiderPos = new BlockPos(1, 4, 2);
        BlockPos firstCandidatePos = new BlockPos(6, 1, 2);
        BlockPos secondCandidatePos = new BlockPos(6, 1, 0);
        BlockPos rightWallAir = new BlockPos(7, 2, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 12.0D);
        var firstCandidate = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, firstCandidatePos);
        var secondCandidate = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, secondCandidatePos);
        firstCandidate.setNoGravity(true);
        secondCandidate.setNoGravity(true);
        firstCandidate.setHealth(2.0F);
        secondCandidate.setHealth(2.0F);
        if (firstCandidate.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            firstCandidate.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        if (secondCandidate.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            secondCandidate.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }

        Direction leftWallDirection = directionBetween(
                helper.absolutePos(spiderPos),
                helper.absolutePos(spiderPos.west()));
        Direction rightWallDirection = directionBetween(
                helper.absolutePos(rightWallAir),
                helper.absolutePos(rightWallAir.east()));
        placeAttached(helper, spider, spiderPos, leftWallDirection);
        // Single-target mixed-surface acquisition is covered separately; seed this fixture to isolate
        // combat and retargeting while both golems are already present.
        spider.setTarget(firstCandidate);

        Vec3 firstAnchor = helper.absoluteVec(new Vec3(
                firstCandidatePos.getX() + 0.5D,
                firstCandidatePos.getY(),
                firstCandidatePos.getZ() + 0.5D));
        Vec3 secondAnchor = helper.absoluteVec(new Vec3(
                secondCandidatePos.getX() + 0.5D,
                secondCandidatePos.getY(),
                secondCandidatePos.getZ() + 0.5D));
        double firstStartHealth = firstCandidate.getHealth();
        double secondStartHealth = secondCandidate.getHealth();
        double[] firstLowestHealth = { firstStartHealth };
        double[] secondLowestHealth = { secondStartHealth };
        AtomicReference<IronGolem> firstSelectedTarget = new AtomicReference<>();
        AtomicBoolean firstSelectedTargetFell = new AtomicBoolean(false);
        AtomicBoolean damagedRemainingTarget = new AtomicBoolean(false);
        AtomicBoolean sawCeiling = new AtomicBoolean(spider.getAttachmentDirection() == Direction.UP);
        AtomicBoolean sawTargetWall = new AtomicBoolean(spider.getAttachmentDirection() == rightWallDirection);
        AtomicBoolean sawChainedPath = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        String[] lastPath = { "null" };
        String rightWallPathToken = ":" + rightWallDirection.getName();
        helper.onEachTick(() -> {
            if (firstCandidate.isAlive()) {
                firstCandidate.setDeltaMovement(Vec3.ZERO);
                firstCandidate.setPos(firstAnchor.x, firstAnchor.y, firstAnchor.z);
                firstLowestHealth[0] = Math.min(firstLowestHealth[0], firstCandidate.getHealth());
            } else {
                firstLowestHealth[0] = 0.0D;
            }
            if (secondCandidate.isAlive()) {
                secondCandidate.setDeltaMovement(Vec3.ZERO);
                secondCandidate.setPos(secondAnchor.x, secondAnchor.y, secondAnchor.z);
                secondLowestHealth[0] = Math.min(secondLowestHealth[0], secondCandidate.getHealth());
            } else {
                secondLowestHealth[0] = 0.0D;
            }

            var currentTarget = spider.getTarget();
            if (currentTarget == firstCandidate || currentTarget == secondCandidate) {
                firstSelectedTarget.compareAndSet(null, (IronGolem) currentTarget);
            }

            IronGolem selectedFirst = firstSelectedTarget.get();
            if (selectedFirst != null) {
                IronGolem remainingTarget = selectedFirst == firstCandidate ? secondCandidate : firstCandidate;
                double remainingStartHealth = remainingTarget == firstCandidate ? firstStartHealth : secondStartHealth;
                double remainingLowestHealth = remainingTarget == firstCandidate ? firstLowestHealth[0] : secondLowestHealth[0];
                if (!selectedFirst.isAlive()) {
                    firstSelectedTargetFell.set(true);
                }
                if (firstSelectedTargetFell.get() && remainingLowestHealth < remainingStartHealth) {
                    damagedRemainingTarget.set(true);
                }
            }

            Path currentPath = spider.getNavigation().getPath();
            if (currentPath != null) {
                String pathDescription = describePath(currentPath);
                lastPath[0] = pathDescription;
                if (pathDescription.contains(":up") && pathDescription.contains(rightWallPathToken)) {
                    sawChainedPath.set(true);
                }
            }
            Direction attachment = spider.getAttachmentDirection();
            if (attachment == Direction.UP) {
                sawCeiling.set(true);
            }
            if (attachment == rightWallDirection) {
                sawTargetWall.set(true);
            }

            if (!completed.get()
                    && firstSelectedTarget.get() != null
                    && firstSelectedTargetFell.get()
                    && damagedRemainingTarget.get()
                    && sawChainedPath.get()
                    && sawCeiling.get()
                    && sawTargetWall.get()) {
                helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                        "Simultaneous mixed-surface combat should not use forced-path mode");
                completed.set(true);
                succeedAndDiscard(helper, firstCandidate, secondCandidate, spider);
            }
        });

        helper.runAfterDelay(1300, () -> {
            if (completed.get()) {
                return;
            }
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Simultaneous mixed-surface combat should not use forced-path mode");
            if (firstSelectedTarget.get() == null || !firstSelectedTargetFell.get()
                    || !damagedRemainingTarget.get() || !sawChainedPath.get()
                    || !sawCeiling.get() || !sawTargetWall.get()) {
                failAndDiscard(helper,
                        "Ground spider should naturally handle two simultaneous mixed-surface golems by killing the first selected target and damaging the remaining target; firstSelected="
                        + firstSelectedTarget.get()
                        + " firstFell=" + firstSelectedTargetFell.get()
                        + " damagedRemaining=" + damagedRemainingTarget.get()
                        + " sawChainedPath=" + sawChainedPath.get()
                        + " sawCeiling=" + sawCeiling.get()
                        + " sawTargetWall=" + sawTargetWall.get()
                        + " firstStartHealth=" + firstStartHealth
                        + " firstLowestHealth=" + firstLowestHealth[0]
                        + " firstAlive=" + firstCandidate.isAlive()
                        + " secondStartHealth=" + secondStartHealth
                        + " secondLowestHealth=" + secondLowestHealth[0]
                        + " secondAlive=" + secondCandidate.isAlive()
                        + " pos=" + spider.position()
                        + " firstPos=" + firstCandidate.position()
                        + " secondPos=" + secondCandidate.position()
                        + " currentTarget=" + spider.getTarget()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " navDone=" + spider.getNavigation().isDone()
                        + " lastPath=" + lastPath[0],
                        firstCandidate, secondCandidate, spider);
                return;
            }
            succeedAndDiscard(helper, firstCandidate, secondCandidate, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 600, batch = "naturalAttackerSwitch")
    public static void naturalTargetingSwitchesToNewAttackerBeforeFirstFalls(GameTestHelper helper) {
        fillFloor(helper, 10, 5);

        BlockPos spiderPos = new BlockPos(1, 1, 2);
        BlockPos firstTargetPos = new BlockPos(3, 1, 2);
        // Keep the threatened attacker inside the authored arena's short approach
        // so generated terrain cannot turn the target switch into a long chase.
        BlockPos attackerPos = new BlockPos(6, 1, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 8.0D);
        var firstTarget = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, firstTargetPos);
        var attacker = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, attackerPos);
        attacker.setHealth(9.0F);
        if (firstTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            firstTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        if (attacker.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            attacker.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }

        Vec3 firstAnchor = helper.absoluteVec(new Vec3(
                firstTargetPos.getX() + 0.5D,
                firstTargetPos.getY(),
                firstTargetPos.getZ() + 0.5D));
        Vec3 attackerAnchor = helper.absoluteVec(new Vec3(
                attackerPos.getX() + 0.5D,
                attackerPos.getY(),
                attackerPos.getZ() + 0.5D));
        AtomicBoolean selectedFirstTarget = new AtomicBoolean(false);
        AtomicBoolean attackerThreatened = new AtomicBoolean(false);
        AtomicBoolean selectedAttacker = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        double attackerStartHealth = attacker.getHealth();
        double firstStartHealth = firstTarget.getHealth();
        double[] attackerLowestHealth = { attackerStartHealth };
        int[] ticks = { 0 };
        helper.onEachTick(() -> {
            ticks[0]++;
            if (firstTarget.isAlive()) {
                firstTarget.setDeltaMovement(Vec3.ZERO);
                firstTarget.setPos(firstAnchor.x, firstAnchor.y, firstAnchor.z);
            }
            if (attacker.isAlive()) {
                attacker.setDeltaMovement(Vec3.ZERO);
                attacker.setPos(attackerAnchor.x, attackerAnchor.y, attackerAnchor.z);
                attackerLowestHealth[0] = Math.min(attackerLowestHealth[0], attacker.getHealth());
            }

            selectedFirstTarget.compareAndSet(false, spider.getTarget() == firstTarget);
            if (!attackerThreatened.get() && selectedFirstTarget.get() && ticks[0] >= 40
                    && attackerThreatened.compareAndSet(false, true)) {
                spider.setLastHurtByMob(attacker);
            }
            selectedAttacker.compareAndSet(false, spider.getTarget() == attacker);

            if (!completed.get()
                    && selectedFirstTarget.get()
                    && attackerThreatened.get()
                    && selectedAttacker.get()
                    && firstTarget.isAlive()
                    && attackerLowestHealth[0] < attackerStartHealth) {
                helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                        "Changing-threat target priority should not use forced-path mode");
                completed.set(true);
                succeedAndDiscard(helper, firstTarget, attacker, spider);
            }
        });

        // Keep the damage invariant, but allow a slow generated-world approach to
        // complete inside the test's bounded 600-tick timeout after the target switch.
        helper.runAfterDelay(560, () -> {
            if (completed.get()) {
                return;
            }
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Changing-threat target priority should not use forced-path mode");
            if (!selectedFirstTarget.get() || !attackerThreatened.get() || !selectedAttacker.get()
                    || !firstTarget.isAlive() || attackerLowestHealth[0] >= attackerStartHealth) {
                failAndDiscard(helper,
                        "Ground spider should switch from a live first target to a new attacker and damage that attacker; selectedFirst="
                        + selectedFirstTarget.get()
                        + " attackerThreatened=" + attackerThreatened.get()
                        + " selectedAttacker=" + selectedAttacker.get()
                        + " firstAlive=" + firstTarget.isAlive()
                        + " firstStartHealth=" + firstStartHealth
                        + " firstHealth=" + firstTarget.getHealth()
                        + " attackerStartHealth=" + attackerStartHealth
                        + " attackerLowestHealth=" + attackerLowestHealth[0]
                        + " attackerHealth=" + attacker.getHealth()
                        + " currentTarget=" + spider.getTarget()
                        + " pos=" + spider.position()
                        + " firstPos=" + firstTarget.position()
                        + " attackerPos=" + attacker.position()
                        + " navDone=" + spider.getNavigation().isDone(),
                        firstTarget, attacker, spider);
                return;
            }
            succeedAndDiscard(helper, firstTarget, attacker, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 1100, batch = "zzzzzzMixedSurfaceChangingThreats")
    public static void naturalMixedSurfaceTargetingSwitchesToNewAttackerBeforeFirstFalls(GameTestHelper helper) {
        fillWall(helper, 0, 1, 5, 0, 4);
        fillCeiling(helper, 1, 7, 5, 0, 4);
        fillWall(helper, 8, 1, 5, 0, 4);
        clearFloor(helper, 0, 8, 0, 4);
        clearLayer(helper, 0, 8, -1, 0, 4);

        BlockPos spiderPos = new BlockPos(1, 4, 2);
        BlockPos firstTargetPos = new BlockPos(6, 1, 2);
        BlockPos attackerPos = new BlockPos(6, 1, 4);
        BlockPos rightWallAir = new BlockPos(7, 2, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 8.0D);
        var firstTarget = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, firstTargetPos);
        firstTarget.setNoGravity(true);
        if (firstTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            firstTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }

        Direction leftWallDirection = directionBetween(
                helper.absolutePos(spiderPos),
                helper.absolutePos(spiderPos.west()));
        Direction rightWallDirection = directionBetween(
                helper.absolutePos(rightWallAir),
                helper.absolutePos(rightWallAir.east()));
        placeAttached(helper, spider, spiderPos, leftWallDirection);

        Vec3 firstAnchor = helper.absoluteVec(new Vec3(
                firstTargetPos.getX() + 0.5D,
                firstTargetPos.getY(),
                firstTargetPos.getZ() + 0.5D));
        Vec3 attackerAnchor = helper.absoluteVec(new Vec3(
                attackerPos.getX() + 0.5D,
                attackerPos.getY(),
                attackerPos.getZ() + 0.5D));
        AtomicBoolean selectedFirstTarget = new AtomicBoolean(false);
        AtomicBoolean sawCeiling = new AtomicBoolean(spider.getAttachmentDirection() == Direction.UP);
        AtomicBoolean sawTargetWall = new AtomicBoolean(spider.getAttachmentDirection() == rightWallDirection);
        AtomicBoolean sawChainedPath = new AtomicBoolean(false);
        AtomicBoolean attackerSpawned = new AtomicBoolean(false);
        AtomicBoolean attackerThreatened = new AtomicBoolean(false);
        AtomicBoolean selectedAttacker = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<IronGolem> attacker = new AtomicReference<>();
        double firstStartHealth = firstTarget.getHealth();
        double[] attackerStartHealth = { Double.NaN };
        double[] attackerLowestHealth = { Double.NaN };
        double[] bestFirstDistance = { spider.distanceToSqr(firstTarget) };
        double[] bestAttackerDistance = { Double.POSITIVE_INFINITY };
        String[] lastPath = { "null" };
        String rightWallPathToken = ":" + rightWallDirection.getName();
        int[] ticks = { 0 };
        helper.onEachTick(() -> {
            ticks[0]++;
            if (firstTarget.isAlive()) {
                firstTarget.setDeltaMovement(Vec3.ZERO);
                firstTarget.setPos(firstAnchor.x, firstAnchor.y, firstAnchor.z);
                bestFirstDistance[0] = Math.min(bestFirstDistance[0], spider.distanceToSqr(firstTarget));
            }

            selectedFirstTarget.compareAndSet(false, spider.getTarget() == firstTarget);
            Path currentPath = spider.getNavigation().getPath();
            if (currentPath != null) {
                String pathDescription = describePath(currentPath);
                lastPath[0] = pathDescription;
                if (pathDescription.contains(":up") && pathDescription.contains(rightWallPathToken)) {
                    sawChainedPath.set(true);
                }
            }
            Direction attachment = spider.getAttachmentDirection();
            if (attachment == Direction.UP) {
                sawCeiling.set(true);
            }
            if (attachment == rightWallDirection) {
                sawTargetWall.set(true);
            }

            if (!attackerSpawned.get()
                    && selectedFirstTarget.get()
                    && sawChainedPath.get()
                    && ticks[0] >= 80
                    && attackerSpawned.compareAndSet(false, true)) {
                IronGolem spawned = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, attackerPos);
                spawned.setNoGravity(true);
                spawned.setHealth(9.0F);
                if (spawned.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
                    spawned.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
                }
                attackerStartHealth[0] = spawned.getHealth();
                attackerLowestHealth[0] = spawned.getHealth();
                attacker.set(spawned);
                spider.setLastHurtByMob(spawned);
                attackerThreatened.set(true);
            }

            IronGolem currentAttacker = attacker.get();
            if (currentAttacker != null && currentAttacker.isAlive()) {
                currentAttacker.setDeltaMovement(Vec3.ZERO);
                currentAttacker.setPos(attackerAnchor.x, attackerAnchor.y, attackerAnchor.z);
                selectedAttacker.compareAndSet(false, spider.getTarget() == currentAttacker);
                attackerLowestHealth[0] = Math.min(attackerLowestHealth[0], currentAttacker.getHealth());
                bestAttackerDistance[0] = Math.min(bestAttackerDistance[0], spider.distanceToSqr(currentAttacker));
            }

            if (!completed.get()
                    && selectedFirstTarget.get()
                    && attackerThreatened.get()
                    && selectedAttacker.get()
                    && firstTarget.isAlive()
                    && attackerLowestHealth[0] < attackerStartHealth[0]
                    && sawChainedPath.get()
                    && sawCeiling.get()
                    && sawTargetWall.get()) {
                helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                        "Mixed-surface changing-threat priority should not use forced-path mode");
                completed.set(true);
                succeedAndDiscard(helper, firstTarget, currentAttacker, spider);
            }
        });

        helper.runAfterDelay(950, () -> {
            if (completed.get()) {
                return;
            }
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Mixed-surface changing-threat priority should not use forced-path mode");
            IronGolem currentAttacker = attacker.get();
            if (!selectedFirstTarget.get() || !sawChainedPath.get() || !sawCeiling.get()
                    || !sawTargetWall.get() || !attackerThreatened.get()
                    || !selectedAttacker.get() || !firstTarget.isAlive()
                    || !(attackerLowestHealth[0] < attackerStartHealth[0])) {
                failAndDiscard(helper,
                        "Ground spider should naturally select a mixed-surface first target, switch to a new attacker before the first falls, and damage the attacker; selectedFirst="
                        + selectedFirstTarget.get()
                        + " sawChainedPath=" + sawChainedPath.get()
                        + " sawCeiling=" + sawCeiling.get()
                        + " sawTargetWall=" + sawTargetWall.get()
                        + " attackerSpawned=" + attackerSpawned.get()
                        + " attackerThreatened=" + attackerThreatened.get()
                        + " selectedAttacker=" + selectedAttacker.get()
                        + " firstStartHealth=" + firstStartHealth
                        + " firstHealth=" + firstTarget.getHealth()
                        + " firstAlive=" + firstTarget.isAlive()
                        + " attackerStartHealth=" + attackerStartHealth[0]
                        + " attackerLowestHealth=" + attackerLowestHealth[0]
                        + " attackerHealth=" + (currentAttacker == null ? "null" : currentAttacker.getHealth())
                        + " bestFirstDistance=" + bestFirstDistance[0]
                        + " bestAttackerDistance=" + bestAttackerDistance[0]
                        + " pos=" + spider.position()
                        + " firstPos=" + firstTarget.position()
                        + " attackerPos=" + (currentAttacker == null ? "null" : currentAttacker.position())
                        + " currentTarget=" + spider.getTarget()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " navDone=" + spider.getNavigation().isDone()
                        + " lastPath=" + lastPath[0],
                        firstTarget, currentAttacker, spider);
                return;
            }
            succeedAndDiscard(helper, firstTarget, currentAttacker, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 1900, batch = "zzzzzzzMixedSurfaceMultiAttackerThreats")
    public static void naturalMixedSurfaceTargetingSwitchesBetweenTwoAttackersBeforeFirstFalls(GameTestHelper helper) {
        fillWall(helper, 0, 1, 5, 0, 4);
        fillCeiling(helper, 1, 7, 5, 0, 4);
        fillWall(helper, 8, 1, 5, 0, 4);
        clearFloor(helper, 0, 8, 0, 4);
        clearLayer(helper, 0, 8, -1, 0, 4);

        BlockPos spiderPos = new BlockPos(1, 4, 2);
        BlockPos firstTargetPos = new BlockPos(6, 1, 2);
        BlockPos firstAttackerPos = new BlockPos(6, 1, 4);
        BlockPos secondAttackerPos = new BlockPos(6, 1, 3);
        BlockPos rightWallAir = new BlockPos(7, 2, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        setFollowRange(spider, 8.0D);
        var firstTarget = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, firstTargetPos);
        firstTarget.setNoGravity(true);
        if (firstTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            firstTarget.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }

        Direction leftWallDirection = directionBetween(
                helper.absolutePos(spiderPos),
                helper.absolutePos(spiderPos.west()));
        Direction rightWallDirection = directionBetween(
                helper.absolutePos(rightWallAir),
                helper.absolutePos(rightWallAir.east()));
        placeAttached(helper, spider, spiderPos, leftWallDirection);

        Vec3 firstTargetAnchor = helper.absoluteVec(new Vec3(
                firstTargetPos.getX() + 0.5D,
                firstTargetPos.getY(),
                firstTargetPos.getZ() + 0.5D));
        Vec3 firstAttackerAnchor = helper.absoluteVec(new Vec3(
                firstAttackerPos.getX() + 0.5D,
                firstAttackerPos.getY(),
                firstAttackerPos.getZ() + 0.5D));
        Vec3 secondAttackerAnchor = helper.absoluteVec(new Vec3(
                secondAttackerPos.getX() + 0.5D,
                secondAttackerPos.getY(),
                secondAttackerPos.getZ() + 0.5D));
        AtomicBoolean selectedFirstTarget = new AtomicBoolean(false);
        AtomicBoolean sawCeiling = new AtomicBoolean(spider.getAttachmentDirection() == Direction.UP);
        AtomicBoolean sawTargetWall = new AtomicBoolean(spider.getAttachmentDirection() == rightWallDirection);
        AtomicBoolean sawChainedPath = new AtomicBoolean(false);
        AtomicBoolean firstAttackerSpawned = new AtomicBoolean(false);
        AtomicBoolean firstAttackerThreatened = new AtomicBoolean(false);
        AtomicBoolean selectedFirstAttacker = new AtomicBoolean(false);
        AtomicBoolean secondAttackerSpawned = new AtomicBoolean(false);
        AtomicBoolean secondAttackerThreatened = new AtomicBoolean(false);
        AtomicBoolean selectedSecondAttacker = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<IronGolem> firstAttacker = new AtomicReference<>();
        AtomicReference<IronGolem> secondAttacker = new AtomicReference<>();
        double firstTargetStartHealth = firstTarget.getHealth();
        double[] firstAttackerStartHealth = { Double.NaN };
        double[] firstAttackerLowestHealth = { Double.NaN };
        double[] secondAttackerStartHealth = { Double.NaN };
        double[] secondAttackerLowestHealth = { Double.NaN };
        double[] bestFirstTargetDistance = { spider.distanceToSqr(firstTarget) };
        double[] bestFirstAttackerDistance = { Double.POSITIVE_INFINITY };
        double[] bestSecondAttackerDistance = { Double.POSITIVE_INFINITY };
        String[] lastPath = { "null" };
        String rightWallPathToken = ":" + rightWallDirection.getName();
        int[] ticks = { 0 };
        helper.onEachTick(() -> {
            ticks[0]++;
            if (firstTarget.isAlive()) {
                firstTarget.setDeltaMovement(Vec3.ZERO);
                firstTarget.setPos(firstTargetAnchor.x, firstTargetAnchor.y, firstTargetAnchor.z);
                bestFirstTargetDistance[0] = Math.min(bestFirstTargetDistance[0], spider.distanceToSqr(firstTarget));
            }

            selectedFirstTarget.compareAndSet(false, spider.getTarget() == firstTarget);
            Path currentPath = spider.getNavigation().getPath();
            if (currentPath != null) {
                String pathDescription = describePath(currentPath);
                lastPath[0] = pathDescription;
                if (pathDescription.contains(":up") && pathDescription.contains(rightWallPathToken)) {
                    sawChainedPath.set(true);
                }
            }
            Direction attachment = spider.getAttachmentDirection();
            if (attachment == Direction.UP) {
                sawCeiling.set(true);
            }
            if (attachment == rightWallDirection) {
                sawTargetWall.set(true);
            }

            if (!firstAttackerSpawned.get()
                    && selectedFirstTarget.get()
                    && sawChainedPath.get()
                    && ticks[0] >= 80
                    && firstAttackerSpawned.compareAndSet(false, true)) {
                IronGolem spawned = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, firstAttackerPos);
                spawned.setNoGravity(true);
                spawned.setHealth(9.0F);
                if (spawned.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
                    spawned.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
                }
                firstAttackerStartHealth[0] = spawned.getHealth();
                firstAttackerLowestHealth[0] = spawned.getHealth();
                firstAttacker.set(spawned);
                spider.setLastHurtByMob(spawned);
                firstAttackerThreatened.set(true);
            }

            IronGolem currentFirstAttacker = firstAttacker.get();
            if (currentFirstAttacker != null && currentFirstAttacker.isAlive()) {
                currentFirstAttacker.setDeltaMovement(Vec3.ZERO);
                currentFirstAttacker.setPos(firstAttackerAnchor.x, firstAttackerAnchor.y, firstAttackerAnchor.z);
                selectedFirstAttacker.compareAndSet(false, spider.getTarget() == currentFirstAttacker);
                firstAttackerLowestHealth[0] = Math.min(firstAttackerLowestHealth[0], currentFirstAttacker.getHealth());
                bestFirstAttackerDistance[0] = Math.min(bestFirstAttackerDistance[0],
                        spider.distanceToSqr(currentFirstAttacker));
            } else if (currentFirstAttacker != null) {
                firstAttackerLowestHealth[0] = Math.min(firstAttackerLowestHealth[0], currentFirstAttacker.getHealth());
            }

            if (!secondAttackerSpawned.get()
                    && firstAttackerThreatened.get()
                    && selectedFirstAttacker.get()
                    && firstAttackerLowestHealth[0] < firstAttackerStartHealth[0]
                    && currentFirstAttacker != null
                    && !currentFirstAttacker.isAlive()
                    && firstTarget.isAlive()
                    && secondAttackerSpawned.compareAndSet(false, true)) {
                IronGolem spawned = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, secondAttackerPos);
                spawned.setNoGravity(true);
                spawned.setHealth(9.0F);
                if (spawned.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
                    spawned.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
                }
                secondAttackerStartHealth[0] = spawned.getHealth();
                secondAttackerLowestHealth[0] = spawned.getHealth();
                secondAttacker.set(spawned);
                spider.setLastHurtByMob(spawned);
                secondAttackerThreatened.set(true);
            }

            IronGolem currentSecondAttacker = secondAttacker.get();
            if (currentSecondAttacker != null && currentSecondAttacker.isAlive()) {
                currentSecondAttacker.setDeltaMovement(Vec3.ZERO);
                currentSecondAttacker.setPos(secondAttackerAnchor.x, secondAttackerAnchor.y, secondAttackerAnchor.z);
                selectedSecondAttacker.compareAndSet(false, spider.getTarget() == currentSecondAttacker);
                secondAttackerLowestHealth[0] = Math.min(secondAttackerLowestHealth[0],
                        currentSecondAttacker.getHealth());
                bestSecondAttackerDistance[0] = Math.min(bestSecondAttackerDistance[0],
                        spider.distanceToSqr(currentSecondAttacker));
            }

            if (!completed.get()
                    && selectedFirstTarget.get()
                    && firstAttackerThreatened.get()
                    && selectedFirstAttacker.get()
                    && firstAttackerLowestHealth[0] < firstAttackerStartHealth[0]
                    && secondAttackerThreatened.get()
                    && selectedSecondAttacker.get()
                    && secondAttackerLowestHealth[0] < secondAttackerStartHealth[0]
                    && firstTarget.isAlive()
                    && sawChainedPath.get()
                    && sawCeiling.get()
                    && sawTargetWall.get()) {
                helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                        "Mixed-surface multi-attacker changing-threat priority should not use forced-path mode");
                completed.set(true);
                succeedAndDiscard(helper, firstTarget, currentFirstAttacker, currentSecondAttacker, spider);
            }
        });

        helper.runAfterDelay(1650, () -> {
            if (completed.get()) {
                return;
            }
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Mixed-surface multi-attacker changing-threat priority should not use forced-path mode");
            IronGolem currentFirstAttacker = firstAttacker.get();
            IronGolem currentSecondAttacker = secondAttacker.get();
            if (!selectedFirstTarget.get() || !sawChainedPath.get() || !sawCeiling.get()
                    || !sawTargetWall.get() || !firstAttackerThreatened.get()
                    || !selectedFirstAttacker.get()
                    || !(firstAttackerLowestHealth[0] < firstAttackerStartHealth[0])
                    || !secondAttackerThreatened.get()
                    || !selectedSecondAttacker.get()
                    || !(secondAttackerLowestHealth[0] < secondAttackerStartHealth[0])
                    || !firstTarget.isAlive()) {
                failAndDiscard(helper,
                        "Ground spider should naturally select a mixed-surface first target, switch to a first attacker, then switch to and damage a second attacker before the first target falls; selectedFirst="
                        + selectedFirstTarget.get()
                        + " sawChainedPath=" + sawChainedPath.get()
                        + " sawCeiling=" + sawCeiling.get()
                        + " sawTargetWall=" + sawTargetWall.get()
                        + " firstAttackerSpawned=" + firstAttackerSpawned.get()
                        + " firstAttackerThreatened=" + firstAttackerThreatened.get()
                        + " selectedFirstAttacker=" + selectedFirstAttacker.get()
                        + " firstAttackerStartHealth=" + firstAttackerStartHealth[0]
                        + " firstAttackerLowestHealth=" + firstAttackerLowestHealth[0]
                        + " firstAttackerHealth=" + (currentFirstAttacker == null ? "null" : currentFirstAttacker.getHealth())
                        + " secondAttackerSpawned=" + secondAttackerSpawned.get()
                        + " secondAttackerThreatened=" + secondAttackerThreatened.get()
                        + " selectedSecondAttacker=" + selectedSecondAttacker.get()
                        + " secondAttackerStartHealth=" + secondAttackerStartHealth[0]
                        + " secondAttackerLowestHealth=" + secondAttackerLowestHealth[0]
                        + " secondAttackerHealth=" + (currentSecondAttacker == null ? "null" : currentSecondAttacker.getHealth())
                        + " firstTargetStartHealth=" + firstTargetStartHealth
                        + " firstTargetHealth=" + firstTarget.getHealth()
                        + " firstTargetAlive=" + firstTarget.isAlive()
                        + " bestFirstTargetDistance=" + bestFirstTargetDistance[0]
                        + " bestFirstAttackerDistance=" + bestFirstAttackerDistance[0]
                        + " bestSecondAttackerDistance=" + bestSecondAttackerDistance[0]
                        + " pos=" + spider.position()
                        + " firstTargetPos=" + firstTarget.position()
                        + " firstAttackerPos=" + (currentFirstAttacker == null ? "null" : currentFirstAttacker.position())
                        + " secondAttackerPos=" + (currentSecondAttacker == null ? "null" : currentSecondAttacker.position())
                        + " currentTarget=" + spider.getTarget()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " navDone=" + spider.getNavigation().isDone()
                        + " lastPath=" + lastPath[0],
                        firstTarget, currentFirstAttacker, currentSecondAttacker, spider);
                return;
            }
            succeedAndDiscard(helper, firstTarget, currentFirstAttacker, currentSecondAttacker, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 360, batch = "naturalCeilingGap")
    public static void naturalCeilingPursuitDetoursAroundGap(GameTestHelper helper) {
        fillCeiling(helper, 0, 8, 5, 0, 4);
        helper.setBlock(4, 5, 2, Blocks.AIR);
        helper.setBlock(7, 3, 2, Blocks.STONE);

        BlockPos spiderPos = new BlockPos(1, 4, 2);
        BlockPos targetPos = new BlockPos(7, 4, 2);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        var target = helper.spawnWithNoFreeWill(EntityType.SHEEP, targetPos);
        target.setNoGravity(true);
        target.setInvulnerable(true);
        placeAttached(helper, spider, spiderPos, Direction.UP);

        double startDistance = spider.distanceToSqr(target);
        spider.setTarget(target);
        boolean pathStarted = spider.getNavigation().moveTo(target, 1.0D);
        if (!pathStarted) {
            failAndDiscard(helper,
                    "Ground spider should create an initial ceiling-gap pursuit path; pos="
                    + spider.position() + " target=" + target.position()
                    + " attachment=" + spider.getAttachmentDirection(),
                    target, spider);
            return;
        }

        Path path = spider.getNavigation().getPath();
        String initialPath = describePath(path);
        BlockPos absoluteStart = helper.absolutePos(spiderPos);
        BlockPos absoluteTarget = helper.absolutePos(targetPos);
        if (!pathDetoursFromStraightLane(path, absoluteStart, absoluteTarget)) {
            failAndDiscard(helper,
                    "Initial ceiling-gap path should detour around the missing support; initialPath="
                    + initialPath + " gap=" + helper.absolutePos(new BlockPos(4, 5, 2)),
                    target, spider);
            return;
        }

        AtomicBoolean sawCeiling = new AtomicBoolean(spider.getAttachmentDirection() == Direction.UP);
        double[] bestCeilingDistance = { startDistance };
        String[] lastPath = { initialPath };
        helper.onEachTick(() -> {
            if (target.isAlive() && spider.getTarget() != target) {
                spider.setTarget(target);
            }
            Path currentPath = spider.getNavigation().getPath();
            if (currentPath != null) {
                lastPath[0] = describePath(currentPath);
            }
            if (spider.getAttachmentDirection() == Direction.UP) {
                sawCeiling.set(true);
                bestCeilingDistance[0] = Math.min(bestCeilingDistance[0], spider.distanceToSqr(target));
            }
        });

        helper.runAfterDelay(180, () -> {
            helper.assertEntityProperty(spider, entity -> !entity.isFollowingForcedPath(),
                    "Natural ceiling-gap pursuit should not use forced-path mode");
            if (!sawCeiling.get() || bestCeilingDistance[0] >= startDistance) {
                failAndDiscard(helper,
                        "Ground spider should detour around a ceiling gap and close distance; sawCeiling="
                        + sawCeiling.get()
                        + " startDistance=" + startDistance
                        + " bestCeilingDistance=" + bestCeilingDistance[0]
                        + " endDistance=" + spider.distanceToSqr(target)
                        + " pos=" + spider.position()
                        + " attachment=" + spider.getAttachmentDirection()
                        + " navDone=" + spider.getNavigation().isDone()
                        + " initialPath=" + initialPath
                        + " lastPath=" + lastPath[0],
                        target, spider);
                return;
            }
            succeedAndDiscard(helper, target, spider);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 80, batch = "attachmentSupport")
    public static void unsupportedCeilingAttachmentSnapsBellyBackToSupport(GameTestHelper helper) {
        fillCeiling(helper, 0, 2, 5, 0, 2);

        BlockPos spiderPos = new BlockPos(1, 4, 1);
        GroundSpiderEntity spider = helper.spawn(EntityRegistry.GROUND_SPIDER.get(), spiderPos);
        spider.setNoAi(true);
        placeAttached(helper, spider, spiderPos, Direction.UP);
        spider.teleportTo(spider.getX(), spider.getY(), spider.getZ() + 1.65D);
        spider.setAttachmentDirection(Direction.UP);
        spider.setNoGravity(true);

        helper.runAfterDelay(10, () -> {
            Direction attachment = spider.getAttachmentDirection();
            BlockPos spiderBlock = spider.blockPosition();
            boolean bellySupported = AttachmentHelper.hasSupport(helper.getLevel(), spiderBlock, attachment);
            BlockPos backBlock = spiderBlock.relative(attachment.getOpposite());
            boolean backAir = helper.getLevel().getBlockState(backBlock)
                    .getCollisionShape(helper.getLevel(), backBlock)
                    .isEmpty();
            if (attachment != Direction.UP || !bellySupported || !backAir) {
                failAndDiscard(helper, "Unsupported ceiling attachment should snap back so the belly faces support; attachment="
                        + attachment
                        + " block=" + spiderBlock
                        + " pos=" + spider.position()
                        + " bellySupported=" + bellySupported
                        + " backAir=" + backAir,
                        spider);
                return;
            }
            succeedAndDiscard(helper, spider);
        });
    }

    private static void fillFloor(GameTestHelper helper, int width, int depth) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }

    private static void setFloorSupports(GameTestHelper helper, BlockPos... supports) {
        for (BlockPos support : supports) {
            helper.setBlock(support.getX(), support.getY(), support.getZ(), Blocks.STONE);
        }
    }

    private static void buildSurfaceCornerShortcutFixture(GameTestHelper helper) {
        clearVolume(helper, 0, 10, 0, 3, 0, 6);

        for (int x = 1; x <= 9; x++) {
            helper.setBlock(x, 0, 5, Blocks.STONE);
        }
        for (int z = 1; z <= 5; z++) {
            helper.setBlock(9, 0, z, Blocks.STONE);
        }
        helper.setBlock(8, 0, 1, Blocks.STONE);
        helper.setBlock(7, 0, 1, Blocks.STONE);

        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
    }

    private static boolean isRaisedWalkAnimation(String animation) {
        return "raised_walk_forward".equals(animation)
                || "raised_walk_forward_right".equals(animation)
                || "raised_walk_forward_left".equals(animation);
    }

    private static void setFollowRange(GroundSpiderEntity spider, double range) {
        if (spider.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            spider.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(range);
        }
    }

    private static IronGolem stressTarget(GameTestHelper helper, BlockPos pos) {
        IronGolem target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, pos);
        target.setNoGravity(true);
        target.setInvulnerable(true);
        if (target.getAttribute(Attributes.MAX_HEALTH) != null) {
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        }
        target.setHealth(200.0F);
        if (target.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        }
        target.addTag(GroundSpiderEntity.DROP_ATTACK_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.CEILING_STALK_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.WEB_SHOT_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.WEB_TRAP_PLACEMENT_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.WEB_LOWER_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.POUNCE_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.RETREAT_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.GRAB_PULL_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.DRAG_NEST_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.PACK_COORDINATION_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.ESCAPE_CUTTING_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.THREAT_DISPLAY_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.LINE_OF_SIGHT_STALKING_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.DARKNESS_PREFERENCE_TEST_TARGET_TAG);
        target.addTag(GroundSpiderEntity.WALL_PEEK_TEST_TARGET_TAG);
        return target;
    }

    private static void recordStressState(Set<String> states, GroundSpiderEntity spider) {
        if (spider.isPackDirectPressureRole()) states.add("pack_direct");
        if (spider.isPackAmbushRole()) states.add("pack_ambush");
        if (spider.isPackFlankRole()) states.add("pack_flank");
        if (spider.isCeilingStalking()) states.add("ceiling_stalk");
        if (spider.isCircleStrafing()) states.add("circle_strafe");
        if (spider.isBackpedalingFacingTarget()) states.add("backpedal");
        if (spider.isSprintBurstActive()) states.add("sprint_burst");
        if (spider.isCombatStalking()) states.add("stalk_pause");
        if (spider.isDropAttackActive()) states.add("drop_attack");
        if (spider.isWebShotActive()) states.add("web_shot");
        if (spider.isWebTrapPlacementActive()) states.add("web_trap");
        if (spider.isWebLowerActive()) states.add("web_lower");
        if (spider.isPounceActive()) states.add("pounce");
        if (spider.isRetreatActive()) states.add("retreat");
        if (spider.isFakeRetreatActive()) states.add("fake_retreat");
        if (spider.isGrabPullActive()) states.add("grab_pull");
        if (spider.isDragNestActive()) states.add("drag_nest");
        if (spider.isEscapeCutting()) states.add("escape_cutting");
        if (spider.isThreatDisplaying()) states.add("threat_display");
        if (spider.isLineOfSightStalking()) states.add("line_of_sight_stalking");
        if (spider.isDarknessPreferenceActive()) states.add("darkness_preference");
        if (spider.isWallPeeking()) states.add("wall_peek");
        if (spider.isPreyInteracting()) states.add("prey_interaction");
    }

    private static Entity[] combineEntities(Entity[] first, Entity[] second) {
        Entity[] combined = new Entity[first.length + second.length];
        System.arraycopy(first, 0, combined, 0, first.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    private static void placeAttached(GameTestHelper helper, GroundSpiderEntity spider, BlockPos airPos,
            Direction attachment) {
        Vec3 anchor = AttachmentHelper.anchorFor(spider, helper.absolutePos(airPos), attachment);
        spider.teleportTo(anchor.x, anchor.y, anchor.z);
        spider.setAttachmentDirection(attachment);
        spider.setNoGravity(true);
    }

    private static void failAndDiscard(GameTestHelper helper, String message, Entity... entities) {
        discardAll(entities);
        helper.fail(message);
    }

    private static void succeedAndDiscard(GameTestHelper helper, Entity... entities) {
        discardAll(entities);
        helper.succeed();
    }

    private static boolean hasCobwebNear(GameTestHelper helper, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 1))) {
            if (helper.getLevel().getBlockState(pos).is(Blocks.COBWEB)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCobwebAt(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockState(pos).is(Blocks.COBWEB);
    }

    private static boolean hasSingleThreadWebAt(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockState(pos).is(BlockRegistry.SINGLE_THREAD_WEB.get());
    }

    private static boolean hasWebTrapWebAt(GameTestHelper helper, BlockPos pos) {
        return hasSingleThreadWebAt(helper, pos) || hasCobwebAt(helper, pos);
    }

    private static void discardAll(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null) {
                entity.discard();
            }
        }
    }

    private static void clearFloor(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ) {
        clearLayer(helper, minX, maxX, 0, minZ, maxZ);
    }

    private static void clearLayer(GameTestHelper helper, int minX, int maxX, int y, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(x, y, z, Blocks.AIR);
            }
        }
    }

    private static void clearVolume(GameTestHelper helper, int minX, int maxX, int minY, int maxY,
            int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
    }

    private static void fillWall(GameTestHelper helper, int x, int minY, int maxY, int minZ, int maxZ) {
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(x, y, z, Blocks.STONE);
            }
        }
    }

    private static void fillCeiling(GameTestHelper helper, int minX, int maxX, int y, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(x, y, z, Blocks.STONE);
            }
        }
    }

    private static Direction directionBetween(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        for (Direction direction : Direction.values()) {
            if (direction.getStepX() == dx && direction.getStepY() == dy && direction.getStepZ() == dz) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Expected adjacent positions: " + from + " to " + to);
    }

    private static boolean pathDetoursFromStraightLane(Path path, BlockPos start, BlockPos target) {
        if (path == null || path.getNodeCount() <= 0) {
            return false;
        }

        int dx = target.getX() - start.getX();
        int dz = target.getZ() - start.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            int laneZ = start.getZ();
            for (int i = 0; i < path.getNodeCount(); i++) {
                if (path.getNode(i).asBlockPos().getZ() != laneZ) {
                    return true;
                }
            }
        } else {
            int laneX = start.getX();
            for (int i = 0; i < path.getNodeCount(); i++) {
                if (path.getNode(i).asBlockPos().getX() != laneX) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean pathUsesAttachment(Path path, Direction attachment) {
        if (path == null) {
            return false;
        }
        for (int i = 0; i < path.getNodeCount(); i++) {
            if (attachmentForPathNode(path.getNode(i)) == attachment) {
                return true;
            }
        }
        return false;
    }

    private static int pathAttachmentTransitionCount(Path path) {
        if (path == null || path.getNodeCount() <= 1) {
            return 0;
        }
        int transitions = 0;
        Direction previous = attachmentForPathNode(path.getNode(0));
        for (int i = 1; i < path.getNodeCount(); i++) {
            Direction current = attachmentForPathNode(path.getNode(i));
            if (current != previous) {
                transitions++;
                previous = current;
            }
        }
        return transitions;
    }

    private static Direction attachmentForPathNode(Node node) {
        if (node instanceof com.horrormods.spiders.entity.ai.ClimberNodeEvaluator.CustomNode customNode
                && customNode.attachment != null) {
            return customNode.attachment;
        }
        return Direction.DOWN;
    }

    private static BlockPos pathEndPos(Path path) {
        if (path == null || path.getNodeCount() <= 0) {
            return null;
        }
        return path.getNode(path.getNodeCount() - 1).asBlockPos();
    }

    private static double pathSegmentLength(GroundSpiderEntity spider, Vec3 start, Path path) {
        if (path == null || path.getNodeCount() <= 0) {
            return Double.POSITIVE_INFINITY;
        }

        double length = 0.0D;
        Vec3 previous = start;
        for (int i = 0; i < path.getNodeCount(); i++) {
            Vec3 next = anchorForPathNode(spider, path.getNode(i));
            length += previous.distanceTo(next);
            previous = next;
        }
        return length;
    }

    private static double anchoredPolylineLength(GroundSpiderEntity spider, GameTestHelper helper,
            Direction attachment, BlockPos... localAirPositions) {
        double length = 0.0D;
        Vec3 previous = spider.position();
        for (BlockPos localAirPosition : localAirPositions) {
            Vec3 next = AttachmentHelper.anchorFor(spider, helper.absolutePos(localAirPosition), attachment);
            length += previous.distanceTo(next);
            previous = next;
        }
        return length;
    }

    private static FakePlayer dummyPlayerAt(GameTestHelper helper, BlockPos localPos) {
        FakePlayer dummyPlayer = FakePlayerFactory.getMinecraft(helper.getLevel());
        dummyPlayer.setInvulnerable(true);
        dummyPlayer.setNoGravity(true);
        dummyPlayer.noPhysics = true;
        dummyPlayer.setHealth(dummyPlayer.getMaxHealth());
        positionDummyPlayer(helper, dummyPlayer, localPos);
        return dummyPlayer;
    }

    private static void positionDummyPlayer(GameTestHelper helper, FakePlayer dummyPlayer, BlockPos localPos) {
        Vec3 pos = helper.absoluteVec(new Vec3(
                localPos.getX() + 0.5D,
                localPos.getY(),
                localPos.getZ() + 0.5D));
        dummyPlayer.teleportTo(pos.x, pos.y, pos.z);
        dummyPlayer.setPos(pos.x, pos.y, pos.z);
        dummyPlayer.setDeltaMovement(Vec3.ZERO);
    }

    private static Vec3 anchorForPathNode(GroundSpiderEntity spider, Node node) {
        if (node instanceof com.horrormods.spiders.entity.ai.ClimberNodeEvaluator.CustomNode customNode
                && !Double.isNaN(customNode.px)) {
            return new Vec3(customNode.px, customNode.py, customNode.pz);
        }
        Direction attachment = node instanceof com.horrormods.spiders.entity.ai.ClimberNodeEvaluator.CustomNode customNode
                ? customNode.attachment
                : Direction.DOWN;
        return AttachmentHelper.anchorFor(spider, node.asBlockPos(), attachment);
    }

    private static boolean facesTargetOnFloor(GroundSpiderEntity spider, Entity target, double maxDegrees) {
        Vec3 toTarget = target.position().subtract(spider.position());
        Vec3 horizontal = new Vec3(toTarget.x, 0.0D, toTarget.z);
        if (horizontal.lengthSqr() <= 1.0E-6D) {
            return true;
        }
        float expectedYaw = (float) (Math.atan2(horizontal.z, horizontal.x) * (180.0D / Math.PI)) - 90.0F;
        return Math.abs(Mth.wrapDegrees(spider.getYRot() - expectedYaw)) <= maxDegrees;
    }

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0.0D, vector.z);
    }

    private static double signedHorizontalAngleDegrees(Vec3 from, Vec3 to) {
        if (from.lengthSqr() <= 1.0E-6D || to.lengthSqr() <= 1.0E-6D) {
            return 0.0D;
        }
        Vec3 a = from.normalize();
        Vec3 b = to.normalize();
        double cross = a.x * b.z - a.z * b.x;
        double dot = a.x * b.x + a.z * b.z;
        return Math.atan2(cross, dot) * (180.0D / Math.PI);
    }

    private static String describePath(Path path) {
        if (path == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder();
        out.append("nodes=").append(path.getNodeCount())
                .append(" next=").append(path.getNextNodeIndex())
                .append("[");
        for (int i = 0; i < path.getNodeCount(); i++) {
            if (i > 0) {
                out.append(" -> ");
            }
            Node node = path.getNode(i);
            out.append(node.asBlockPos());
            if (node instanceof com.horrormods.spiders.entity.ai.ClimberNodeEvaluator.CustomNode customNode) {
                out.append(":").append(customNode.attachment.getName());
            }
        }
        out.append("]");
        return out.toString();
    }

    private static final class ExposedClimberPathNavigator extends ClimberPathNavigator {
        private ExposedClimberPathNavigator(GroundSpiderEntity mob, Level level) {
            super(mob, level, true, true);
        }

        private Path createBestPath(Set<BlockPos> positions) {
            return super.createPath(positions, 16, false, 0);
        }
    }
}
