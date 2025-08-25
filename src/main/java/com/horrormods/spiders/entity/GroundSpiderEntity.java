package com.horrormods.spiders.entity;

import com.horrormods.spiders.entity.ai.ClimberMoveControl;
import com.horrormods.spiders.entity.ai.ClimberNodeEvaluator;
import com.horrormods.spiders.entity.ai.ClimberPathNavigator;
import com.horrormods.spiders.entity.util.AttachmentHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import software.bernie.geckolib3.util.GeckoLibUtil;

public class GroundSpiderEntity extends Monster implements IAnimatable, IClimberEntity {

    private static final EntityDataAccessor<Direction> ATTACHMENT_DIRECTION =
            SynchedEntityData.defineId(GroundSpiderEntity.class, EntityDataSerializers.DIRECTION);

    private final AnimationFactory factory = GeckoLibUtil.createFactory(this);

    private transient ClimberNodeEvaluator probeEvaluator;
    private Direction pendingAttachment = Direction.DOWN;
    private int pendingAttachTicks = 0;
    private static final int ATTACH_CONFIRM_TICKS = 4;

    public GroundSpiderEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new ClimberMoveControl(this);
        this.navigation = new ClimberPathNavigator(this, this.level, true, true);
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
        super.tick();
        if (!this.level.isClientSide) {
            if (this.probeEvaluator == null) this.probeEvaluator = new ClimberNodeEvaluator();

            // Probe region so the evaluator has collision data
            BlockPos min = this.blockPosition().offset(-2, -2, -2);
            BlockPos max = this.blockPosition().offset( 2,  2,  2);
            PathNavigationRegion region = new PathNavigationRegion(this.level, min, max);
            this.probeEvaluator.prepare(region, this);
            this.probeEvaluator.setCanPathWalls(true);
            this.probeEvaluator.setCanPathCeiling(true);

            Direction current = getAttachmentDirection();
            Direction found = AttachmentHelper.findAttachment(this.level, this, this.blockPosition());
            if (found == null && current != Direction.DOWN &&
                    AttachmentHelper.hasSupport(level, blockPosition(), current) &&
                    AttachmentHelper.aabbFitsOnSurface(level, this, blockPosition(), current)) {
                found = current;
            }
            if (found == null) found = Direction.DOWN;

            if (found == current) {
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
            }
        }
    }

    @Override
    public void travel(Vec3 input) {
        Direction attach = getAttachmentDirection();
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
        v = plane.add(n.scale(0.04D)); // glue toward surface

        this.setDeltaMovement(v);
        this.move(MoverType.SELF, v);
    }

    // AI goals unchanged from your existing code
    @Override protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    @Override public MobType getMobType() { return MobType.ARTHROPOD; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.SPIDER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.SPIDER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SPIDER_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.SPIDER_STEP, 0.15F, 1.0F);
    }

    // GeckoLib animation methods
    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        if (event.isMoving()) {
            event.getController().setAnimation(new AnimationBuilder().addAnimation("walk_forward", ILoopType.EDefaultLoopTypes.LOOP));
        } else {
            event.getController().setAnimation(new AnimationBuilder().addAnimation("idle", ILoopType.EDefaultLoopTypes.LOOP));
        }
        return PlayState.CONTINUE;
    }
    @Override public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 5, this::predicate));
    }
    @Override public AnimationFactory getFactory() { return this.factory; }
}
