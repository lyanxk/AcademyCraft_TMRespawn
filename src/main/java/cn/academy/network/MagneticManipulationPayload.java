package cn.academy.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public record MagneticManipulationPayload(int caster, int slot, int contextId, int requestId, int phase,
                                         int entityId, int action, Vec3 value) implements CustomPacketPayload {
    public static final int START=0, RELEASE=1, END=2, ENTITY_SYNC=3, PLACE=4;
    public static final String QUEUE="academy:mag_manip_queue";
    public static final Type<MagneticManipulationPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","mag_manip"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MagneticManipulationPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{ b.writeVarInt(v.caster); b.writeVarInt(v.slot); b.writeInt(v.contextId); b.writeVarInt(v.requestId);
                b.writeVarInt(v.phase); b.writeVarInt(v.entityId); b.writeVarInt(v.action);
                b.writeDouble(v.value.x); b.writeDouble(v.value.y); b.writeDouble(v.value.z); },
            b->new MagneticManipulationPayload(b.readVarInt(),b.readVarInt(),b.readInt(),b.readVarInt(),b.readVarInt(),
                    b.readVarInt(),b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void applyTo(Player player) {
        if(player==null)return;
        var queue=player.getPersistentData().getList(QUEUE,10);var tag=new CompoundTag();
        tag.putInt("Caster",caster);tag.putInt("Slot",slot);tag.putInt("Context",contextId);tag.putInt("Request",requestId);
        tag.putInt("Phase",phase);tag.putInt("Entity",entityId);tag.putInt("Action",action);
        tag.putDouble("X",value.x);tag.putDouble("Y",value.y);tag.putDouble("Z",value.z);
        queue.add(tag);player.getPersistentData().put(QUEUE,queue);
    }
}
