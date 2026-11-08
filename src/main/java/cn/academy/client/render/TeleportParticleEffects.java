package cn.academy.client.render;

import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** TPParticleFactory's unlit sprites and Rigidbody movement, shared by the item teleport skills. */
public final class TeleportParticleEffects {
    private static final Random RANDOM=new Random();
    private static final List<Particle> PARTICLES=new ArrayList<>();
    private static ClientLevel world;private static Player local;
    private static final class Particle {
        Vec3 position,previous;final Vec3 velocity;final float size;final int initialAlpha;
        int age,alpha;
        Particle(Vec3 point,Vec3 velocity){position=previous=point;this.velocity=velocity;size=.1f+RANDOM.nextFloat()*(.2f-.1f);initialAlpha=153+RANDOM.nextInt(51);alpha=initialAlpha;}
        void tick(){previous=position;position=position.add(velocity);age++;float factor=age<5?age/5f:age>20?Math.max(0,1-(age-20)/20f):1;alpha=(int)(initialAlpha/255f*factor*255);}
    }
    private static boolean sync(Minecraft mc){if(world!=mc.level || local!=mc.player){PARTICLES.clear();world=mc.level;local=mc.player;}return world!=null && local!=null;}
    public static void trail(Player owner,Vec3 destination,boolean shift) {
        if(!sync(Minecraft.getInstance()))return;
        Vec3 position=owner.position().add(0,-.5,0),delta=destination.subtract(position),direction=delta.normalize();
        double distance=delta.length(),move=1,traveled=1,spread=shift?.05:.02;
        while(traveled<=distance) {
            position=position.add(direction.scale(move));
            PARTICLES.add(new Particle(position,new Vec3(range(-spread,spread),range(-.02,.05),range(-spread,spread))));
            move=shift?range(.6,1):range(1,2);traveled+=move;
        }
    }
    public static void markParticle(Vec3 position) {
        if(!sync(Minecraft.getInstance()))return;
        PARTICLES.add(new Particle(position.add(range(-1,1),range(.2,1.6)-1.6,range(-1,1)),
                new Vec3(range(-.03,.03),range(0,.05),range(-.03,.03))));
    }
    private static double range(double lo,double hi){return lo+RANDOM.nextDouble()*(hi-lo);}
    public static void tick(Minecraft mc){if(!sync(mc) || mc.isPaused())return;PARTICLES.forEach(Particle::tick);PARTICLES.removeIf(p->p.age>40);}
    public static void render(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !sync(mc))return;
        var pose=event.getPoseStack();var camera=event.getCamera();var cameraPosition=camera.getPosition();var buffers=mc.renderBuffers().bufferSource();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        for(var particle:PARTICLES) {
            if(particle.age==0)continue;
            var point=particle.previous.lerp(particle.position,partial).subtract(cameraPosition);pose.pushPose();pose.translate(point.x,point.y,point.z);
            pose.mulPose(Axis.YP.rotationDegrees(180-camera.getYRot()));float pitch=mc.options.getCameraType()==CameraType.THIRD_PERSON_FRONT?-camera.getXRot():camera.getXRot();pose.mulPose(Axis.XP.rotationDegrees(-pitch));
            var out=buffers.getBuffer(ACShaders.TP_PARTICLE);float half=particle.size/2;
            out.addVertex(pose.last(),-half,half,0).setUv(0,0).setColor(255,255,255,particle.alpha);
            out.addVertex(pose.last(),-half,-half,0).setUv(0,1).setColor(255,255,255,particle.alpha);
            out.addVertex(pose.last(),half,-half,0).setUv(1,1).setColor(255,255,255,particle.alpha);
            out.addVertex(pose.last(),half,half,0).setUv(1,0).setColor(255,255,255,particle.alpha);pose.popPose();
        }
        buffers.endBatch(ACShaders.TP_PARTICLE);
    }
    private TeleportParticleEffects(){}
}
