package cn.academy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client jet lifetime notification; server resolves only the sender's own context. */
public record MeltdownerEndPayload(int context) implements CustomPacketPayload {
    public static final Type<MeltdownerEndPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","md_context_end"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MeltdownerEndPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->b.writeVarInt(v.context),b->new MeltdownerEndPayload(b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
