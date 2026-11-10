package cn.academy.event.ability;

import net.minecraft.world.entity.player.Player;

/** Fired on both sides after the ability changes from inactive to active. */
public class AbilityActivateEvent extends AbilityEvent {
    public AbilityActivateEvent(Player player) { super(player); }
}
