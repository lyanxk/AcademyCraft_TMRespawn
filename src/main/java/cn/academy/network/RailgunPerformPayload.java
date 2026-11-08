package cn.academy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Original client item/coin perform requests; server always resolves ammunition belonging to the sender. */
public record RailgunPerformPayload(int slot, boolean coin) implements CustomPacketPayload {
    public static final Type<RailgunPerformPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "railgun_perform"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunPerformPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeVarInt(value.slot); buffer.writeBoolean(value.coin); },
            buffer -> new RailgunPerformPayload(buffer.readVarInt(), buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
