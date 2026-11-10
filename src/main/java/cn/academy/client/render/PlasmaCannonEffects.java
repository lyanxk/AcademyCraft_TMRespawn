package cn.academy.client.render;

import cn.academy.data.SharedSkillCosts;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.vecmanip.skill.PlasmaCannonContext;
import cn.academy.client.render.util.*;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.network.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static cn.academy.network.PlasmaCannonPayload.*;

/** Converging blue energy streams, a contracting spherical wind shell and the charged plasma sphere. */
public final class PlasmaCannonEffects {
    private static final ThunderClapRequests REQUESTS=new ThunderClapRequests();
    private static final List<Body> BODIES=new ArrayList<>();
    private static final Random RANDOM=new Random();
    private static ClientLevel world;private static Player local;
    private static final class Body {
        final Player owner;final int slot,request,soundId;final float chargeTime;
        final PlasmaChargeEffects chargeEffects=new PlasmaChargeEffects(RANDOM);
        int id,ticks,age,deadTicks;boolean flying,terminated,released,bodyRemoved;
        final double animationStart=RailgunHandEffects.seconds();
        Vec3 position,destination;float alpha;double frameTime=animationStart;
        Body(Player owner,int slot,int request,int id,float experience,Vec3 position) {
            this.owner=owner;this.slot=slot;this.request=request;this.id=id;this.position=position;
            soundId=owner==local?-request:id;chargeTime=PlasmaCannonContext.chargeTime(experience);
            ACContextSounds.once(owner.getUUID(),"vecmanip.plasma_cannon",1,SoundSource.AMBIENT,soundId);
        }
        float progress(float partial){return flying?1:Math.clamp((age+partial)/chargeTime,0,1);}
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
        int request=REQUESTS.begin(slot);BODIES.add(new Body(local,slot,request,0,state.experience(skill.id()),PlasmaCannonContext.chargePosition(local)));return request;
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
                    if(body.ticks<body.chargeTime){var skill=AbilityRegistry.skill("vecmanip.plasma_cannon");state.consumeRaw(skill,SharedSkillCosts.plasmaCharge(state.experience(skill.id())),0,local.isCreative());}
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
        var view=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        for(var body:BODIES) {
            if(!body.terminated && body.progress(partial)<1) {
                var relative=body.position.subtract(camera);pose.pushPose();pose.translate(relative.x,relative.y,relative.z);
                body.chargeEffects.draw(pose,buffers,camera.subtract(body.position),(seconds-body.animationStart)*60/body.chargeTime,
                        body.progress(partial),Math.min(1,(body.age+partial)/5));pose.popPose();
            }
            if(!body.bodyRemoved && body.progress(partial)>=1)
                drawFormedLines(body,pose,buffers,camera,seconds,partial,false);
        }
        buffers.endBatch(ACShaders.VECTOR_TORNADO);
        buffers.endBatch(ACShaders.ARC);
        buffers.endBatch(ACShaders.SUB_ARCS);
        var shader=ACShaders.plasmaBody();if(shader==null)return;
        for(var body:BODIES) {
            if(body.bodyRemoved)continue;
            float dt=(float)(seconds-body.frameTime),desired=body.terminated?0:1;
            float animationTime=(float)(seconds-body.animationStart);
            body.alpha+=Math.min(Math.abs(desired-body.alpha),dt*(body.terminated?2:3))*Math.signum(desired-body.alpha);body.frameTime=seconds;
            float progress=body.progress(partial);
            float growth=PlasmaChargeEffects.coreGrowth(progress);
            shader.safeGetUniform("alpha").set(body.alpha*Math.min(1,growth*5));
            shader.safeGetUniform("chargeProgress").set(progress);shader.safeGetUniform("animationTime").set(animationTime);
            shader.safeGetUniform("ballCount").set(1);
            var relative=body.position.subtract(camera);
            var point=new Vector4f((float)relative.x,(float)relative.y,(float)relative.z,1).mul(view);
            shader.safeGetUniform("Ball0").set(point.x,point.y,-point.z,2.5f*growth);
            pose.pushPose();pose.translate(relative.x,relative.y,relative.z);pose.mulPose(event.getCamera().rotation());pose.scale(14,14,1);
            var out=buffers.getBuffer(ACShaders.PLASMA_BODY);
            out.addVertex(pose.last(),-.5f,-.5f,0);out.addVertex(pose.last(),.5f,-.5f,0);
            out.addVertex(pose.last(),.5f,.5f,0);out.addVertex(pose.last(),-.5f,.5f,0);
            buffers.endBatch(ACShaders.PLASMA_BODY);pose.popPose();
            if(progress>=1) {
                drawFormedLines(body,pose,buffers,camera,seconds,partial,true);
                buffers.endBatch(ACShaders.SUB_ARCS);
            }
        }
    }
    private static void drawFormedLines(Body body,com.mojang.blaze3d.vertex.PoseStack pose,
            net.minecraft.client.renderer.MultiBufferSource buffers,Vec3 camera,double seconds,float partial,boolean front) {
        var relative=body.position.subtract(camera);
        float appearance=Math.clamp((body.age+partial-body.chargeTime)/8,0,1);
        pose.pushPose();pose.translate(relative.x,relative.y,relative.z);
        body.chargeEffects.drawFormed(pose,buffers.getBuffer(ACShaders.SUB_ARCS),camera.subtract(body.position),
                seconds-body.animationStart,appearance*body.alpha,front);
        pose.popPose();
    }
    private PlasmaCannonEffects(){}
}
