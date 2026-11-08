package cn.academy.ability.vanilla.meltdowner.skill;

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
        float exp=state.experience(ID);
        var ball=ACElectronBallEntity.create(player,exp>.8f?5:20,6+(12-6)*exp);
        player.serverLevel().addFreshEntity(ball);
        state.addExperience(skill,.005f);
        state.finishContext(skill,(int)(20+(10-20)*exp),0);
        AbilityManager.completeUse(player,state,skill);
        return ball;
    }
    /** LambdaLib2 getLookingPos adds 0.6 eye height to an entity-position hitVec. */
    public static Vec3 destination(net.minecraft.world.entity.player.Player player) {
        var trace=ElectricAttackTrace.trace(player,15,false);
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
        for(var viewer:player.serverLevel().players())if(viewer.distanceToSqr(player)<400)
            PacketDistributor.sendToPlayer(viewer,visual);
    }
    private ElectronBomb(){}
}
