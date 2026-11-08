package cn.academy.ability.vanilla.teleporter.skill;

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
        kind=skill.name().equals("penetrate_teleport")?PENETRATE:MARK;experience=state.experience(skill.id());selected=10+(35-10)*experience;
        audience.add(player);for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=625)audience.add(viewer);
    }
    public static boolean handles(AbilitySkill skill){return skill.name().equals("penetrate_teleport") || skill.name().equals("mark_teleport");}
    public static void reject(ServerPlayer player,AbilitySkill skill,int slot,int request) {
        if(handles(skill) && request>0)PacketDistributor.sendToPlayer(player,new TargetTeleportPayload(0,player.getId(),END,slot,request,0,0));
    }
    public void start(){send(START);}
    public void tick(){ticks++;}
    public float selected(){return selected;}
    public void wheel(float delta){float proposed=selected+delta;if(proposed>=.5f && proposed<=10+(35-10)*experience)selected=proposed;}
    public record Destination(Vec3 position,boolean available){}
    public static Destination penetrate(Player player,float selected,float liveExperience,float cp) {
        double range=Math.min(selected,cp/(14+(9-14)*liveExperience));
        return penetrateAtRange(player,range);
    }
    public static Destination penetrateAtRange(Player player,double range) {
        Vec3 position=player.position(),direction=player.getLookAngle().normalize();int stage=0,counter=0;double travelled=0;
        while(travelled<=range) {
            BlockPos feet=new BlockPos((int)position.x,(int)position.y,(int)position.z);
            boolean open=passable(player,feet) && passable(player,feet.above());
            if(stage==0){if(!open)stage=1;}else if(stage==1){if(open)stage=2;}
            else {counter++;if(!open || counter>4)break;}
            travelled+=.8;position=position.add(direction.scale(.8));
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
        double range=Math.min((ticks+1)*2,Math.min(25+(60-25)*liveExperience,cp/(12+(4-12)*liveExperience)));
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
            state.consumeForce(skill,(float)(distance*(14+(9-14)*experience)),80+(50-80)*experience,player.isCreative());
            state.addExperience(skill,(float)(.00014f*distance));state.finishContext(skill,(int)(50+(30-50)*experience),0);
            count(state);move(point);performed=true;
        } else {
            var point=mark(player,ticks,state.experience(skill.id()),state.cp());float distance=(float)point.distanceTo(player.position());
            if(distance>=3) {
                send(SOUND);state.consumeForce(skill,distance*(12+(4-12)*experience),40+(20-40)*experience,player.isCreative());
                if(player.isPassenger())player.stopRiding();player.teleportTo(point.x,point.y,point.z);
                state.addExperience(skill,.00018f*distance);player.fallDistance=0;
                state.finishContext(skill,(int)(30+(0-30)*experience),0);count(state);performed=true;
            }
        }
    }
    private void move(Vec3 point){if(player.isPassenger())player.stopRiding();player.teleportTo(point.x,point.y,point.z);player.fallDistance=0;}
    private void count(AbilityState state){if(state.recordTeleport()>=400)ACAdvancements.grant(player,"legacy/teleporter/mastery");}
    public boolean performed(){return performed;}
    public void end(){if(ended)return;ended=true;send(END);audience.clear();}
    private void send(int phase){var packet=new TargetTeleportPayload(id,player.getId(),phase,slot,request,kind,experience);for(var viewer:audience)PacketDistributor.sendToPlayer(viewer,packet);}
}
