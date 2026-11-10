package cn.academy.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** The original release window is measured in local ticks, independently of server/network delay. */
public record VectorStrikeInputPayload(int request,int ticks,boolean abort) implements CustomPacketPayload {
    public static final Type<VectorStrikeInputPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","vector_strike_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VectorStrikeInputPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.request);b.writeVarInt(v.ticks);b.writeBoolean(v.abort);},
            b->new VectorStrikeInputPayload(b.readVarInt(),b.readVarInt(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
