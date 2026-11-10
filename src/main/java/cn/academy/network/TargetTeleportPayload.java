package cn.academy.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
public record TargetTeleportPayload(int context,int caster,int phase,int slot,int request,int kind,float experience) implements CustomPacketPayload {
    public static final int START=0,END=1,SOUND=2;
    public static final Type<TargetTeleportPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","target_teleport"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TargetTeleportPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.context);b.writeVarInt(v.caster);b.writeVarInt(v.phase);b.writeVarInt(v.slot);b.writeVarInt(v.request);b.writeVarInt(v.kind);b.writeFloat(v.experience);},
            b->new TargetTeleportPayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void applyTo(Player player){if(player!=null && player.level().isClientSide)NeoForge.EVENT_BUS.post(new Received(this));}
    public static final class Received extends Event {public final TargetTeleportPayload payload;public Received(TargetTeleportPayload payload){this.payload=payload;}}
}
