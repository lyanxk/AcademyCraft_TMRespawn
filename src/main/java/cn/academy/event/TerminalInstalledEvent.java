package cn.academy.event;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/** Fired on both logical sides when the data terminal is first installed. */
public final class TerminalInstalledEvent extends Event {
    public final Player player;
    public TerminalInstalledEvent(Player player) { this.player = player; }
}
