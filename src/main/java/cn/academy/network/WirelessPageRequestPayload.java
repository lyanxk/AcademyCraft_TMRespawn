package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WirelessPageRequestPayload(int containerId, int requestId, BlockPos pos) implements CustomPacketPayload {
    public static final Type<WirelessPageRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "wireless_page_request"));
    public static final StreamCodec<ByteBuf, WirelessPageRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WirelessPageRequestPayload::containerId,
            ByteBufCodecs.VAR_INT, WirelessPageRequestPayload::requestId,
            BlockPos.STREAM_CODEC, WirelessPageRequestPayload::pos, WirelessPageRequestPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
