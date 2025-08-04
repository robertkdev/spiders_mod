package com.horrormods.spiders.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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

    // This method is now STATIC and takes the packet as the first parameter
    public static void handle(DisplayPathPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Make sure we are on the client
            if (Minecraft.getInstance().level != null) {
                // Use the 'packet' parameter to get the list of nodes
                for (BlockPos pos : packet.pathNodes) {
                    Minecraft.getInstance().level.addParticle(ParticleTypes.FLAME,
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5,
                            0, 0, 0);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}