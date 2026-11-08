package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WirelessConfigPayload(BlockPos pos, String network, String password,
                                    java.util.Optional<BlockPos> target) implements CustomPacketPayload {
    public WirelessConfigPayload(BlockPos pos, String network, String password) {
        this(pos, network, password, java.util.Optional.empty());
    }
    public static final Type<WirelessConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "wireless_config"));
    public static final StreamCodec<ByteBuf, WirelessConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, WirelessConfigPayload::pos,
            ByteBufCodecs.STRING_UTF8, WirelessConfigPayload::network,
            ByteBufCodecs.STRING_UTF8, WirelessConfigPayload::password,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), WirelessConfigPayload::target,
            WirelessConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
