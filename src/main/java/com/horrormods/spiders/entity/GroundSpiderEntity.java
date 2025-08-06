package com.horrormods.spiders.entity;

import com.horrormods.spiders.entity.ai.AdvancedClimberPathNavigator;
import com.horrormods.spiders.entity.ai.AdvancedWalkNodeEvaluator;
import com.horrormods.spiders.entity.ai.ClimberMoveControl;
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
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
import software.bernie.geckolib3.util.GeckoLibUtil;

public class GroundSpiderEntity extends Monster implements IAnimatable {
    private final AnimationFactory factory = GeckoLibUtil.createFactory(this);

    private static final EntityDataAccessor<Direction> ATTACHMENT_DIRECTION = SynchedEntityData.defineId(GroundSpiderEntity.class, EntityDataSerializers.DIRECTION);
    private transient AdvancedWalkNodeEvaluator nodeEvaluator;

    public GroundSpiderEntity(EntityType<? extends Monster> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        // THIS IS THE CORRECTED LOGIC: Initialize our custom controls and navigation here.
        this.moveControl = new ClimberMoveControl(this);
        this.navigation = new AdvancedClimberPathNavigator(this, this.level, true, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3F)
                .add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ATTACHMENT_DIRECTION, Direction.DOWN);
    }

    public Direction getAttachmentDirection() {
        return this.entityData.get(ATTACHMENT_DIRECTION);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level.isClientSide()) {
            if (this.nodeEvaluator == null) {
                this.nodeEvaluator = new AdvancedWalkNodeEvaluator();
            }

            BlockPos corner1 = this.blockPosition().offset(-2, -2, -2);
            BlockPos corner2 = this.blockPosition().offset(2, 2, 2);
            PathNavigationRegion region = new PathNavigationRegion(this.level, corner1, corner2);

            this.nodeEvaluator.prepare(region, this);
            this.nodeEvaluator.setCanPathWalls(true);
            this.nodeEvaluator.setCanPathCeiling(true);

            Direction currentAttachment = this.nodeEvaluator.findValidAttachment(this.blockPosition());

            if (currentAttachment != null && currentAttachment != getAttachmentDirection()) {
                this.entityData.set(ATTACHMENT_DIRECTION, currentAttachment);
            }
        }
    }

    @Override
    protected void registerGoals() {
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

    // The createNavigation override is no longer needed, as we assign it directly in the constructor.

    @Override
    public MobType getMobType() {
        return MobType.ARTHROPOD;
    }

    // --- Sounds ---
    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.SPIDER_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource pDamageSource) { return SoundEvents.SPIDER_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.SPIDER_DEATH; }

    @Override
    protected void playStepSound(BlockPos pPos, BlockState pBlock) {
        this.playSound(SoundEvents.SPIDER_STEP, 0.15F, 1.0F);
    }

    // --- Animation Handling ---
    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        if (event.isMoving()) {
            event.getController().setAnimation(new AnimationBuilder().addAnimation("walk_forward", ILoopType.EDefaultLoopTypes.LOOP));
        } else {
            event.getController().setAnimation(new AnimationBuilder().addAnimation("idle", ILoopType.EDefaultLoopTypes.LOOP));
        }
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 5, this::predicate));
    }

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }
}