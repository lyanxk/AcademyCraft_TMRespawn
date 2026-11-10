package cn.academy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** VecDeviation/VecReflection fixed-audience messages; entity IDs refer to the original projectile. */
public record VectorDefensePayload(int context,int caster,boolean reflection,int phase,int entity,Vec3 point)
        implements CustomPacketPayload {
    public static final int START=0,END=1,STOP_ENTITY=2,REFLECT_ENTITY=3,EFFECT=4,PLAY=5,SYNC_VELOCITY=6;
    public static final Type<VectorDefensePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","vector_defense"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VectorDefensePayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.context);b.writeVarInt(v.caster);b.writeBoolean(v.reflection);b.writeVarInt(v.phase);b.writeVarInt(v.entity);b.writeVec3(v.point);},
            b->new VectorDefensePayload(b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readVec3()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void applyTo(Player player){if(player!=null && player.level().isClientSide)NeoForge.EVENT_BUS.post(new Received(this));}
    public static final class Received extends Event {
        public final VectorDefensePayload payload;
        public Received(VectorDefensePayload payload){this.payload=payload;}
    }
}
