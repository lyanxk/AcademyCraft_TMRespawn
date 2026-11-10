package cn.academy.terminal;

import cn.academy.ability.AbilityState;
import cn.academy.event.AppInstalledEvent;
import cn.academy.event.TerminalInstalledEvent;
import cn.academy.network.ACNetwork;
import cn.academy.network.TerminalInstalledPayload;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** TerminalData's lifecycle over the port's existing persisted/synchronized ability data. */
public final class TerminalData {
    private final Player player;
    private TerminalData(Player player) { this.player = player; }
    public static TerminalData get(Player player) { return new TerminalData(player); }

    public boolean isTerminalInstalled() { return AbilityState.load(player).terminalInstalled(); }
    public boolean isInstalled(App app) {
        return AppRegistry.enumeration().contains(app) && (app.isPreInstalled() || AbilityState.load(player).apps().contains(app.getName()));
    }
    public List<App> getInstalledApps() {
        var state = AbilityState.load(player);
        return AppRegistry.enumeration().stream()
                .filter(app -> app.isPreInstalled() || state.apps().contains(app.getName())).toList();
    }

    public void install() {
        ServerPlayer serverPlayer = serverPlayer();
        var state = AbilityState.load(player);
        if (state.terminalInstalled()) return;
        state.installTerminal();
        state.save(player);
        ACNetwork.sync(serverPlayer, state);
        NeoForge.EVENT_BUS.post(new TerminalInstalledEvent(player));
        PacketDistributor.sendToPlayer(serverPlayer, new TerminalInstalledPayload(""));
    }

    public void installApp(App app) {
        ServerPlayer serverPlayer = serverPlayer();
        var state = AbilityState.load(player);
        if (app.isPreInstalled() || !state.installApp(app.getName())) return;
        state.save(player);
        ACNetwork.sync(serverPlayer, state);
        NeoForge.EVENT_BUS.post(new AppInstalledEvent(player, app));
        PacketDistributor.sendToPlayer(serverPlayer, new TerminalInstalledPayload(app.getName()));
    }

    private ServerPlayer serverPlayer() {
        if (player instanceof ServerPlayer serverPlayer) return serverPlayer;
        throw new IllegalStateException("Terminal installation is server-only");
    }
}
