package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MachineActionPayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final int CYCLE_MODE = 0;
    public static final int CYCLE_MODE_BACK = 1;
    public static final Type<MachineActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "machine_action"));
    public static final StreamCodec<ByteBuf, MachineActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MachineActionPayload::pos,
            ByteBufCodecs.VAR_INT, MachineActionPayload::action,
            MachineActionPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
