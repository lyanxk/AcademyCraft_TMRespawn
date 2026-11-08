package cn.academy.client.render;

import cn.academy.client.render.util.*;
import cn.academy.network.MdRayEffectPayload;
import cn.academy.registry.ACSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** EntityBarrageRayPre and EntityMdRayBarrage; the burst shares one animation across its sub-rays. */
public final class BarrageEffects {
    private static final Random RANDOM=new Random();
    private static final List<Effect> EFFECTS=new ArrayList<>();
    private static ClientLevel world;
    private static final class Effect {
        final Vec3 position;
        final boolean burst;
        final int life;
        final double length,created=RailgunHandEffects.seconds();
        final List<float[]> angles=new ArrayList<>();
        int ticks;
        double lastFrame,widthWiggle,glowWiggle;
        Effect(MdRayEffectPayload packet) {
            position=packet.from();burst=packet.kind()==MdRayEffectPayload.BARRAGE;
            life=packet.kind()==MdRayEffectPayload.PRE_PLAIN?30:50;
            if(burst) {
                length=15;float range=50+RANDOM.nextFloat()*10;int count=25+RANDOM.nextInt(5);
                for(int i=0;i<count;i++)angles.add(new float[]{(float)packet.to().x-range+RANDOM.nextFloat()*2*range,
                        (float)packet.to().y-range/2+RANDOM.nextFloat()*range});
            }else {
                var delta=packet.to().subtract(position);length=delta.length();
                angles.add(new float[]{(float)-Math.toDegrees(Math.atan2(delta.x,delta.z)),
                        (float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)))});
            }
        }
        RayAnimation.Frame frame(double time) {
            if(lastFrame!=0) {
                long dt=(long)((time-lastFrame)*1000);
                widthWiggle=Math.clamp(widthWiggle+dt*(-.4+RANDOM.nextDouble()*.8)/1000,0,.1);
                glowWiggle=Math.clamp(glowWiggle+dt*(-.4+RANDOM.nextDouble()*.8)/1000,0,.1);
            }
            lastFrame=time;long dt=(long)((time-created)*1000),lifeMS=life*50L;
            int blend=burst?100:200,fade=burst?300:400;
            double alpha=dt>lifeMS-fade?1-(double)(dt+fade-lifeMS)/fade:1;
            // BRP's clampd(1,0,...) keeps width at 1 in its positive-time shrinking branch.
            double width=burst?widthWiggle+(dt>lifeMS-300?1-(double)(dt+300-lifeMS)/300:1):1;
            return new RayAnimation.Frame((dt<blend?(double)dt/blend:1)*length,width,alpha,(.9+glowWiggle)*alpha);
        }
    }
    private static void sync(Minecraft mc){if(world!=mc.level){EFFECTS.clear();world=mc.level;}}
    public static void receive(MdRayEffectPayload payload) {
        var mc=Minecraft.getInstance();sync(mc);
        if(world!=null && payload.kind()>=MdRayEffectPayload.BARRAGE && payload.kind()<=MdRayEffectPayload.PRE_SCATTER)
            EFFECTS.add(new Effect(payload));
    }
    public static void tick(Minecraft mc) {
        sync(mc);if(world==null || mc.isPaused())return;
        for(var effect:EFFECTS) {
            if(effect.ticks++==0)world.playLocalSound(effect.position.x,effect.position.y,effect.position.z,
                    ACSounds.MD_RAY.get(),SoundSource.AMBIENT,effect.burst?.5f:.8f,1,false);
        }
        EFFECTS.removeIf(e->e.ticks>=e.life);
    }
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();sync(mc);if(world==null || mc.getCameraEntity()==null)return;
        var pose=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        var camera=event.getCamera().getPosition();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        var feet=mc.getCameraEntity().getPosition(partial);double time=RailgunHandEffects.seconds();
        for(var effect:EFFECTS) {
            var frame=effect.frame(time);int alpha=(int)(127*frame.alpha()*frame.glowAlpha());
            pose.pushPose();pose.translate(effect.position.x-camera.x,effect.position.y-camera.y,effect.position.z-camera.z);
            for(var angle:effect.angles) {
                float yaw=angle[0],pitch=angle[1];
                if(effect.ticks==0){yaw=net.minecraft.util.Mth.rotLerp(partial,0,yaw);pitch=net.minecraft.util.Mth.rotLerp(partial,0,pitch);}
                var glow=RailgunGeometry.freeGlow(effect.position,feet,yaw,pitch,frame,effect.burst?.3:.4);
                LegacyRailgunEffects.glow(glow.head(),alpha,ACShaders.MD_SMALL_GLOW[0],pose,buffers);
                LegacyRailgunEffects.glow(glow.body(),alpha,ACShaders.MD_SMALL_GLOW[1],pose,buffers);
                LegacyRailgunEffects.glow(glow.tail(),alpha,ACShaders.MD_SMALL_GLOW[2],pose,buffers);
                pose.pushPose();pose.mulPose(RailgunGeometry.freeCylinderTransform(yaw,pitch,frame.length()));
                LegacyRailgunEffects.cylinder(pose,buffers,frame,effect.burst?.03:.045,.98,216,248,216,230,ACShaders.MD_SMALL_CYLINDER);
                LegacyRailgunEffects.cylinder(pose,buffers,frame,effect.burst?.045:.052,1,106,242,106,50,ACShaders.MD_SMALL_CYLINDER);
                pose.popPose();
            }
            pose.popPose();
        }
    }
    private BarrageEffects(){}
}
