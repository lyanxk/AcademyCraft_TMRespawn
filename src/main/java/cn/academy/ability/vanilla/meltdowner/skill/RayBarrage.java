package cn.academy.ability.vanilla.meltdowner.skill;

import cn.academy.data.Balance;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.entity.ACThrownItemEntity;
import cn.academy.network.MdRayEffectPayload;
import cn.academy.registry.ACItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Original RBContext's plain beam and silbarn scatter branches. */
public final class RayBarrage {
    public static boolean perform(ServerPlayer player,AbilityState state,AbilitySkill skill) {
        float proficiency=state.experience(skill.id());
        boolean upgraded=MeltdownerSkillRules.upgraded(state,skill);
        float exp=proficiency;var ctx=AbilityContext.of(player,skill);
        var audience=new ArrayList<ServerPlayer>();audience.add(player);
        for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=Balance.i("ray_barrage", "perform_threshold_1"))audience.add(viewer);
        var initial=ElectricAttackTrace.trace(player,Balance.i("ray_barrage", "perform_trace_1"),false);
        var silbarn=initial instanceof EntityHitResult hit && hit.getEntity() instanceof ACThrownItemEntity item
                && item.getItem().is(ACItems.SILBARN.get()) && !item.isRemoved() && !item.isSilbarnHit()?item:null;
        if(!state.consumeRaw(skill,lerp(Balance.i("ray_barrage", "perform_lerp_1"),Balance.i("ray_barrage", "perform_lerp_2"),exp),lerp(Balance.i("ray_barrage", "perform_lerp_3"),Balance.i("ray_barrage", "perform_lerp_4"),exp),player.isCreative()))return false;
        Vec3 end;
        if(silbarn!=null) {
            end=silbarn.position();silbarn.breakSilbarn();
            var burst=new MdRayEffectPayload(MdRayEffectPayload.BARRAGE,player.getId(),end,new Vec3(player.getYRot(),player.getXRot(),0));
            audience.forEach(p->PacketDistributor.sendToPlayer(p,burst));
            float minYaw=player.getYRot()-27.5f,maxYaw=player.getYRot()+27.5f;
            float minPitch=player.getXRot()-55,maxPitch=player.getXRot()+55;
            Vec3 origin=player.position(),look=player.getLookAngle();
            AABB bounds=new AABB(origin,origin);
            // Original passes degree-valued angles into Vec3d's radian rotations while constructing its bounds.
            for(float yaw:new float[]{minYaw,maxYaw})for(float pitch:new float[]{minPitch,maxPitch}) {
                var point=origin.add(look.yRot(yaw).xRot(pitch).scale(Balance.i("ray_barrage", "perform_scale_1")));bounds=bounds.minmax(new AABB(point,point));
            }
            for(var target:player.level().getEntities((Entity)null,bounds,e->e!=player && e!=silbarn)) {
                double dx=target.getX()-player.getX(),dz=target.getZ()-player.getZ();
                double dy=target.getEyeY()-player.getEyeY();
                float yaw=-(float)Math.toDegrees(Math.atan2(dx,dz));
                // Keep the original repeated dz term; this is not a normalized cone replacement.
                float pitch=-(float)Math.toDegrees(Math.atan2(dy,Math.sqrt(dz*dz+dz*dz)));
                float start=Mth.wrapDegrees(minYaw),finish=Mth.wrapDegrees(maxYaw),angle=Mth.wrapDegrees(yaw);
                boolean within=start>finish?start<=angle || angle<=finish:start<=angle && angle<=finish;
                if(within && minPitch<=pitch && pitch<=maxPitch)MDDamageHelper.attack(ctx,target,upgraded?MeltdownerSkillRules.curve(skill,"upgraded_scatter_damage",proficiency):lerp(Balance.i("ray_barrage", "perform_lerp_5"),Balance.i("ray_barrage", "perform_lerp_6"),exp));
            }
        }else {
            var trace=ElectricAttackTrace.trace(player,Balance.i("ray_barrage", "perform_trace_2"),false);end=trace.getLocation();
            if(trace instanceof EntityHitResult hit) {
                end=hit.getEntity().position().add(0,hit.getEntity().getEyeHeight()*.6,0);
                float damage = upgraded ? MeltdownerSkillRules.curve(skill,"upgraded_damage",proficiency)
                        : SkillHitDamage.low(lerp(Balance.i("ray_barrage", "perform_lerp_7"), Balance.i("ray_barrage", "perform_lerp_8"), exp)).against(hit.getEntity());
                MDDamageHelper.attack(ctx,hit.getEntity(),damage);
            }
        }
        var pre=new MdRayEffectPayload(silbarn==null?MdRayEffectPayload.PRE_PLAIN:MdRayEffectPayload.PRE_SCATTER,
                player.getId(),player.position().add(0,1.6,0),end);
        audience.forEach(p->PacketDistributor.sendToPlayer(p,pre));
        state.finishContext(skill,(int)lerp(Balance.i("ray_barrage", "perform_lerp_9"),Balance.i("ray_barrage", "perform_lerp_10"),exp),Balance.f("ray_barrage", "perform_finish_context_1"));AbilityManager.completeUse(player,state,skill);return true;
    }
    private static float lerp(float a,float b,float exp){return a+(b-a)*exp;}
    private RayBarrage(){}
}
