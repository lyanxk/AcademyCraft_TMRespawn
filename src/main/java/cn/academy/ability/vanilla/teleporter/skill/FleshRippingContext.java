package cn.academy.ability.vanilla.teleporter.skill;

import cn.academy.data.Balance;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.ability.vanilla.teleporter.util.TPSkillHelper;
import cn.academy.network.FleshRippingPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.network.FleshRippingPayload.*;

/** Original FRContext: cache the server target each tick; force consumption and critical attack on key-up. */
public final class FleshRippingContext {
    private static int next;
    public final int id=++next,slot,request;
    private final ServerPlayer player;private final AbilitySkill skill;private final float experience,cost;
    private final List<ServerPlayer> audience=new ArrayList<>();
    private Entity target;private boolean sampled,performed,ended;
    public FleshRippingContext(ServerPlayer player,AbilityState state,AbilitySkill skill,int slot,int request) {
        this.player=player;this.skill=skill;this.slot=slot;this.request=request;
        experience=state.experience(skill.id());cost=130+(270-130)*experience;
        audience.add(player);for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=Balance.i("flesh_ripping_context", "flesh_ripping_context_threshold_1"))audience.add(viewer);
    }
    public static void reject(ServerPlayer player,AbilitySkill skill,int slot,int request) {
        if(skill.name().equals("flesh_ripping") && request>0)PacketDistributor.sendToPlayer(player,new FleshRippingPayload(0,player.getId(),END,slot,request,0,-1));
    }
    public static HitResult target(Player player,float experience) {
        Vec3 eye=player.getEyePosition();double range=Balance.i("flesh_ripping_context", "target_range_1")+(Balance.i("flesh_ripping_context", "target_range_2")-Balance.i("flesh_ripping_context", "target_range_3"))*experience;
        return ElectricAttackTrace.traceSegment(player,eye,eye.add(player.getLookAngle().scale(range)),e->e!=player && e instanceof LivingEntity);
    }
    private void sample(){var hit=target(player,experience);target=hit instanceof EntityHitResult entityHit?entityHit.getEntity():null;sampled=true;}
    public void start(){send(START,-1);}
    public boolean tick(AbilityState state){sample();return player.isCreative() || state.cp()>=cost;}
    public void release(AbilityState state) {
        if(ended)return;
        // Original target is null before the first server tick; avoid a same-tick release NPE.
        if(!sampled)sample();
        send(EFFECT,target==null?-1:target.getId());
        if(target==null)return;
        state.consumeForce(skill,cost,Balance.i("flesh_ripping_context", "release_consume_force_1")+(Balance.i("flesh_ripping_context", "release_consume_force_2")-Balance.i("flesh_ripping_context", "release_consume_force_3"))*experience,player.isCreative());
        TPSkillHelper.attack(AbilityContext.of(player,skill),target,Balance.i("flesh_ripping_context", "release_attack_1")+(Balance.i("flesh_ripping_context", "release_attack_2")-Balance.i("flesh_ripping_context", "release_attack_3"))*experience,true);
        if(player.getRandom().nextDouble()<.05f)player.addEffect(new MobEffectInstance(MobEffects.CONFUSION,Balance.i("flesh_ripping_context", "release_add_effect_1")));
        state.finishContext(skill,(int)(Balance.i("flesh_ripping_context", "release_finish_context_1")+(Balance.i("flesh_ripping_context", "release_finish_context_2")-Balance.i("flesh_ripping_context", "release_finish_context_3"))*experience),Balance.f("flesh_ripping_context", "release_finish_context_4"));performed=true;
    }
    public boolean performed(){return performed;}
    public void end(){if(ended)return;ended=true;send(END,-1);audience.clear();}
    private void send(int phase,int target){var packet=new FleshRippingPayload(id,player.getId(),phase,slot,request,experience,target);for(var viewer:audience)PacketDistributor.sendToPlayer(viewer,packet);}
}
