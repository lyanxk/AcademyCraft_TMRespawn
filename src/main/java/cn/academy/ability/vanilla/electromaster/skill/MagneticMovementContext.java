package cn.academy.ability.vanilla.electromaster.skill;

import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.network.MagneticMovementPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** MovementContext's server half: unchecked startup payment, independent locked target, and reward on every termination. */
public final class MagneticMovementContext {
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    public final int id = NEXT_ID.incrementAndGet();
    private final ServerPlayer player;
    private final AbilitySkill skill;
    private final Vec3 start;
    private final float cp, overload;
    private final int slot, request;
    private final ArrayList<ServerPlayer> audience = new ArrayList<>();
    private MagneticTarget target;
    private boolean effectsStarted, ended;
    public MagneticMovementContext(ServerPlayer player, AbilityState state, AbilitySkill skill, int slot, int request) {
        this.player = player; this.skill = skill; this.slot = slot; this.request = request;
        start = player.position(); float exp = state.experience(skill.id());
        cp = state.level()>=3 ? 8-4*exp : 15+(8-15)*exp;
        overload = state.level()>=3 ? 30*(1-exp) : 60+(30-60)*exp;
        audience.add(player);
        for (var viewer : player.serverLevel().players()) if (viewer != player && viewer.distanceToSqr(player) <= 625) audience.add(viewer);
    }
    public boolean start(AbilityState state) {
        state.consumeRaw(skill,0,overload,player.isCreative()); // Original does not inspect this return value.
        float exp=state.experience(skill.id());
        target = MagneticTarget.trace(player,MagneticMovementMotion.range(state.level(),exp),state.level()>=3?1:exp);
        if (target == null) return false;
        PacketDistributor.sendToPlayer(player,new MagneticMovementPayload(player.getId(),slot,id,request,
                MagneticMovementPayload.LINK,state.overload(),Vec3.ZERO));
        return true;
    }
    public boolean alive() { return target != null && (target.entity() == null || !target.entity().isRemoved()); }
    public boolean tick(AbilityState state) {
        return alive() && state.consumeRaw(skill,cp,0,player.isCreative());
    }
    public void updateEffects(int phase, Vec3 point) {
        if (ended || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) return;
        if (phase == MagneticMovementPayload.START && !effectsStarted) { effectsStarted = true; send(MagneticMovementPayload.START,Vec3.ZERO); }
        if (effectsStarted && (phase == MagneticMovementPayload.START || phase == MagneticMovementPayload.UPDATE)) send(MagneticMovementPayload.UPDATE,point);
    }
    public void end(AbilityState state) {
        if (ended) return; ended = true;
        state.addExperience(skill,MagneticMovementMotion.reward(start.distanceTo(player.position())));
        player.resetFallDistance(); send(MagneticMovementPayload.END,Vec3.ZERO); audience.clear();
    }
    private void send(int phase, Vec3 point) {
        var payload = new MagneticMovementPayload(player.getId(),slot,id,request,phase,0,point);
        audience.forEach(viewer -> PacketDistributor.sendToPlayer(viewer,payload));
    }
}
