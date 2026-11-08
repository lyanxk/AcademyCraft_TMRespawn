package cn.academy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** Fixed-audience lifecycle for LSContext, MDContext and JEContext. */
public record MeltdownerContextPayload(int id,int caster,int kind,int phase,int slot,int request,int related,Vec3 target,double length)
        implements CustomPacketPayload {
    public static final int SHIELD=0,BEAM=1,JET=2,START=0,END=1,PERFORM=2,REFLECT=3,TRIGGER=4;
    public static final Type<MeltdownerContextPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","md_context"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MeltdownerContextPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.id);b.writeVarInt(v.caster);b.writeVarInt(v.kind);b.writeVarInt(v.phase);
                b.writeVarInt(v.slot);b.writeVarInt(v.request);b.writeVarInt(v.related);b.writeVec3(v.target);b.writeDouble(v.length);},
            b->new MeltdownerContextPayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),
                    b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVec3(),b.readDouble()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void applyTo(Player receiver) {
        if(receiver!=null && receiver.level().isClientSide)NeoForge.EVENT_BUS.post(new Received(receiver,this));
    }
    public static final class Received extends Event {
        public final Player receiver;public final MeltdownerContextPayload payload;
        public Received(Player receiver,MeltdownerContextPayload payload){this.receiver=receiver;this.payload=payload;}
    }
}
