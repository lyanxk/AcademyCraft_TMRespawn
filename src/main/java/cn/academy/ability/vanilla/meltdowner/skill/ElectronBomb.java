package cn.academy.ability.vanilla.meltdowner.skill;

import cn.academy.data.Balance;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.entity.ACElectronBallEntity;
import cn.academy.network.ElectronBombEffectPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Original ElectronBomb.Ctx: no consume call, immediate reward/cooldown, delayed ball callback. */
public final class ElectronBomb {
    public static final String ID="meltdowner.electron_bomb";
    public static ACElectronBallEntity perform(ServerPlayer player,AbilityState state) {
        var skill=AbilityRegistry.skill(ID);
        if(!state.canStartContext(skill))return null;
        float proficiency=state.experience(ID);
        boolean upgraded=MeltdownerSkillRules.upgraded(state,skill);
        float exp=upgraded?1:proficiency;
        var ball=ACElectronBallEntity.create(player,exp>Balance.f("electron_bomb", "perform_threshold_1")?5:20,upgraded?MeltdownerSkillRules.curve(skill,"upgraded_damage",proficiency):6+(12-6)*exp);
        player.serverLevel().addFreshEntity(ball);
        state.addExperience(skill,Balance.f("electron_bomb", "perform_add_experience_1"));
        state.finishContext(skill,(int)(Balance.i("electron_bomb", "perform_finish_context_1")+(Balance.i("electron_bomb", "perform_finish_context_2")-Balance.i("electron_bomb", "perform_finish_context_3"))*exp),0);
        AbilityManager.completeUse(player,state,skill);
        return ball;
    }
    /** LambdaLib2 getLookingPos adds 0.6 eye height to an entity-position hitVec. */
    public static Vec3 destination(net.minecraft.world.entity.player.Player player) {
        var state=AbilityState.load(player);
        var skill=AbilityRegistry.skill(ID);
        double range=MeltdownerSkillRules.upgraded(state,skill)?MeltdownerSkillRules.curve(skill,"upgraded_range",state.experience(ID)):Balance.i("electron_bomb", "destination_trace_1");
        var trace=ElectricAttackTrace.trace(player,range,false);
        return trace instanceof EntityHitResult entity
                ? trace.getLocation().add(0,entity.getEntity().getEyeHeight()*.6,0):trace.getLocation();
    }
    public static void fire(ServerPlayer player,ACElectronBallEntity ball,float damage) {
        Vec3 start=ball.position().add(0,player.getDimensions(Pose.STANDING).eyeHeight(),0);
        Vec3 destination=destination(player);
        var trace=ElectricAttackTrace.traceSegment(player,start,destination,
                target->target!=player && !(target instanceof ACElectronBallEntity));
        if(trace instanceof EntityHitResult hit) {
            MDDamageHelper.attack(AbilityContext.of(player,AbilityRegistry.skill(ID)),hit.getEntity(),damage);
        }
        // Original callback notifies around the caster at firing time, even when no entity was hit.
        var visual=new ElectronBombEffectPayload(ball.getId());
        for(var viewer:player.serverLevel().players())if(viewer.distanceToSqr(player)<Balance.i("electron_bomb", "fire_threshold_1"))
            PacketDistributor.sendToPlayer(viewer,visual);
    }
    private ElectronBomb(){}
}
