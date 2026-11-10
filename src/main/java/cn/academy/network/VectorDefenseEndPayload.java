package cn.academy.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record VectorDefenseEndPayload(int context) implements CustomPacketPayload {
    public static final Type<VectorDefenseEndPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","vector_defense_end"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VectorDefenseEndPayload> STREAM_CODEC=StreamCodec.of((b,v)->b.writeVarInt(v.context),b->new VectorDefenseEndPayload(b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
