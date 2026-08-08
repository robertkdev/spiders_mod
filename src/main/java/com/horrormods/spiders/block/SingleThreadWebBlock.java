package com.horrormods.spiders.block;

import com.horrormods.spiders.block.entity.SingleThreadWebBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class SingleThreadWebBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    private static final VoxelShape X_VISUAL_SHAPE = Block.box(0.0D, 7.0D, 7.0D, 16.0D, 9.0D, 9.0D);
    private static final VoxelShape Y_VISUAL_SHAPE = Block.box(7.0D, 0.0D, 7.0D, 9.0D, 16.0D, 9.0D);
    private static final VoxelShape Z_VISUAL_SHAPE = Block.box(7.0D, 7.0D, 0.0D, 9.0D, 9.0D, 16.0D);
    private static final ThreadLocal<Boolean> REMOVING_STRAND = ThreadLocal.withInitial(() -> false);

    public SingleThreadWebBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SingleThreadWebBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return visualShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (!level.isClientSide && entity instanceof Player && fallDistance > 0.0F) {
            level.destroyBlock(pos, false, entity);
            entity.resetFallDistance();
            return;
        }
        super.fallOn(level, state, pos, entity, fallDistance);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && !REMOVING_STRAND.get()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof SingleThreadWebBlockEntity strand && strand.hasStrand()) {
                REMOVING_STRAND.set(true);
                try {
                    destroyStrand(level, strand.getFirstAnchor(), strand.getSecondAnchor(), pos);
                } finally {
                    REMOVING_STRAND.set(false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        Direction.Axis axis = state.getValue(AXIS);
        if (axis == Direction.Axis.Y) {
            return state;
        }
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> state.setValue(AXIS,
                    axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            default -> state;
        };
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    public static boolean placeLine(Level level, BlockPos first, BlockPos second, Block block) {
        if (level.isClientSide || first == null || second == null || first.equals(second)) {
            return false;
        }

        int dx = second.getX() - first.getX();
        int dy = second.getY() - first.getY();
        int dz = second.getZ() - first.getZ();
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (steps <= 0) {
            return false;
        }

        Direction.Axis axis = dominantAxis(dx, dy, dz);
        BlockState state = block.defaultBlockState().setValue(AXIS, axis);
        boolean placedAny = false;
        BlockPos host = null;
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos pos : positionsBetween(first, second)) {
            if (level.isEmptyBlock(pos)) {
                level.setBlock(pos, state, 3);
                placedAny = true;
                if (host == null) {
                    host = pos.immutable();
                }
                placed.add(pos.immutable());
            }
        }

        for (BlockPos pos : placed) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof SingleThreadWebBlockEntity strand) {
                strand.setStrand(first, second, pos.equals(host));
            }
        }
        return placedAny;
    }

    public static List<BlockPos> positionsBetween(BlockPos first, BlockPos second) {
        int dx = second.getX() - first.getX();
        int dy = second.getY() - first.getY();
        int dz = second.getZ() - first.getZ();
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        List<BlockPos> positions = new ArrayList<>();
        if (steps <= 0) {
            return positions;
        }
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            BlockPos pos = new BlockPos(
                    first.getX() + Math.round(dx * t),
                    first.getY() + Math.round(dy * t),
                    first.getZ() + Math.round(dz * t));
            if (positions.isEmpty() || !positions.get(positions.size() - 1).equals(pos)) {
                positions.add(pos);
            }
        }
        return positions;
    }

    private static void destroyStrand(Level level, BlockPos first, BlockPos second, BlockPos except) {
        for (BlockPos pos : positionsBetween(first, second)) {
            if (!pos.equals(except) && level.getBlockState(pos).getBlock() instanceof SingleThreadWebBlock) {
                level.destroyBlock(pos, false);
            }
        }
    }

    private static Direction.Axis dominantAxis(int dx, int dy, int dz) {
        int ax = Math.abs(dx);
        int ay = Math.abs(dy);
        int az = Math.abs(dz);
        if (ay >= ax && ay >= az) {
            return Direction.Axis.Y;
        }
        return ax >= az ? Direction.Axis.X : Direction.Axis.Z;
    }

    private static VoxelShape visualShape(BlockState state) {
        return switch (state.getValue(AXIS)) {
            case X -> X_VISUAL_SHAPE;
            case Y -> Y_VISUAL_SHAPE;
            case Z -> Z_VISUAL_SHAPE;
        };
    }
}
