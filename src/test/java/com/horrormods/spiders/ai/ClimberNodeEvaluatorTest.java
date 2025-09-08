package com.horrormods.spiders.ai;

import com.horrormods.spiders.entity.ai.ClimberNodeEvaluator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * Tests surface orientation transitions for the {@link ClimberNodeEvaluator}.
 * These tests use a stub evaluator that defines attachment faces manually,
 * allowing verification of orientation switching without depending on the
 * Minecraft engine.
 */
public class ClimberNodeEvaluatorTest {

    /** Simple evaluator with manually supplied attachment data. */
    private static class TestEvaluator extends ClimberNodeEvaluator {
        private final Map<BlockPos, EnumSet<Direction>> attachments = new HashMap<>();

        void put(BlockPos pos, Direction... dirs) {
            attachments.put(pos, EnumSet.copyOf(Arrays.asList(dirs)));
        }

        @Override
        public EnumSet<Direction> findAttachments(BlockPos pos) {
            return attachments.getOrDefault(pos, EnumSet.noneOf(Direction.class));
        }

        @Override
        public boolean isPositionValidWithAttachment(BlockPos pos, Direction a) {
            return findAttachments(pos).contains(a);
        }

        @Override
        public List<CustomNode> getRawNeighbors(CustomNode current) {
            Set<CustomNode> out = new HashSet<>();
            BlockPos pos = current.asBlockPos();
            Direction attach = current.attachment != null ? current.attachment : Direction.DOWN;

            Direction axis1, axis2;
            switch (attach) {
                case DOWN, UP -> {
                    axis1 = Direction.EAST;
                    axis2 = Direction.SOUTH;
                }
                case NORTH, SOUTH -> {
                    axis1 = Direction.EAST;
                    axis2 = Direction.UP;
                }
                case EAST, WEST -> {
                    axis1 = Direction.NORTH;
                    axis2 = Direction.UP;
                }
                default -> {
                    axis1 = Direction.EAST;
                    axis2 = Direction.SOUTH;
                }
            }

            Direction[] tangential = new Direction[]{axis1, axis1.getOpposite(), axis2, axis2.getOpposite()};
            for (Direction dir : tangential) {
                simpleAdd(pos.relative(dir), out);
            }
            for (int i = 0; i < tangential.length; i++) {
                Direction d1 = tangential[i];
                if (d1 == null) continue;
                for (int j = i + 1; j < tangential.length; j++) {
                    Direction d2 = tangential[j];
                    if (d2 == null || d1.getAxis() == d2.getAxis()) continue;
                    simpleAdd(pos.relative(d1).relative(d2), out);
                }
            }

            EnumSet<Direction> here = findAttachments(pos);
            for (Direction d : here) {
                if (d != attach) {
                    out.add((CustomNode) getNode(pos, d));
                }
            }

            return new ArrayList<>(out);
        }

        private void simpleAdd(BlockPos p, Set<CustomNode> out) {
            EnumSet<Direction> dirs = findAttachments(p);
            for (Direction d : dirs) {
                out.add((CustomNode) getNode(p, d));
            }
        }
    }

    private static boolean reachable(TestEvaluator eval, ClimberNodeEvaluator.CustomNode start,
                                     ClimberNodeEvaluator.CustomNode goal) {
        Set<ClimberNodeEvaluator.CustomNode> visited = new HashSet<>();
        Queue<ClimberNodeEvaluator.CustomNode> q = new ArrayDeque<>();
        q.add(start);
        visited.add(start);
        while (!q.isEmpty()) {
            ClimberNodeEvaluator.CustomNode n = q.poll();
            if (n.equals(goal)) return true;
            for (ClimberNodeEvaluator.CustomNode m : eval.getRawNeighbors(n)) {
                if (visited.add(m)) q.add(m);
            }
        }
        return false;
    }

    @Test
    public void testFloorToWallPath() {
        TestEvaluator eval = new TestEvaluator();
        eval.put(new BlockPos(0, 0, 0), Direction.DOWN);
        eval.put(new BlockPos(1, 0, 0), Direction.WEST);
        var start = (ClimberNodeEvaluator.CustomNode) eval.getNode(new BlockPos(0, 0, 0), Direction.DOWN);
        var goal = (ClimberNodeEvaluator.CustomNode) eval.getNode(new BlockPos(1, 0, 0), Direction.WEST);
        Assertions.assertTrue(reachable(eval, start, goal));
    }

    @Test
    public void testWallToCeilingPath() {
        TestEvaluator eval = new TestEvaluator();
        eval.put(new BlockPos(1, 0, 0), Direction.WEST);
        eval.put(new BlockPos(1, 1, 0), Direction.WEST, Direction.UP);
        var start = (ClimberNodeEvaluator.CustomNode) eval.getNode(new BlockPos(1, 0, 0), Direction.WEST);
        var goal = (ClimberNodeEvaluator.CustomNode) eval.getNode(new BlockPos(1, 1, 0), Direction.UP);
        Assertions.assertTrue(reachable(eval, start, goal));
    }

    @Test
    public void testCeilingToFloorPath() {
        TestEvaluator eval = new TestEvaluator();
        eval.put(new BlockPos(0, 0, 0), Direction.DOWN);
        eval.put(new BlockPos(1, 0, 0), Direction.WEST);
        eval.put(new BlockPos(1, 1, 0), Direction.WEST, Direction.UP);
        var start = (ClimberNodeEvaluator.CustomNode) eval.getNode(new BlockPos(1, 1, 0), Direction.UP);
        var goal = (ClimberNodeEvaluator.CustomNode) eval.getNode(new BlockPos(0, 0, 0), Direction.DOWN);
        Assertions.assertTrue(reachable(eval, start, goal));
    }
}

