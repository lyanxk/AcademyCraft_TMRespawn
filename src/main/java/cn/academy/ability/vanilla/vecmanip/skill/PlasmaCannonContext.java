package cn.academy.ability.vanilla.vecmanip.skill;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.network.PlasmaCannonPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.network.PlasmaCannonPayload.*;

/** PlasmaCannon.scala server listeners: flight remains part of the original context after key-up. */
public final class PlasmaCannonContext {
    private static int next;
    public final int id=++next,slot,request;
    private final ServerPlayer player;
    private final AbilitySkill skill;
    private final float exp,chargeTime;
    private final List<ServerPlayer> audience=new ArrayList<>();
    private int ticks,syncTicks;
    private boolean flying,finished,ended;
    private Vec3 position,destination;
    public PlasmaCannonContext(ServerPlayer player,AbilityState state,AbilitySkill skill,int slot,int request) {
        this.player=player;this.skill=skill;this.slot=slot;this.request=request;
        exp=state.experience(skill.id());chargeTime=chargeTime(exp);position=chargePosition(player);
        audience.add(player);for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=625)audience.add(viewer);
    }
    public static float chargeTime(float experience){return 200-100*experience;}
    public static float damage(Entity target,float experience) {
        return 300+700*experience+(target instanceof LivingEntity living?living.getMaxHealth()*(.05f+.05f*experience):0);
    }
    public static float explosionPower(float experience){return 36+9*experience;}
    public static Vec3 chargePosition(Entity player){return new Vec3(player.getX(),player.getBoundingBox().maxY+3.5,player.getZ());}
    public static void reject(ServerPlayer player,AbilitySkill skill,int slot,int request) {
        if(skill.name().equals("plasma_cannon") && request>0)PacketDistributor.sendToPlayer(player,new PlasmaCannonPayload(0,player.getId(),END,slot,request,0,Vec3.ZERO));
    }
    public void start(AbilityState state) {
        state.consumeRaw(skill,0,500+(400-500)*exp,player.isCreative());send(START,position);
    }
    public boolean active(){return flying || ticks>=chargeTime;}
    public int chargeTicks(){return (int)Math.ceil(chargeTime);}
    public boolean performed(){return flying;}
    public boolean releaseWithoutClient(AbilityState state){if(ticks<chargeTime)return false;perform(state);return true;}
    public void perform(AbilityState state) {
        if(flying || finished || ended)return;
        state.addExperience(skill,.008f);
        Vec3 eye=player.getEyePosition();
        var hit=ElectricAttackTrace.traceSegment(player,eye,eye.add(player.getLookAngle().scale(100)),e->e!=player && e instanceof LivingEntity);
        destination=hit.getLocation();
        // LambdaLib2 getLookingPos offsets an entity hit from its feet by 60% of its eye height.
        if(hit instanceof EntityHitResult entityHit)destination=destination.add(0,entityHit.getEntity().getEyeHeight()*.6,0);
        flying=true;ticks=0;
        state.finishContext(skill,(int)(1000+(600-1000)*state.experience(skill.id())),0);
        send(GO,destination);
    }
    public boolean tick(AbilityState state) {
        if(finished || ended)return false;
        ticks++;
        if(!flying) {
            return ticks>=chargeTime || state.consumeRaw(skill,18+(25-18)*state.experience(skill.id()),0,player.isCreative());
        }
        Vec3 previous=position;position=move(position,destination);
        if(ElectricAttackTrace.traceSegment(player,previous,position,e->true).getType()!=HitResult.Type.MISS)explode(state);
        // Two independent original checks: a collision at arrival can execute both explosions.
        if(ticks>=240 || position.distanceTo(destination)<1.5)explode(state);
        if(syncTicks==0){syncTicks=5;send(POSITION,position);}else syncTicks--;
        return !finished;
    }
    public static Vec3 move(Vec3 from,Vec3 to){var delta=to.subtract(from);return delta.length()<1?from:from.add(delta.normalize());}
    private void explode(AbilityState state) {
        var world=player.serverLevel();var ctx=AbilityContext.of(player,skill);
        for(var entity:world.getEntities((Entity)null,new AABB(destination,destination).inflate(10),e->e.distanceToSqr(destination)<=100)) {
            ctx.attack(entity,damage(entity,state.experience(skill.id())));entity.invulnerableTime=-1;
        }
        float radius=explosionPower(state.experience(skill.id()));
        var explosion=new PlasmaExplosion(player,destination,radius,ctx.canBreakBlock(world));
        // Do not use Level.explode(NONE): that would still damage/knock back entities when breaking is disabled.
        if(ctx.canBreakBlock(world))explosion.explode();
        explosion.finalizeExplosion(true);
        // The original manually invokes the phases, without WorldServer.newExplosion's explosion packet.
        finished=true;
    }
    public void end(){if(ended)return;ended=true;send(END,position);audience.clear();}
    private void send(int phase,Vec3 point){var packet=new PlasmaCannonPayload(id,player.getId(),phase,slot,request,exp,point);for(var viewer:audience)PacketDistributor.sendToPlayer(viewer,packet);}
}
