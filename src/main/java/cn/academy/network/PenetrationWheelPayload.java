package cn.academy.network;

import cn.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Preserve fractional and multiple-detent scroll offsets, as original Mouse.getEventDWheel()/120f. */
public record PenetrationWheelPayload(float offset) implements CustomPacketPayload {
    public static final Type<PenetrationWheelPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "penetration_wheel"));
    public static final StreamCodec<ByteBuf, PenetrationWheelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, PenetrationWheelPayload::offset, PenetrationWheelPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
