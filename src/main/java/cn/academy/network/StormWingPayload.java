package cn.academy.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
public record StormWingPayload(int context,int caster,int phase,int slot,int request,float experience,boolean applying) implements CustomPacketPayload {
    public static final int START=0,END=1,ACTIVE=2,APPLYING=3;
    public static final Type<StormWingPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","storm_wing"));
    public static final StreamCodec<RegistryFriendlyByteBuf,StormWingPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.context);b.writeVarInt(v.caster);b.writeVarInt(v.phase);b.writeVarInt(v.slot);b.writeVarInt(v.request);b.writeFloat(v.experience);b.writeBoolean(v.applying);},
            b->new StormWingPayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void applyTo(Player player){if(player!=null && player.level().isClientSide)NeoForge.EVENT_BUS.post(new Received(this));}
    public static final class Received extends Event {public final StormWingPayload payload;public Received(StormWingPayload payload){this.payload=payload;}}
}
