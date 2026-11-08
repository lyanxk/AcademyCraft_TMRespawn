package cn.academy.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
public record ShiftTeleportPayload(int context,int caster,int phase,int slot,int request,float experience,boolean performed) implements CustomPacketPayload {
    public static final int START=0,END=1,EFFECT=2;
    public static final Type<ShiftTeleportPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","shift_teleport"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ShiftTeleportPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.context);b.writeVarInt(v.caster);b.writeVarInt(v.phase);b.writeVarInt(v.slot);b.writeVarInt(v.request);b.writeFloat(v.experience);b.writeBoolean(v.performed);},
            b->new ShiftTeleportPayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void applyTo(Player player){if(player!=null && player.level().isClientSide)NeoForge.EVENT_BUS.post(new Received(this));}
    public static final class Received extends Event {public final ShiftTeleportPayload payload;public Received(ShiftTeleportPayload payload){this.payload=payload;}}
}
