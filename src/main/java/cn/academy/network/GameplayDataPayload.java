package cn.academy.network;

import cn.academy.data.GameplayData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GameplayDataPayload(String json) implements CustomPacketPayload {
    public static final Type<GameplayDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "gameplay_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GameplayDataPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(1_048_576), GameplayDataPayload::json, GameplayDataPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void apply() { GameplayData.receive(json); }
}
