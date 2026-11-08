package cn.academy.client.render;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.ability.vanilla.vecmanip.skill.PlasmaCannonContext;
import cn.academy.client.render.util.*;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.network.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static cn.academy.network.PlasmaCannonPayload.*;

/** Original local charge, position snaps, independent tornado tail and ray-marched PlasmaBodyEffect. */
public final class PlasmaCannonEffects {
    private static final ThunderClapRequests REQUESTS=new ThunderClapRequests();
    private static final List<Body> BODIES=new ArrayList<>();
    private static final Random RANDOM=new Random();
    private static ClientLevel world;private static Player local;
    private record Trig(float amplitude,float speed,float phase) {
        float phase(float delta){return speed*delta-phase;}
    }
    private record Ball(float size,Vector3f center,Trig horizontal,Trig vertical){}
    private static float random(float lo,float hi){return lo+(hi-lo)*RANDOM.nextFloat();}
    private static Trig trig(float size){return new Trig(random(1.4f,2)*size,random(.5f,.7f),random(0,(float)Math.PI*2));}
    private static Ball ball(float lo,float hi,float spread,float motion){return new Ball(random(lo,hi),new Vector3f(random(-spread,spread),random(-spread,spread),random(-spread,spread)),trig(motion),trig(motion));}
    private static final class Body {
        final Player owner;final int slot,request,soundId;final float chargeTime;
        final TornadoMesh tornado=new TornadoMesh(RANDOM,12,8,1,.3);
        final List<Ball> balls=new ArrayList<>();
        final Vec3 funnel;
        int id,ticks,age,deadTicks;boolean flying,terminated,released,bodyRemoved;
        Vec3 position,destination;float alpha;double frameTime=RailgunHandEffects.seconds();
        Body(Player owner,int slot,int request,int id,float experience,Vec3 position) {
            this.owner=owner;this.slot=slot;this.request=request;this.id=id;this.position=position;
            soundId=owner==local?-request:id;chargeTime=60+(30-60)*experience;
            funnel=ElectricAttackTrace.traceBlockSegment(owner,position,position.add(0,-20,0),null).getLocation();
            for(int i=0;i<4;i++)balls.add(ball(1,1.5f,1.5f,1));
            int small=4+RANDOM.nextInt(2);for(int i=0;i<small;i++)balls.add(ball(.1f,.3f,3,2.5f));
            ACContextSounds.once(owner.getUUID(),"vecmanip.plasma_cannon",1,SoundSource.AMBIENT,soundId);
        }
        void stop(boolean notify) {
            if(terminated)return;terminated=true;
            ACContextSounds.handle(owner.getUUID(),"vecmanip.plasma_cannon",false,1,SoundSource.AMBIENT,soundId);
            if(owner==local){if(REQUESTS.accepts(slot,request))REQUESTS.end(slot);if(notify)PacketDistributor.sendToServer(new PlasmaCannonInputPayload(request,true));}
        }
    }
    private static boolean sync(Minecraft mc) {
        if(world!=mc.level || local!=mc.player){BODIES.forEach(b->b.stop(false));BODIES.clear();REQUESTS.end(-1);world=mc.level;local=mc.player;}
        return world!=null && local!=null;
    }
    public static int beginLocal(int slot) {
        if(!sync(Minecraft.getInstance()) || slot<0 || slot >= cn.academy.ability.AbilityState.SKILL_CHANNELS)return 0;
        var state=AbilityState.load(local);var skill=AbilityRegistry.skill(state.controlSkill(slot));
        if(skill==null || !skill.name().equals("plasma_cannon") || !state.canStartContext(skill))return 0;
        for(var body:BODIES)if(body.owner==local && !body.terminated && body.slot==slot)body.stop(false);
        int request=REQUESTS.begin(slot);BODIES.add(new Body(local,slot,request,0,state.experience(skill.id()),local.position().add(0,15,0)));return request;
    }
    public static void endLocal(int slot,boolean abort) {
        if(!sync(Minecraft.getInstance()))return;
        for(var body:BODIES)if(body.owner==local && !body.terminated && (slot<0 || body.slot==slot)) {
            if(abort){body.stop(true);continue;}
            if(body.released || body.flying)continue;
            if(body.ticks<body.chargeTime)body.stop(true);
            else {body.released=true;PacketDistributor.sendToServer(new PlasmaCannonInputPayload(body.request,false));}
        }
    }
    public static void receive(PlasmaCannonPayload packet) {
        if(!sync(Minecraft.getInstance()))return;
        Body found=null;for(var body:BODIES)if(!body.terminated && body.owner.getId()==packet.caster()
                && (body.id==packet.context() && body.id!=0 || body.owner==local && body.request==packet.request())){found=body;break;}
        if(packet.phase()==END){if(found!=null)found.stop(false);return;}
        if(!(world.getEntity(packet.caster()) instanceof Player owner))return;
        if(packet.phase()==START) {
            if(owner==local && packet.request()!=0) {
                if(found==null || !REQUESTS.accepts(packet.slot(),packet.request())){PacketDistributor.sendToServer(new PlasmaCannonInputPayload(packet.request(),true));return;}
                found.id=packet.context();
            } else if(found==null)BODIES.add(new Body(owner,packet.slot(),packet.request(),packet.context(),packet.experience(),packet.point()));
        } else if(found!=null) {
            if(packet.phase()==GO){found.flying=true;found.destination=packet.point();}
            else if(packet.phase()==POSITION)found.position=packet.point();
        }
    }
    public static void tick(Minecraft mc) {
        if(!sync(mc) || mc.isPaused())return;
        for(var body:BODIES) {
            body.age++;
            if(body.owner.isRemoved())body.stop(body.owner==local);
            if(body.flying || body.terminated)body.deadTicks++;
            if(body.terminated){if(Math.abs(body.alpha)<=.001f)body.bodyRemoved=true;continue;}
            if(body.owner==local) {
                var state=AbilityState.load(local);
                if(!state.active()){body.stop(true);continue;}
                body.ticks++;
                if(!body.flying) {
                    if(body.ticks<body.chargeTime){var skill=AbilityRegistry.skill("vecmanip.plasma_cannon");state.consumeRaw(skill,18+(25-18)*state.experience(skill.id()),0,local.isCreative());}
                    if(body.ticks==(int)body.chargeTime)ACContextSounds.once(local.getUUID(),"vecmanip.plasma_cannon_t",.5f,SoundSource.AMBIENT,body.soundId);
                }
            }
            if(body.flying)body.position=PlasmaCannonContext.move(body.position,body.destination);
        }
        BODIES.removeIf(body->body.terminated && body.deadTicks>=30 && body.bodyRemoved);
    }
    public static void render(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !sync(mc))return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        double seconds=RailgunHandEffects.seconds();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        var inverseViewNormal=new org.joml.Matrix3f(pose.last().normal()).invert();
        Vec3 viewer=event.getCamera().getEntity().getPosition(partial);
        var legacyView=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose())
                .translate((float)(viewer.x-camera.x),(float)(viewer.y-camera.y),(float)(viewer.z-camera.z));
        for(var body:BODIES) {
            if(body.deadTicks<30) {
                var relative=body.funnel.subtract(camera);pose.pushPose();pose.translate(relative.x,relative.y,relative.z);
                double alpha=body.flying || body.terminated?1-body.deadTicks/20.0:Math.min(1,body.age/20.0);
                body.tornado.draw(pose,buffers.getBuffer(ACShaders.VECTOR_TORNADO),seconds,alpha*.5,inverseViewNormal,
                        net.minecraft.client.renderer.LevelRenderer.getLightColor(world,net.minecraft.core.BlockPos.containing(body.funnel.add(0,1.53,0))));pose.popPose();
            }
        }
        buffers.endBatch(ACShaders.VECTOR_TORNADO);
        var shader=ACShaders.plasmaBody();if(shader==null)return;
        for(var body:BODIES) {
            if(body.bodyRemoved)continue;
            float dt=(float)(seconds-body.frameTime),desired=body.terminated?0:1;
            body.alpha+=Math.min(Math.abs(desired-body.alpha),dt*(body.terminated?1:.3f))*Math.signum(desired-body.alpha);body.frameTime=seconds;
            shader.safeGetUniform("alpha").set(body.alpha*body.alpha);shader.safeGetUniform("ballCount").set(body.balls.size());
            for(int i=0;i<body.balls.size();i++) {
                var ball=body.balls.get(i);float hp=ball.horizontal.phase(dt),vp=ball.vertical.phase(dt);
                // Deliberately retain original float world subtraction and per-frame phase input.
                var point=new Vector4f((float)body.position.x+ball.center.x+ball.horizontal.amplitude*Mth.sin(hp)-(float)viewer.x,
                        (float)body.position.y+ball.center.y+ball.vertical.amplitude*Mth.sin(vp)-(float)viewer.y,
                        (float)body.position.z+ball.center.z+ball.horizontal.amplitude*Mth.cos(hp)-(float)viewer.z,1).mul(legacyView);
                shader.safeGetUniform("Ball"+i).set(point.x,point.y,-point.z,ball.size);
            }
            var delta=body.position.subtract(viewer);var relative=body.position.subtract(camera);
            float yaw=-(float)Math.atan2(delta.x,delta.z)*180/(float)Math.PI;
            float pitch=-(float)Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))*180/(float)Math.PI;
            pose.pushPose();pose.translate(relative.x,relative.y,relative.z);pose.mulPose(Axis.YP.rotationDegrees(-yaw+180));pose.mulPose(Axis.XP.rotationDegrees(-pitch));pose.scale(22,22,1);
            var out=buffers.getBuffer(ACShaders.PLASMA_BODY);
            out.addVertex(pose.last(),-.5f,-.5f,0);out.addVertex(pose.last(),.5f,-.5f,0);
            out.addVertex(pose.last(),.5f,.5f,0);out.addVertex(pose.last(),-.5f,.5f,0);
            buffers.endBatch(ACShaders.PLASMA_BODY);pose.popPose();
        }
    }
    private PlasmaCannonEffects(){}
}
