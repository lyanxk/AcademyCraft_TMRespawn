package cn.academy.ability.vanilla.vecmanip.skill;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.network.VectorImpulsePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.network.VectorImpulsePayload.*;

/** Server listeners for VecAccelContext and BloodRetroContext; movement and target selection originate locally. */
public final class VectorImpulseContext {
    private static int next;
    public final int id=++next,slot,request,kind;
    private final ServerPlayer player;
    private final AbilitySkill skill;
    private final float exp;
    private final List<ServerPlayer> audience=new ArrayList<>();
    private boolean performed,ended;
    public static boolean handles(AbilitySkill skill){return skill.name().equals("vec_accel") || skill.name().equals("blood_retro");}
    public static int kind(AbilitySkill skill){return skill.name().equals("vec_accel")?ACCEL:BLOOD;}
    public static void reject(ServerPlayer player,AbilitySkill skill,int slot,int request) {
        if(handles(skill) && request>0)PacketDistributor.sendToPlayer(player,new VectorImpulsePayload(0,player.getId(),kind(skill),END,slot,request,0,-1));
    }
    public VectorImpulseContext(ServerPlayer player,AbilityState state,AbilitySkill skill,int slot,int request) {
        this.player=player;this.skill=skill;this.slot=slot;this.request=request;kind=kind(skill);exp=state.experience(skill.id());
        audience.add(player);for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=625)audience.add(viewer);
    }
    public void start(){send(START,-1);}
    public void perform(AbilityState state,int targetId) {
        if(ended || performed)return;
        if(kind==ACCEL) {
            // Original server listener ignores the payment result; local prediction has already paid and moved.
            state.consumeRaw(skill,snapshot(120,80),live(state,30,15),player.isCreative());
            player.resetFallDistance();
            // This port snapshots cooldowns with AbilityState; mirror the local cooldown before the next state sync.
            state.finishContext(skill,(int)live(state,80,50),0);state.addExperience(skill,.002f);
            performed=true;send(PERFORM,-1);
        } else if(player.serverLevel().getEntity(targetId) instanceof LivingEntity target) {
            if(state.consumeRaw(skill,live(state,280,350),live(state,55,40),player.isCreative())) {
                state.finishContext(skill,(int)live(state,90,40),0);send(PERFORM,targetId);
                attackBloodRetrograde(AbilityContext.of(player,skill),target,exp);state.addExperience(skill,.002f);performed=true;
            }
        }
    }
    public static void attackBloodRetrograde(AbilityContext context,LivingEntity target,float experience) {
        float proficiency=Math.clamp(experience,0f,1f);
        float damage=40+40*proficiency;
        // Combine the bonus into one hit so damage immunity frames cannot discard it.
        if(proficiency>=1f)damage+=target.getMaxHealth();
        if(proficiency>=.5f)context.attackIgnoreArmor(target,damage);
        else context.attack(target,damage);
    }
    public void releaseWithoutClient(AbilityState state) {
        if(kind==BLOOD) {
            var hit=ElectricAttackTrace.trace(player,2,false);
            if(hit instanceof EntityHitResult entity && entity.getEntity() instanceof LivingEntity)perform(state,entity.getEntity().getId());
        }
    }
    public boolean performed(){return performed;}
    public void end(){if(ended)return;ended=true;send(END,-1);audience.clear();}
    private float snapshot(float a,float b){return a+(b-a)*exp;}
    private float live(AbilityState state,float a,float b){return a+(b-a)*state.experience(skill.id());}
    private void send(int phase,int target){var packet=new VectorImpulsePayload(id,player.getId(),kind,phase,slot,request,exp,target);audience.forEach(p->PacketDistributor.sendToPlayer(p,packet));}
}
