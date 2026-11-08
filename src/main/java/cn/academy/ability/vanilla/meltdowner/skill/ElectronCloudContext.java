package cn.academy.ability.vanilla.meltdowner.skill;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.entity.ACElectronBallEntity;
import cn.academy.network.MdRayEffectPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** SBContext and EMContext, with their distinct spawn/payment/termination ordering. */
public final class ElectronCloudContext {
    private final ServerPlayer player;
    private final AbilitySkill skill;
    private final AbilityContext ctx;
    private final boolean missile;
    private final float exp;
    private final List<ACElectronBallEntity> balls=new ArrayList<>();
    private final List<ServerPlayer> audience=new ArrayList<>();
    private int ticks;
    private boolean ended;
    public static boolean handles(AbilitySkill skill) {
        return skill.name().equals("scatter_bomb") || skill.name().equals("electron_missile");
    }
    public ElectronCloudContext(ServerPlayer player,AbilityState state,AbilitySkill skill) {
        this.player=player;this.skill=skill;ctx=AbilityContext.of(player,skill);
        missile=skill.name().equals("electron_missile");exp=state.experience(skill.id());
        audience.add(player);
        for(var other:player.serverLevel().players())if(other!=player && other.distanceToSqr(player)<=625)audience.add(other);
    }
    public void start(AbilityState state) {
        state.consumeRaw(skill,0,missile?200:lerp(80,60),player.isCreative());
    }
    public boolean tick(AbilityState state) {
        if(ended)return false;
        if(!missile) {
            ticks++;
            if(ticks<=80) {
                if(ticks>=20 && ticks%10==0)spawn();
                if(!state.consumeRaw(skill,lerp(3,6),0,player.isCreative()))return false;
            }
            if(ticks==200){player.hurt(player.damageSources().playerAttack(player),6);return false;}
            return true;
        }
        if(!state.consumeRaw(skill,lerp(12,5),0,player.isCreative()))return false;
        boolean alive=ticks<=(int)lerp(80,200);
        if(alive) {
            if(ticks%10==0 && balls.size()<5)spawn();
            if(ticks!=0 && ticks%8==0 && !balls.isEmpty()) {
                float range=lerp(5,13);
                var target=nearby(range,e->e instanceof LivingEntity).stream()
                        .min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                if(target!=null && state.consumeRaw(skill,lerp(60,25),lerp(9,4),player.isCreative())) {
                    var ball=balls.remove(player.getRandom().nextInt(balls.size()));
                    send(new MdRayEffectPayload(MdRayEffectPayload.RAY,player.getId(),ball.position(),target.getEyePosition()));
                    // The original missile damages the selected entity directly, without a second line-of-sight trace.
                    target.invulnerableTime=-1;MDDamageHelper.attack(ctx,target,lerp(10,18));
                    state.addExperience(skill,.001f);ball.discard();
                }
            }
        }
        send(new MdRayEffectPayload(MdRayEffectPayload.MISSILE_PARTICLES,player.getId(),Vec3.ZERO,Vec3.ZERO));
        ticks++;return alive;
    }
    private void spawn() {
        var ball=ACElectronBallEntity.createHeld(player,missile?2:1);
        player.serverLevel().addFreshEntity(ball);balls.add(ball);
    }
    private List<Entity> nearby(double radius,java.util.function.Predicate<Entity> filter) {
        return player.level().getEntities(player,player.getBoundingBox().inflate(radius),
                e->e.distanceToSqr(player)<=radius*radius && filter.test(e));
    }
    private void send(MdRayEffectPayload packet) {
        for(var viewer:audience)PacketDistributor.sendToPlayer(viewer,packet);
    }
    public void end(AbilityState state) {
        if(ended)return;ended=true;
        if(missile) {
            // Original MathUtils.clampi(700,400,exp.toInt), not lerpf: its result is always 700.
            state.finishContext(skill,700,0);
            balls.forEach(Entity::discard);balls.clear();return;
        }
        int auto=exp>.5f?(int)(balls.size()*exp):0;
        var targets=exp>.5f?nearby(5,e->e instanceof Mob):List.<Entity>of();
        for(var ball:balls) {
            var look=player.getLookAngle();
            float pitch=(float)Math.toRadians((player.getRandom().nextFloat()-.5f)*25);
            float yaw=(float)Math.toRadians((player.getRandom().nextFloat()-.5f)*25);
            Vec3 end=player.getEyePosition().add(look.scale(15)).add(look.xRot(pitch).yRot(yaw).scale(15));
            if(auto>0 && !targets.isEmpty()){end=targets.get(player.getRandom().nextInt(targets.size())).getEyePosition();auto--;}
            // EntityMdBall inherits Entity's default 1.8*.85 eye height, not the player's eye height.
            Vec3 start=ball.position().add(0,(double)(1.8f*.85f),0);
            var trace=ElectricAttackTrace.traceSegment(player,start,end,e->e!=player);
            if(trace instanceof EntityHitResult hit) {
                hit.getEntity().invulnerableTime=-1;MDDamageHelper.attack(ctx,hit.getEntity(),lerp(5,9));
            }
            var packet=new MdRayEffectPayload(MdRayEffectPayload.RAY,player.getId(),start,end);
            for(var viewer:player.serverLevel().players())if(viewer.distanceToSqr(player)<625)PacketDistributor.sendToPlayer(viewer,packet);
            ball.discard();
        }
        state.addExperience(skill,.001f*balls.size());balls.clear();
    }
    private float lerp(float a,float b){return a+(b-a)*exp;}
}
