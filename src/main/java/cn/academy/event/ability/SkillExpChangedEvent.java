package cn.academy.event.ability;

import cn.academy.ability.AbilitySkill;
import net.minecraft.world.entity.player.Player;

/** Server only, after an experience award or explicit experience assignment, never for category reset. */
public class SkillExpChangedEvent extends AbilityEvent {
    public final AbilitySkill skill;
    public SkillExpChangedEvent(Player player, AbilitySkill skill) {
        super(player);
        this.skill = skill;
    }
}
