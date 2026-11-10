package cn.academy.event.ability;

import cn.academy.ability.AbilitySkill;
import net.minecraft.world.entity.player.Player;

/** Server only; amount is the intended award, including any part beyond the proficiency cap. */
public class SkillExpAddedEvent extends AbilityEvent {
    public final AbilitySkill skill;
    public final float amount;
    public SkillExpAddedEvent(Player player, AbilitySkill skill, float amount) {
        super(player);
        this.skill = skill;
        this.amount = amount;
    }
}
