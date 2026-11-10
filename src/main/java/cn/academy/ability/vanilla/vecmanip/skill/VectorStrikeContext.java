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
import net.minecraft.world.level.ClipContext;
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
    private final boolean levelFiveShock;
    private final boolean levelFiveBlast;
    private final List<ServerPlayer> audience=new ArrayList<>();
    private boolean ended,released,performed;
    private int ticks,punchTicks;
    public static boolean handles(AbilitySkill skill){return skill.name().equals("dir_shock") || skill.name().equals("ground_shock") || skill.name().equals("dir_blast");}
    public static int kind(AbilitySkill skill){return skill.name().equals("dir_shock")?SHOCK:skill.name().equals("ground_shock")?GROUND:BLAST;}
    public static int kind(AbilitySkill skill,int level){return skill.name().equals("dir_shock") && level>=3?ENHANCED_SHOCK:kind(skill);}
    public static void reject(ServerPlayer player,AbilitySkill skill,int slot,int request) {
        if(handles(skill) && request>0)PacketDistributor.sendToPlayer(player,
                new VectorStrikePayload(0,player.getId(),kind(skill),END,slot,request,0,Vec3.ZERO,List.of(),List.of()));
    }
    public VectorStrikeContext(ServerPlayer player,AbilityState state,AbilitySkill skill,int slot,int request) {
        this.player=player;this.skill=skill;this.slot=slot;this.request=request;kind=kind(skill,state.level());ctx=AbilityContext.of(player,skill);exp=state.experience(skill.id());
        levelFiveShock=kind==ENHANCED_SHOCK && state.level()>=5;
        levelFiveBlast=kind==BLAST && state.level()>=5;
        audience.add(player);for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=625)audience.add(viewer);
    }
    public void start(){send(START,Vec3.ZERO,List.of(),List.of());}
    public boolean tick(){ticks++;return request!=0 || (kind==GROUND || ticks<200) && (!performed || ++punchTicks<=6);}
    public boolean performed(){return performed;}
    public int ticks(){return ticks;}
    public static boolean accepts(int kind,int ticks){return kind==GROUND?ticks>=0:ticks>6 && ticks<50;}
    /** Blast keeps its context for the client punch animation; shock and ground terminate after performing. */
    public boolean release(AbilityState state,int localTicks) {
        if(ended)return false;if(released)return (kind==BLAST || kind==ENHANCED_SHOCK) && performed;released=true;
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
            Vec3 landing=player.onGround()?player.position():findGroundLanding();
            if(landing==null)return false;
            if(!state.consumeRaw(skill,snapshot(80,150),snapshot(15,10),player.isCreative()))return false;
            if(!player.onGround()) {
                if(player.isPassenger())player.stopRiding();
                if(player.isFallFlying())player.stopFallFlying();
                player.getAbilities().flying=false;
                player.teleportTo(landing.x,landing.y,landing.z);
                player.setDeltaMovement(Vec3.ZERO);player.resetFallDistance();
                player.setOnGround(true);player.hurtMarked=true;player.onUpdateAbilities();
            }
            performed=true;ground(state);return false;
        }
        boolean enhanced=kind==ENHANCED_SHOCK;
        if(!state.consumeRaw(skill,levelFiveShock?100:levelFiveBlast?200:enhanced?live(state,50,100):snapshot(160,200),
                levelFiveShock?12:levelFiveBlast?30:enhanced?live(state,18,12):live(state,50,30),player.isCreative()))return false;
        performed=true;blast(state);return true;
    }
    private Vec3 findGroundLanding() {
        var world=player.serverLevel();var from=player.position();
        var hit=world.clip(new ClipContext(from, new Vec3(from.x,world.getMinBuildHeight()-1,from.z),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(hit.getType()!=HitResult.Type.BLOCK || hit.getDirection()!=net.minecraft.core.Direction.UP)return null;
        var landing=hit.getLocation();
        return world.noCollision(player,player.getBoundingBox().move(landing.subtract(from)))?landing:null;
    }
    private HitResult trace(double length) {
        var start=player.getEyePosition();var end=start.add(player.getLookAngle().scale(length));
        if(kind!=ENHANCED_SHOCK)return ElectricAttackTrace.traceSegment(player,start,end,e->e!=player && e instanceof LivingEntity);
        var world=player.serverLevel();
        HitResult hit=world.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        double nearest=start.distanceToSqr(hit.getLocation());
        for(var entity:world.getEntities(player,new AABB(start,end).inflate(1),e->e instanceof LivingEntity && e.isPickable())) {
            var intersection=entity.getBoundingBox().inflate(.3).clip(start,end);
            if(intersection.isPresent()) {
                var point=intersection.get();double distance=start.distanceToSqr(point);
                if(distance<=nearest){nearest=distance;hit=new EntityHitResult(entity,point);}
            }
        }
        return hit;
    }
    public static void knockback(Entity owner,Entity target,int kind,float exp) {
        if(kind==ENHANCED_SHOCK) {
            DirectedShockKnockback.start(target,owner.getLookAngle(),(int)(exp*4));
            return;
        }
        if(kind==SHOCK && exp<.25f)return;
        var delta=owner.getEyePosition().subtract(target.getEyePosition()).normalize();
        delta=new Vec3(delta.x,delta.y-(kind==SHOCK?.6f:.4f),delta.z).normalize();
        target.setPos(target.getX(),target.getY()+.1,target.getZ());
        if(kind==SHOCK)target.setDeltaMovement(delta.x*-.7f,delta.y*-.7f,delta.y*-.7f);
        else target.setDeltaMovement(delta.scale(-1.2f));
        target.hurtMarked=true;
    }
    private static boolean withinBlast(Entity entity,Vec3 point) {
        var box=entity.getBoundingBox();
        double x=Math.clamp(point.x,box.minX,box.maxX)-point.x;
        double y=Math.clamp(point.y,box.minY,box.maxY)-point.y;
        double z=Math.clamp(point.z,box.minZ,box.maxZ)-point.z;
        return x*x+y*y+z*z<=9;
    }
    private void blast(AbilityState state) {
        boolean enhanced=kind==ENHANCED_SHOCK;
        var world=player.serverLevel();var hit=enhanced?trace(4):ElectricAttackTrace.traceBlocks(player,4);
        Vec3 point=enhanced?hit.getLocation():hit instanceof EntityHitResult entity?entity.getEntity().getEyePosition():
                hit.getType()==HitResult.Type.BLOCK
                        ?Vec3.atLowerCornerOf(((BlockHitResult)hit).getBlockPos()):player.position().add(player.getLookAngle().scale(4));
        state.finishContext(skill,levelFiveShock?20:levelFiveBlast?50:(int)(enhanced?snapshot(60,20):snapshot(80,50)),0);send(PERFORM,point,List.of(),List.of());
        if(enhanced) {
            var targets=world.getEntities(player,new AABB(point,point).inflate(3),entity->withinBlast(entity,point));
            for(var entity:targets) {
                float damage=levelFiveShock?snapshot(25,100):snapshot(10,25);
                if(levelFiveShock && exp>=1f)ctx.attackIgnoreArmor(entity,damage);
                else ctx.attack(entity,damage);
                knockback(player,entity,kind,levelFiveShock?1f:state.experience(skill.id()));
                entity.hurtMarked=true;
            }
            send(HIT,Vec3.ZERO,targets.stream().map(Entity::getId).toList(),List.of());
            state.addExperience(skill,targets.isEmpty()?.001f:.0035f);
            return;
        }
        int x=(int)Math.round(point.x),y=(int)Math.round(point.y),z=(int)Math.round(point.z);
        if(levelFiveBlast) {
            DirectedBlastUpgrade.perform(player,ctx,new BlockPos(x,y,z),player.getLookAngle(),exp);
            state.addExperience(skill,.0012f);
            return;
        }
        float hardnessLimit=exp<.25f?2.9f:exp<.5f?25:55;
        double radius=Math.sqrt(6)+2*exp;
        double radiusSquared=6+4*exp*(Math.sqrt(6)+exp);
        int reach=(int)Math.ceil(radius);
        for(int i=x-reach;i<=x+reach;i++)for(int j=y-reach;j<=y+reach;j++)for(int k=z-reach;k<=z+reach;k++) {
            int distance=(i-x)*(i-x)+(j-y)*(j-y)+(k-z)*(k-z);
            if(distance>radiusSquared)continue;
            var pos=new BlockPos(i,j,k);var block=world.getBlockState(pos);float hardness=block.getDestroySpeed(world,pos);
            if(hardness<0 || hardness>hardnessLimit)continue;
            if(ctx.canBreakBlock(world,pos))world.removeBlock(pos,false);
        }
        // Original MSG_GENERATE_EFFECT_BLOCKS has no registered listener.
        state.addExperience(skill,.0012f);
    }
    private void ground(AbilityState state) {
        var world=player.serverLevel();var facing=player.getLookAngle();
        var look=new Vec3(facing.x,0,facing.z);
        if(look.lengthSqr()<1.0e-6) {
            double yaw=Math.toRadians(player.getYRot());look=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        } else look=look.normalize();
        var plotter=new Plotter((int)Math.floor(player.getX()),(int)Math.floor(player.getY()-.01),(int)Math.floor(player.getZ()),look.x,0,look.z);
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
