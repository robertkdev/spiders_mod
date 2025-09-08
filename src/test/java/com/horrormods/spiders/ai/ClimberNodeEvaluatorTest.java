package com.horrormods.spiders.ai;

import com.horrormods.spiders.entity.ai.ClimberNodeEvaluator;
import com.horrormods.spiders.entity.ai.ClimberNodeEvaluator.CustomNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class ClimberNodeEvaluatorTest {

    private static class TestEvaluator extends ClimberNodeEvaluator {
        private final Map<BlockPos, EnumSet<Direction>> supports;

        TestEvaluator(Map<BlockPos, EnumSet<Direction>> supports) {
            this.supports = supports;
            this.setCanPathWalls(true);
        }

        @Override
        public EnumSet<Direction> findAttachments(BlockPos pos) {
            return supports.getOrDefault(pos, EnumSet.noneOf(Direction.class));
        }

        @Override
        public boolean isPositionValidWithAttachment(BlockPos pos, Direction a) {
            return supports.getOrDefault(pos, EnumSet.noneOf(Direction.class)).contains(a);
        }
    }

    @Test
    public void cornerWrapGeneratesNeighbor() {
        BlockPos start = new BlockPos(0, 0, 0);
        BlockPos corner = start.relative(Direction.EAST).relative(Direction.SOUTH);

        Map<BlockPos, EnumSet<Direction>> supports = new HashMap<>();
        supports.put(start, EnumSet.of(Direction.NORTH));
        supports.put(corner, EnumSet.of(Direction.EAST));

        TestEvaluator eval = new TestEvaluator(supports);

        CustomNode startNode = (CustomNode) eval.getNode(start, Direction.NORTH);
        List<CustomNode> neighbors = eval.getRawNeighbors(startNode);

        CustomNode expected = (CustomNode) eval.getNode(corner, Direction.EAST);
        assertTrue("Corner-wrap neighbor missing", neighbors.contains(expected));
        assertEquals(Direction.EAST, expected.attachment);

        // Ensure movement didn't simply cross through the wall block
        CustomNode straight = (CustomNode) eval.getNode(start.relative(Direction.EAST), Direction.NORTH);
        assertFalse("Should not move straight through solid blocks", neighbors.contains(straight));
    }
}
