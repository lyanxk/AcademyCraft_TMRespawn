package cn.academy.network;

import cn.academy.config.ACGeneralConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Runtime update for clients already connected to an integrated server opened to LAN. */
public record SettingsPolicyPayload(boolean attackPlayers, boolean destroyBlocks, java.util.List<String> worldsDestroyingBlocks) implements CustomPacketPayload {
    public SettingsPolicyPayload { worldsDestroyingBlocks = java.util.List.copyOf(worldsDestroyingBlocks); }
    public static final Type<SettingsPolicyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "settings_policy"));
    public static final StreamCodec<ByteBuf, SettingsPolicyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SettingsPolicyPayload::attackPlayers,
            ByteBufCodecs.BOOL, SettingsPolicyPayload::destroyBlocks,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SettingsPolicyPayload::worldsDestroyingBlocks,
            SettingsPolicyPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void apply() {
        // The host shares the local policy with its server; it must not use an older network snapshot.
        if (net.minecraft.client.Minecraft.getInstance().isLocalServer()) return;
        ACGeneralConfig.receiveRemote(new ACGeneralConfig.Policy(attackPlayers, destroyBlocks, worldsDestroyingBlocks));
    }
}
