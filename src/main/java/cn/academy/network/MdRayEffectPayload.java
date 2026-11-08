package cn.academy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** Scatter and missile effects carry endpoints, unlike ElectronBomb's receipt-time ball reference. */
public record MdRayEffectPayload(int kind,int caster,Vec3 from,Vec3 to) implements CustomPacketPayload {
    public static final int RAY=0,MISSILE_PARTICLES=1,BARRAGE=2,PRE_PLAIN=3,PRE_SCATTER=4;
    public static final Type<MdRayEffectPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","md_ray_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MdRayEffectPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.kind);b.writeVarInt(v.caster);b.writeVec3(v.from);b.writeVec3(v.to);},
            b->new MdRayEffectPayload(b.readVarInt(),b.readVarInt(),b.readVec3(),b.readVec3()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void applyTo(Player receiver) {
        if(receiver!=null && receiver.level().isClientSide)NeoForge.EVENT_BUS.post(new Received(receiver,this));
    }
    public static final class Received extends Event {
        public final Player receiver;
        public final MdRayEffectPayload payload;
        public Received(Player receiver,MdRayEffectPayload payload){this.receiver=receiver;this.payload=payload;}
    }
}
