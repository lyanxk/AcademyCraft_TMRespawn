package cn.academy.ability.vanilla.teleporter.skill;

import cn.academy.data.GameplayRules;

import cn.academy.data.Balance;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.ability.vanilla.teleporter.util.TPSkillHelper;
import cn.academy.network.ThreateningTeleportPayload;
import cn.academy.registry.ACItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.network.ThreateningTeleportPayload.*;

/** Original TTContext, including target-first wall bypass and copying the stack AFTER shrinking. */
public final class ThreateningTeleportContext {
    private static int next;
    public final int id=++next,slot,request;
    private final ServerPlayer player;private final AbilitySkill skill;private final float experience;
    private final List<ServerPlayer> audience=new ArrayList<>();private boolean performed,ended;
    public record Target(Vec3 point,Entity entity){}
    public ThreateningTeleportContext(ServerPlayer player,AbilityState state,AbilitySkill skill,int slot,int request) {
        this.player=player;this.skill=skill;this.slot=slot;this.request=request;experience=state.experience(skill.id());
        audience.add(player);for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=Balance.i("threatening_teleport_context", "threatening_teleport_context_threshold_1"))audience.add(viewer);
    }
    public static void reject(ServerPlayer player,AbilitySkill skill,int slot,int request) {
        if(skill.name().equals("threatening_teleport") && request>0)PacketDistributor.sendToPlayer(player,new ThreateningTeleportPayload(0,player.getId(),END,slot,request,0,false));
    }
    public static Target target(Player player,float experience) {
        Vec3 start=player.getEyePosition(),end=start.add(player.getLookAngle().scale(Balance.i("threatening_teleport_context", "target_scale_1")+(Balance.i("threatening_teleport_context", "target_scale_2")-Balance.i("threatening_teleport_context", "target_scale_3"))*experience));
        var hit=ElectricAttackTrace.traceSegmentFiltered(player,start,end,e->e!=player && e instanceof LivingEntity,s->false);
        if(hit instanceof EntityHitResult entityHit){var entity=entityHit.getEntity();return new Target(entity.position().add(0,entity.getBbHeight(),0),entity);}
        return new Target(ElectricAttackTrace.traceBlockSegment(player,start,end,null).getLocation(),null);
    }
    public void start(){send(START);}
    public boolean tick(){return !player.getMainHandItem().isEmpty();}
    public void release(AbilityState state) {
        if(ended)return;var stack=player.getMainHandItem();
        if(!stack.isEmpty() && state.consumeRaw(skill,Balance.i("threatening_teleport_context", "release_consume_raw_1")+(Balance.i("threatening_teleport_context", "release_consume_raw_2")-Balance.i("threatening_teleport_context", "release_consume_raw_3"))*experience,Balance.i("threatening_teleport_context", "release_consume_raw_4")+(Balance.i("threatening_teleport_context", "release_consume_raw_5")-Balance.i("threatening_teleport_context", "release_consume_raw_6"))*experience,player.isCreative())) {
            performed=true;var target=target(player,experience);
            if(target.entity!=null) {
                float damage=(Balance.i("threatening_teleport_context", "release_damage_1")+(Balance.i("threatening_teleport_context", "release_damage_2")-Balance.i("threatening_teleport_context", "release_damage_3"))*state.experience(skill.id()))*(stack.is(GameplayRules.itemTag("threatening_teleport_damage_boost"))?Balance.f("threatening_teleport_context", "release_damage_4"):1);
                TPSkillHelper.attack(AbilityContext.of(player,skill),target.entity,damage,true);
            }
            if(!player.isCreative())stack.shrink(1);
            if(player.getRandom().nextDouble()<(target.entity==null?1:.3)) {
                // In 1.12 copy() of a now-empty stack stays empty even after setCount(1).
                var drop=stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount(1);
                player.serverLevel().addFreshEntity(new ItemEntity(player.level(),target.point.x,target.point.y,target.point.z,drop));
            }
            state.addExperience(skill,(target.entity==null?Balance.f("threatening_teleport_context", "release_add_experience_1"):Balance.i("threatening_teleport_context", "release_add_experience_2"))*Balance.f("threatening_teleport_context", "release_add_experience_3"));
            state.finishContext(skill,(int)(Balance.i("threatening_teleport_context", "release_finish_context_1")+(Balance.i("threatening_teleport_context", "release_finish_context_2")-Balance.i("threatening_teleport_context", "release_finish_context_3"))*experience),0);
        }
        send(EFFECT);
    }
    public boolean performed(){return performed;}
    public void end(){if(ended)return;ended=true;send(END);audience.clear();}
    private void send(int phase){var packet=new ThreateningTeleportPayload(id,player.getId(),phase,slot,request,experience,performed);for(var viewer:audience)PacketDistributor.sendToPlayer(viewer,packet);}
}
