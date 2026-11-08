package cn.academy.event;

import cn.academy.terminal.App;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/** Fired on both logical sides for a newly installed, non-preinstalled application. */
public final class AppInstalledEvent extends Event {
    public final Player player;
    public final App app;
    public AppInstalledEvent(Player player, App app) {
        this.player = player;
        this.app = app;
    }
}
