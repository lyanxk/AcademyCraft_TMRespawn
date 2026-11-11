package cn.academy.ability;

import cn.academy.data.GameplayData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Resolves both parts of a hit against the same target before combat modifiers. */
public record SkillHitDamage(float fixed, float maxHealthFraction) {
    public static SkillHitDamage flat(float damage) {
        return new SkillHitDamage(damage, 0);
    }

    /** The input curve gives the damage against a target with the reference health. */
    public static SkillHitDamage low(float original) {
        float fraction = GameplayData.decimal("ability", "low_damage_health_fraction");
        float reference = GameplayData.decimal("ability", "low_damage_reference_health");
        return new SkillHitDamage(original - reference * fraction, fraction);
    }

    public SkillHitDamage scaled(float multiplier) {
        return new SkillHitDamage(fixed * multiplier, maxHealthFraction * multiplier);
    }

    public float against(Entity target) {
        return target instanceof LivingEntity living ? fixed + maxHealthFraction * living.getMaxHealth() : fixed;
    }
}
