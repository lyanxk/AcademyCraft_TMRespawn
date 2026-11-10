package cn.academy.ability.vanilla.meltdowner.skill;

import cn.academy.data.Balance;

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
    private final boolean missile,upgraded;
    private final float exp,proficiency;
    private final List<ACElectronBallEntity> balls=new ArrayList<>();
    private final List<ServerPlayer> audience=new ArrayList<>();
    private int ticks;
    private boolean ended;
    public static boolean handles(AbilitySkill skill) {
        return skill.name().equals("scatter_bomb") || skill.name().equals("electron_missile");
    }
    public ElectronCloudContext(ServerPlayer player,AbilityState state,AbilitySkill skill) {
        this.player=player;this.skill=skill;ctx=AbilityContext.of(player,skill);
        missile=skill.name().equals("electron_missile");proficiency=state.experience(skill.id());
        upgraded=MeltdownerSkillRules.upgraded(state,skill);exp=upgraded?1:proficiency;
        audience.add(player);
        for(var other:player.serverLevel().players())if(other!=player && other.distanceToSqr(player)<=Balance.i("electron_cloud_context", "electron_cloud_context_threshold_1"))audience.add(other);
    }
    public void start(AbilityState state) {
        // Continuous costs are paid by tick().
    }
    public boolean tick(AbilityState state) {
        if(ended)return false;
        if(!missile) {
            ticks++;
            if(ticks<=Balance.i("electron_cloud_context", "tick_threshold_1")) {
                int first=Balance.i("electron_cloud_context","tick_threshold_2"),last=Balance.i("electron_cloud_context","tick_threshold_1");
                if(upgraded) {
                    int count=(int)MeltdownerSkillRules.curve(skill,"upgraded_ball_count",proficiency);
                    int due=ticks<first?0:1+(ticks-first)*(count-1)/(last-first);
                    while(balls.size()<due)spawn();
                } else if(ticks>=first && ticks%10==0)spawn();
            }
            float cp=ticks<=Balance.i("electron_cloud_context","tick_threshold_1")
                    ?(upgraded?MeltdownerSkillRules.curve(skill,"upgraded_cp_per_second",proficiency)/20:lerp(Balance.i("electron_cloud_context","tick_lerp_1"),Balance.i("electron_cloud_context","tick_lerp_2"))):0;
            if(!state.consumeRaw(skill,cp,lerp(Balance.i("electron_cloud_context","start_lerp_1"),Balance.i("electron_cloud_context","start_lerp_2"))/20,player.isCreative()))return false;
            if(ticks==200){player.hurt(player.damageSources().playerAttack(player),Balance.i("electron_cloud_context", "tick_hurt_1"));return false;}
            return true;
        }
        if(!state.consumeRaw(skill,lerp(Balance.i("electron_cloud_context", "tick_lerp_3"),Balance.i("electron_cloud_context", "tick_lerp_4")),Balance.f("electron_cloud_context","start_consume_raw_1")/20,player.isCreative()))return false;
        boolean alive=ticks<=(int)lerp(Balance.i("electron_cloud_context", "tick_lerp_5"),Balance.i("electron_cloud_context", "tick_lerp_6"));
        if(alive) {
            if(ticks%10==0 && balls.size()<5)spawn();
            if(ticks!=0 && ticks%8==0 && !balls.isEmpty()) {
                float range=lerp(Balance.i("electron_cloud_context", "tick_lerp_7"),Balance.i("electron_cloud_context", "tick_lerp_8"));
                var target=nearby(range,e->e instanceof LivingEntity).stream()
                        .min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                if(target!=null && state.consumeRaw(skill,lerp(Balance.i("electron_cloud_context", "tick_lerp_9"),Balance.i("electron_cloud_context", "tick_lerp_10")),lerp(Balance.i("electron_cloud_context", "tick_lerp_11"),Balance.i("electron_cloud_context", "tick_lerp_12")),player.isCreative())) {
                    var ball=balls.remove(player.getRandom().nextInt(balls.size()));
                    send(new MdRayEffectPayload(MdRayEffectPayload.RAY,player.getId(),ball.position(),target.getEyePosition()));
                    // The original missile damages the selected entity directly, without a second line-of-sight trace.
                    target.invulnerableTime=-1;MDDamageHelper.attack(ctx,target,lerp(Balance.i("electron_cloud_context", "tick_lerp_13"),Balance.i("electron_cloud_context", "tick_lerp_14")));
                    state.addExperience(skill,Balance.f("electron_cloud_context", "tick_add_experience_1"));ball.discard();
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
            state.finishContext(skill,Balance.i("electron_cloud_context", "end_finish_context_1"),0);
            balls.forEach(Entity::discard);balls.clear();return;
        }
        int auto=exp>Balance.f("electron_cloud_context", "end_threshold_1")?(int)(balls.size()*exp):0;
        var targets=exp>Balance.f("electron_cloud_context", "end_threshold_2")?nearby(upgraded?MeltdownerSkillRules.curve(skill,"upgraded_auto_range",proficiency):5,e->e instanceof Mob):List.<Entity>of();
        for(var ball:balls) {
            var look=player.getLookAngle();
            float pitch=(float)Math.toRadians((player.getRandom().nextFloat()-.5f)*25);
            float yaw=(float)Math.toRadians((player.getRandom().nextFloat()-.5f)*25);
            Vec3 end=player.getEyePosition().add(look.scale(Balance.i("electron_cloud_context", "end_scale_1"))).add(look.xRot(pitch).yRot(yaw).scale(Balance.i("electron_cloud_context", "end_scale_2")));
            if(auto>0 && !targets.isEmpty()){end=targets.get(player.getRandom().nextInt(targets.size())).getEyePosition();auto--;}
            // EntityMdBall inherits Entity's default 1.8*.85 eye height, not the player's eye height.
            Vec3 start=ball.position().add(0,(double)(1.8f*.85f),0);
            var trace=ElectricAttackTrace.traceSegment(player,start,end,e->e!=player);
            if(trace instanceof EntityHitResult hit) {
                hit.getEntity().invulnerableTime=-1;MDDamageHelper.attack(ctx,hit.getEntity(),upgraded?MeltdownerSkillRules.curve(skill,"upgraded_damage",proficiency):lerp(Balance.i("electron_cloud_context", "end_lerp_1"),Balance.i("electron_cloud_context", "end_lerp_2")));
            }
            var packet=new MdRayEffectPayload(MdRayEffectPayload.RAY,player.getId(),start,end);
            for(var viewer:player.serverLevel().players())if(viewer.distanceToSqr(player)<Balance.i("electron_cloud_context", "end_threshold_3"))PacketDistributor.sendToPlayer(viewer,packet);
            ball.discard();
        }
        state.addExperience(skill,Balance.f("electron_cloud_context", "end_add_experience_1")*balls.size());balls.clear();
    }
    private float lerp(float a,float b){return a+(b-a)*exp;}
}
