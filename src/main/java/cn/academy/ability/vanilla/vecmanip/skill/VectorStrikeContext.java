package cn.academy.ability.vanilla.vecmanip.skill;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.network.VectorStrikePayload;
import cn.academy.util.Plotter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.network.VectorStrikePayload.*;

/** Original ShockContext, GroundshockContext and BlastwaveContext server listeners. */
public final class VectorStrikeContext {
    private static int next;
    public final int id=++next,request,slot,kind;
    private final ServerPlayer player;
    private final AbilitySkill skill;
    private final AbilityContext ctx;
    private final float exp;
    private final List<ServerPlayer> audience=new ArrayList<>();
    private boolean ended,released,performed;
    private int ticks,punchTicks;
    public static boolean handles(AbilitySkill skill){return skill.name().equals("dir_shock") || skill.name().equals("ground_shock") || skill.name().equals("dir_blast");}
    public static int kind(AbilitySkill skill){return skill.name().equals("dir_shock")?SHOCK:skill.name().equals("ground_shock")?GROUND:BLAST;}
    public static void reject(ServerPlayer player,AbilitySkill skill,int slot,int request) {
        if(handles(skill) && request>0)PacketDistributor.sendToPlayer(player,
                new VectorStrikePayload(0,player.getId(),kind(skill),END,slot,request,0,Vec3.ZERO,List.of(),List.of()));
    }
    public VectorStrikeContext(ServerPlayer player,AbilityState state,AbilitySkill skill,int slot,int request) {
        this.player=player;this.skill=skill;this.slot=slot;this.request=request;kind=kind(skill);ctx=AbilityContext.of(player,skill);exp=state.experience(skill.id());
        audience.add(player);for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=625)audience.add(viewer);
    }
    public void start(){send(START,Vec3.ZERO,List.of(),List.of());}
    public boolean tick(){ticks++;return request!=0 || (kind==GROUND || ticks<200) && (!performed || ++punchTicks<=6);}
    public boolean performed(){return performed;}
    public int ticks(){return ticks;}
    public static boolean accepts(int kind,int ticks){return kind==GROUND?ticks>=5:ticks>6 && ticks<50;}
    /** Blast keeps its context for the client punch animation; shock and ground terminate after performing. */
    public boolean release(AbilityState state,int localTicks) {
        if(ended)return false;if(released)return kind==BLAST && performed;released=true;
        if(!accepts(kind,localTicks))return false;
        if(kind==SHOCK) {
            send(PERFORM,Vec3.ZERO,List.of(),List.of());
            if(!state.consumeRaw(skill,live(state,50,100),live(state,18,12),player.isCreative()))return false;
            performed=true;var hit=trace(3);
            if(hit instanceof EntityHitResult target) {
                var entity=target.getEntity();ctx.attack(entity,snapshot(7,15));knockback(player,entity,SHOCK,state.experience(skill.id()));
                state.finishContext(skill,(int)live(state,60,20),0);send(HIT,Vec3.ZERO,List.of(entity.getId()),List.of());
                entity.setDeltaMovement(entity.getDeltaMovement().add(entity.position().subtract(player.position()).normalize().scale(.24)));
                entity.hurtMarked=true;state.addExperience(skill,.0035f);
            } else state.addExperience(skill,.001f);
            return false;
        }
        if(kind==GROUND) {
            if(player.onGround() && state.consumeRaw(skill,snapshot(80,150),snapshot(15,10),player.isCreative())) {
                performed=true;ground(state);
            }
            return false;
        }
        if(!state.consumeRaw(skill,snapshot(160,200),live(state,50,30),player.isCreative()))return false;
        performed=true;blast(state);return true;
    }
    private HitResult trace(double length) {
        var start=player.getEyePosition();return ElectricAttackTrace.traceSegment(player,start,start.add(player.getLookAngle().scale(length)),e->e!=player && e instanceof LivingEntity);
    }
    public static void knockback(Entity owner,Entity target,int kind,float exp) {
        if(kind==SHOCK && exp<.25f)return;
        var delta=owner.getEyePosition().subtract(target.getEyePosition()).normalize();
        delta=new Vec3(delta.x,delta.y-(kind==SHOCK?.6f:.4f),delta.z).normalize();
        target.setPos(target.getX(),target.getY()+.1,target.getZ());
        if(kind==SHOCK)target.setDeltaMovement(delta.x*-.7f,delta.y*-.7f,delta.y*-.7f);
        else target.setDeltaMovement(delta.scale(-1.2f));
        target.hurtMarked=true;
    }
    private void blast(AbilityState state) {
        var world=player.serverLevel();var hit=trace(4);
        Vec3 point=hit instanceof EntityHitResult entity?entity.getEntity().getEyePosition():
                hit.getType()==HitResult.Type.BLOCK?Vec3.atLowerCornerOf(((BlockHitResult)hit).getBlockPos()):player.position().add(player.getLookAngle().scale(4));
        state.finishContext(skill,(int)snapshot(80,50),0);send(PERFORM,point,List.of(),List.of());
        var targets=world.getEntities(player,new AABB(point,point).inflate(3),entity->entity.distanceToSqr(point)<=9);
        for(var entity:targets) {
            ctx.attack(entity,snapshot(10,25));knockback(player,entity,BLAST,state.experience(skill.id()));
            entity.setDeltaMovement(entity.position().subtract(player.position()).normalize().scale(.24));entity.hurtMarked=true;
        }
        send(HIT,Vec3.ZERO,targets.stream().map(Entity::getId).toList(),List.of());
        int x=(int)Math.round(point.x),y=(int)Math.round(point.y),z=(int)Math.round(point.z);
        float hardnessLimit=exp<.25f?2.9f:exp<.5f?25:55;
        for(int i=x-3;i<x+3;i++)for(int j=y-3;j<y+3;j++)for(int k=z-3;k<z+3;k++) {
            int distance=(i-x)*(i-x)+(j-y)*(j-y)+(k-z)*(k-z);
            if(distance>6 || distance!=0 && world.random.nextFloat()>=snapshot(.5f,.8f))continue;
            var pos=new BlockPos(i,j,k);var block=world.getBlockState(pos);float hardness=block.getDestroySpeed(world,pos);
            if(hardness<0 || hardness>hardnessLimit)continue;
            if(state.experience(skill.id())==1f) {
                if(ctx.canBreakBlock(world,pos)) {
                    world.addFreshEntity(new ItemEntity(world,i,j,k,new ItemStack(block.getBlock())));world.removeBlock(pos,false);
                }
            } else if(ctx.canBreakBlock(world,pos) && ctx.canBreakBlock(world,pos)) {
                if(world.random.nextFloat()<snapshot(.4f,.9f))Block.dropResources(block,world,pos,world.getBlockEntity(pos),player,ItemStack.EMPTY);
                world.removeBlock(pos,false);
            }
        }
        // Original MSG_GENERATE_EFFECT_BLOCKS has no registered listener.
        state.addExperience(skill,targets.isEmpty()?.0012f:.0025f);
    }
    private void ground(AbilityState state) {
        var world=player.serverLevel();var look=player.getLookAngle().normalize();
        var plotter=new Plotter((int)Math.floor(player.getX()),(int)Math.floor(player.getY())-1,(int)Math.floor(player.getZ()),look.x,0,look.z);
        double energy=snapshot(60,120),ySpeed=GroundShockLaunch.speed(exp);int iterations=(int)snapshot(10,25);
        Set<BlockPos> affected=new HashSet<>();Set<Entity> attacked=Collections.newSetFromMap(new IdentityHashMap<>());
        // Vec3d.rotateYaw returns a new vector; original rot.rotateYaw(90) discards that result.
        Vec3[] deltas={Vec3.ZERO,look,look.scale(-1),look.scale(2),look.scale(-2)};double[] chances={1,.7,.7,.3,.3};
        while(energy>0 && iterations-->0) {
            int[] xyz=plotter.next();int x=xyz[0],y=xyz[1],z=xyz[2];
            for(int index=0;index<deltas.length;index++) {
                var delta=deltas[index];var pos=BlockPos.containing(x+delta.x,y+delta.y,z+delta.z);var block=world.getBlockState(pos);
                if(world.random.nextDouble()<chances[index] && !block.isAir() && affected.add(pos)) {
                    if(block.is(Blocks.STONE))energy-=.4;
                    else if(block.is(Blocks.GRASS_BLOCK))energy-=.2;
                    else energy-=block.is(Blocks.FARMLAND)?.1:.5;
                    var box=new AABB(pos.getX()-.2,pos.getY()-.2,pos.getZ()-.2,pos.getX()+1.4,pos.getY()+2.2,pos.getZ()+1.4);
                    for(var target:world.getEntities(player,box,e->e instanceof LivingEntity))if(attacked.add(target)) {
                        energy-=1;ctx.attack(target,snapshot(4,6));target.setDeltaMovement(0,ySpeed,0);
                        target.hurtMarked=true;state.addExperience(skill,.002f);
                    }
                }
            }
        }
        state.addExperience(skill,.001f);state.finishContext(skill,(int)snapshot(80,40),0);
        send(GROUND_EFFECT,Vec3.ZERO,List.of(),List.copyOf(affected));
    }
    public void end(){if(ended)return;ended=true;send(END,Vec3.ZERO,List.of(),List.of());audience.clear();}
    private float snapshot(float a,float b){return a+(b-a)*exp;}
    private float live(AbilityState state,float a,float b){return a+(b-a)*state.experience(skill.id());}
    private void send(int phase,Vec3 point,List<Integer> entities,List<BlockPos> blocks) {
        var packet=new VectorStrikePayload(id,player.getId(),kind,phase,slot,request,exp,point,entities,blocks);
        audience.forEach(viewer->PacketDistributor.sendToPlayer(viewer,packet));
    }
}
