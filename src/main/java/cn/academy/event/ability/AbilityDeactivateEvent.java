package cn.academy.event.ability;

import net.minecraft.world.entity.player.Player;

/** Fired on both sides after the ability changes from active to inactive. */
public class AbilityDeactivateEvent extends AbilityEvent {
    public AbilityDeactivateEvent(Player player) { super(player); }
}
