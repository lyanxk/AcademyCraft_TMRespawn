package cn.academy.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record PlasmaCannonInputPayload(int request,boolean abort) implements CustomPacketPayload {
    public static final Type<PlasmaCannonInputPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","plasma_cannon_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PlasmaCannonInputPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.request);b.writeBoolean(v.abort);},b->new PlasmaCannonInputPayload(b.readVarInt(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
