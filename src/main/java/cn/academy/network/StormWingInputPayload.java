package cn.academy.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record StormWingInputPayload(int request,int phase,boolean applying) implements CustomPacketPayload {
    public static final Type<StormWingInputPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","storm_wing_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf,StormWingInputPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.request);b.writeVarInt(v.phase);b.writeBoolean(v.applying);},b->new StormWingInputPayload(b.readVarInt(),b.readVarInt(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
