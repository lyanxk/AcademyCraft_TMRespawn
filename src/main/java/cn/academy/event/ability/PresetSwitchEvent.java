package cn.academy.event.ability;

import net.minecraft.world.entity.player.Player;

/** Client only: the preset-switch key changed the selected combat preset. */
public class PresetSwitchEvent extends AbilityEvent {
    public PresetSwitchEvent(Player player) { super(player); }
}
