package cn.academy.event.ability;

import cn.academy.ability.AbilitySkill;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;

/** Canceling this event reflects the attack instead of applying its damage. */
public class ReflectEvent extends AbilityEvent implements ICancellableEvent {
    public final AbilitySkill skill;
    public final Entity target;
    public ReflectEvent(Player player, AbilitySkill skill, Entity target) {
        super(player); this.skill = skill; this.target = target;
    }
}
