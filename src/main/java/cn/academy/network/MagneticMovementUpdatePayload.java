package cn.academy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Client coordinates affect the visual endpoint only; the server keeps its independent target and costs. */
public record MagneticMovementUpdatePayload(int contextId, int phase, Vec3 target) implements CustomPacketPayload {
    public static final Type<MagneticMovementUpdatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "magnetic_movement_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MagneticMovementUpdatePayload> STREAM_CODEC = StreamCodec.of(
            (b,v) -> { b.writeInt(v.contextId); b.writeVarInt(v.phase); b.writeDouble(v.target.x); b.writeDouble(v.target.y); b.writeDouble(v.target.z); },
            b -> new MagneticMovementUpdatePayload(b.readInt(),b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
