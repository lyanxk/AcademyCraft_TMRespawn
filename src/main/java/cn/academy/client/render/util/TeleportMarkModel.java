package cn.academy.client.render.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** SimpleModelBiped and 1.12 ModelBox face order; order matters with blending and depth disabled. */
public final class TeleportMarkModel {
    private static final int[][] FACES={{5,1,2,6},{0,4,7,3},{5,4,0,1},{2,3,7,6},{1,0,3,2},{4,5,6,7}};
    public void draw(PoseStack pose,VertexConsumer out,boolean available) {
        box(pose,out,0,0,-4,-8,-4,8,8,8,0,0,0,0,false,available);
        box(pose,out,16,16,-4,0,-2,8,12,4,0,0,0,0,false,available);
        box(pose,out,40,16,-3,-2,-2,4,12,4,0,-5,2,0,false,available);
        box(pose,out,40,16,-1,-2,-2,4,12,4,0,5,2,0,true,available);
        box(pose,out,0,16,-2,0,-2,4,12,4,0,-1.9f,12,0,false,available);
        box(pose,out,0,16,-2,0,-2,4,12,4,0,1.9f,12,0,true,available);
        box(pose,out,32,0,-4,-8,-4,8,8,8,.5f,0,0,0,false,available);
    }
    private static void box(PoseStack pose,VertexConsumer out,int u,int v,float x,float y,float z,int dx,int dy,int dz,
                            float inflation,float pivotX,float pivotY,float pivotZ,boolean mirror,boolean available) {
        float x1=x+dx+inflation,y1=y+dy+inflation,z1=z+dz+inflation;x-=inflation;y-=inflation;z-=inflation;
        if(mirror){float swap=x1;x1=x;x=swap;}
        float[][] vertices={{x,y,z},{x1,y,z},{x1,y1,z},{x,y1,z},{x,y,z1},{x1,y,z1},{x1,y1,z1},{x,y1,z1}};
        int[][] uv={{u+dz+dx,v+dz,u+dz+dx+dz,v+dz+dy},{u,v+dz,u+dz,v+dz+dy},
                {u+dz,v,u+dz+dx,v+dz},{u+dz+dx,v+dz,u+dz+dx+dx,v},
                {u+dz,v+dz,u+dz+dx,v+dz+dy},{u+dz+dx+dz,v+dz,u+dz+dx+dz+dx,v+dz+dy}};
        pose.pushPose();pose.translate(pivotX*.0625f,pivotY*.0625f,pivotZ*.0625f);
        for(int face=0;face<6;face++)for(int index=0;index<4;index++) {
            int corner=mirror?3-index:index;var point=vertices[FACES[face][corner]];
            float tu=(corner==0 || corner==3?uv[face][2]:uv[face][0])/64f,tv=(corner<2?uv[face][1]:uv[face][3])/32f;
            out.addVertex(pose.last(),point[0]*.0625f,point[1]*.0625f,point[2]*.0625f).setUv(tu,tv).setColor(255,available?255:51,available?255:51,255);
        }
        pose.popPose();
    }
}
