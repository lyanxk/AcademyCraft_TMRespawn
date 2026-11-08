package cn.academy.ability.vanilla.vecmanip.skill;

import cn.academy.ability.*;
import cn.academy.network.StormWingPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.network.StormWingPayload.*;

/** Original StormWingContext server listeners; charge completion and flight velocity belong to the local context. */
public final class StormWingContext {
    private static int next;
    public final int id=++next,slot,request;
    private final ServerPlayer player;
    private final AbilitySkill skill;
    private final float exp;
    private final List<ServerPlayer> audience=new ArrayList<>();
    private boolean previousAllowFlying,active,applying,ended;
    public StormWingContext(ServerPlayer player,AbilityState state,AbilitySkill skill,int slot,int request) {
        this.player=player;this.skill=skill;this.slot=slot;this.request=request;exp=state.experience(skill.id());
        audience.add(player);for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=625)audience.add(viewer);
    }
    public static void reject(ServerPlayer player,AbilitySkill skill,int slot,int request) {
        if(skill.name().equals("storm_wing") && request>0)PacketDistributor.sendToPlayer(player,new StormWingPayload(0,player.getId(),END,slot,request,0,false));
    }
    public void start(){previousAllowFlying=player.getAbilities().mayfly;player.getAbilities().mayfly=true;send(START,false);}
    public boolean tick(AbilityState state) {
        if(ended)return false;player.resetFallDistance();
        if(state.experience(skill.id())<.15f) {
            var world=player.serverLevel();var ctx=AbilityContext.of(player,skill);
            for(int i=0;i<40;i++) {
                var pos=new BlockPos((int)(player.getX()-10+world.random.nextDouble()*20),(int)(player.getY()-10+world.random.nextDouble()*20),(int)(player.getZ()-10+world.random.nextDouble()*20));
                var block=world.getBlockState(pos);float hardness=block.getDestroySpeed(world,pos);
                if(!block.isAir() && hardness>=0 && hardness<=.3f && ctx.canBreakBlock(world,pos))world.removeBlock(pos,false);
                // Original positional WorldServer.playSound overload is empty.
            }
        }
        if(!active)return true;
        state.addExperience(skill,.00005f);
        return state.consumeRaw(skill,40+(25-40)*exp,10+(7-10)*exp,player.isCreative());
    }
    public void input(int phase,boolean value) {
        if(ended)return;
        if(phase==ACTIVE && !active) {
            active=true;burst(player,AbilityState.load(player).experience(skill.id()));send(ACTIVE,true);
        } else if(phase==APPLYING){applying=value;send(APPLYING,true);}
    }
    public static void burst(Player player,float experience) {
        if(experience!=1f)return;
        for(var entity:player.level().getEntities(player,player.getBoundingBox().inflate(6),e->e.distanceToSqr(player)<=36)) {
            Vec3 delta=entity.getEyePosition().subtract(player.position()).scale(.9+player.getRandom().nextDouble()*.3);
            entity.setDeltaMovement(delta.normalize().scale(.5+player.getRandom().nextDouble()*.5));entity.hurtMarked=true;
        }
    }
    public boolean active(){return active;}
    public void end(AbilityState state) {
        if(ended)return;ended=true;player.getAbilities().mayfly=previousAllowFlying;
        state.finishContext(skill,(int)(30+(10-30)*state.experience(skill.id())),0);send(END,false);audience.clear();
    }
    private void send(int phase,boolean excludeLocal) {
        var packet=new StormWingPayload(id,player.getId(),phase,slot,request,exp,applying);
        for(var viewer:audience)if(!excludeLocal || viewer!=player)PacketDistributor.sendToPlayer(viewer,packet);
    }
}
