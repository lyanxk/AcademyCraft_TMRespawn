package cn.academy.event.ability;

import net.minecraft.world.entity.player.Player;

/** Server only; repeated assignment of the same level does not fire this event. */
public class LevelChangeEvent extends AbilityEvent {
    public LevelChangeEvent(Player player) { super(player); }
}
