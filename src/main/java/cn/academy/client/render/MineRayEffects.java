package cn.academy.client.render;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.meltdowner.skill.MineRayContext;
import cn.academy.client.render.util.*;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.network.MineRayEffectPayload;
import cn.academy.registry.ACSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** Persistent EntityMineRayBasic/Expert/Luck beam and MRContextC sound lifecycle. */
public final class MineRayEffects {
    private static final Map<Integer,Ray> RAYS=new LinkedHashMap<>();
    private static final List<MdParticle> PARTICLES=new ArrayList<>();
    private static final ThunderClapRequests REQUESTS=new ThunderClapRequests();
    private static final Random RANDOM=new Random();
    private static ClientLevel world;
    private static Player local;
    private static long rawTime=-1,time=1;
    private static final class Ray {
        final Player owner;
        final int id,slot,variant;
        final RayAnimation miningAnimation=RayAnimation.mining(time);
        Vec3 position,previous;
        float yaw,pitch,previousYaw,previousPitch;
        double length;
        int ticks;
        Ray(Player player,int id,int slot,int variant) {
            owner=player;this.id=id;this.slot=slot;this.variant=variant;update();previous=position;previousYaw=yaw;previousPitch=pitch;
            ACContextSounds.handle(owner.getUUID(),"md.mine_loop",true,.3f,SoundSource.PLAYERS,id);
            world.playLocalSound(owner.getX(),owner.getY(),owner.getZ(),ACSounds.get("md.mine_"+MineRayStyle.STYLES[variant].startup()+"_startup"),
                    SoundSource.AMBIENT,.4f,1,false);
        }
        void update() {
            position=owner.position().add(0,variant==0?owner.getDimensions(Pose.STANDING).eyeHeight():1.55,0);
            Vec3 end=owner.getEyePosition().add(owner.getLookAngle().scale(15)),delta=end.subtract(position);
            yaw=(float)(-Math.atan2(delta.x,delta.z)*180/Math.PI);
            pitch=(float)(-Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))*180/Math.PI);
            length=delta.length();
        }
        void tick(){
            if(ticks>=233333)return;
            previous=position;previousYaw=yaw;previousPitch=pitch;update();ticks++;
            var particle=MdParticle.along(RANDOM,owner.getEyePosition(),owner.getLookAngle(),variant);
            if(particle!=null)PARTICLES.add(particle);
        }
        void stop(){ACContextSounds.handle(owner.getUUID(),"md.mine_loop",false,.3f,SoundSource.PLAYERS,id);}
    }
    private static boolean sync(Minecraft mc) {
        long raw=net.minecraft.Util.getMillis();
        if(rawTime>=0 && !mc.isPaused())time+=Math.max(0,raw-rawTime);rawTime=raw;
        if(world!=mc.level || local!=mc.player) {
            RAYS.values().forEach(Ray::stop);RAYS.clear();PARTICLES.clear();REQUESTS.end(-1);world=mc.level;local=mc.player;
        }
        return world!=null && local!=null;
    }
    public static int beginLocal(int slot) {
        if(!sync(Minecraft.getInstance()))return 0;
        var skill=AbilityRegistry.skill(AbilityState.load(local).controlSkill(slot));
        return skill!=null && MineRayContext.handles(skill)?REQUESTS.begin(slot):0;
    }
    public static void endLocal(int slot) {
        if(!sync(Minecraft.getInstance()))return;
        REQUESTS.end(slot);RAYS.values().removeIf(ray->{if(ray.owner==local && (slot<0 || slot==ray.slot)){ray.stop();return true;}return false;});
    }
    public static void tick(Minecraft mc) {
        if(!sync(mc) || mc.isPaused())return;
        PARTICLES.removeIf(particle->{particle.tick();return particle.dead;});
        RAYS.values().removeIf(ray->{if(ray.owner.isRemoved()){ray.stop();return true;}ray.tick();return false;});
        var queue=local.getPersistentData().getList(MineRayEffectPayload.QUEUE,10);
        for(int i=0;i<queue.size();i++) {
            var tag=queue.getCompound(i);int id=tag.getInt("Context"),variant=tag.getInt("Variant"),slot=tag.getInt("Slot");
            if(tag.getBoolean("Particles")) {
                if(RAYS.containsKey(id))PARTICLES.addAll(MdParticle.impact(RANDOM,new net.minecraft.core.BlockPos(tag.getInt("X"),tag.getInt("Y"),tag.getInt("Z"))));
                continue;
            }
            if(!tag.getBoolean("Start")){var ray=RAYS.remove(id);if(ray!=null)ray.stop();continue;}
            if(variant<0 || variant>=MineRayStyle.STYLES.length || RAYS.containsKey(id))continue;
            if(world.getEntity(tag.getInt("Caster")) instanceof Player owner && (owner!=local || REQUESTS.accepts(slot,tag.getInt("Request")))) {
                RAYS.put(id,new Ray(owner,id,slot,variant));
            }
        }
        local.getPersistentData().remove(MineRayEffectPayload.QUEUE);
    }
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES || !sync(Minecraft.getInstance()))return;
        var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();var pose=event.getPoseStack();
        Vec3 camera=event.getCamera().getPosition();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        for(var ray:RAYS.values()) {
            if(ray.ticks>=233333)continue; // Ray entity lifetime does not itself terminate MRContextC's loop sound.
            var style=MineRayStyle.STYLES[ray.variant];boolean first=ray.owner==local && mc.options.getCameraType().isFirstPerson();
            Vec3 position=ray.previous.lerp(ray.position,partial);float yaw=Mth.rotLerp(partial,ray.previousYaw,ray.yaw),pitch=Mth.rotLerp(partial,ray.previousPitch,ray.pitch);
            var frame=ray.miningAnimation.frame(time,ray.length,RANDOM);
            pose.pushPose();pose.translate(position.x-camera.x,position.y-camera.y,position.z-camera.z);
            var glow=RailgunGeometry.glow(position,camera,yaw,pitch,first,frame,style.glow(),0,0,ray.position);
            int alpha=(int)(style.glowAlpha()*frame.alpha()*frame.glowAlpha());var textures=ACShaders.MINE_RAY_GLOW[ray.variant];
            LegacyRailgunEffects.glow(glow.head(),alpha,textures[0],pose,buffers);
            LegacyRailgunEffects.glow(glow.body(),alpha,textures[1],pose,buffers);
            LegacyRailgunEffects.glow(glow.tail(),alpha,textures[2],pose,buffers);
            pose.pushPose();pose.mulPose(RailgunGeometry.cylinderTransform(yaw,pitch,first,frame.length()));
            LegacyRailgunEffects.cylinder(pose,buffers,frame,style.inner(),.98,style.red(),style.green(),style.blue(),style.innerAlpha());
            LegacyRailgunEffects.cylinder(pose,buffers,frame,style.outer(),1,style.outerRed(),style.outerGreen(),style.outerBlue(),50);
            pose.popPose();pose.popPose();
        }
        for(var particle:PARTICLES) {
            if(particle.age==0)continue; // RenderParticle skips particles until their first update initializes alpha.
            Vec3 position=particle.previous.lerp(particle.position,partial);
            pose.pushPose();pose.translate(position.x-camera.x,position.y-camera.y,position.z-camera.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-event.getCamera().getYRot()));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-event.getCamera().getXRot()));
            var type=particle.lucky?ACShaders.MD_PARTICLE_LUCK:ACShaders.MD_PARTICLE;var out=buffers.getBuffer(type);float half=particle.size/2;
            out.addVertex(pose.last(),-half,half,0).setUv(0,0).setColor(255,255,255,particle.alpha);
            out.addVertex(pose.last(),-half,-half,0).setUv(0,1).setColor(255,255,255,particle.alpha);
            out.addVertex(pose.last(),half,-half,0).setUv(1,1).setColor(255,255,255,particle.alpha);
            out.addVertex(pose.last(),half,half,0).setUv(1,0).setColor(255,255,255,particle.alpha);
            buffers.endBatch(type);pose.popPose();
        }
    }
    private MineRayEffects(){}
}
