package com.adam8797.create_metro.content.turnstile;

import com.adam8797.create_metro.MetroPackets;
import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client -> server: the turnstile GUI reports a new fare. Applied only if the sender is trusted
 * for that turnstile (the server clamps the value to the configured range).
 */
public class TurnstileConfigurationPacket extends BlockEntityConfigurationPacket<TurnstileBlockEntity> {

    public static final StreamCodec<RegistryFriendlyByteBuf, TurnstileConfigurationPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, packet -> packet.pos,
            ByteBufCodecs.VAR_INT, packet -> packet.fare,
            ByteBufCodecs.BOOL, packet -> packet.chargeTrusted,
            ByteBufCodecs.BOOL, packet -> packet.noExit,
            ByteBufCodecs.BOOL, packet -> packet.autoPay,
            TurnstileConfigurationPacket::new
    );

    private final int fare;
    private final boolean chargeTrusted;
    private final boolean noExit;
    private final boolean autoPay;

    public TurnstileConfigurationPacket(BlockPos pos, int fare, boolean chargeTrusted, boolean noExit, boolean autoPay) {
        super(pos);
        this.fare = fare;
        this.chargeTrusted = chargeTrusted;
        this.noExit = noExit;
        this.autoPay = autoPay;
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return MetroPackets.TURNSTILE_CONFIG;
    }

    @Override
    protected int maxRange() {
        return 20;
    }

    @Override
    protected void applySettings(ServerPlayer player, TurnstileBlockEntity be) {
        if (!be.isTrusted(player))
            return;
        be.applyConfig(fare, chargeTrusted, noExit, autoPay);
    }
}
