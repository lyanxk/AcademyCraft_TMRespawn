package cn.academy.ability.vanilla.teleporter.util;

import cn.academy.data.Balance;

import cn.academy.ability.AbilityContext;
import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilityState;
import cn.academy.advancement.ACAdvancements;
import cn.academy.event.ability.AbilityEvent;
import cn.academy.network.TPCritHitPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Direct port of the original TPSkillHelper's ordered critical rolls and experience awards. */
public final class TPSkillHelper {


    public static void attack(AbilityContext context, Entity target, float damage, boolean ignoreArmor) {
        AbilityState state = AbilityState.load(context.player);
        for (int tier = 0; tier < 3; tier++) {
            if (context.player.getRandom().nextFloat() < probability(state, tier)) {
                damage *= Balance.af("t_p_skill_helper", "rates", tier);
                context.player.sendSystemMessage(Component.translatable("ac.ability.teleporter.crithit", Balance.af("t_p_skill_helper", "rates", tier)));
                // Original helper calls AbilityData.addSkillExp directly, not AbilityContext.addSkillExp.
                // That also learns the passive on its first award, even if it was not previously learned.
                state.addRawExperience(AbilityRegistry.skill("teleporter.dim_folding_theorem"), (tier + Balance.i("t_p_skill_helper", "attack_add_raw_experience_1")) * Balance.f("t_p_skill_helper", "attack_add_raw_experience_2"));
                state.addRawExperience(AbilityRegistry.skill("teleporter.space_fluct"), Balance.f("t_p_skill_helper", "attack_add_raw_experience_3"));
                NeoForge.EVENT_BUS.post(new TPCritHitEvent(context.player, target, tier));
                PacketDistributor.sendToPlayer(context.player, new TPCritHitPayload(target.getId(), tier));
                ACAdvancements.grant(context.player, "legacy/teleporter/critical_attack");
                break;
            }
        }
        if (ignoreArmor) context.attackIgnoreArmor(target, damage);
        else context.attack(target, damage);
    }

    private static float probability(AbilityState state, int tier) {
        float dim = state.learned().contains("teleporter.dim_folding_theorem")
                ? state.experience("teleporter.dim_folding_theorem") : -1;
        float space = state.learned().contains("teleporter.space_fluct") ? state.experience("teleporter.space_fluct") : -1;
        return switch (tier) {
            case 0 -> lerp(Balance.f("t_p_skill_helper", "probability_lerp_1"), Balance.f("t_p_skill_helper", "probability_lerp_2"), dim) + lerp(Balance.f("t_p_skill_helper", "probability_lerp_3"), Balance.f("t_p_skill_helper", "probability_lerp_4"), space);
            case 1 -> lerp(Balance.f("t_p_skill_helper", "probability_lerp_5"), Balance.f("t_p_skill_helper", "probability_lerp_6"), space);
            case 2 -> lerp(Balance.f("t_p_skill_helper", "probability_lerp_7"), Balance.f("t_p_skill_helper", "probability_lerp_8"), space);
            default -> throw new IllegalArgumentException("Critical tier: " + tier);
        };
    }

    private static float lerp(float start, float end, float experience) {
        return experience < 0 ? 0 : start + experience * (end - start);
    }

    public static final class TPCritHitEvent extends AbilityEvent {
        public final int level;
        public final Entity target;

        public TPCritHitEvent(Player player, Entity target, int level) {
            super(player);
            this.target = target;
            this.level = level;
        }
    }

    private TPSkillHelper() {}
}
