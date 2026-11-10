package cn.academy.client.render.util;

import cn.academy.util.ImprovedNoise;
import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import java.util.*;

/** Direct geometry translation of TornadoEffect.scala; shared by wings and the plasma charge funnel. */
public final class TornadoMesh {
    private record Ring(double y,double width,double phase,double size){}
    private final List<Ring> rings=new ArrayList<>();
    private final double height,size,displacement,timeOffset;
    public TornadoMesh(Random random,double height,double size,double density,double displacement) {
        this.height=height;this.size=size;this.displacement=displacement;timeOffset=random.nextDouble()*20;
        double accumulated=0,step=height/40;
        while(accumulated<height) {
            accumulated+=step*(1+random.nextGaussian()*.2);
            if(random.nextDouble()<density) {
                rings.add(new Ring(accumulated,step*(1.8+random.nextDouble()*.4),random.nextDouble()*360,.9+random.nextDouble()*.3));
                if(random.nextDouble()<.35)rings.add(new Ring(accumulated,step*(1.8+random.nextDouble()*.4),random.nextDouble()*360,1.2+random.nextDouble()*.5));
            }
        }
    }
    public void draw(PoseStack pose,VertexConsumer out,double seconds,double alpha,Matrix3f inverseViewNormal,int light) {
        double time=seconds*4-timeOffset;int a=(int)(Math.clamp(alpha*.7,0,1)*255);
        var normalTransform=new Matrix3f(inverseViewNormal).mul(pose.last().normal());
        var light0=new Vector3f(.2f,1,-.7f).normalize();var light1=new Vector3f(-.2f,1,.7f).normalize();
        for(var ring:rings) {
            double ny=ring.y/height,scale=.3+Math.pow(ny*2,1.4);
            double dx=ImprovedNoise.noise(ny,time*.1)*scale*size*displacement,dz=ImprovedNoise.noise(ny,time*.1,1)*scale*size*displacement;
            double radius=(.5+.3*ImprovedNoise.noise(ny,.2*time)+.5*Math.pow(1.5*ny,2)+ImprovedNoise.noise(ny))*size*ring.size;
            double rotation=.1*(1+.5*ny)*time+ring.phase;
            for(int i=0;i<20;i++) {
                double r0=i/20.0*Math.PI*2,r1=((i+1)%20)/20.0*Math.PI*2;
                float x0=(float)(Math.sin(r0)*radius+dx),z0=(float)(Math.cos(r0)*radius+dz),x1=(float)(Math.sin(r1)*radius+dx),z1=(float)(Math.cos(r1)*radius+dz);
                float y0=(float)(ring.y+ring.width/2),y1=(float)(ring.y-ring.width/2),u0=(float)(i/20.0-rotation),u1=(float)(i/20.0-rotation+.05);
                // Original glNormal3d(x0,y0,0) precedes all four vertices. It excludes dx and is NOT normalized.
                var normal=new Vector3f((float)(Math.sin(r0)*radius),y0,0).mul(normalTransform);
                float brightness=Math.clamp(.4f+.6f*(Math.max(0,normal.dot(light0))+Math.max(0,normal.dot(light1))),0,1);
                // UV1 carries computed fixed-function light at 15-bit precision, avoiding clamped byte normals.
                int diffuse=Math.round(brightness*32767);
                vertex(pose,out,x0,y0,z0,u0,0,a,diffuse,light);
                vertex(pose,out,x0,y1,z0,u0,1,a,diffuse,light);
                vertex(pose,out,x1,y1,z1,u1,1,a,diffuse,light);
                vertex(pose,out,x1,y0,z1,u1,0,a,diffuse,light);
            }
        }
    }
    /** Original layered ribbons and noise, wrapped onto a sphere instead of a widening funnel. */
    public void drawSphere(PoseStack pose,VertexConsumer out,double seconds,double alpha,double sphereRadius) {
        double time=seconds*4-timeOffset;
        int a=(int)(Math.clamp(alpha*.7,0,1)*255);
        var light0=new Vector3f(.2f,1,-.7f).normalize();
        var light1=new Vector3f(-.2f,1,.7f).normalize();
        for(var ring:rings) {
            double ny=ring.y/height;
            double latitude=(ny-.5)*Math.PI;
            double halfWidth=ring.width/height*Math.PI*.5;
            double shell=sphereRadius*(1+(ring.size-1)*.2+.06*ImprovedNoise.noise(ny,time*.2));
            double dx=ImprovedNoise.noise(ny,time*.1)*sphereRadius*.06;
            double dz=ImprovedNoise.noise(ny,time*.1,1)*sphereRadius*.06;
            double rotation=.1*(1+.5*ny)*time+ring.phase;
            double top=Math.clamp(latitude+halfWidth,-Math.PI/2,Math.PI/2);
            double bottom=Math.clamp(latitude-halfWidth,-Math.PI/2,Math.PI/2);
            for(int i=0;i<32;i++) {
                double r0=i/32.0*Math.PI*2,r1=(i+1)/32.0*Math.PI*2;
                var normal=new Vector3f((float)(Math.sin(r0)*Math.cos(latitude)),(float)Math.sin(latitude),(float)(Math.cos(r0)*Math.cos(latitude)));
                float brightness=Math.clamp(.4f+.6f*(Math.max(0,normal.dot(light0))+Math.max(0,normal.dot(light1))),0,1);
                int diffuse=Math.round(brightness*32767);
                float u0=(float)(i/32.0-rotation),u1=(float)((i+1)/32.0-rotation);
                sphereVertex(pose,out,r0,top,shell,dx,dz,u0,0,a,diffuse);
                sphereVertex(pose,out,r0,bottom,shell,dx,dz,u0,1,a,diffuse);
                sphereVertex(pose,out,r1,bottom,shell,dx,dz,u1,1,a,diffuse);
                sphereVertex(pose,out,r1,top,shell,dx,dz,u1,0,a,diffuse);
            }
        }
    }
    private static void sphereVertex(PoseStack pose,VertexConsumer out,double longitude,double latitude,double radius,double dx,double dz,float u,float v,int alpha,int diffuse) {
        vertex(pose,out,(float)(Math.sin(longitude)*Math.cos(latitude)*radius+dx),(float)(Math.sin(latitude)*radius),
                (float)(Math.cos(longitude)*Math.cos(latitude)*radius+dz),u,v,alpha,diffuse,0xF000F0);
    }
    private static void vertex(PoseStack pose,VertexConsumer out,float x,float y,float z,float u,float v,int alpha,int diffuse,int light) {
        out.addVertex(pose.last(),x,y,z).setUv(u,v).setColor(255,255,255,alpha).setUv1(diffuse,0).setLight(light).setNormal(0,1,0);
    }
}
