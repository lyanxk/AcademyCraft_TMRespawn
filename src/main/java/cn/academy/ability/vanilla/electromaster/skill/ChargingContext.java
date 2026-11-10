package cn.academy.ability.vanilla.electromaster.skill;

import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.support.EnergyBlockHelper;
import cn.academy.support.EnergyItemHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.HitResult;

/** Server tick ordering and constructor snapshots from 1.12.2 CurrentCharging.scala. */
public final class ChargingContext {
    private static final double[] CHARGE={15,35,200,1000,10_000,1_000_000};
    private static final float[] CP_PER_SECOND={60,140,240,300,340,440};
    private static final float[] OL_START={20,40,80,100,120};
    private static final float[] OL_END={10,20,40,50,80};
    private final ServerPlayer player;
    private final AbilitySkill skill;
    private final float experience;
    private final int level;
    private final boolean itemMode;
    private final java.util.ArrayList<ServerPlayer> audience = new java.util.ArrayList<>();

    public ChargingContext(ServerPlayer player, AbilityState state, AbilitySkill skill) {
        this.player = player; this.skill = skill;
        experience = state.experience(skill.id());
        level = state.level();
        itemMode = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
        // ContextManager captures targets before MADEALIVE invokes SkillPerform listeners.
        audience.add(player);
        for (var viewer : player.serverLevel().players())
            if (viewer != player && viewer.distanceToSqr(player) <= 625) audience.add(viewer);
    }
    public void onStarted(AbilityState state) {
        send(true);
    }
    public void end() { send(false); audience.clear(); }
    private void send(boolean start) {
        var payload = new cn.academy.network.ChargingEffectPayload(player.getId(), start, itemMode);
        for (var viewer : audience) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, payload);
    }
    public boolean isItemMode() { return itemMode; }
    public boolean tick(AbilityState state) {
        float consumption = (CP_PER_SECOND[level-1]+(CP_PER_SECOND[level]-CP_PER_SECOND[level-1])*experience)/20;
        float overload = (OL_START[level-1]+(OL_END[level-1]-OL_START[level-1])*experience)/20;
        double charge = Math.floor(CHARGE[level-1]+(CHARGE[level]-CHARGE[level-1])*experience);
        if (itemMode) {
            // Main hand is the target even if the off hand originally selected item mode.
            // Payment precedes charging/experience in this branch.
            if (!state.consumeRaw(skill, consumption, overload, player.isCreative())) return false;
            var stack = player.getMainHandItem();
            boolean good = EnergyItemHelper.isSupported(stack);
            if (good) EnergyItemHelper.charge(stack, charge, false);
            state.addExperience(skill, good ? .0001f : .00003f);
            return true;
        }
        var hit = ElectricAttackTrace.trace(player, 15, false);
        boolean good = false;
        if (hit instanceof net.minecraft.world.phys.BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            var tile = player.serverLevel().getBlockEntity(block.getBlockPos());
            good = EnergyBlockHelper.isSupported(tile);
            if (good) EnergyBlockHelper.charge(tile, charge, true);
        }
        // The original block branch charges and rewards before attempting CP payment,
        // including the last tick that terminates for insufficient CP.
        state.addExperience(skill, good ? .0001f : .00003f);
        return state.consumeRaw(skill, consumption, overload, player.isCreative());
    }
}
