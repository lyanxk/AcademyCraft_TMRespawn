package cn.academy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** direction 1..4 follows the shared W/S/A/D input IDs; zero terminates the context. */
public record FlashingInputPayload(int request, int direction) implements CustomPacketPayload {
    public static final Type<FlashingInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "flashing_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlashingInputPayload> STREAM_CODEC = StreamCodec.of(
            (b,v) -> { b.writeVarInt(v.request); b.writeVarInt(v.direction); },
            b -> new FlashingInputPayload(b.readVarInt(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
