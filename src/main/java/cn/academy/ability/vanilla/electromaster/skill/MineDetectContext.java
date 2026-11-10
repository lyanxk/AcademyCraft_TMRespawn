package cn.academy.ability.vanilla.electromaster.skill;

import cn.academy.data.Balance;

import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.network.MineDetectEffectPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** MDContext snapshots view parameters before consumption, but reads cooldown proficiency after XP callbacks. */
public final class MineDetectContext {
    private final ServerPlayer player;
    private final AbilityState state;
    private final AbilitySkill skill;
    private final float range;
    private final boolean advanced;
    public MineDetectContext(ServerPlayer player, AbilityState state, AbilitySkill skill) {
        this.player = player; this.state = state; this.skill = skill;
        range = Balance.i("mine_detect_context", "mine_detect_context_range_1") + state.experience(skill.id()) * Balance.i("mine_detect_context", "mine_detect_context_range_2");
        advanced = state.experience(skill.id()) > Balance.f("mine_detect_context", "mine_detect_context_threshold_1") && state.level() >= Balance.i("mine_detect_context", "mine_detect_context_threshold_2");
    }
    public boolean perform() {
        float exp = state.experience(skill.id());
        if (!state.consumeRaw(skill, Balance.i("mine_detect_context", "perform_consume_raw_1") + exp * (Balance.i("mine_detect_context", "perform_consume_raw_2") - Balance.i("mine_detect_context", "perform_consume_raw_3")), Balance.i("mine_detect_context", "perform_consume_raw_4") + exp * (Balance.i("mine_detect_context", "perform_consume_raw_5") - Balance.i("mine_detect_context", "perform_consume_raw_6")), player.isCreative())) return false;
        state.addExperience(skill, Balance.f("mine_detect_context", "perform_add_experience_1"));
        // The original broadcasts MSG_EFFECT, but all non-local clients intentionally do nothing.
        PacketDistributor.sendToPlayer(player, new MineDetectEffectPayload(range, advanced));
        state.finishContext(skill, (int) (Balance.i("mine_detect_context", "perform_finish_context_1") + state.experience(skill.id()) * (Balance.i("mine_detect_context", "perform_finish_context_2") - Balance.i("mine_detect_context", "perform_finish_context_3"))), 0);
        return true;
    }
}
