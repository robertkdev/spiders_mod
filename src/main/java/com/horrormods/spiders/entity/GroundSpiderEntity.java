package com.horrormods.spiders.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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

    private static final AnimationBuilder IDLE_ANIMATION = new AnimationBuilder().addAnimation("idle", ILoopType.EDefaultLoopTypes.LOOP);
    private static final AnimationBuilder WALK_ANIMATION = new AnimationBuilder().addAnimation("walk_forward", ILoopType.EDefaultLoopTypes.LOOP);
    private static final AnimationBuilder WEB_WALK_ANIMATION = new AnimationBuilder().addAnimation("walk_forward_on_web", ILoopType.EDefaultLoopTypes.LOOP);
    private static final AnimationBuilder RAISED_WALK_ANIMATION = new AnimationBuilder().addAnimation("raised_walk_forward", ILoopType.EDefaultLoopTypes.LOOP);
    private static final AnimationBuilder CIRCLE_RIGHT_ANIMATION = new AnimationBuilder().addAnimation("circle_right", ILoopType.EDefaultLoopTypes.LOOP);
    private static final AnimationBuilder RAISED_CIRCLE_RIGHT = new AnimationBuilder().addAnimation("raised_circle_right", ILoopType.EDefaultLoopTypes.LOOP);
    private static final AnimationBuilder RAISED_WALK_FORWARD_RIGHT = new AnimationBuilder().addAnimation("raised_walk_forward_right", ILoopType.EDefaultLoopTypes.LOOP);

    private static final AnimationBuilder RAISED_WALK_FORWARD_LEFT = new AnimationBuilder().addAnimation("raised_walk_forward_left", ILoopType.EDefaultLoopTypes.LOOP);

    public GroundSpiderEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)  // Increased from 0.25D to 0.3D
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        event.getController().setAnimation(RAISED_WALK_ANIMATION);
        event.getController().setAnimationSpeed(1.0);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }
}