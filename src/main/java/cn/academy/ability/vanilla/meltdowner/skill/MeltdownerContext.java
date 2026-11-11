package cn.academy.ability.vanilla.meltdowner.skill;

import cn.academy.data.Balance;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.network.MeltdownerContextPayload;
import cn.academy.util.RangedRayDamage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.network.MeltdownerContextPayload.*;

/** Remaining particle-category contexts translated from LightShield.java, Meltdowner.scala and JetEngine.scala. */
public final class MeltdownerContext {
    private static final java.util.concurrent.atomic.AtomicInteger NEXT=new java.util.concurrent.atomic.AtomicInteger();
    public final int id=NEXT.incrementAndGet();
    private final ServerPlayer player;
    private final AbilitySkill skill;
    private final AbilityContext ctx;
    private final int kind,slot,request;
    private final float exp,proficiency;
    private final boolean upgraded;
    private final List<ServerPlayer> audience=new ArrayList<>();
    private int ticks,lastAbsorb=-1;
    private boolean triggering,ended;
    private Vec3 previous;
    public static boolean handles(AbilitySkill skill) {
        return switch(skill.name()){case "light_shield","meltdowner","jet_engine"->true;default->false;};
    }
    public MeltdownerContext(ServerPlayer player,AbilityState state,AbilitySkill skill,int slot,int request) {
        this.player=player;this.skill=skill;this.slot=slot;this.request=request;
        proficiency=state.experience(skill.id());upgraded=MeltdownerSkillRules.upgraded(state,skill);
        exp=upgraded && skill.name().equals("light_shield")?1:proficiency;ctx=AbilityContext.of(player,skill);
        kind=skill.name().equals("light_shield")?SHIELD:skill.name().equals("meltdowner")?BEAM:JET;
        audience.add(player);for(var viewer:player.serverLevel().players())
            if(viewer!=player && viewer.distanceToSqr(player)<=Balance.i("meltdowner_context", "meltdowner_context_threshold_1"))audience.add(viewer);
        previous=player.position();
    }
    public void start(AbilityState state) {
        send(START,-1,Vec3.ZERO,0);
    }
    public boolean tick(AbilityState state) {
        if(ended)return false;
        ticks++;
        if(kind==JET) {
            boolean alive=player.isCreative() || state.cp()>=lerp(Balance.i("meltdowner_context", "tick_lerp_1"),Balance.i("meltdowner_context", "tick_lerp_2"));
            if(triggering) {
                var hit=ElectricAttackTrace.traceSegment(player,previous,player.position(),e->e!=player && e instanceof LivingEntity);
                if(player.isPassenger())player.stopRiding();
                if(hit instanceof EntityHitResult entity)MDDamageHelper.attack(ctx,entity.getEntity(),lerp(Balance.i("meltdowner_context", "tick_lerp_3"),Balance.i("meltdowner_context", "tick_lerp_4")));
            }
            previous=player.position();return alive;
        }
        boolean paid=state.consumeRaw(skill,kind==SHIELD?lerp(Balance.i("meltdowner_context", "tick_lerp_5"),Balance.i("meltdowner_context", "tick_lerp_6")):lerp(Balance.i("meltdowner_context", "tick_lerp_7"),Balance.i("meltdowner_context", "tick_lerp_8")),(kind==SHIELD?lerp(Balance.i("meltdowner_context","start_lerp_1"),Balance.i("meltdowner_context","start_lerp_2")):lerp(Balance.i("meltdowner_context","start_lerp_3"),Balance.i("meltdowner_context","start_lerp_4")))/20,player.isCreative());
        if(kind==BEAM)return paid && ticks<=Balance.i("meltdowner_context", "tick_threshold_1");
        state.addExperience(skill,Balance.f("meltdowner_context", "tick_add_experience_1"));
        for(var target:player.level().getEntities(player,player.getBoundingBox().inflate(Balance.i("meltdowner_context", "tick_inflate_1")),
                e->e.distanceToSqr(player)<=Balance.i("meltdowner_context", "tick_threshold_2") && reachable(e))) {
            if(target.invulnerableTime<=0 && state.consumeRaw(skill,lerp(Balance.i("meltdowner_context", "tick_lerp_9"),Balance.i("meltdowner_context", "tick_lerp_10")),lerp(Balance.i("meltdowner_context", "tick_lerp_11"),Balance.i("meltdowner_context", "tick_lerp_12")),player.isCreative())) {
                MDDamageHelper.attack(ctx,target,upgraded?MeltdownerSkillRules.curve(skill,"upgraded_damage",proficiency):lerp(Balance.i("meltdowner_context", "tick_lerp_13"),Balance.i("meltdowner_context", "tick_lerp_14")));state.addExperience(skill,Balance.f("meltdowner_context", "tick_add_experience_2"));
            }
        }
        return paid && ticks<=(upgraded?20*MeltdownerSkillRules.curve(skill,"upgraded_duration_seconds",proficiency):lerp(Balance.i("meltdowner_context", "tick_lerp_15"),Balance.i("meltdowner_context", "tick_lerp_16")));
    }
    /** Called by key-up. Only a successfully triggered jet keeps its context after release. */
    public boolean release(AbilityState state) {
        if(ended)return false;
        if(kind==BEAM && ticks>=Balance.i("meltdowner_context", "release_threshold_1")) {
            int charge=Math.min(ticks,Balance.i("meltdowner_context", "release_charge_1"));float rate=Balance.f("meltdowner_context", "release_rate_1")+(Balance.f("meltdowner_context", "release_rate_2")-Balance.f("meltdowner_context", "release_rate_3"))*((charge-Balance.f("meltdowner_context", "release_rate_4"))/Balance.f("meltdowner_context", "release_rate_5"));
            double[] length={30};
            var ray=new RangedRayDamage.Reflectible(ctx,upgraded?MeltdownerSkillRules.curve(skill,"upgraded_radius",proficiency):lerp(Balance.i("meltdowner_context", "release_lerp_1"),Balance.i("meltdowner_context", "release_lerp_2")),rate*lerp(Balance.i("meltdowner_context", "release_lerp_3"),Balance.i("meltdowner_context", "release_lerp_4")),reflector->{
                length[0]=Math.min(length[0],reflector.distanceToSqr(player));
                var result=ElectricAttackTrace.trace(reflector,Balance.i("meltdowner_context", "release_trace_1"),false);
                if(result instanceof EntityHitResult target) {
                    float reflected = upgraded ? MeltdownerSkillRules.curve(skill,"upgraded_damage",proficiency)
                            : SkillHitDamage.low(lerp(Balance.i("meltdowner_context","release_lerp_5"), Balance.i("meltdowner_context","release_lerp_6"))).against(target.getEntity());
                    ctx.attack(target.getEntity(),Balance.f("meltdowner_context","release_attack_1") * reflected);
                }
                send(REFLECT,reflector.getId(),Vec3.ZERO,10);
            });
            var beamDamage = upgraded ? SkillHitDamage.flat(MeltdownerSkillRules.curve(skill,"upgraded_damage",proficiency))
                    : SkillHitDamage.low(lerp(Balance.i("meltdowner_context","release_lerp_7"), Balance.i("meltdowner_context","release_lerp_8")));
            ray.startDamage = beamDamage.scaled(rate);
            ray.perform();
            state.addExperience(skill,rate*Balance.f("meltdowner_context", "release_add_experience_1"));state.finishContext(skill,(int)(rate*Balance.i("meltdowner_context", "release_finish_context_1")*lerp(Balance.i("meltdowner_context", "release_lerp_9"),Balance.i("meltdowner_context", "release_lerp_10"))),0);
            send(PERFORM,-1,Vec3.ZERO,length[0]);
        } else if(kind==JET) {
            if(triggering)return true;
            // Preserve s_onEnd's consume(consumption,overload) argument order: 50..60 CP and 140..170 overload.
            if(!state.consumeRaw(skill,lerp(Balance.i("meltdowner_context", "release_lerp_11"),Balance.i("meltdowner_context", "release_lerp_12")),lerp(Balance.i("meltdowner_context", "release_lerp_13"),Balance.i("meltdowner_context", "release_lerp_14")),player.isCreative()))return false;
            triggering=true;previous=player.position();
            send(TRIGGER,-1,ElectricAttackTrace.traceBlocks(player,12).getLocation(),0);
            state.addExperience(skill,Balance.f("meltdowner_context", "release_add_experience_2"));state.finishContext(skill,(int)lerp(Balance.i("meltdowner_context", "release_lerp_15"),Balance.i("meltdowner_context", "release_lerp_16")),0);return true;
        }
        return false;
    }
    public float absorb(AbilityState state,DamageSource source,float damage) {
        if(ended || kind!=SHIELD || damage==0 || lastAbsorb!=-1 && ticks-lastAbsorb<=Balance.i("meltdowner_context", "absorb_threshold_1"))return damage;
        var direct=source.getDirectEntity();float result=damage;
        if(direct==null || reachable(direct)) {
            lastAbsorb=ticks;
            // Touch and incoming attack use opposite original consume argument orders.
            if(state.consumeRaw(skill,lerp(Balance.i("meltdowner_context", "absorb_lerp_1"),Balance.i("meltdowner_context", "absorb_lerp_2")),lerp(Balance.i("meltdowner_context", "absorb_lerp_3"),Balance.i("meltdowner_context", "absorb_lerp_4")),player.isCreative()))result-=Math.min(damage,upgraded?MeltdownerSkillRules.curve(skill,"upgraded_absorption",proficiency):lerp(Balance.i("meltdowner_context", "absorb_lerp_5"),Balance.i("meltdowner_context", "absorb_lerp_6")));
        }
        state.addExperience(skill,Balance.f("meltdowner_context", "absorb_add_experience_1"));return result;
    }
    private boolean reachable(Entity target) {
        double yaw=-Math.toDegrees(Math.atan2(target.getX()-player.getX(),target.getZ()-player.getZ()));
        return Math.abs(yaw-player.getYRot())%360<60;
    }
    public void end(AbilityState state) {
        if(ended)return;ended=true;
        if(kind==SHIELD) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,Balance.i("meltdowner_context", "end_add_effect_1"),1));
            state.finishContext(skill,(int)lerp(Balance.i("meltdowner_context", "end_lerp_1")*ticks,ticks),0);
        }
        send(END,-1,Vec3.ZERO,0);audience.clear();
    }
    public boolean isShield(){return kind==SHIELD && !ended;}
    private float lerp(float a,float b){return a+(b-a)*exp;}
    private void send(int phase,int related,Vec3 target,double length) {
        var packet=new MeltdownerContextPayload(id,player.getId(),kind,phase,slot,request,related,target,length);
        audience.forEach(viewer->PacketDistributor.sendToPlayer(viewer,packet));
    }
}
