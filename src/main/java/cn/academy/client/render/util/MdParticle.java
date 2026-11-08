package cn.academy.client.render.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** MdParticleFactory + Particle + Rigidbody; no collide listener is registered by either mining emitter. */
public final class MdParticle {
    public Vec3 position,previous,velocity;
    public final int life,initialAlpha;
    public final float size;
    public final boolean lucky;
    public final double gravity;
    public int age,alpha;
    public boolean dead;
    MdParticle(Random random,Vec3 position,Vec3 velocity,boolean lucky,double gravity) {
        this.position=this.previous=position;this.velocity=velocity;this.lucky=lucky;this.gravity=gravity;
        life=25+random.nextInt(30);initialAlpha=76+random.nextInt(76);
        size=.05f+random.nextFloat()*(.07f-.05f);alpha=initialAlpha;
    }
    public static MdParticle along(Random random,Vec3 eye,Vec3 look,int variant) {
        if(random.nextDouble()>=(variant==0?.5:.6))return null;
        Vec3 position=eye.add(look.scale(range(random,0,10)));
        Vec3 velocity=new Vec3(range(random,-.03,.03),range(random,-.03,.03),range(random,-.03,.03));
        return new MdParticle(random,position,velocity,variant==2,0);
    }
    public static MdParticle smallRay(Random random,Vec3 rayPosition,Vec3 look) {
        // VecUtils.lookingPos(EntityMdRaySmall) includes Entity's default height * .85 eye height.
        Vec3 position=rayPosition.add(0,(double)(1.8f*.85f),0).add(look.scale(range(random,0,10)));
        Vec3 velocity=new Vec3(range(random,-.015,.015),range(random,-.015,.015),range(random,-.015,.015));
        return new MdParticle(random,position,velocity,false,0);
    }
    public static List<MdParticle> radiation(Random random,Random vectors,Vec3 origin,float width,float height) {
        int count=random.nextInt(3);
        List<MdParticle> result=new ArrayList<>(count);
        while(count-->0) {
            double radius=range(random,.6,.7)*width;
            double theta=random.nextDouble()*2*Math.PI;
            double y=range(random,0,height);
            Vec3 position=origin.add(radius*Math.sin(theta),y,radius*Math.cos(theta));
            // VecUtils.random owns a separate RNG and returns three independent, unnormalized components.
            Vec3 velocity=new Vec3(-1+2*vectors.nextDouble(),-1+2*vectors.nextDouble(),-1+2*vectors.nextDouble()).scale(.02);
            result.add(new MdParticle(random,position,velocity,false,0));
        }
        return result;
    }
    public static List<MdParticle> missile(Random random,Vec3 origin) {
        int count=1+random.nextInt(2);
        List<MdParticle> result=new ArrayList<>(count);
        while(count-->0) {
            double radius=range(random,.5,1),theta=range(random,0,Math.PI*2),height=range(random,-1.2,0);
            Vec3 position=origin.add(radius*Math.sin(theta),height,radius*Math.cos(theta));
            Vec3 velocity=new Vec3(range(random,-.02,.02),range(random,.01,.05),range(random,-.02,.02));
            result.add(new MdParticle(random,position,velocity,false,0));
        }
        return result;
    }
    public static MdParticle shield(Random random,Vec3 eye,Vec3 look) {
        if(random.nextFloat()>=.3f)return null;
        var position=eye.add(look).add(range(random,-.5,.5),range(random,-.5,.5),range(random,-.5,.5));
        return new MdParticle(random,position,new Vec3(range(random,-.02,.02),range(random,-.01,.05),range(random,-.02,.02)),false,0);
    }
    public static MdParticle jet(Random random,Vec3 position) {
        return new MdParticle(random,position.add(range(random,-.3,.3),range(random,-.3,.3),range(random,-.3,.3)),
                new Vec3(range(random,-.02,.02),range(random,-.02,.02),range(random,-.02,.02)),false,0);
    }
    public static MdParticle largeRay(Random random,Vec3 position,Vec3 look) {
        if(random.nextDouble()>=.8)return null;
        return new MdParticle(random,position.add(0,(double)(1.8f*.85f),0).add(look.scale(range(random,0,10))),
                new Vec3(range(random,-.03,.03),range(random,-.03,.03),range(random,-.03,.03)),false,0);
    }
    public static List<MdParticle> impact(Random random,BlockPos pos) {
        int max=2+random.nextInt(1); // RandUtils.rangei excludes its upper bound; Scala 0 to max is inclusive.
        List<MdParticle> result=new ArrayList<>(max+1);
        for(int i=0;i<=max;i++) {
            Vec3 position=new Vec3(pos.getX()+range(random,-.2,1.2),pos.getY()+range(random,-.2,1.2),pos.getZ()+range(random,-.2,1.2));
            Vec3 velocity=new Vec3(range(random,-.06,.06),range(random,-.06,.06),range(random,-.06,.06));
            // MineRaysBase.particleTexture is never assigned by any of the three original skills.
            result.add(new MdParticle(random,position,velocity,false,.01));
        }
        return result;
    }
    public void tick() {
        previous=position;age++;
        velocity=new Vec3(velocity.x,velocity.y-gravity,velocity.z);
        position=position.add(velocity);
        float initial=initialAlpha/255f;
        if(age>life) {
            float remaining=1-(float)(age-life)/20;
            if(remaining<0){dead=true;remaining=0;}
            alpha=(int)(remaining*initial*255);
        }else if(age<5)alpha=(int)(initial*((float)age/5)*255);
        else alpha=(int)(initial*255);
    }
    private static double range(Random random,double min,double max){return min+random.nextDouble()*(max-min);}
}
