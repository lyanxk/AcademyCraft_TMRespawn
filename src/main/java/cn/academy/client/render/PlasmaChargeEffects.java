package cn.academy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import cn.academy.client.render.util.TornadoMesh;
import java.util.Random;

/** Inward blue energy streams, spherical wind ribbons and short surface discharges. */
final class PlasmaChargeEffects {
    private final Vec3[][] sparks=new Vec3[5][13];
    private record Stream(Vec3 direction,Vec3 tangent,Vec3 bitangent,double phase,double reach,double speed){}
    private final Stream[] streams=new Stream[36];
    private record Orbit(Vec3 axis,Vec3 tangent,Vec3 normal,double radius,double phase,double speed,double span,double width,boolean floating){}
    private final Orbit[] orbits=new Orbit[30];
    private final TornadoMesh windShell;

    PlasmaChargeEffects(Random random) {
        windShell=new TornadoMesh(random,2,1,1,.3);
        double rotation=random.nextDouble()*Math.PI*2;
        for(int i=0;i<orbits.length;i++) {
            boolean floating=i>=20;
            double azimuth=random.nextDouble()*Math.PI*2,tilt=(random.nextDouble()-.5)*Math.PI;
            var axis=new Vec3(Math.cos(azimuth),0,Math.sin(azimuth));
            var tangent=new Vec3(-Math.sin(azimuth)*Math.cos(tilt),Math.sin(tilt),Math.cos(azimuth)*Math.cos(tilt));
            double radius=2.65+random.nextDouble()*.3;
            orbits[i]=new Orbit(axis,tangent,axis.cross(tangent),radius,random.nextDouble()*Math.PI*2,
                    (random.nextBoolean()?1:-1)*(floating?.15+random.nextDouble()*.25:.45+random.nextDouble()*.7),
                    floating?.12+random.nextDouble()*.25:1.6+random.nextDouble()*3,
                    floating?.025+random.nextDouble()*.025:.035+random.nextDouble()*.045,floating);
        }
        for(int i=0;i<streams.length;i++) {
            double y=1-2*(i+.5)/streams.length,angle=rotation+i*2.399963,ring=Math.sqrt(1-y*y);
            var direction=new Vec3(ring*Math.cos(angle),y,ring*Math.sin(angle));
            var tangent=direction.cross(new Vec3(0,1,0)).normalize();
            streams[i]=new Stream(direction,tangent,direction.cross(tangent),random.nextDouble(),8+random.nextDouble()*4,.975+random.nextDouble()*.45);
        }
        for(var spark:sparks) {
            var normal=new Vec3(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5).normalize();
            var tangent=normal.cross(Math.abs(normal.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0)).normalize();
            for(int i=0;i<spark.length;i++) {
                double angle=(i/(double)(spark.length-1)-.5)*1.2;
                spark[i]=normal.scale(Math.cos(angle)).add(tangent.scale(Math.sin(angle)))
                        .add((random.nextDouble()-.5)*.06,(random.nextDouble()-.5)*.06,(random.nextDouble()-.5)*.06).normalize();
            }
        }
    }

    static float merge(float progress) {
        float t=Math.clamp((progress-.65f)/.35f,0,1);
        return t*t*(3-2*t);
    }

    static float coreGrowth(float progress) {
        float t=Math.clamp((progress-.18f)/.82f,0,1);
        return t*t*(3-2*t);
    }

    /** Split front/back arcs around the sphere so foreground ribbons cross its face naturally. */
    void drawFormed(PoseStack pose,VertexConsumer out,Vec3 cameraOffset,double time,float opacity,boolean front) {
        for(var orbit:orbits) {
            int segments=orbit.floating()?6:40;
            double head=orbit.phase()+time*orbit.speed();
            for(int i=0;i<segments;i++) {
                double angle0=head-orbit.span()+orbit.span()*i/segments;
                double angle1=head-orbit.span()+orbit.span()*(i+1)/segments;
                var a=orbitPoint(orbit,angle0,time);var b=orbitPoint(orbit,angle1,time);
                if((a.add(b).dot(cameraOffset)>0)!=front)continue;
                var side=b.subtract(a).cross(cameraOffset.subtract(a)).normalize();
                float taper=(float)Math.sin(Math.PI*(i+.5)/segments);
                float intensity=opacity*taper*(float)(.75+.25*Math.sin(time*1.4+orbit.phase()));
                double width=orbit.width()*(.45+.55*taper);
                ribbon(pose,out,a,b,side,width*4,intensity*.16f,.08f,.5f,1);
                ribbon(pose,out,a,b,side,width*1.6,intensity*.65f,.2f,.8f,1);
                ribbon(pose,out,a,b,side,width*.45,intensity,.78f,.96f,1);
            }
        }
    }

    private static Vec3 orbitPoint(Orbit orbit,double angle,double time) {
        double ripple=Math.sin(angle*3-time*.8+orbit.phase())*.05;
        double radius=orbit.radius()+ripple;
        double drift=Math.sin(time*.65+orbit.phase())*(orbit.floating()?.12:.05);
        return orbit.axis().scale(Math.cos(angle))
                .add(orbit.tangent().scale(Math.sin(angle)))
                .add(orbit.normal().scale(drift)).normalize().scale(radius);
    }

    void draw(PoseStack pose,MultiBufferSource buffers,Vec3 cameraOffset,double time,float progress,float opacity) {
        if(progress>=1)return;
        float merge=merge(progress),fade=(1-merge)*opacity;
        drawStreams(pose,buffers.getBuffer(ACShaders.SUB_ARCS),cameraOffset,time,progress,opacity);
        double radius=4.8-2.1*merge;
        windShell.drawSphere(pose,buffers.getBuffer(ACShaders.VECTOR_TORNADO),time,fade*.8,radius);
        var arc=buffers.getBuffer(ACShaders.ARC);
        int frame=(int)(time*12);
        for(int index=0;index<sparks.length;index++) {
            if((frame+index*3)%7>1)continue;
            var spark=sparks[index];double sparkRadius=2.2+.45*progress;
            for(int i=1;i<spark.length;i++) {
                var a=spark[i-1].scale(sparkRadius);var b=spark[i].scale(sparkRadius);
                var side=b.subtract(a).cross(cameraOffset.subtract(a)).normalize().scale(.025);
                arcVertex(pose,arc,a.add(side),0,0,fade);
                arcVertex(pose,arc,a.subtract(side),0,1,fade);
                arcVertex(pose,arc,b.subtract(side),1,1,fade);
                arcVertex(pose,arc,b.add(side),1,0,fade);
            }
        }
    }

    private void drawStreams(PoseStack pose,VertexConsumer out,Vec3 cameraOffset,double time,float progress,float fade) {
        for(var stream:streams) {
            double head=(time*stream.speed()+stream.phase())%1;
            double tail=Math.max(0,head-.42);
            for(int i=0;i<24;i++) {
                double t0=tail+(head-tail)*i/24,t1=tail+(head-tail)*(i+1)/24;
                var a=streamPoint(stream,t0,time,progress);var b=streamPoint(stream,t1,time,progress);
                var side=b.subtract(a).cross(cameraOffset.subtract(a)).normalize();
                float strength=fade*(float)Math.sin(Math.PI*(i+.5)/24)*Math.min(1,(float)head*8);
                // A wide cyan sheath and a narrow white-blue core produce a luminous curved strand.
                ribbon(pose,out,a,b,side,.16,strength*.18f,.08f,.55f,1);
                ribbon(pose,out,a,b,side,.055,strength*.65f,.25f,.8f,1);
                ribbon(pose,out,a,b,side,.016,strength,.8f,.96f,1);
            }
        }
    }

    private static Vec3 streamPoint(Stream stream,double t,double time,float progress) {
        double remaining=1-t,angle=stream.phase()*Math.PI*2+t*3.6+time*.35;
        double curl=Math.sin(Math.PI*t)*remaining*3;
        double radius=stream.reach()*remaining*remaining+coreGrowth(progress)*2*t;
        return stream.direction().scale(radius).add(stream.tangent().scale(Math.cos(angle)*curl))
                .add(stream.bitangent().scale(Math.sin(angle)*curl));
    }

    private static void ribbon(PoseStack pose,VertexConsumer out,Vec3 a,Vec3 b,Vec3 side,double width,float alpha,float red,float green,float blue) {
        var offset=side.scale(width);
        streamVertex(pose,out,a.add(offset),0,0,alpha,red,green,blue);
        streamVertex(pose,out,a.subtract(offset),0,1,alpha,red,green,blue);
        streamVertex(pose,out,b.subtract(offset),1,1,alpha,red,green,blue);
        streamVertex(pose,out,b.add(offset),1,0,alpha,red,green,blue);
    }

    private static void streamVertex(PoseStack pose,VertexConsumer out,Vec3 p,float u,float v,float alpha,float red,float green,float blue) {
        out.addVertex(pose.last(),(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(red,green,blue,alpha);
    }

    private static void arcVertex(PoseStack pose,VertexConsumer out,Vec3 p,float u,float v,float alpha) {
        out.addVertex(pose.last(),(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(.75f,.88f,1,alpha);
    }
}
