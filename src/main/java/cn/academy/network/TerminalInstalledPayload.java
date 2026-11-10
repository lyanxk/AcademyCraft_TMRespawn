package cn.academy.network;

import cn.academy.event.AppInstalledEvent;
import cn.academy.event.TerminalInstalledEvent;
import cn.academy.terminal.AppRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

/** Empty name means terminal installation; otherwise one application-install event. */
public record TerminalInstalledPayload(String appName) implements CustomPacketPayload {
    public static final Type<TerminalInstalledPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("academy", "terminal_installed"));
    public static final StreamCodec<ByteBuf, TerminalInstalledPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TerminalInstalledPayload::appName, TerminalInstalledPayload::new);

    public void applyTo(Player player) {
        if (appName.isEmpty()) NeoForge.EVENT_BUS.post(new TerminalInstalledEvent(player));
        else {
            var app = AppRegistry.getByName(appName);
            if (app != null) NeoForge.EVENT_BUS.post(new AppInstalledEvent(player, app));
        }
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
