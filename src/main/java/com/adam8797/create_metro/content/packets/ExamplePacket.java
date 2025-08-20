package com.adam8797.create_metro.content.packets;
import com.adam8797.create_metro.MetroPackets;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionPacket;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ExamplePacket extends BlockEntityConfigurationPacket<SyncedBlockEntity> {

    public static final StreamCodec<RegistryFriendlyByteBuf, ExamplePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, packet -> packet.pos,
            ByteBufCodecs.TAG, packet -> packet.instructions,
            ExamplePacket::new
    );

    private final ListTag instructions;

    public ExamplePacket(BlockPos pos, Tag instructions) {
        super(pos);
        this.instructions = (ListTag) instructions;
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return MetroPackets.EXAMPLE_PACKET;
    }

    @Override
    protected int maxRange() {
        return 16;
    }

    @Override
    protected void applySettings(ServerPlayer player, SyncedBlockEntity be) {
//        be.currentInstruction = -1;
//        be.instructions = Instruction.deserializeAll(instructions);
//        be.sendData();
    }
}