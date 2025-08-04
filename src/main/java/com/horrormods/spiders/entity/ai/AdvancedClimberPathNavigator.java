package com.horrormods.spiders.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

public class AdvancedClimberPathNavigator extends GroundPathNavigation {

    private final boolean canClimbWalls;
    private final boolean canClimbCeilings;

    public AdvancedClimberPathNavigator(Mob mob, Level level, boolean canClimbWalls, boolean canClimbCeilings) {
        super(mob, level);
        this.canClimbWalls = canClimbWalls;
        this.canClimbCeilings = canClimbCeilings;
    }

    /**
     * This is the correct way to inject our custom pathfinding logic.
     * We override this method to return a PathFinder that uses our custom NodeEvaluator.
     */
    @Override
    protected PathFinder createPathFinder(int pMaxVisitedNodes) {
        this.nodeEvaluator = new AdvancedWalkNodeEvaluator();
        ((AdvancedWalkNodeEvaluator) this.nodeEvaluator).setCanPathWalls(this.canClimbWalls);
        ((AdvancedWalkNodeEvaluator) this.nodeEvaluator).setCanPathCeiling(this.canClimbCeilings);

        return new PathFinder(this.nodeEvaluator, pMaxVisitedNodes);
    }
}