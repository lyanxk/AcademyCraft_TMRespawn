package cn.academy.event.ability;

import cn.academy.ability.AbilitySkill;
import net.minecraft.world.entity.player.Player;

/** Fired on both sides for a newly learned skill, excluding the original bulk learn-all command. */
public class SkillLearnEvent extends AbilityEvent {
    public final AbilitySkill skill;
    public SkillLearnEvent(Player player, AbilitySkill skill) {
        super(player);
        this.skill = skill;
    }
}
