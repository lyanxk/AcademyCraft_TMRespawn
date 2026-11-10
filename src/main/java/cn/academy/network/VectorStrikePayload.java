package cn.academy.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import java.util.List;

public record VectorStrikePayload(int context,int caster,int kind,int phase,int slot,int request,float experience,
                                  Vec3 point,List<Integer> entities,List<BlockPos> blocks) implements CustomPacketPayload {
    public static final int SHOCK=0,GROUND=1,BLAST=2,ENHANCED_SHOCK=3,START=0,END=1,PERFORM=2,HIT=3,GROUND_EFFECT=4,KNOCKBACK_VELOCITY=5;
    public static final Type<VectorStrikePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","vector_strike"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VectorStrikePayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.context);b.writeVarInt(v.caster);b.writeVarInt(v.kind);b.writeVarInt(v.phase);b.writeVarInt(v.slot);b.writeVarInt(v.request);
                b.writeFloat(v.experience);b.writeVec3(v.point);b.writeCollection(v.entities,(out,id)->out.writeVarInt(id));b.writeCollection(v.blocks,(out,pos)->out.writeBlockPos(pos));},
            b->new VectorStrikePayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat(),b.readVec3(),
                    b.readList(in->in.readVarInt()),b.readList(in->in.readBlockPos())));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void applyTo(Player player){if(player!=null && player.level().isClientSide)NeoForge.EVENT_BUS.post(new Received(this));}
    public static final class Received extends Event {public final VectorStrikePayload payload;public Received(VectorStrikePayload payload){this.payload=payload;}}
}
