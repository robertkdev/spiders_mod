package com.horrormods.spiders.network;

import com.horrormods.spiders.client.ClientPathManager; // NEW Import
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class DisplayPathPacket {
    private final List<BlockPos> pathNodes;

    public DisplayPathPacket(List<BlockPos> pathNodes) {
        this.pathNodes = pathNodes;
    }

    public DisplayPathPacket(FriendlyByteBuf buf) {
        this.pathNodes = buf.readList(FriendlyByteBuf::readBlockPos);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(this.pathNodes, FriendlyByteBuf::writeBlockPos);
    }

    // UPDATED: This method now just passes the path to the manager.
    // The ClientListener will handle the actual drawing.
    public static void handle(DisplayPathPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPathManager.setPath(packet.pathNodes);
        });
        ctx.get().setPacketHandled(true);
    }
}