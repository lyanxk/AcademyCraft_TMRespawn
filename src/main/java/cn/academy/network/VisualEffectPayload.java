package cn.academy.network;

import cn.academy.AcademyCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Compact server-authoritative description of a transient AcademyCraft visual effect. */
public record VisualEffectPayload(String effect,
                                  double startX, double startY, double startZ,
                                  double endX, double endY, double endZ,
                                  float scale, int color, int duration) implements CustomPacketPayload {
    public static final Type<VisualEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "visual_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VisualEffectPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUtf(value.effect, 32);
                buffer.writeDouble(value.startX); buffer.writeDouble(value.startY); buffer.writeDouble(value.startZ);
                buffer.writeDouble(value.endX); buffer.writeDouble(value.endY); buffer.writeDouble(value.endZ);
                buffer.writeFloat(value.scale); buffer.writeInt(value.color); buffer.writeVarInt(value.duration);
            },
            buffer -> new VisualEffectPayload(buffer.readUtf(32),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readFloat(), buffer.readInt(), buffer.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
