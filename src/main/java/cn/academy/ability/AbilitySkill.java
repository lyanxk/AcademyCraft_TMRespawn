package cn.academy.ability;

import cn.academy.data.GameplayData;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Immutable description of one skill from the original four skill trees. */
public record AbilitySkill(
        String category,
        String name,
        int level,
        float cpCost,
        float overload,
        int cooldownTicks,
        boolean controllable,
        List<Requirement> requirements,
        List<Enhancement> enhancements
) {
    public AbilitySkill {
        requirements = List.copyOf(requirements);
        enhancements = List.copyOf(enhancements);
    }

    public boolean crossesEnhancement(int previousLevel,int nextLevel) {
        return enhancements.stream().anyMatch(tag->previousLevel<tag.level() && nextLevel>=tag.level());
    }

    public String id() {
        return category + "." + name;
    }

    private String dataPath() { return "skills/" + category + "/" + name; }
    public boolean isEnabled() { return GameplayData.flag(dataPath(), "enabled"); }
    public boolean shouldDestroyBlocks() { return GameplayData.flag(dataPath(), "destroy_blocks"); }
    public float damageScale() { return GameplayData.decimal(dataPath(), "damage_scale"); }
    public float getCPConsumeSpeed() { return GameplayData.decimal(dataPath(), "cp_consume_speed"); }
    public float getOverloadConsumeSpeed() { return GameplayData.decimal(dataPath(), "overload_consume_speed"); }
    public float getExpIncrSpeed() { return GameplayData.decimal(dataPath(), "exp_increase_speed"); }

    public List<Requirement> enabledRequirements() {
        return requirements.stream().filter(requirement -> {
            AbilitySkill dependency = AbilityRegistry.skill(category + "." + requirement.skillId());
            return dependency == null || dependency.isEnabled();
        }).toList();
    }

    /** Registry entries preserve setParent as the first dependency, followed by addSkillDep entries. */
    public AbilitySkill parent() {
        if (requirements.isEmpty()) return null;
        AbilitySkill parent = AbilityRegistry.skill(category + "." + requirements.getFirst().skillId());
        return parent != null && parent.isEnabled() ? parent : null;
    }

    /** The original generic courses accept any learned skill at this level, including passive skills. */
    public int requiredAnySkillLevel() {
        return GameplayData.integer(dataPath(), "required_any_skill_level");
    }

    private String localizationCategory() {
        return name.equals("brain_course") || name.equals("brain_course_advanced") || name.equals("mind_course")
                ? "generic" : category;
    }

    public Component displayName() {
        return Component.translatable("ac.ability." + localizationCategory() + "." + name + ".name");
    }

    public Component description() {
        return Component.translatable("ac.ability." + localizationCategory() + "." + name + ".desc");
    }

    public Component description(AbilityState state) {
        if(id().equals("vecmanip.dir_shock") && state.level()>=5)
            return Component.translatable("ac.ability.vecmanip.dir_shock.lv3.desc")
                    .append(Component.translatable("ac.ability.vecmanip.dir_shock.lv5.extra"));
        return id().equals("vecmanip.dir_shock") && state.level()>=3
                ? Component.translatable("ac.ability.vecmanip.dir_shock.lv3.desc") : description();
    }

    public ResourceLocation icon() {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/abilities/" + localizationCategory() + "/skills/" + name + ".png");
    }

    public record Requirement(String skillId, float experience) {}
    /** Skill metadata marking a level-triggered enhancement and proficiency reset. */
    public record Enhancement(int level) {}
}
