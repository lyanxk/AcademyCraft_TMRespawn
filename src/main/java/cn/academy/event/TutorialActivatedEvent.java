package cn.academy.event;

import cn.academy.tutorial.ACTutorial;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Fired once on both logical sides when a non-default tutorial is first activated. */
public final class TutorialActivatedEvent extends PlayerEvent {
    public final ACTutorial tutorial;
    public TutorialActivatedEvent(Player player, ACTutorial tutorial) {
        super(player);
        this.tutorial = tutorial;
    }
}
