package cn.academy.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TPCritHitPayload(int target, int level) implements CustomPacketPayload {
    public static final Type<TPCritHitPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "tp_crit_hit"));
    public static final StreamCodec<ByteBuf, TPCritHitPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TPCritHitPayload::target,
            ByteBufCodecs.VAR_INT, TPCritHitPayload::level, TPCritHitPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
