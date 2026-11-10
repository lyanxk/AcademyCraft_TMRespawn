package cn.academy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/** EffectDelegate's ball reference; the receiving client computes the ray, not the server. */
public record ElectronBombEffectPayload(int ball) implements CustomPacketPayload {
    public static final Type<ElectronBombEffectPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","electron_bomb_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ElectronBombEffectPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->b.writeVarInt(v.ball),b->new ElectronBombEffectPayload(b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public void applyTo(Player player) {
        if(player!=null && player.level().isClientSide)NeoForge.EVENT_BUS.post(new Received(player,ball));
    }
    /** Dispatched on the payload handler's main thread, without an extra tick queue. */
    public static final class Received extends Event {
        public final Player receiver;
        public final int ball;
        public Received(Player receiver,int ball){this.receiver=receiver;this.ball=ball;}
    }
}
