package cn.academy.event.ability;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

public class AbilityEvent extends Event {
    public final Player player;

    public AbilityEvent(Player player) { this.player = player; }
}
