package cn.academy.event.ability;

import net.minecraft.world.entity.player.Player;

/** Fired on both sides after the player's category changes. */
public class CategoryChangeEvent extends AbilityEvent {
    public CategoryChangeEvent(Player player) { super(player); }
}
