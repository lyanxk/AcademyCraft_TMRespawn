package cn.academy.ability.vanilla.teleporter.skill;

import cn.academy.data.Balance;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.advancement.ACAdvancements;
import cn.academy.network.TargetTeleportPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.network.TargetTeleportPayload.*;

/** Original PTContext and MTContext, sharing only their targeting preview transport. */
public final class TargetTeleportContext {
    public static final int PENETRATE=0,MARK=1;
    private static int next;
    public final int id=++next,kind,slot,request;
    private final ServerPlayer player;private final AbilitySkill skill;private final float experience;
    private final List<ServerPlayer> audience=new ArrayList<>();
    private int ticks;private float selected;private boolean ended,performed;
    public TargetTeleportContext(ServerPlayer player,AbilityState state,AbilitySkill skill,int slot,int request) {
        this.player=player;this.skill=skill;this.slot=slot;this.request=request;
        kind=skill.name().equals("penetrate_teleport")?PENETRATE:MARK;experience=state.experience(skill.id());selected=maximumRange(state,experience);
        audience.add(player);for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=Balance.i("target_teleport_context", "target_teleport_context_threshold_1"))audience.add(viewer);
    }
    public static boolean handles(AbilitySkill skill){return skill.name().equals("penetrate_teleport") || skill.name().equals("mark_teleport");}
    public static void reject(ServerPlayer player,AbilitySkill skill,int slot,int request) {
        if(handles(skill) && request>0)PacketDistributor.sendToPlayer(player,new TargetTeleportPayload(0,player.getId(),END,slot,request,0,0));
    }
    public static float maximumRange(AbilityState state,float experience) {
        float start=Balance.f("target_teleport_context","initial_range_start");
        float end=Balance.f("target_teleport_context",state.level()>=Balance.i("target_teleport_context","upgrade_level")
                ?"upgraded_max_range":"initial_range_end");
        return start+(end-start)*experience;
    }
    public void start(){send(START);}
    public void tick(){ticks++;}
    public float selected(){return selected;}
    public void wheel(float delta){float proposed=selected+delta;if(proposed>=.5f && proposed<=maximumRange(AbilityState.load(player),experience))selected=proposed;}
    public record Destination(Vec3 position,boolean available){}
    public static Destination penetrate(Player player,float selected,float liveExperience,float cp) {
        double range=Math.min(selected,cp/(Balance.i("target_teleport_context", "penetrate_range_1")+(Balance.i("target_teleport_context", "penetrate_range_2")-Balance.i("target_teleport_context", "penetrate_range_3"))*liveExperience));
        return penetrateAtRange(player,range);
    }
    public static Destination penetrateAtRange(Player player,double range) {
        Vec3 position=player.position(),direction=player.getLookAngle().normalize();int stage=0,counter=0;double travelled=0;
        while(travelled<=range) {
            BlockPos feet=new BlockPos((int)position.x,(int)position.y,(int)position.z);
            boolean open=passable(player,feet) && passable(player,feet.above());
            if(stage==0){if(!open)stage=1;}else if(stage==1){if(open)stage=2;}
            else {counter++;if(!open || counter>4)break;}
            travelled+=.8;position=position.add(direction.scale(Balance.d("target_teleport_context", "penetrate_at_range_scale_1")));
        }
        return new Destination(position,stage!=1);
    }
    private static boolean passable(Player player,BlockPos pos) {
        var state=player.level().getBlockState(pos);
        // 1.12 Block.canCollideCheck defaults to true even for noncolliding plants.
        // Air/liquids/fire override it; an empty collision shape is not equivalent.
        return state.isAir() || state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof BaseFireBlock;
    }
    public static Vec3 mark(Player player,int ticks,float liveExperience,float cp) {
        double range=Math.min((ticks+1)*Balance.i("target_teleport_context", "mark_range_1"),Math.min(Balance.i("target_teleport_context", "mark_range_2")+(Balance.i("target_teleport_context", "mark_range_3")-Balance.i("target_teleport_context", "mark_range_4"))*liveExperience,cp/(Balance.i("target_teleport_context", "mark_range_5")+(Balance.i("target_teleport_context", "mark_range_6")-Balance.i("target_teleport_context", "mark_range_7"))*liveExperience)));
        return markAtRange(player,range);
    }
    public static Vec3 markAtRange(Player player,double range) {
        var hit=ElectricAttackTrace.trace(player,range,false);
        if(hit.getType()==HitResult.Type.MISS)return player.getEyePosition().add(player.getLookAngle().scale(range));
        Vec3 point=hit.getLocation();double x=point.x,y=point.y,z=point.z;
        if(hit instanceof EntityHitResult entity)y+=entity.getEntity().getEyeHeight();
        else if(hit instanceof BlockHitResult block) {
            switch(block.getDirection()) {
                case DOWN->y-=1;
                case UP->y+=1.8;
                case NORTH->{z-=.6;y=block.getBlockPos().getY()+1.7;}
                case SOUTH->{z+=.6;y=block.getBlockPos().getY()+1.7;}
                case WEST->{x-=.6;y=block.getBlockPos().getY()+1.7;}
                case EAST->{x+=.6;y=block.getBlockPos().getY()+1.7;}
            }
            if(block.getDirection().get3DDataValue()>1 && !player.level().getBlockState(new BlockPos((int)x,(int)(y+1),(int)z)).isAir())y-=1.25;
        }
        return new Vec3(x,y,z);
    }
    public void release(AbilityState state,float clientDistance) {
        if(ended)return;
        if(kind==PENETRATE) {
            // Clients send their selected distance, not the CP-limited preview result.
            selected=clientDistance;
            var destination=penetrate(player,selected,state.experience(skill.id()),state.cp());
            if(!destination.available())end(); // Original terminate() does NOT return from s_execute.
            Vec3 point=destination.position();
            // Entity.getDistance in 1.12 returned a float sqrt, then PTContext promoted it to double.
            double distance=(float)Math.sqrt(player.distanceToSqr(point));
            state.consumeForce(skill,(float)(distance*(Balance.i("target_teleport_context", "release_consume_force_1")+(Balance.i("target_teleport_context", "release_consume_force_2")-Balance.i("target_teleport_context", "release_consume_force_3"))*experience)),Balance.i("target_teleport_context", "release_consume_force_4")+(Balance.i("target_teleport_context", "release_consume_force_5")-Balance.i("target_teleport_context", "release_consume_force_6"))*experience,player.isCreative());
            state.addExperience(skill,(float)(Balance.f("target_teleport_context", "release_add_experience_1")*distance));state.finishContext(skill,cooldown(state,experience),0);
            count(state);move(point);performed=true;
        } else {
            var point=mark(player,ticks,state.experience(skill.id()),state.cp());float distance=(float)point.distanceTo(player.position());
            if(distance>=Balance.i("target_teleport_context", "release_threshold_1")) {
                send(SOUND);state.consumeForce(skill,distance*(Balance.i("target_teleport_context", "release_consume_force_7")+(Balance.i("target_teleport_context", "release_consume_force_8")-Balance.i("target_teleport_context", "release_consume_force_9"))*experience),Balance.i("target_teleport_context", "release_consume_force_10")+(Balance.i("target_teleport_context", "release_consume_force_11")-Balance.i("target_teleport_context", "release_consume_force_12"))*experience,player.isCreative());
                if(player.isPassenger())player.stopRiding();player.teleportTo(point.x,point.y,point.z);
                state.addExperience(skill,Balance.f("target_teleport_context", "release_add_experience_2")*distance);player.fallDistance=0;
                state.finishContext(skill,(int)(Balance.i("target_teleport_context", "release_finish_context_4")+(0-Balance.i("target_teleport_context", "release_finish_context_5"))*experience),0);count(state);performed=true;
            }
        }
    }
    private int cooldown(AbilityState state,float experience) {
        var data=cn.academy.data.GameplayData.document("skills/teleporter/penetrate_teleport");
        var curve=data.getAsJsonArray(state.level()>=data.get("cooldown_enhanced_level").getAsInt()
                ?"cooldown_enhanced_curve":"cooldown_curve");
        return (int)(curve.get(0).getAsFloat()+(curve.get(1).getAsFloat()-curve.get(0).getAsFloat())*experience);
    }
    private void move(Vec3 point){if(player.isPassenger())player.stopRiding();player.teleportTo(point.x,point.y,point.z);player.fallDistance=0;}
    private void count(AbilityState state){if(state.recordTeleport()>=400)ACAdvancements.grant(player,"legacy/teleporter/mastery");}
    public boolean performed(){return performed;}
    public void end(){if(ended)return;ended=true;send(END);audience.clear();}
    private void send(int phase){var packet=new TargetTeleportPayload(id,player.getId(),phase,slot,request,kind,experience);for(var viewer:audience)PacketDistributor.sendToPlayer(viewer,packet);}
}
