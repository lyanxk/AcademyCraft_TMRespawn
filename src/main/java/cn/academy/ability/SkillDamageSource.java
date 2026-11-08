package cn.academy.ability;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Skill attribution and death message from the original 1.12.2 SkillDamageSource. */
public final class SkillDamageSource extends DamageSource {
    public static final ResourceKey<DamageType> NORMAL = key("skill");
    public static final ResourceKey<DamageType> IGNORE_ARMOR = key("skill_ignore_armor");
    public final AbilitySkill skill;

    public SkillDamageSource(Player player, AbilitySkill skill, boolean ignoreArmor) {
        super(player.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ignoreArmor ? IGNORE_ARMOR : NORMAL), player);
        this.skill = java.util.Objects.requireNonNull(skill);
    }

    @Override
    public Component getLocalizedDeathMessage(LivingEntity target) {
        return Component.translatable("death.attack.ac_skill", target.getDisplayName(),
                getEntity().getDisplayName(), skill.displayName());
    }

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("academy", name));
    }
}
