package cn.academy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

public record PlasmaCannonPayload(int context,int caster,int phase,int slot,int request,float experience,Vec3 point)
        implements CustomPacketPayload {
    public static final int START=0,END=1,GO=2,POSITION=3;
    public static final Type<PlasmaCannonPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","plasma_cannon"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PlasmaCannonPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.context);b.writeVarInt(v.caster);b.writeVarInt(v.phase);b.writeVarInt(v.slot);b.writeVarInt(v.request);b.writeFloat(v.experience);b.writeDouble(v.point.x);b.writeDouble(v.point.y);b.writeDouble(v.point.z);},
            b->new PlasmaCannonPayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void applyTo(Player player){if(player!=null && player.level().isClientSide)NeoForge.EVENT_BUS.post(new Received(this));}
    public static final class Received extends Event {public final PlasmaCannonPayload payload;public Received(PlasmaCannonPayload payload){this.payload=payload;}}
}
