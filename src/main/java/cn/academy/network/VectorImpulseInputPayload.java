package cn.academy.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record VectorImpulseInputPayload(int request,int target,boolean abort) implements CustomPacketPayload {
    public static final Type<VectorImpulseInputPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","vector_impulse_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VectorImpulseInputPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.request);b.writeVarInt(v.target);b.writeBoolean(v.abort);},b->new VectorImpulseInputPayload(b.readVarInt(),b.readVarInt(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
