package cn.academy.client.render;

import cn.academy.ability.vanilla.meltdowner.skill.ElectronBomb;
import cn.academy.client.render.util.*;
import cn.academy.entity.ACElectronBallEntity;
import cn.academy.registry.ACSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** ElectronBomb.EffectDelegate and EntityMdRaySmall, independent of the already-finished server context. */
public final class MdSmallRayEffects {
    private static final List<MdSmallRay> RAYS=new ArrayList<>();
    private static final List<MdParticle> PARTICLES=new ArrayList<>();
    private static final Random RANDOM=new Random();
    private static ClientLevel world;
    private static void sync(Minecraft mc) {
        if(world!=mc.level){RAYS.clear();PARTICLES.clear();world=mc.level;}
    }
    public static void receive(int ballId) {
        var mc=Minecraft.getInstance();sync(mc);
        if(world==null || !(world.getEntity(ballId) instanceof ACElectronBallEntity ball)
                || !(ball.owner() instanceof Player owner))return;
        // Do not retry missing entities/owners or use a server position snapshot. Original listener drops them.
        var from=ball.position().add(0,owner.getDimensions(Pose.STANDING).eyeHeight(),0);
        RAYS.add(new MdSmallRay(RailgunHandEffects.seconds(),from,ElectronBomb.destination(owner)));
    }
    public static void tick(Minecraft mc) {
        sync(mc);if(world==null || mc.isPaused())return;
        for(var ray:RAYS) {
            if(ray.ticks==0)world.playLocalSound(ray.position.x,ray.position.y,ray.position.z,
                    ACSounds.MD_RAY.get(),SoundSource.AMBIENT,.8f,1,false);
            var particle=ray.tick(RANDOM);if(particle!=null)PARTICLES.add(particle);
        }
        RAYS.removeIf(MdSmallRay::dead);
        // Original world entity iteration also reaches the particles just appended by a ray's update.
        PARTICLES.removeIf(particle->{particle.tick();return particle.dead;});
    }
    public static void receive(cn.academy.network.MdRayEffectPayload packet) {
        var mc=Minecraft.getInstance();sync(mc);if(world==null)return;
        if(packet.kind()==cn.academy.network.MdRayEffectPayload.RAY)
            RAYS.add(new MdSmallRay(RailgunHandEffects.seconds(),packet.from(),packet.to()));
        else if(packet.kind()==cn.academy.network.MdRayEffectPayload.MISSILE_PARTICLES
                && world.getEntity(packet.caster()) instanceof Player player)
            PARTICLES.addAll(MdParticle.missile(RANDOM,player.position().add(0,1.6,0)));
        else BarrageEffects.receive(packet);
    }
    public static int activeRayCount(){return RAYS.size();}
    public static int activeParticleCount(){return PARTICLES.size();}
    public record RaySnapshot(net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to,int ticks){}
    public static List<RaySnapshot> raySnapshots(){return RAYS.stream().map(ray->new RaySnapshot(ray.position,ray.destination,ray.ticks)).toList();}
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();sync(mc);if(world==null || mc.getCameraEntity()==null)return;
        var pose=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();var camera=event.getCamera();
        var cameraPosition=camera.getPosition();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        // Old RenderManager's x/y/z were relative to interpolated camera-entity feet, even in third person.
        var oldRenderOrigin=mc.getCameraEntity().getPosition(partial);
        double now=RailgunHandEffects.seconds();
        for(var ray:RAYS) {
            var frame=ray.frame(now,RANDOM);float yaw=ray.renderYaw(partial),pitch=ray.renderPitch(partial);
            pose.pushPose();pose.translate(ray.position.x-cameraPosition.x,ray.position.y-cameraPosition.y,ray.position.z-cameraPosition.z);
            var glow=RailgunGeometry.freeGlow(ray.position,oldRenderOrigin,yaw,pitch,frame,.3);
            int alpha=(int)(127*frame.alpha()*frame.glowAlpha());var textures=ACShaders.MD_SMALL_GLOW;
            LegacyRailgunEffects.glow(glow.head(),alpha,textures[0],pose,buffers);
            LegacyRailgunEffects.glow(glow.body(),alpha,textures[1],pose,buffers);
            LegacyRailgunEffects.glow(glow.tail(),alpha,textures[2],pose,buffers);
            pose.pushPose();pose.mulPose(RailgunGeometry.freeCylinderTransform(yaw,pitch,frame.length()));
            LegacyRailgunEffects.cylinder(pose,buffers,frame,.03,.98,216,248,216,230,ACShaders.MD_SMALL_CYLINDER);
            LegacyRailgunEffects.cylinder(pose,buffers,frame,.045,1,106,242,106,50,ACShaders.MD_SMALL_CYLINDER);
            pose.popPose();pose.popPose();
        }
        MdParticleRenderer.render(PARTICLES,event);
    }
    private MdSmallRayEffects(){}
}
