package cn.academy.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Original MSG_PARTICLES carries the post-update integer coordinates, including (-1,-1,-1) after breaking. */
public record MineRayParticlesPayload(int contextId,BlockPos position) implements CustomPacketPayload {
    public static final Type<MineRayParticlesPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","mine_ray_particles"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MineRayParticlesPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeInt(v.contextId);b.writeBlockPos(v.position);},b->new MineRayParticlesPayload(b.readInt(),b.readBlockPos()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void applyTo(Player player) {
        if(player==null)return;
        var queue=player.getPersistentData().getList(MineRayEffectPayload.QUEUE,10);var tag=new CompoundTag();
        tag.putBoolean("Particles",true);tag.putInt("Context",contextId);
        tag.putInt("X",position.getX());tag.putInt("Y",position.getY());tag.putInt("Z",position.getZ());
        queue.add(tag);player.getPersistentData().put(MineRayEffectPayload.QUEUE,queue);
    }
}
