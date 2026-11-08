package cn.academy.client.render.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.Random;

/** EntityMdRaySmall's stationary entity, first-update-relative lifetime and render-time curves. */
public final class MdSmallRay {
    public final Vec3 position,destination;
    public final float yaw,pitch;
    public final double length;
    private final double created;
    private double lastFrame,widthWiggle,glowWiggle;
    public int ticks;

    public MdSmallRay(double created,Vec3 from,Vec3 to) {
        this.created=created;position=from;destination=to;
        Vec3 delta=to.subtract(from);double xz=delta.x*delta.x+delta.z*delta.z;
        yaw=(float)(-Math.atan2(delta.x,delta.z)*180/Math.PI);
        pitch=(float)(-Math.atan2(delta.y,Math.sqrt(xz))*180/Math.PI);
        length=Math.sqrt(xz+delta.y*delta.y);
    }
    public MdParticle tick(Random random) {
        if(dead())return null;
        ticks++;
        // The callback on tick 15 marks the ray dead, but subclass onUpdate still emits its last particle.
        return MdParticle.smallRay(random,position,RailgunGeometry.direction(yaw,pitch));
    }
    public boolean dead(){return ticks>=15;}
    public float renderYaw(float partial){return ticks==0?Mth.rotLerp(partial,0,yaw):yaw;}
    public float renderPitch(float partial){return ticks==0?Mth.rotLerp(partial,0,pitch):pitch;}

    public RayAnimation.Frame frame(double time,Random random) {
        if(lastFrame!=0) {
            long dt=(long)((time-lastFrame)*1000);
            // The width walk is unused by getWidth(), but the original still advances its random stream.
            widthWiggle=Math.clamp(widthWiggle+dt*(-.4+random.nextDouble()*.8)/1000.0,0,.1);
            glowWiggle=Math.clamp(glowWiggle+dt*(-.4+random.nextDouble()*.8)/1000.0,0,.1);
        }
        lastFrame=time;
        long dt=(long)((time-created)*1000);
        double alpha=dt>300?1-(double)(dt+400-700)/400:1;
        double width=dt>200?1-Math.clamp((double)(dt-200)/500,0,1):1;
        return new RayAnimation.Frame((dt<200?(double)dt/200:1)*length,width,alpha,(.9+glowWiggle)*alpha);
    }
}
