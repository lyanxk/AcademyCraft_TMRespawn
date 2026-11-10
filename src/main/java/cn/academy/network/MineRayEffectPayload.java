package cn.academy.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public record MineRayEffectPayload(int caster,int slot,int contextId,int requestId,int variant,int range,boolean start) implements CustomPacketPayload {
    public static final String QUEUE="academy:mine_ray_effect_queue";
    public static final Type<MineRayEffectPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","mine_ray_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MineRayEffectPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.caster);b.writeVarInt(v.slot);b.writeInt(v.contextId);b.writeVarInt(v.requestId);b.writeVarInt(v.variant);b.writeVarInt(v.range);b.writeBoolean(v.start);},
            b->new MineRayEffectPayload(b.readVarInt(),b.readVarInt(),b.readInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void applyTo(Player player) {
        if(player==null)return;
        var queue=player.getPersistentData().getList(QUEUE,10);var tag=new CompoundTag();
        tag.putInt("Caster",caster);tag.putInt("Slot",slot);tag.putInt("Context",contextId);tag.putInt("Request",requestId);
        tag.putInt("Variant",variant);tag.putInt("Range",range);tag.putBoolean("Start",start);queue.add(tag);player.getPersistentData().put(QUEUE,queue);
    }
}
