package cn.academy.ability.vanilla.meltdowner.skill;

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
    private static final List<MeltdownerContext> SHIELDS=new ArrayList<>();
    public final int id=NEXT.incrementAndGet();
    private final ServerPlayer player;
    private final AbilitySkill skill;
    private final AbilityContext ctx;
    private final int kind,slot,request;
    private final float exp;
    private final List<ServerPlayer> audience=new ArrayList<>();
    private int ticks,lastAbsorb=-1;
    private boolean triggering,ended;
    private Vec3 previous;
    public static boolean handles(AbilitySkill skill) {
        return switch(skill.name()){case "light_shield","meltdowner","jet_engine"->true;default->false;};
    }
    public MeltdownerContext(ServerPlayer player,AbilityState state,AbilitySkill skill,int slot,int request) {
        this.player=player;this.skill=skill;this.slot=slot;this.request=request;
        exp=state.experience(skill.id());ctx=AbilityContext.of(player,skill);
        kind=skill.name().equals("light_shield")?SHIELD:skill.name().equals("meltdowner")?BEAM:JET;
        audience.add(player);for(var viewer:player.serverLevel().players())
            if(viewer!=player && viewer.distanceToSqr(player)<=625)audience.add(viewer);
        previous=player.position();
    }
    public void start(AbilityState state) {
        if(kind==SHIELD)SHIELDS.add(this);
        if(kind!=JET)state.consumeRaw(skill,0,kind==SHIELD?lerp(110,60):lerp(200,170),player.isCreative());
        send(START,-1,Vec3.ZERO,0);
    }
    public boolean tick(AbilityState state) {
        if(ended)return false;
        ticks++;
        if(kind==JET) {
            boolean alive=player.isCreative() || state.cp()>=lerp(170,140);
            if(triggering) {
                var hit=ElectricAttackTrace.traceSegment(player,previous,player.position(),e->e!=player && e instanceof LivingEntity);
                if(player.isPassenger())player.stopRiding();
                if(hit instanceof EntityHitResult entity)MDDamageHelper.attack(ctx,entity.getEntity(),lerp(7,20));
            }
            previous=player.position();return alive;
        }
        boolean paid=state.consumeRaw(skill,kind==SHIELD?lerp(9,4):lerp(10,15),0,player.isCreative());
        if(kind==BEAM)return paid && ticks<=100;
        state.addExperience(skill,1e-6f);
        for(var target:player.level().getEntities(player,player.getBoundingBox().inflate(3),
                e->e.distanceToSqr(player)<=9 && reachable(e))) {
            if(target.invulnerableTime<=0 && state.consumeRaw(skill,lerp(50,30),lerp(5,3),player.isCreative())) {
                MDDamageHelper.attack(ctx,target,lerp(2,6));state.addExperience(skill,.001f);
            }
        }
        return paid && ticks<=lerp(120,180);
    }
    /** Called by key-up. Only a successfully triggered jet keeps its context after release. */
    public boolean release(AbilityState state) {
        if(ended)return false;
        if(kind==BEAM && ticks>=20) {
            int charge=Math.min(ticks,40);float rate=.8f+(1.2f-.8f)*((charge-20f)/20f);
            double[] length={30};
            var ray=new RangedRayDamage.Reflectible(ctx,lerp(2,3),rate*lerp(300,700),reflector->{
                length[0]=Math.min(length[0],reflector.distanceToSqr(player));
                var result=ElectricAttackTrace.trace(reflector,10,false);
                if(result instanceof EntityHitResult target)ctx.attack(target.getEntity(),.5f*lerp(20,50));
                send(REFLECT,reflector.getId(),Vec3.ZERO,10);
            });
            ray.startDamage=rate*lerp(18,50);ray.perform();
            state.addExperience(skill,rate*.002f);state.finishContext(skill,(int)(rate*20*lerp(15,7)),0);
            send(PERFORM,-1,Vec3.ZERO,length[0]);
        } else if(kind==JET) {
            if(triggering)return true;
            // Preserve s_onEnd's consume(consumption,overload) argument order: 50..60 CP and 140..170 overload.
            if(!state.consumeRaw(skill,lerp(60,50),lerp(170,140),player.isCreative()))return false;
            triggering=true;previous=player.position();
            send(TRIGGER,-1,ElectricAttackTrace.traceBlocks(player,12).getLocation(),0);
            state.addExperience(skill,.004f);state.finishContext(skill,(int)lerp(60,30),0);return true;
        }
        return false;
    }
    public float absorb(AbilityState state,DamageSource source,float damage) {
        if(ended || kind!=SHIELD || damage==0 || lastAbsorb!=-1 && ticks-lastAbsorb<=18)return damage;
        var direct=source.getDirectEntity();float result=damage;
        if(direct==null || reachable(direct)) {
            lastAbsorb=ticks;
            // Touch and incoming attack use opposite original consume argument orders.
            if(state.consumeRaw(skill,lerp(5,3),lerp(50,30),player.isCreative()))result-=Math.min(damage,lerp(15,50));
        }
        state.addExperience(skill,.001f);return result;
    }
    public static float absorbFirstShield(DamageSource source,float damage) {
        // Original ContextManager.find(LSContext.class) searches all server contexts, without victim ownership.
        for(var shield:SHIELDS)if(shield.isShield())return shield.absorb(AbilityState.load(shield.player),source,damage);
        return damage;
    }
    private boolean reachable(Entity target) {
        double yaw=-Math.toDegrees(Math.atan2(target.getX()-player.getX(),target.getZ()-player.getZ()));
        return Math.abs(yaw-player.getYRot())%360<60;
    }
    public void end(AbilityState state) {
        if(ended)return;ended=true;
        if(kind==SHIELD) {
            SHIELDS.remove(this);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,100,1));
            state.finishContext(skill,(int)lerp(2*ticks,ticks),0);
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
