package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests used by the original frequency-transmitter state machine. */
public record FrequencyTransmitterPayload(int requestId, int action, BlockPos primary,
                                          BlockPos target, String password) implements CustomPacketPayload {
    public static final int AUTH_MATRIX = 0;
    public static final int AUTH_NODE = 1;
    public static final int LINK_NODE = 2;
    public static final int LINK_USER = 3;

    public static final Type<FrequencyTransmitterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "frequency_transmitter"));
    public static final StreamCodec<ByteBuf, FrequencyTransmitterPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FrequencyTransmitterPayload decode(ByteBuf buffer) {
            return new FrequencyTransmitterPayload(ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), BlockPos.STREAM_CODEC.decode(buffer),
                    BlockPos.STREAM_CODEC.decode(buffer), ByteBufCodecs.STRING_UTF8.decode(buffer));
        }

        @Override
        public void encode(ByteBuf buffer, FrequencyTransmitterPayload value) {
            ByteBufCodecs.VAR_INT.encode(buffer, value.requestId);
            ByteBufCodecs.VAR_INT.encode(buffer, value.action);
            BlockPos.STREAM_CODEC.encode(buffer, value.primary);
            BlockPos.STREAM_CODEC.encode(buffer, value.target);
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.password);
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
