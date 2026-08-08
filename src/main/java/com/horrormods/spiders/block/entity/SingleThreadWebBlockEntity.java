package com.horrormods.spiders.block.entity;

import com.horrormods.spiders.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SingleThreadWebBlockEntity extends BlockEntity {
    private BlockPos firstAnchor = BlockPos.ZERO;
    private BlockPos secondAnchor = BlockPos.ZERO;
    private boolean hasStrand;
    private boolean renderHost;

    public SingleThreadWebBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.SINGLE_THREAD_WEB.get(), pos, state);
    }

    public void setStrand(BlockPos firstAnchor, BlockPos secondAnchor, boolean renderHost) {
        this.firstAnchor = firstAnchor.immutable();
        this.secondAnchor = secondAnchor.immutable();
        this.hasStrand = true;
        this.renderHost = renderHost;
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean hasStrand() {
        return hasStrand;
    }

    public boolean isRenderHost() {
        return renderHost;
    }

    public BlockPos getFirstAnchor() {
        return firstAnchor;
    }

    public BlockPos getSecondAnchor() {
        return secondAnchor;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeStrand(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.hasStrand = tag.getBoolean("HasStrand");
        this.renderHost = tag.getBoolean("RenderHost");
        this.firstAnchor = readBlockPos(tag, "First");
        this.secondAnchor = readBlockPos(tag, "Second");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeStrand(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void writeStrand(CompoundTag tag) {
        tag.putBoolean("HasStrand", hasStrand);
        tag.putBoolean("RenderHost", renderHost);
        writeBlockPos(tag, "First", firstAnchor);
        writeBlockPos(tag, "Second", secondAnchor);
    }

    private static void writeBlockPos(CompoundTag tag, String prefix, BlockPos pos) {
        tag.putInt(prefix + "X", pos.getX());
        tag.putInt(prefix + "Y", pos.getY());
        tag.putInt(prefix + "Z", pos.getZ());
    }

    private static BlockPos readBlockPos(CompoundTag tag, String prefix) {
        return new BlockPos(tag.getInt(prefix + "X"), tag.getInt(prefix + "Y"), tag.getInt(prefix + "Z"));
    }
}
