package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FrequencyTransmitterResultPayload(int requestId, int action, int result,
                                                String displayName) implements CustomPacketPayload {
    public static final Type<FrequencyTransmitterResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "frequency_transmitter_result"));
    public static final StreamCodec<ByteBuf, FrequencyTransmitterResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FrequencyTransmitterResultPayload::requestId,
            ByteBufCodecs.VAR_INT, FrequencyTransmitterResultPayload::action,
            ByteBufCodecs.VAR_INT, FrequencyTransmitterResultPayload::result,
            ByteBufCodecs.STRING_UTF8, FrequencyTransmitterResultPayload::displayName,
            FrequencyTransmitterResultPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
