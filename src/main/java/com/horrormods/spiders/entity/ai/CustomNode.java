package com.horrormods.spiders.entity.ai;

import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.Node;

/**
 * A custom Node class that extends the default one to add a field
 * for tracking the surface the entity is attached to.
 */
public class CustomNode extends Node {
    public Direction attachment;

    public CustomNode(int x, int y, int z) {
        super(x, y, z);
    }
}