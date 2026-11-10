package cn.academy.ability.vanilla.meltdowner.skill;

import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.data.GameplayData;

/** Level upgrades share the skill's datapack values on both sides. */
public final class MeltdownerSkillRules {
    private static String path(AbilitySkill skill) { return "skills/" + skill.category() + "/" + skill.name(); }

    public static boolean upgraded(AbilityState state, AbilitySkill skill) {
        var data = GameplayData.document(path(skill));
        return data.has("upgrade_level") && state.level() >= data.get("upgrade_level").getAsInt();
    }

    public static float curve(AbilitySkill skill, String key, float experience) {
        float start = (float) GameplayData.at(path(skill), key, 0);
        float end = (float) GameplayData.at(path(skill), key, 1);
        return start + (end - start) * experience;
    }

    public static int integer(AbilitySkill skill, String key) {
        return GameplayData.integer(path(skill), key);
    }

    private MeltdownerSkillRules() { }
}
