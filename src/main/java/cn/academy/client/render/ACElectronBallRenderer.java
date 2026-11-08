package cn.academy.client.render;

import cn.academy.AcademyCraft;
import cn.academy.entity.ACElectronBallEntity;
import cn.academy.client.render.util.MdBallAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.Minecraft;
import com.mojang.math.Axis;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.resources.ResourceLocation;

/** EntityMdBall.R / RenderIcon: two unlit, offset billboards without depth writes. */
public final class ACElectronBallRenderer extends EntityRenderer<ACElectronBallEntity> {
    private static final java.util.Map<ACElectronBallEntity,MdBallAnimation> ANIMATIONS = new java.util.WeakHashMap<>();
    private static final java.util.Random GLOBAL_RANDOM = new java.util.Random();
    public static void spawned(ACElectronBallEntity ball) {
        ANIMATIONS.put(ball,new MdBallAnimation(RailgunHandEffects.seconds()));
    }
    public ACElectronBallRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public void render(ACElectronBallEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        // Original shouldRenderInPass(1): draw after translucent terrain, not in the opaque entity pass.
    }

    public static void renderDeferred(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();if(mc.level==null || mc.player==null)return;
        var camera=event.getCamera().getPosition();
        for(var entity:mc.level.entitiesForRendering())
            if(entity instanceof ACElectronBallEntity ball && ball.shouldRender(camera.x,camera.y,camera.z))
                renderBall(ball,event);
    }

    private static void renderBall(ACElectronBallEntity entity,RenderLevelStageEvent event) {
        var minecraft=Minecraft.getInstance();
        if(!entity.prepareRenderPosition())return;
        double now=RailgunHandEffects.seconds();
        var animation=ANIMATIONS.computeIfAbsent(entity,ignored->new MdBallAnimation(now));
        var frame=animation.frame(now,entity.renderLife(),entity::nextAnimationInt,GLOBAL_RANDOM::nextDouble);
        var local=minecraft.player;
        float partialTick=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        var localInterpolated=local.getPosition(partialTick);
        var camera=event.getCamera();var cameraPosition=camera.getPosition();
        var pose=event.getPoseStack();var buffers=minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        // Old render origin was relative to the interpolated local player's feet; MdBall
        // deliberately replaced its arguments with current ball minus current local feet.
        pose.translate(localInterpolated.x-cameraPosition.x,localInterpolated.y-cameraPosition.y,
                localInterpolated.z-cameraPosition.z);
        pose.translate(frame.x(),frame.y(),frame.z());
        pose.translate((float)(entity.getX()-local.getX()),(float)(entity.getY()-local.getY()+1.6),
                (float)(entity.getZ()-local.getZ()));
        // Old RenderManager adds 180 to front-view yaw but does not invert playerViewX.
        float pitch=minecraft.options.getCameraType()==net.minecraft.client.CameraType.THIRD_PERSON_FRONT
                ? -camera.getXRot() : camera.getXRot();
        quad(buffers,pose,ACShaders.MD_BALL_GLOW,.7f*frame.size(),frame.glowAlpha(),camera.getYRot(),pitch);
        quad(buffers,pose,ACShaders.MD_BALL[frame.texture()],.5f*frame.size(),frame.coreAlpha(),camera.getYRot(),pitch);
        pose.popPose();
    }

    private static void quad(MultiBufferSource buffers, PoseStack pose, RenderType type, float size, int alpha,
                             float cameraYaw,float cameraPitch) {
        pose.pushPose();
        pose.scale(size,size,size);
        pose.mulPose(Axis.YP.rotationDegrees(180-cameraYaw));
        pose.mulPose(Axis.XP.rotationDegrees(-cameraPitch));
        var out=buffers.getBuffer(type);
        vertex(out,pose,-.5f,-.25f,0,1,alpha);
        vertex(out,pose,.5f,-.25f,1,1,alpha);
        vertex(out,pose,.5f,.75f,1,0,alpha);
        vertex(out,pose,-.5f,.75f,0,0,alpha);
        if(buffers instanceof MultiBufferSource.BufferSource source)source.endBatch(type);
        pose.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack pose, float x, float y,
                               float u, float v, int alpha) {
        out.addVertex(pose.last(),x,y,0).setColor(255,255,255,alpha).setUv(u,v);
    }

    @Override public ResourceLocation getTextureLocation(ACElectronBallEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/effects/mdball/0.png");
    }
}
