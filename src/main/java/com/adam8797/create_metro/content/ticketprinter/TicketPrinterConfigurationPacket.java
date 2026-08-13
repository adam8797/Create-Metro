package com.adam8797.create_metro.content.ticketprinter;

import com.adam8797.create_metro.MetroPackets;
import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client -> server: the ticket printer GUI reports fare + station address + ticket display name.
 * Applied only if the sender is trusted (an owner).
 */
public class TicketPrinterConfigurationPacket extends BlockEntityConfigurationPacket<TicketPrinterBlockEntity> {

    public static final StreamCodec<RegistryFriendlyByteBuf, TicketPrinterConfigurationPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, packet -> packet.pos,
            ByteBufCodecs.VAR_INT, packet -> packet.fare,
            ByteBufCodecs.STRING_UTF8, packet -> packet.station,
            ByteBufCodecs.STRING_UTF8, packet -> packet.ticketName,
            TicketPrinterConfigurationPacket::new
    );

    private final int fare;
    private final String station;
    private final String ticketName;

    public TicketPrinterConfigurationPacket(BlockPos pos, int fare, String station, String ticketName) {
        super(pos);
        this.fare = fare;
        this.station = station;
        this.ticketName = ticketName;
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return MetroPackets.TICKET_PRINTER_CONFIG;
    }

    @Override
    protected int maxRange() {
        return 20;
    }

    @Override
    protected void applySettings(ServerPlayer player, TicketPrinterBlockEntity be) {
        if (be.isTrusted(player))
            be.applyConfig(fare, station, ticketName);
    }
}
