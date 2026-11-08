package cn.academy.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record TargetTeleportInputPayload(int request,float distance,boolean abort) implements CustomPacketPayload {
    public static final Type<TargetTeleportInputPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","target_teleport_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TargetTeleportInputPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.request);b.writeFloat(v.distance);b.writeBoolean(v.abort);},b->new TargetTeleportInputPayload(b.readVarInt(),b.readFloat(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
