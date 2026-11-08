package cn.academy.client.render;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.ability.vanilla.vecmanip.skill.StormWingContext;
import cn.academy.client.render.util.*;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.network.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.network.StormWingPayload.*;

/** Original local wing movement, W/S/A/D delegate group, four procedural funnels and dust. */
public final class StormWingEffects {
    private static final ThunderClapRequests REQUESTS=new ThunderClapRequests();
    private static final List<Wing> WINGS=new ArrayList<>();
    private static final Random RANDOM=new Random();
    private static ClientLevel world;private static Player local;
    private static final class Wing {
        final Player owner;final int slot,request,soundId;final float exp,chargeTime,speed;
        final TornadoMesh[] funnels=new TornadoMesh[4];final StormWingControls controls=new StormWingControls();
        int id,stateTicks,terminateTicks;boolean active,applying,terminated;
        Vec3 previous,position;
        Wing(Player owner,int slot,int request,int id,float exp) {
            this.owner=owner;this.slot=slot;this.request=request;this.id=id;this.exp=exp;soundId=owner==local?-request:id;
            chargeTime=70+(30-70)*exp;speed=(exp<.45f?.7f:1.2f)*(2+exp);
            for(int i=0;i<4;i++)funnels[i]=new TornadoMesh(RANDOM,2,.16,1,2);
            previous=position=owner.position().add(0,1.6,0);
            ACContextSounds.handle(owner.getUUID(),"vecmanip.storm_wing",true,1,SoundSource.AMBIENT,soundId);
        }
        void stop(boolean notify) {
            if(terminated)return;terminated=true;controls.clear();
            ACContextSounds.handle(owner.getUUID(),"vecmanip.storm_wing",false,1,SoundSource.AMBIENT,soundId);
            if(notify && owner==local)PacketDistributor.sendToServer(new StormWingInputPayload(request,END,false));
        }
    }
    private static final class Dust extends TerrainParticle {
        Dust(ClientLevel world,Player owner,double x,double y,double z,double vx,double vy,double vz) {
            super(world,x,y,z,vx,vy,vz,Blocks.DIRT.defaultBlockState(),owner.blockPosition());
            xd=vx;yd=vy;zd=vz;gravity=.02f;scale(.5f);
        }
    }
    private static boolean sync(Minecraft mc) {
        if(world!=mc.level || local!=mc.player){WINGS.forEach(w->w.stop(false));WINGS.clear();REQUESTS.end(-1);world=mc.level;local=mc.player;}
        return world!=null && local!=null;
    }
    private static Wing localWing(){for(var wing:WINGS)if(wing.owner==local && !wing.terminated)return wing;return null;}
    public static boolean controlsActive(){var wing=localWing();return wing!=null && wing.active;}
    public static int direction(){var wing=localWing();return wing==null?0:wing.controls.direction();}
    public static int state(){var wing=localWing();return wing==null?0:wing.active?AbilityContextSyncPayload.ACTIVE:AbilityContextSyncPayload.CHARGING;}
    public static boolean endByActivate() {
        if(!sync(Minecraft.getInstance()))return false;var wing=localWing();if(wing==null)return false;
        wing.stop(true);REQUESTS.end(wing.slot);return true;
    }
    public static int beginLocal(int slot) {
        if(!sync(Minecraft.getInstance()) || slot<0 || slot >= cn.academy.ability.AbilityState.SKILL_CHANNELS)return 0;
        var state=AbilityState.load(local);var skill=AbilityRegistry.skill(state.controlSkill(slot));
        if(skill==null || !skill.name().equals("storm_wing"))return 0;
        var existing=localWing();
        if(existing!=null){existing.stop(false);REQUESTS.end(existing.slot);return 0;} // Generic key-down toggles the server context once.
        if(!state.canStartContext(skill))return 0;
        int request=REQUESTS.begin(slot);WINGS.add(new Wing(local,slot,request,0,state.experience(skill.id())));return request;
    }
    public static void movement(int bits,boolean enabled) {
        if(!sync(Minecraft.getInstance()))return;var wing=localWing();if(wing==null || !wing.active)return;
        int previous=wing.controls.direction(),next=wing.controls.update(bits,enabled);
        if(previous!=next) {
            wing.applying=next!=0;PacketDistributor.sendToServer(new StormWingInputPayload(wing.request,APPLYING,wing.applying));
        }
    }
    public static void receive(StormWingPayload packet) {
        if(!sync(Minecraft.getInstance()))return;
        if(packet.phase()==END) {
            for(var wing:WINGS)if(wing.owner.getId()==packet.caster() && (wing.id==packet.context() || wing.owner==local && wing.request==packet.request()))wing.stop(false);
            return;
        }
        if(!(world.getEntity(packet.caster()) instanceof Player owner))return;
        Wing found=null;for(var wing:WINGS)if(!wing.terminated && wing.owner==owner && (wing.id==packet.context() || owner==local && wing.request==packet.request())){found=wing;break;}
        if(packet.phase()==START) {
            if(owner==local && packet.request()!=0) {
                if(found==null || !REQUESTS.accepts(packet.slot(),packet.request())){PacketDistributor.sendToServer(new StormWingInputPayload(packet.request(),END,false));return;}
                found.id=packet.context();
            } else if(found==null)WINGS.add(new Wing(owner,packet.slot(),packet.request(),packet.context(),packet.experience()));
        } else if(found!=null && packet.phase()==ACTIVE) {
            found.active=true;StormWingContext.burst(owner,packet.experience());
        } else if(found!=null && packet.phase()==APPLYING)found.applying=packet.applying();
    }
    public static void tick(Minecraft mc) {
        if(!sync(mc) || mc.isPaused())return;
        for(var wing:WINGS) {
            wing.previous=wing.position;wing.position=wing.owner.position().add(0,1.6,0);
            if(wing.owner.isRemoved())wing.stop(wing.owner==local);
            if(wing.terminated){wing.terminateTicks++;continue;}
            if(wing.owner==local) {
                var state=AbilityState.load(local);
                if(!state.active()){wing.stop(true);continue;}
                int direction=wing.controls.direction();var velocity=local.getDeltaMovement();
                if(direction!=0) {
                    Vec3 axis=switch(direction){case 1->new Vec3(0,0,1);case 2->new Vec3(0,0,-1);case 3->new Vec3(1,0,0);default->new Vec3(-1,0,0);};
                    float yaw=local.getYHeadRot()*(float)Math.PI/180,pitch=local.getXRot()*(float)Math.PI/180;
                    var target=axis.xRot(-pitch).yRot(-yaw).scale(wing.speed);
                    if(local.isPassenger())local.stopRiding();
                    local.setDeltaMovement(move(velocity.x,target.x),move(velocity.y,target.y),move(velocity.z,target.z));
                } else {
                    var hit=ElectricAttackTrace.traceBlockSegment(local,local.position().add(0,.5,0),local.position().add(0,-.3,0),null);
                    local.setDeltaMovement(velocity.x,hit.getType()==HitResult.Type.MISS?velocity.y+.078:.1,velocity.z);
                }
                local.resetFallDistance();
                if(wing.active) {
                    var skill=AbilityRegistry.skill("vecmanip.storm_wing");state.addExperience(skill,.00005f);
                    state.consumeRaw(skill,40+(25-40)*wing.exp,10+(7-10)*wing.exp,local.isCreative());
                }
                if(!wing.active && wing.stateTicks>wing.chargeTime) {
                    wing.active=true;wing.stateTicks=0;
                    PacketDistributor.sendToServer(new StormWingInputPayload(wing.request,ACTIVE,false));
                }
            }
            wing.stateTicks++;
            for(int i=0;i<12;i++) {
                double theta=RANDOM.nextDouble()*Math.PI*2,phi=-Math.PI+RANDOM.nextDouble()*Math.PI*2,r=3+RANDOM.nextDouble()*5;
                double horizontal=r*Math.sin(phi),cos=Math.cos(theta),sin=Math.sin(theta);
                mc.particleEngine.add(new Dust(world,wing.owner,wing.owner.getX()+horizontal*cos,wing.owner.getY()+r*Math.cos(phi),wing.owner.getZ()+horizontal*sin,
                        sin*.7f,-.01f+RANDOM.nextDouble()*(.05f-(-.01f)),-cos*.7f));
            }
        }
        WINGS.removeIf(wing->wing.terminated && wing.terminateTicks>15);
    }
    private static double move(double current,double target){double delta=target-current;return current+Math.min(Math.abs(delta),.16)*Math.signum(delta);}
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !sync(Minecraft.getInstance()))return;
        var mc=Minecraft.getInstance();var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        double seconds=RailgunHandEffects.seconds();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        var inverseViewNormal=new org.joml.Matrix3f(pose.last().normal()).invert();
        for(var wing:WINGS) {
            var point=wing.previous.lerp(wing.position,partial).subtract(camera);pose.pushPose();pose.translate(point.x,point.y,point.z);
            pose.mulPose(Axis.YP.rotationDegrees(-wing.owner.yBodyRot));pose.mulPose(Axis.XP.rotationDegrees(wing.owner.getXRot()*.2f));pose.mulPose(Axis.XP.rotationDegrees(-70));pose.translate(0,.2,-.5);
            int light=net.minecraft.client.renderer.LevelRenderer.getLightColor(world,net.minecraft.core.BlockPos.containing(wing.position.add(0,1.53,0)));
            double alpha=!wing.active?wing.stateTicks/(double)wing.chargeTime*.7:wing.terminated?.7*(1-wing.terminateTicks/15.0):.7;
            for(int i=0;i<4;i++) {
                pose.pushPose();pose.translate(i%2==0?-.1:.1,i<2?-.3:-.5,i<2?.1:-.1);
                pose.mulPose(Axis.YP.rotationDegrees(i==0 || i==3?45:-45));pose.mulPose(Axis.ZP.rotationDegrees(i%2==0?45:-45));
                wing.funnels[i].draw(pose,buffers.getBuffer(ACShaders.VECTOR_TORNADO),seconds,alpha,inverseViewNormal,light);pose.popPose();
            }
            pose.popPose();
        }
        buffers.endBatch(ACShaders.VECTOR_TORNADO);
    }
    private StormWingEffects(){}
}
