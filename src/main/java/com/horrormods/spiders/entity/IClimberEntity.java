package com.horrormods.spiders.entity;

import net.minecraft.core.Direction;

/**
 * Indicates that a mob can cling to floors, walls or ceilings.
 */
public interface IClimberEntity {
    Direction getAttachmentDirection();
    void setAttachmentDirection(Direction direction);
}
