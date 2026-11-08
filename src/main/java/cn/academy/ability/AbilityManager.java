package cn.academy.ability;

import cn.academy.network.ACNetwork;
import cn.academy.advancement.ACAdvancements;
import cn.academy.registry.ACSounds;
import cn.academy.network.OpenClientScreenPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

public final class AbilityManager {
    public static void toggle(ServerPlayer player, AbilityState state) {
        if (!state.hasCategory()) {
            deny(player, Component.translatable("ac.ability.no_category"));
            return;
        }
        if (state.active() && (AbilityContextManager.terminateToggleContexts(player, state)
                || AbilityContextManager.abortHeldContexts(player, state))) {
            state.save(player);
            return;
        }
        state.setActive(!state.active());
        state.save(player);
        player.displayClientMessage(Component.translatable(state.active() ? "ac.ability.activated" : "ac.ability.deactivated"), true);
    }

    public static void usePreset(ServerPlayer player, AbilityState state, int slot) {
        if (slot < 0 || slot >= cn.academy.ability.AbilityState.SKILL_CHANNELS) return;
        String skillId = state.controlSkill(slot);
        AbilitySkill skill = AbilityRegistry.skill(skillId);
        if (skill == null) {
            deny(player, Component.translatable("ac.ability.empty_preset", slot + 1));
            return;
        }
        if (skill.name().equals("location_teleport")) {
            if (!state.canUse() || state.cooldown(skill.id()) > 0) {
                Component reason = !state.active() ? Component.translatable("ac.ability.not_activated")
                        : state.interfered() ? Component.translatable("ac.ability.interfered")
                        : state.overloadLocked() ? Component.translatable("ac.ability.overload_locked")
                        : Component.translatable("ac.ability.cooldown", state.cooldown(skill.id()) / 20f);
                deny(player, reason);
                return;
            }
            PacketDistributor.sendToPlayer(player, new OpenClientScreenPayload("locations"));
            return;
        }
        // USE follows the same press/hold/release contract as keyboard input for every category.
        AbilityContextManager.keyDown(player,state,slot);
    }

    public static void completeUse(ServerPlayer player, AbilityState state, AbilitySkill skill) {
        ACAdvancements.grantLegacyLevels(player, state.category(), state.level());
        ACAdvancements.grantLegacySkillUse(player, skill.category(), skill.name());
        state.save(player);
    }

    public static void tryLearnFromTerminal(ServerPlayer player, AbilityState state, String skillId) {
        if (!state.terminalInstalled() || !state.apps().contains("skill_tree")) return;
        AbilitySkill skill = AbilityRegistry.skill(skillId);
        learn(player, state, skill, 5, true);
    }

    public static AbilitySkill develop(ServerPlayer player, AbilityState state, int maximumLevel, boolean consumeExperience) {
        if (!state.hasCategory()) {
            deny(player, Component.translatable("ac.ability.no_category"));
            return null;
        }
        AbilityCategory category = AbilityRegistry.category(state.category());
        if (category == null) return null;
        for (AbilitySkill skill : category.skills()) {
            if (skill.level() <= maximumLevel && state.canLearn(skill)) {
                if (learn(player, state, skill, maximumLevel, consumeExperience)) return skill;
            }
        }
        deny(player, Component.translatable("ac.developer.no_available_skill"));
        return null;
    }

    private static boolean learn(ServerPlayer player, AbilityState state, AbilitySkill skill,
                                 int maximumLevel, boolean consumeExperience) {
        if (skill == null || skill.level() > maximumLevel || !state.canLearn(skill)) return false;
        int cost = 3 + skill.level() * skill.level() / 2;
        if (consumeExperience && !player.isCreative() && player.experienceLevel < cost) {
            deny(player, Component.translatable("ac.developer.need_levels", cost));
            return false;
        }
        if (consumeExperience && !player.isCreative()) player.giveExperienceLevels(-cost);
        if (!state.learn(skill)) return false;
        state.save(player);
        player.displayClientMessage(Component.translatable("ac.developer.learned", skill.displayName())
                .withStyle(ChatFormatting.AQUA), false);
        ACNetwork.sync(player, state);
        ACAdvancements.grantLegacySkillLearn(player, skill.category(), skill.name());
        return true;
    }

    public static void setCategory(ServerPlayer player, AbilityState state, String category) {
        state.setCategory(category);
        state.save(player);
        player.displayClientMessage(Component.translatable("ac.ability.category_acquired",
                AbilityRegistry.category(category).displayName()).withStyle(ChatFormatting.AQUA), false);
        ACNetwork.sync(player, state);
        ACAdvancements.grantLegacyLevels(player, category, 1);
    }

    public static void deny(ServerPlayer player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        player.serverLevel().playSound(null, player.blockPosition(), ACSounds.ABILITY_DENY.get(),
                SoundSource.PLAYERS, .7f, 1f);
    }

    private AbilityManager() {}
}
