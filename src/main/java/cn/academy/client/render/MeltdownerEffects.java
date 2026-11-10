package cn.academy.client.render;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.ability.vanilla.meltdowner.skill.MeltdownerContext;
import cn.academy.client.render.util.*;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.network.*;
import cn.academy.registry.ACSounds;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.network.MeltdownerContextPayload.*;

/** LSContext/MDContext/JEContext client lifecycle, plus their independently-lived release beams. */
public final class MeltdownerEffects {
    private static final ThunderClapRequests REQUESTS=new ThunderClapRequests();
    private static final Set<Integer> RELEASED=new HashSet<>();
    private static final Map<Integer,Effect> EFFECTS=new LinkedHashMap<>();
    private static final List<Beam> BEAMS=new ArrayList<>();
    private static final List<MdParticle> PARTICLES=new ArrayList<>();
    private static final Random RANDOM=new Random();
    private static ClientLevel world;
    private static Player local;
    private static final class Effect {
        final int id,kind,slot,request;
        final Player owner;
        final double created=RailgunHandEffects.seconds();
        int ticks,flightTicks;
        boolean marking=true,triggering;
        Vec3 start,target,velocity;
        double lastRender;
        float rotation;
        Effect(MeltdownerContextPayload packet,Player owner) {
            id=packet.id();kind=packet.kind();slot=packet.slot();request=packet.request();this.owner=owner;
            start=owner.position();target=start;velocity=Vec3.ZERO;
            if(kind==SHIELD) {
                world.playLocalSound(owner.getX(),owner.getY(),owner.getZ(),ACSounds.get("md.shield_startup"),SoundSource.AMBIENT,.5f,1,false);
                ACContextSounds.handle(owner.getUUID(),"md.shield_loop",true,1,SoundSource.AMBIENT,id);
            } else if(kind==BEAM)ACContextSounds.once(owner.getUUID(),"md.md_charge",1,SoundSource.AMBIENT,id);
        }
        void stop() {
            if(kind==SHIELD)ACContextSounds.handle(owner.getUUID(),"md.shield_loop",false,1,SoundSource.AMBIENT,id);
            if(kind==BEAM)ACContextSounds.handle(owner.getUUID(),"md.md_charge",false,1,SoundSource.AMBIENT,id);
            if(owner==local && (kind==BEAM || kind==JET && triggering))owner.getAbilities().setWalkingSpeed(.1f);
        }
    }
    private static final class Beam {
        final Player owner;
        final Vec3 position;
        final float yaw,pitch;
        final double length,created=RailgunHandEffects.seconds();
        double lastFrame,widthWiggle,glowWiggle;
        int ticks;
        Beam(Player owner,double length,Vec3 override) {
            this.owner=owner;this.length=length;
            var from=owner.getEyePosition().add(owner.getLookAngle());
            var to=owner.position().add(owner.getLookAngle().scale(length));
            var delta=to.subtract(from);position=override==null?from:override;
            yaw=(float)-Math.toDegrees(Math.atan2(delta.x,delta.z));
            pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        }
        RayAnimation.Frame frame(double now) {
            if(lastFrame!=0) {
                long dt=(long)((now-lastFrame)*1000);
                widthWiggle=Math.clamp(widthWiggle+dt*(-.4+RANDOM.nextDouble()*.8)/1000,0,.1);
                glowWiggle=Math.clamp(glowWiggle+dt*(-.4+RANDOM.nextDouble()*.8)/1000,0,.1);
            }
            lastFrame=now;long dt=(long)((now-created)*1000);
            double alpha=dt>1800?1-(double)(dt-1800)/700:1;
            double width=widthWiggle+(dt>2200?1-(double)(dt-2200)/300:1);
            return new RayAnimation.Frame((dt<200?(double)dt/200:1)*length,width,alpha,(.9+glowWiggle)*alpha);
        }
    }
    private static boolean sync(Minecraft mc) {
        if(world!=mc.level || local!=mc.player) {
            EFFECTS.values().forEach(Effect::stop);EFFECTS.clear();BEAMS.clear();PARTICLES.clear();REQUESTS.end(-1);RELEASED.clear();
            world=mc.level;local=mc.player;
        }
        return world!=null && local!=null;
    }
    public static int beginLocal(int slot) {
        if(!sync(Minecraft.getInstance()))return 0;
        var skill=AbilityRegistry.skill(AbilityState.load(local).controlSkill(slot));
        if(skill==null || !MeltdownerContext.handles(skill))return 0;
        RELEASED.remove(slot);return REQUESTS.begin(slot);
    }
    public static void endLocal(int slot,boolean abort) {
        if(!sync(Minecraft.getInstance()))return;
        if(abort)REQUESTS.end(slot);else RELEASED.add(slot);
        EFFECTS.values().removeIf(effect->{
            if(effect.owner!=local || slot>=0 && effect.slot!=slot)return false;
            if(!abort && effect.kind==JET){effect.marking=false;return false;}
            effect.stop();if(abort)PacketDistributor.sendToServer(new MeltdownerEndPayload(effect.id));return true;
        });
    }
    public static void receive(MeltdownerContextPayload packet) {
        if(!sync(Minecraft.getInstance()))return;
        if(packet.phase()==END) {
            var old=EFFECTS.remove(packet.id());if(old!=null)old.stop();return;
        }
        if(!(world.getEntity(packet.caster()) instanceof Player owner))return;
        if(packet.phase()==PERFORM || packet.phase()==REFLECT) {
            Vec3 origin=null;
            if(packet.phase()==REFLECT) {
                var reflector=world.getEntity(packet.related());if(reflector==null)return;
                origin=owner.getEyePosition().add(owner.getLookAngle().normalize().scale(owner.getEyePosition().distanceTo(reflector.getEyePosition())));
            }else world.playLocalSound(owner.getX(),owner.getY(),owner.getZ(),ACSounds.get("md.meltdowner"),SoundSource.PLAYERS,.5f,1,false);
            BEAMS.add(new Beam(owner,packet.length(),origin));return;
        }
        if(owner==local && !REQUESTS.accepts(packet.slot(),packet.request())) {
            PacketDistributor.sendToServer(new MeltdownerEndPayload(packet.id()));return;
        }
        if(packet.phase()==START) {
            if(owner==local && RELEASED.contains(packet.slot()) && packet.kind()!=JET)return;
            EFFECTS.computeIfAbsent(packet.id(),id->new Effect(packet,owner));
            if(owner==local && RELEASED.contains(packet.slot()))EFFECTS.get(packet.id()).marking=false;
        }else if(packet.phase()==TRIGGER) {
            var effect=EFFECTS.get(packet.id());if(effect==null)return;
            effect.marking=false;effect.triggering=true;effect.start=owner.position();effect.target=packet.target();
            effect.velocity=effect.target.subtract(effect.start).scale(1d/8);effect.flightTicks=0;
        }
    }
    public static void tick(Minecraft mc) {
        if(!sync(mc) || mc.isPaused())return;
        var iterator=EFFECTS.values().iterator();
        while(iterator.hasNext()) {
            var effect=iterator.next();var owner=effect.owner;
            if(owner.isRemoved()){effect.stop();iterator.remove();continue;}
            effect.ticks++;
            if(effect.kind==SHIELD) {
                var particle=MdParticle.shield(RANDOM,owner.getEyePosition(),owner.getLookAngle());if(particle!=null)PARTICLES.add(particle);
            }else if(effect.kind==BEAM) {
                if(owner==local)owner.getAbilities().setWalkingSpeed(.1f-effect.ticks*.001f);
                // Scala `rangei(2,3) to 0` is an empty ascending range: do not invent charge particles.
            }else if(effect.triggering) {
                boolean finish=effect.flightTicks>=15;
                effect.flightTicks++;
                if(owner==local) {
                    // Original lerp intentionally overshoots after tick eight and still executes tick sixteen.
                    owner.setPos(effect.start.add(effect.target.subtract(effect.start).scale(effect.flightTicks/8f)));
                    owner.setDeltaMovement(effect.velocity);owner.resetFallDistance();owner.getAbilities().setWalkingSpeed(.07f);
                }
                for(int i=0;i<=10;i++)PARTICLES.add(MdParticle.jet(RANDOM,owner.position()));
                if(finish && owner==local) {
                    PacketDistributor.sendToServer(new MeltdownerEndPayload(effect.id));effect.stop();iterator.remove();
                }
            }else if(effect.marking && owner==local)effect.target=ElectricAttackTrace.traceBlocks(owner,12).getLocation();
        }
        for(var beam:BEAMS) {
            beam.ticks++;var particle=MdParticle.largeRay(RANDOM,beam.position,RailgunGeometry.direction(beam.yaw,beam.pitch));
            if(particle!=null)PARTICLES.add(particle);
        }
        BEAMS.removeIf(b->b.ticks>=50);
        PARTICLES.removeIf(p->{p.tick();return p.dead;});
    }
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !sync(Minecraft.getInstance()))return;
        var mc=Minecraft.getInstance();var pose=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        var camera=event.getCamera().getPosition();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double now=RailgunHandEffects.seconds();
        for(var effect:EFFECTS.values()) {
            if(effect.kind==JET && effect.marking && effect.owner==local) {
                pose.pushPose();pose.translate(effect.target.x-camera.x,effect.target.y-camera.y,effect.target.z-camera.z);
                var out=buffers.getBuffer(ACShaders.RIPPLE_MARK);
                for(int i=0;i<3;i++) {
                    var ring=RippleMarkAnimation.ring(now-effect.created,i);
                    pose.pushPose();pose.translate(0,ring.height(),0);pose.scale(ring.size(),1,ring.size());
                    out.addVertex(pose.last(),-.5f,0,-.5f).setUv(0,0).setColor(51,255,51,ring.alpha());
                    out.addVertex(pose.last(),.5f,0,-.5f).setUv(0,1).setColor(51,255,51,ring.alpha());
                    out.addVertex(pose.last(),.5f,0,.5f).setUv(1,1).setColor(51,255,51,ring.alpha());
                    out.addVertex(pose.last(),-.5f,0,.5f).setUv(1,0).setColor(51,255,51,ring.alpha());pose.popPose();
                }
                buffers.endBatch(ACShaders.RIPPLE_MARK);pose.popPose();
            }
            if(effect.kind==SHIELD || effect.kind==JET && effect.triggering && effect.flightTicks>0) {
                var owner=effect.owner;var position=owner.getPosition(partial).add(owner.getLookAngle()).add(0,1.1,0);
                pose.pushPose();pose.translate(position.x-camera.x,position.y-camera.y,position.z-camera.z);
                pose.mulPose(Axis.YP.rotationDegrees(-owner.yHeadRot));pose.mulPose(Axis.XP.rotationDegrees(owner.getXRot()));
                if(effect.kind==SHIELD) {
                    double dt=effect.lastRender==0?0:now-effect.lastRender;effect.lastRender=now;
                    effect.rotation+=(.8f+1.2f*Math.min(effect.ticks/30f,1))*dt;if(effect.rotation>=360)effect.rotation-=360;
                    pose.mulPose(Axis.ZP.rotationDegrees(effect.rotation));float size=1.8f*(.2f+.8f*Math.min(effect.ticks/15f,1));
                    pose.scale(size,size,1);int alpha=(int)(255*Math.min(effect.ticks/6f,1));var out=buffers.getBuffer(ACShaders.MD_SHIELD);
                    out.addVertex(pose.last(),-.5f,.5f,0).setUv(0,0).setColor(255,255,255,alpha);
                    out.addVertex(pose.last(),-.5f,-.5f,0).setUv(0,1).setColor(255,255,255,alpha);
                    out.addVertex(pose.last(),.5f,-.5f,0).setUv(1,1).setColor(255,255,255,alpha);
                    out.addVertex(pose.last(),.5f,.5f,0).setUv(1,0).setColor(255,255,255,alpha);buffers.endBatch(ACShaders.MD_SHIELD);
                }else {
                    pose.scale(1.5f,1.5f,1.5f);var out=buffers.getBuffer(ACShaders.DIAMOND_SHIELD);
                    float[][] vertices={{-1,0,0,0,0},{0,-1,0,1,1},{1,0,0,0,0},{0,1,0,1,1},{0,0,1,0,1}};
                    for(int index:new int[]{0,1,4,1,2,4,2,3,4,3,0,4}) {
                        var v=vertices[index];out.addVertex(pose.last(),v[0],v[1],v[2]).setUv(v[3],v[4]).setColor(255,255,255,255);
                    }
                    buffers.endBatch(ACShaders.DIAMOND_SHIELD);
                }
                pose.popPose();
            }
        }
        if(mc.getCameraEntity()!=null)for(var beam:BEAMS) {
            var frame=beam.frame(now);boolean first=beam.owner==local && mc.options.getCameraType().isFirstPerson();
            float yaw=beam.ticks==0?net.minecraft.util.Mth.rotLerp(partial,0,beam.yaw):beam.yaw;
            float pitch=beam.ticks==0?net.minecraft.util.Mth.rotLerp(partial,0,beam.pitch):beam.pitch;
            pose.pushPose();pose.translate(beam.position.x-camera.x,beam.position.y-camera.y,beam.position.z-camera.z);
            var glow=RailgunGeometry.glow(beam.position,mc.getCameraEntity().getPosition(partial),yaw,pitch,first,frame,1.5,0,0);
            int alpha=(int)(204*frame.alpha()*frame.glowAlpha());
            LegacyRailgunEffects.glow(glow.head(),alpha,ACShaders.MD_GLOW[0],pose,buffers);
            LegacyRailgunEffects.glow(glow.body(),alpha,ACShaders.MD_GLOW[1],pose,buffers);
            LegacyRailgunEffects.glow(glow.tail(),alpha,ACShaders.MD_GLOW[2],pose,buffers);
            pose.pushPose();pose.mulPose(RailgunGeometry.cylinderTransform(yaw,pitch,first,frame.length()));
            LegacyRailgunEffects.cylinder(pose,buffers,frame,.17,.98,216,248,216,230,ACShaders.MD_SMALL_CYLINDER);
            LegacyRailgunEffects.cylinder(pose,buffers,frame,.22,1,106,242,106,50,ACShaders.MD_SMALL_CYLINDER);
            pose.popPose();pose.popPose();
        }
        MdParticleRenderer.render(PARTICLES,event);
    }
    private MeltdownerEffects(){}
}
