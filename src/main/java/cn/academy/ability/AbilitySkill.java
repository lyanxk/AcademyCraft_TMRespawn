package cn.academy.ability;

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

    public AbilitySkill(String category,String name,int level,float cpCost,float overload,int cooldownTicks,
                        boolean controllable,List<Requirement> requirements) {
        this(category,name,level,cpCost,overload,cooldownTicks,controllable,requirements,List.of());
    }

    public AbilitySkill withEnhancements(int... levels) {
        return new AbilitySkill(category,name,level,cpCost,overload,cooldownTicks,controllable,requirements,
                java.util.Arrays.stream(levels).mapToObj(Enhancement::new).toList());
    }

    public boolean crossesEnhancement(int previousLevel,int nextLevel) {
        return enhancements.stream().anyMatch(tag->previousLevel<tag.level() && nextLevel>=tag.level());
    }

    public String id() {
        return category + "." + name;
    }

    public boolean isEnabled() { return cn.academy.config.ACConfig.skillSettings(this).enabled().get(); }
    public boolean shouldDestroyBlocks() { return cn.academy.config.ACConfig.skillSettings(this).destroyBlocks().get(); }
    public float getCPConsumeSpeed() { return cn.academy.config.ACConfig.skillSettings(this).cpConsumeSpeed().get().floatValue(); }
    public float getOverloadConsumeSpeed() { return cn.academy.config.ACConfig.skillSettings(this).overloadConsumeSpeed().get().floatValue(); }
    public float getExpIncrSpeed() { return cn.academy.config.ACConfig.skillSettings(this).expIncreaseSpeed().get().floatValue(); }

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
        return switch (name) {
            case "brain_course" -> 3;
            case "brain_course_advanced" -> 4;
            case "mind_course" -> 5;
            default -> 0;
        };
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
