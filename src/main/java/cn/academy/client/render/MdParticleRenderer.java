package cn.academy.client.render;

import cn.academy.client.render.util.MdParticle;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Original Sprite particle quad in the translucent-entity pass. */
final class MdParticleRenderer {
    static void render(Iterable<MdParticle> particles,RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();var camera=event.getCamera();var cameraPosition=camera.getPosition();
        var pose=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        for(var particle:particles) {
            if(particle.age==0)continue;
            var position=particle.previous.lerp(particle.position,partial);
            pose.pushPose();pose.translate(position.x-cameraPosition.x,position.y-cameraPosition.y,position.z-cameraPosition.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-camera.getYRot()));
            float pitch=mc.options.getCameraType()==net.minecraft.client.CameraType.THIRD_PERSON_FRONT?-camera.getXRot():camera.getXRot();
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-pitch));
            var type=ACShaders.MD_SMALL_PARTICLE;var out=buffers.getBuffer(type);float half=particle.size/2;
            out.addVertex(pose.last(),-half,half,0).setUv(0,0).setColor(255,255,255,particle.alpha);
            out.addVertex(pose.last(),-half,-half,0).setUv(0,1).setColor(255,255,255,particle.alpha);
            out.addVertex(pose.last(),half,-half,0).setUv(1,1).setColor(255,255,255,particle.alpha);
            out.addVertex(pose.last(),half,half,0).setUv(1,0).setColor(255,255,255,particle.alpha);
            buffers.endBatch(type);pose.popPose();
        }
    }
    private MdParticleRenderer(){}
}
