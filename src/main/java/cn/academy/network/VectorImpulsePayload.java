package cn.academy.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
public record VectorImpulsePayload(int context,int caster,int kind,int phase,int slot,int request,float experience,int target) implements CustomPacketPayload {
    public static final int ACCEL=0,BLOOD=1,START=0,END=1,PERFORM=2;
    public static final Type<VectorImpulsePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","vector_impulse"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VectorImpulsePayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.context);b.writeVarInt(v.caster);b.writeVarInt(v.kind);b.writeVarInt(v.phase);b.writeVarInt(v.slot);b.writeVarInt(v.request);b.writeFloat(v.experience);b.writeVarInt(v.target);},
            b->new VectorImpulsePayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat(),b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void applyTo(Player player){if(player!=null && player.level().isClientSide)NeoForge.EVENT_BUS.post(new Received(this));}
    public static final class Received extends Event {public final VectorImpulsePayload payload;public Received(VectorImpulsePayload payload){this.payload=payload;}}
}
