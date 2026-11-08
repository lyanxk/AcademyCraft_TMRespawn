package cn.academy.client.render.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

/** RenderMarker's eight corners, three 0.2-width arms per corner, and slow unpaused bob. */
public final class LegacyEntityMarker {
    private static final long BEGIN=System.nanoTime();
    private static final int[][] CORNERS={{0,0,0},{1,0,0},{1,0,1},{0,0,1},{0,1,0},{1,1,0},{1,1,1},{0,1,1}};
    public static void draw(PoseStack pose,VertexConsumer out,float width,float height,int red,int green,int blue,int alpha) {
        pose.pushPose();pose.translate(-width/2,.05*Math.sin(((System.nanoTime()-BEGIN)/1.0e9)/400),-width/2);
        float length=.2f*width;
        for(int i=0;i<8;i++) {
            pose.pushPose();pose.translate(width*CORNERS[i][0],height*CORNERS[i][1],width*CORNERS[i][2]);pose.mulPose(Axis.YP.rotationDegrees(-90*(i%4)));
            line(pose,out,0,i<4?length:-length,0,red,green,blue,alpha);
            line(pose,out,length,0,0,red,green,blue,alpha);line(pose,out,0,0,length,red,green,blue,alpha);pose.popPose();
        }
        pose.popPose();
    }
    private static void line(PoseStack pose,VertexConsumer out,float x,float y,float z,int r,int g,int b,int a) {
        out.addVertex(pose.last(),0,0,0).setColor(r,g,b,a);out.addVertex(pose.last(),x,y,z).setColor(r,g,b,a);
    }
    private LegacyEntityMarker(){}
}
