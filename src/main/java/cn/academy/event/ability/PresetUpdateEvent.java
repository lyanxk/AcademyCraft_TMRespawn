package cn.academy.event.ability;

import net.minecraft.world.entity.player.Player;

/** Both sides: a preset edit or a client preset synchronization has completed. */
public class PresetUpdateEvent extends AbilityEvent {
    public PresetUpdateEvent(Player player) { super(player); }
}
