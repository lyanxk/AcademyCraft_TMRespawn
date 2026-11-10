package cn.academy.ability.vanilla.vecmanip.skill;

import cn.academy.data.Balance;

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
    public static int kind(AbilitySkill skill,int level){return skill.name().equals("dir_shock") && level>=Balance.i("vector_strike_context", "kind_threshold_1")?ENHANCED_SHOCK:kind(skill);}
    public static void reject(ServerPlayer player,AbilitySkill skill,int slot,int request) {
        if(handles(skill) && request>0)PacketDistributor.sendToPlayer(player,
                new VectorStrikePayload(0,player.getId(),kind(skill),END,slot,request,0,Vec3.ZERO,List.of(),List.of()));
    }
    public VectorStrikeContext(ServerPlayer player,AbilityState state,AbilitySkill skill,int slot,int request) {
        this.player=player;this.skill=skill;this.slot=slot;this.request=request;kind=kind(skill,state.level());ctx=AbilityContext.of(player,skill);exp=state.experience(skill.id());
        levelFiveShock=kind==ENHANCED_SHOCK && state.level()>=Balance.i("vector_strike_context", "vector_strike_context_threshold_1");
        levelFiveBlast=kind==BLAST && state.level()>=Balance.i("vector_strike_context", "vector_strike_context_threshold_2");
        audience.add(player);for(var viewer:player.serverLevel().players())if(viewer!=player && viewer.distanceToSqr(player)<=Balance.i("vector_strike_context", "vector_strike_context_threshold_3"))audience.add(viewer);
    }
    public void start(){send(START,Vec3.ZERO,List.of(),List.of());}
    public boolean tick(){ticks++;return request!=0 || (kind==GROUND || ticks<Balance.i("vector_strike_context", "tick_threshold_1")) && (!performed || ++punchTicks<=Balance.i("vector_strike_context", "tick_threshold_2"));}
    public boolean performed(){return performed;}
    public int ticks(){return ticks;}
    public static boolean accepts(int kind,int ticks){return kind==GROUND?ticks>=0:ticks>Balance.i("vector_strike_context", "accepts_threshold_1") && ticks<Balance.i("vector_strike_context", "accepts_threshold_2");}
    /** Blast keeps its context for the client punch animation; shock and ground terminate after performing. */
    public boolean release(AbilityState state,int localTicks) {
        if(ended)return false;if(released)return (kind==BLAST || kind==ENHANCED_SHOCK) && performed;released=true;
        if(!accepts(kind,localTicks))return false;
        if(kind==SHOCK) {
            send(PERFORM,Vec3.ZERO,List.of(),List.of());
            if(!state.consumeRaw(skill,live(state,Balance.i("vector_strike_context", "release_live_1"),Balance.i("vector_strike_context", "release_live_2")),live(state,Balance.i("vector_strike_context", "release_live_3"),Balance.i("vector_strike_context", "release_live_4")),player.isCreative()))return false;
            performed=true;var hit=trace(Balance.i("vector_strike_context", "release_trace_1"));
            if(hit instanceof EntityHitResult target) {
                var entity=target.getEntity();ctx.attack(entity,snapshot(Balance.i("vector_strike_context", "release_snapshot_1"),Balance.i("vector_strike_context", "release_snapshot_2")));knockback(player,entity,SHOCK,state.experience(skill.id()));
                state.finishContext(skill,(int)live(state,Balance.i("vector_strike_context", "release_live_5"),Balance.i("vector_strike_context", "release_live_6")),0);send(HIT,Vec3.ZERO,List.of(entity.getId()),List.of());
                entity.setDeltaMovement(entity.getDeltaMovement().add(entity.position().subtract(player.position()).normalize().scale(Balance.d("vector_strike_context", "release_scale_1"))));
                entity.hurtMarked=true;state.addExperience(skill,Balance.f("vector_strike_context", "release_add_experience_1"));
            } else state.addExperience(skill,Balance.f("vector_strike_context", "release_add_experience_2"));
            return false;
        }
        if(kind==GROUND) {
            Vec3 landing=player.onGround()?player.position():findGroundLanding();
            if(landing==null)return false;
            if(!state.consumeRaw(skill,snapshot(Balance.i("vector_strike_context", "release_snapshot_3"),Balance.i("vector_strike_context", "release_snapshot_4")),snapshot(Balance.i("vector_strike_context", "release_snapshot_5"),Balance.i("vector_strike_context", "release_snapshot_6")),player.isCreative()))return false;
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
        if(!state.consumeRaw(skill,levelFiveShock?Balance.i("vector_strike_context", "release_consume_raw_1"):levelFiveBlast?Balance.i("vector_strike_context", "release_consume_raw_2"):enhanced?live(state,Balance.i("vector_strike_context", "release_live_7"),Balance.i("vector_strike_context", "release_live_8")):snapshot(Balance.i("vector_strike_context", "release_snapshot_7"),Balance.i("vector_strike_context", "release_snapshot_8")),
                levelFiveShock?Balance.i("vector_strike_context", "release_consume_raw_3"):levelFiveBlast?Balance.i("vector_strike_context", "release_consume_raw_4"):enhanced?live(state,Balance.i("vector_strike_context", "release_live_9"),Balance.i("vector_strike_context", "release_live_10")):live(state,Balance.i("vector_strike_context", "release_live_11"),Balance.i("vector_strike_context", "release_live_12")),player.isCreative()))return false;
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
            var intersection=entity.getBoundingBox().inflate(Balance.d("vector_strike_context", "trace_inflate_1")).clip(start,end);
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
        if(kind==SHOCK && exp<Balance.f("vector_strike_context", "knockback_threshold_1"))return;
        var delta=owner.getEyePosition().subtract(target.getEyePosition()).normalize();
        delta=new Vec3(delta.x,delta.y-(kind==SHOCK?.6f:.4f),delta.z).normalize();
        target.setPos(target.getX(),target.getY()+.1,target.getZ());
        if(kind==SHOCK)target.setDeltaMovement(delta.x*-Balance.f("vector_strike_context", "knockback_set_delta_movement_1"),delta.y*-Balance.f("vector_strike_context", "knockback_set_delta_movement_2"),delta.y*-Balance.f("vector_strike_context", "knockback_set_delta_movement_3"));
        else target.setDeltaMovement(delta.scale(-Balance.f("vector_strike_context", "knockback_scale_1")));
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
        var world=player.serverLevel();var hit=enhanced?trace(Balance.i("vector_strike_context", "blast_trace_1")):ElectricAttackTrace.traceBlocks(player,4);
        Vec3 point=enhanced?hit.getLocation():hit instanceof EntityHitResult entity?entity.getEntity().getEyePosition():
                hit.getType()==HitResult.Type.BLOCK
                        ?Vec3.atLowerCornerOf(((BlockHitResult)hit).getBlockPos()):player.position().add(player.getLookAngle().scale(Balance.i("vector_strike_context", "blast_scale_1")));
        state.finishContext(skill,levelFiveShock?Balance.i("vector_strike_context", "blast_finish_context_1"):levelFiveBlast?Balance.i("vector_strike_context", "blast_finish_context_2"):(int)(enhanced?snapshot(Balance.i("vector_strike_context", "blast_snapshot_1"),Balance.i("vector_strike_context", "blast_snapshot_2")):snapshot(Balance.i("vector_strike_context", "blast_snapshot_3"),Balance.i("vector_strike_context", "blast_snapshot_4"))),0);send(PERFORM,point,List.of(),List.of());
        if(enhanced) {
            var targets=world.getEntities(player,new AABB(point,point).inflate(Balance.i("vector_strike_context", "blast_inflate_1")),entity->withinBlast(entity,point));
            for(var entity:targets) {
                float damage=levelFiveShock?snapshot(Balance.i("vector_strike_context", "blast_snapshot_5"),Balance.i("vector_strike_context", "blast_snapshot_6")):snapshot(Balance.i("vector_strike_context", "blast_snapshot_7"),Balance.i("vector_strike_context", "blast_snapshot_8"));
                if(levelFiveShock && exp>=1f)ctx.attackIgnoreArmor(entity,damage);
                else ctx.attack(entity,damage);
                knockback(player,entity,kind,levelFiveShock?1f:state.experience(skill.id()));
                entity.hurtMarked=true;
            }
            send(HIT,Vec3.ZERO,targets.stream().map(Entity::getId).toList(),List.of());
            state.addExperience(skill,targets.isEmpty()?Balance.f("vector_strike_context", "blast_add_experience_1"):Balance.f("vector_strike_context", "blast_add_experience_2"));
            return;
        }
        int x=(int)Math.round(point.x),y=(int)Math.round(point.y),z=(int)Math.round(point.z);
        if(levelFiveBlast) {
            DirectedBlastUpgrade.perform(player,ctx,new BlockPos(x,y,z),player.getLookAngle(),exp);
            state.addExperience(skill,Balance.f("vector_strike_context", "blast_add_experience_3"));
            return;
        }
        float hardnessLimit=exp<Balance.f("vector_strike_context", "blast_hardness_limit_1")?Balance.f("vector_strike_context", "blast_hardness_limit_2"):exp<Balance.f("vector_strike_context", "blast_hardness_limit_3")?Balance.i("vector_strike_context", "blast_hardness_limit_4"):Balance.i("vector_strike_context", "blast_hardness_limit_5");
        double radius=Math.sqrt(Balance.i("vector_strike_context", "blast_radius_1"))+Balance.i("vector_strike_context", "blast_radius_2")*exp;
        double radiusSquared=Balance.i("vector_strike_context", "blast_radius_squared_1")+Balance.i("vector_strike_context", "blast_radius_squared_2")*exp*(Math.sqrt(Balance.i("vector_strike_context", "blast_radius_squared_3"))+exp);
        int reach=(int)Math.ceil(radius);
        for(int i=x-reach;i<=x+reach;i++)for(int j=y-reach;j<=y+reach;j++)for(int k=z-reach;k<=z+reach;k++) {
            int distance=(i-x)*(i-x)+(j-y)*(j-y)+(k-z)*(k-z);
            if(distance>radiusSquared)continue;
            var pos=new BlockPos(i,j,k);var block=world.getBlockState(pos);float hardness=block.getDestroySpeed(world,pos);
            if(hardness<0 || hardness>hardnessLimit)continue;
            if(ctx.canBreakBlock(world,pos))world.removeBlock(pos,false);
        }
        // Original MSG_GENERATE_EFFECT_BLOCKS has no registered listener.
        state.addExperience(skill,Balance.f("vector_strike_context", "blast_add_experience_4"));
    }
    private void ground(AbilityState state) {
        var world=player.serverLevel();var facing=player.getLookAngle();
        var look=new Vec3(facing.x,0,facing.z);
        if(look.lengthSqr()<1.0e-6) {
            double yaw=Math.toRadians(player.getYRot());look=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        } else look=look.normalize();
        var plotter=new Plotter((int)Math.floor(player.getX()),(int)Math.floor(player.getY()-.01),(int)Math.floor(player.getZ()),look.x,0,look.z);
        double energy=snapshot(Balance.i("vector_strike_context", "ground_snapshot_1"),Balance.i("vector_strike_context", "ground_snapshot_2")),ySpeed=GroundShockLaunch.speed(exp);int iterations=(int)snapshot(Balance.i("vector_strike_context", "ground_snapshot_3"),Balance.i("vector_strike_context", "ground_snapshot_4"));
        Set<BlockPos> affected=new HashSet<>();Set<Entity> attacked=Collections.newSetFromMap(new IdentityHashMap<>());
        // Vec3d.rotateYaw returns a new vector; original rot.rotateYaw(90) discards that result.
        Vec3[] deltas={Vec3.ZERO,look,look.scale(-1),look.scale(Balance.i("vector_strike_context", "ground_scale_1")),look.scale(-Balance.i("vector_strike_context", "ground_scale_2"))};double[] chances={1,Balance.d("vector_strike_context", "ground_chances_1"),Balance.d("vector_strike_context", "ground_chances_2"),Balance.d("vector_strike_context", "ground_chances_3"),Balance.d("vector_strike_context", "ground_chances_4")};
        while(energy>0 && iterations-->0) {
            int[] xyz=plotter.next();int x=xyz[0],y=xyz[1],z=xyz[2];
            for(int index=0;index<deltas.length;index++) {
                var delta=deltas[index];var pos=BlockPos.containing(x+delta.x,y+delta.y,z+delta.z);var block=world.getBlockState(pos);
                if(world.random.nextDouble()<chances[index] && !block.isAir() && affected.add(pos)) {
                    if(block.is(Blocks.STONE))energy-=Balance.d("vector_strike_context", "ground_energy_1");
                    else if(block.is(Blocks.GRASS_BLOCK))energy-=Balance.d("vector_strike_context", "ground_energy_2");
                    else energy-=block.is(Blocks.FARMLAND)?Balance.d("vector_strike_context", "ground_energy_3"):Balance.d("vector_strike_context", "ground_energy_4");
                    var box=new AABB(pos.getX()-.2,pos.getY()-.2,pos.getZ()-.2,pos.getX()+1.4,pos.getY()+2.2,pos.getZ()+1.4);
                    for(var target:world.getEntities(player,box,e->e instanceof LivingEntity))if(attacked.add(target)) {
                        energy-=1;ctx.attack(target,snapshot(Balance.i("vector_strike_context", "ground_snapshot_5"),Balance.i("vector_strike_context", "ground_snapshot_6")));target.setDeltaMovement(0,ySpeed,0);
                        target.hurtMarked=true;state.addExperience(skill,Balance.f("vector_strike_context", "ground_add_experience_1"));
                    }
                }
            }
        }
        state.addExperience(skill,Balance.f("vector_strike_context", "ground_add_experience_2"));state.finishContext(skill,(int)snapshot(Balance.i("vector_strike_context", "ground_snapshot_7"),Balance.i("vector_strike_context", "ground_snapshot_8")),0);
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
