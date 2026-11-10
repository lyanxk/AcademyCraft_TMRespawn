package cn.academy.ability.vanilla.electromaster.skill;

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
        range = 15 + state.experience(skill.id()) * 15;
        advanced = state.experience(skill.id()) > .5f && state.level() >= 4;
    }
    public boolean perform() {
        float exp = state.experience(skill.id());
        if (!state.consumeRaw(skill, 1500 + exp * (1000 - 1500), 200 + exp * (180 - 200), player.isCreative())) return false;
        state.addExperience(skill, .008f);
        // The original broadcasts MSG_EFFECT, but all non-local clients intentionally do nothing.
        PacketDistributor.sendToPlayer(player, new MineDetectEffectPayload(range, advanced));
        state.finishContext(skill, (int) (900 + state.experience(skill.id()) * (400 - 900)), 0);
        return true;
    }
}
