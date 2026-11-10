package cn.academy.client.render;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.vecmanip.skill.*;
import cn.academy.client.render.util.LegacyCubicCurve;
import cn.academy.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.*;
import static cn.academy.network.VectorDefensePayload.*;

/** Original WaveEffect and WaveEffectUI, shared by the two vector defense contexts. */
public final class VectorDefenseEffects {
    private static final Random RANDOM=new Random();
    private static final Map<Integer,Defense> ACTIVE=new LinkedHashMap<>();
    private static final List<Wave> WAVES=new ArrayList<>();
    private static final LegacyCubicCurve ALPHA=new LegacyCubicCurve(0,0,.2,1,.5,1,.8,1,1,0);
    private static final LegacyCubicCurve SIZE=new LegacyCubicCurve(0,.4,.2,.8,2.5,1.5);
    private static ClientLevel world;
    private static Entity localPlayer;
    private static final class Defense {
        final int caster;final boolean reflection;final List<Ripple> ripples=new ArrayList<>();long lastFrame=System.nanoTime();
        Defense(int caster,boolean reflection){this.caster=caster;this.reflection=reflection;}
    }
    private static final class Ripple {
        final float life,x,y,size;double age;
        Ripple(float life,float x,float y,float size){this.life=life;this.x=x;this.y=y;this.size=size;}
    }
    private record Ring(int life,double offset,double size,int timeOffset){}
    private static final class Wave {
        final Vec3 point;final float yaw,pitch;final List<Ring> rings=new ArrayList<>();int ticks;
        Wave(Vec3 point,Entity owner,int count,double size,boolean reflection) {
            this(point,reflection?owner.getYHeadRot():owner.getYRot(),owner.getXRot(),count,size);
        }
        Wave(Vec3 point,float yaw,float pitch,int count,double size) {
            this.point=point;this.yaw=yaw;this.pitch=pitch;
            for(int i=0;i<count;i++)rings.add(new Ring(8+RANDOM.nextInt(4),i*1.5-.3+RANDOM.nextDouble()*.6,
                    size*(.8+RANDOM.nextDouble()*.4),i*2-1+RANDOM.nextInt(2)));
        }
    }
    public static void wave(Vec3 point,float yaw,float pitch,int rings,double size) {
        sync(Minecraft.getInstance());if(world!=null)WAVES.add(new Wave(point,yaw,pitch,rings,size));
    }
    private static void sync(Minecraft mc){if(world!=mc.level || localPlayer!=mc.player){world=mc.level;localPlayer=mc.player;ACTIVE.clear();WAVES.clear();}}
    public static void receive(VectorDefensePayload packet) {
        var mc=Minecraft.getInstance();sync(mc);if(world==null)return;
        if(packet.phase()==SYNC_VELOCITY) {
            var entity=world.getEntity(packet.entity());if(entity!=null)entity.setDeltaMovement(packet.point());return;
        }
        if(packet.phase()==START){ACTIVE.put(packet.context(),new Defense(packet.caster(),packet.reflection()));return;}
        if(packet.phase()==END){ACTIVE.remove(packet.context());return;}
        var owner=world.getEntity(packet.caster());if(owner==null)return;
        var entity=world.getEntity(packet.entity());
        switch(packet.phase()) {
            case STOP_ENTITY -> {
                if(entity==null)return;
                // Original receiver tests its own unsynchronized mark; receiving a stop does not set that mark.
                if(EntityAffection.marked(entity)){WAVES.add(new Wave(entity.getEyePosition(),owner,1,.6,false));sound(entity.getEyePosition(),false);}
                entity.setDeltaMovement(Vec3.ZERO);
            }
            case REFLECT_ENTITY -> {
                if(entity==null)return;entity.setDeltaMovement(packet.point());
                WAVES.add(new Wave(entity.getEyePosition(),owner,2,1.1,true));sound(entity.getEyePosition(),true);
            }
            case EFFECT -> {WAVES.add(new Wave(packet.point(),owner,2,1.1,true));sound(packet.point(),true);}
            case PLAY -> sound(packet.point(),false);
        }
    }
    private static void sound(Vec3 position,boolean reflection) {
        world.playLocalSound(position.x,position.y,position.z,reflection?cn.academy.registry.ACSounds.VEC_REFLECTION.get():cn.academy.registry.ACSounds.VEC_DEVIATION.get(),SoundSource.AMBIENT,.5f,1,false);
    }
    public static void tick(Minecraft mc) {
        sync(mc);if(world==null || mc.player==null || mc.isPaused())return;
        WAVES.removeIf(wave->++wave.ticks>=15);
        var iterator=ACTIVE.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();var effect=entry.getValue();
            var owner=world.getEntity(effect.caster);if(owner==null || owner.isRemoved()){iterator.remove();continue;}
            if(owner!=mc.player)continue;
            var state=AbilityState.load(mc.player);var skill=AbilityRegistry.skill(effect.reflection?"vecmanip.vec_reflection":"vecmanip.vec_deviation");
            if(!state.active()){PacketDistributor.sendToServer(new VectorDefenseEndPayload(entry.getKey()));iterator.remove();continue;}
            float exp=effect.reflection && state.level()>=5?1:state.experience(skill.id());
            boolean paid=effect.reflection?state.consumeRaw(skill,15+(11-15)*exp,(100-50*exp)/20,mc.player.isCreative()):
                    state.consumeRaw(skill,5+(2.5f-5)*exp,.5f+(.2f-.5f)*exp,mc.player.isCreative());
            if(effect.reflection && !paid){PacketDistributor.sendToServer(new VectorDefenseEndPayload(entry.getKey()));iterator.remove();}
        }
    }
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();sync(mc);if(world==null)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        for(var wave:WAVES) {
            pose.pushPose();pose.translate(wave.point.x-camera.x,wave.point.y-camera.y,wave.point.z-camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(-wave.yaw));pose.mulPose(Axis.XP.rotationDegrees(wave.pitch));pose.translate(0,0,wave.ticks/40.0);
            double maxAlpha=clamp(ALPHA.at(wave.ticks/15.0)),scale=SIZE.at(Math.min(1.62,wave.ticks/20.0));
            for(var ring:wave.rings) {
                double alpha=Math.min(maxAlpha,clamp(ALPHA.at((wave.ticks-ring.timeOffset)/(double)ring.life)));
                if(alpha<=0)continue;pose.pushPose();pose.translate(0,0,ring.offset);
                // LegacyMeshUtils takes opposite corner coordinates: (-.5,-.5) to (1,1), not width/height.
                float size=(float)(ring.size*scale);pose.translate(size*.25,size*.25,0);
                quad(pose,buffers,size*1.5f,255,(int)(alpha*.7f*255));pose.popPose();
            }
            pose.popPose();
        }
        buffers.endBatch(ACShaders.VECTOR_WAVE);
    }
    public static void renderHud(GuiGraphics gui) {
        var mc=Minecraft.getInstance();sync(mc);if(world==null || mc.player==null)return;
        gui.flush();var buffers=mc.renderBuffers().bufferSource();long now=System.nanoTime();
        for(var effect:ACTIVE.values()) {
            if(effect.caster!=mc.player.getId())continue;
            double delta=(now-effect.lastFrame)/1e9;effect.lastFrame=now;
            effect.ripples.removeIf(r->{r.age+=delta;return r.age>=r.life;});
            if(RANDOM.nextFloat()<delta*(effect.reflection?1.6f:1.4f)) {
                float size=(.8f+RANDOM.nextFloat()*.4f)*(effect.reflection?110:100),life=1.5f+RANDOM.nextFloat();
                effect.ripples.add(new Ripple(life,RANDOM.nextFloat()*gui.guiWidth(),RANDOM.nextFloat()*gui.guiHeight(),size));
            }
            for(var ripple:effect.ripples) {
                double progress=ripple.age/ripple.life;
                float alpha=(float)(progress<.2f?progress/.2f:progress<.5f?1:1-(progress-.5f)/.5f)*(effect.reflection?.4f:.2f);
                // vm_wave.glsl maps to [-.5,.5], not [-1,1]; its Y axis also runs upward.
                var pose=gui.pose();pose.pushPose();pose.translate(gui.guiWidth()*.25+ripple.x*.5,gui.guiHeight()*.75-ripple.y*.5,0);pose.scale(1,-1,1);
                int color=(int)(255*Math.clamp(alpha,0,1));
                quad(pose,buffers,(float)(ripple.size+ripple.age*20)*.5f,color,color);pose.popPose();
            }
        }
        buffers.endBatch(ACShaders.VECTOR_WAVE);
    }
    private static void quad(PoseStack pose,MultiBufferSource buffers,float size,int rgb,int alpha) {
        var out=buffers.getBuffer(ACShaders.VECTOR_WAVE);float h=size/2;
        out.addVertex(pose.last(),-h,-h,0).setUv(0,0).setColor(rgb,rgb,rgb,alpha);
        out.addVertex(pose.last(),-h,h,0).setUv(0,1).setColor(rgb,rgb,rgb,alpha);
        out.addVertex(pose.last(),h,h,0).setUv(1,1).setColor(rgb,rgb,rgb,alpha);
        out.addVertex(pose.last(),h,-h,0).setUv(1,0).setColor(rgb,rgb,rgb,alpha);
    }
    private static double clamp(double value){return Math.clamp(value,0,1);}
    private VectorDefenseEffects(){}
}
