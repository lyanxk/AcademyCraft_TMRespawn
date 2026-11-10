package cn.academy.network;

import cn.academy.ability.vanilla.meltdowner.skill.MDDamageHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Original "sync", player, marktick: intentionally contains no target id or damage multiplier. */
public record RadiationMarkPayload(int entity,int ticks) implements CustomPacketPayload {
    public static final Type<RadiationMarkPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","radiation_mark"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RadiationMarkPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeVarInt(v.entity);b.writeInt(v.ticks);},b->new RadiationMarkPayload(b.readVarInt(),b.readInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void applyTo(Player receiver) {
        if(receiver==null)return;
        var target=receiver.level().getEntity(entity);
        if(target!=null)MDDamageHelper.setTicks(target,ticks);
    }
}
