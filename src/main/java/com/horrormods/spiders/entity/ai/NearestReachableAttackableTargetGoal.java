package com.horrormods.spiders.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class NearestReachableAttackableTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {

    public NearestReachableAttackableTargetGoal(Mob mob, Class<T> targetType, boolean mustSee) {
        this(mob, targetType, 10, mustSee, null);
    }

    public NearestReachableAttackableTargetGoal(
            Mob mob,
            Class<T> targetType,
            int randomInterval,
            boolean mustSee,
            @Nullable Predicate<LivingEntity> selector) {
        super(mob, targetType, randomInterval, mustSee, false, selector);
        this.setUnseenMemoryTicks(600);
    }

    @Override
    protected void findTarget() {
        this.target = null;
        List<T> candidates = this.mob.level
                .getEntitiesOfClass(this.targetType, this.getTargetSearchArea(this.getFollowDistance()), entity -> true);
        candidates.sort(Comparator.comparingDouble(this.mob::distanceToSqr));

        TargetingConditions reachProofConditions = this.targetConditions.copy().ignoreLineOfSight();
        for (T candidate : candidates) {
            boolean normalTarget = this.canAttack(candidate, this.targetConditions);
            if (candidate instanceof Player) {
                if (normalTarget) {
                    this.target = candidate;
                    return;
                }
                continue;
            }

            boolean reachable = this.canReachForClimberMelee(candidate);
            boolean climberReachTarget = this.canAttack(candidate, reachProofConditions)
                    && reachable;
            if ((normalTarget || climberReachTarget) && reachable) {
                this.target = candidate;
                return;
            }
        }
    }

    private boolean canReachForClimberMelee(LivingEntity candidate) {
        if (this.mob.getNavigation() instanceof ClimberPathNavigator climberNavigation) {
            return climberNavigation.canReachEntityTarget(candidate);
        }
        return this.mob.getNavigation().createPath(candidate, 0) != null;
    }
}
