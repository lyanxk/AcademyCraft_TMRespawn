package cn.academy.client.render;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.teleporter.skill.FleshRippingContext;
import cn.academy.client.render.util.*;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;
import static cn.academy.network.FleshRippingPayload.*;

/** Local FRContext marker and shared original blood-splash/sound listener. */
public final class FleshRippingEffects {
    private static final ThunderClapRequests REQUESTS=new ThunderClapRequests();
    private static final Map<Integer,Marker> MARKERS=new HashMap<>();
    private static ClientLevel world;private static Player local;
    private static final class Marker {
        final int slot,request;final float experience;int id;
        Vec3 previous,position;float width=1,height=1;boolean hit,updated,released;
        Marker(int slot,int request,float experience){this.slot=slot;this.request=request;this.experience=experience;previous=position=local.position();}
    }
    private static boolean sync(Minecraft mc) {
        if(world!=mc.level || local!=mc.player){MARKERS.clear();REQUESTS.end(-1);world=mc.level;local=mc.player;}
        return world!=null && local!=null;
    }
    public static int beginLocal(int slot) {
        if(!sync(Minecraft.getInstance()) || slot<0 || slot >= cn.academy.ability.AbilityState.SKILL_CHANNELS)return 0;
        var state=AbilityState.load(local);var skill=AbilityRegistry.skill(state.controlSkill(slot));
        if(skill==null || !skill.name().equals("flesh_ripping") || !state.canStartContext(skill))return 0;
        MARKERS.values().removeIf(m->m.slot==slot);int request=REQUESTS.begin(slot);MARKERS.put(request,new Marker(slot,request,state.experience(skill.id())));return request;
    }
    public static void endLocal(int slot,boolean abort) {
        if(!sync(Minecraft.getInstance()))return;
        if(abort)for(var marker:MARKERS.values())if(slot<0 || marker.slot==slot)marker.released=true;
        // Generic ordered key-up/abort packets drive the server. Keep request matching until END arrives.
    }
    public static void receive(FleshRippingPayload packet) {
        if(!sync(Minecraft.getInstance()))return;
        if(packet.caster()==local.getId()) {
            var marker=MARKERS.get(packet.request());
            if(packet.phase()==END) {
                if(marker!=null){MARKERS.remove(packet.request());if(REQUESTS.accepts(marker.slot,marker.request))REQUESTS.end(marker.slot);}
            } else if(packet.phase()==START) {
                if(marker==null && packet.request()==0){marker=new Marker(packet.slot(),0,packet.experience());MARKERS.put(0,marker);}
                if(marker!=null)marker.id=packet.context();
            }
            else if(packet.phase()==EFFECT && marker!=null)marker.released=true;
        }
        if(packet.phase()==EFFECT && world.getEntity(packet.caster()) instanceof Player owner && world.getEntity(packet.target())!=null) {
            ACContextSounds.once(owner.getUUID(),"tp.guts",.6f,SoundSource.AMBIENT,packet.context());
            VectorImpulseEffects.fleshBlood(world.getEntity(packet.target()));
        }
    }
    public static void tick(Minecraft mc) {
        if(!sync(mc) || mc.isPaused())return;
        var state=AbilityState.load(local);
        for(var marker:MARKERS.values()) {
            if(!state.active())marker.released=true;
            if(marker.released)continue;
            var hit=FleshRippingContext.target(local,marker.experience);marker.previous=marker.position;marker.position=hit.getLocation();marker.updated=true;
            marker.hit=hit instanceof EntityHitResult;
            if(hit instanceof EntityHitResult entityHit){marker.width=entityHit.getEntity().getBbWidth()*1.2f;marker.height=entityHit.getEntity().getBbHeight()*1.2f;}
            else marker.width=marker.height=1;
        }
    }
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES || !sync(Minecraft.getInstance()))return;
        var pose=event.getPoseStack();var buffers=Minecraft.getInstance().renderBuffers().bufferSource();var camera=event.getCamera().getPosition();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        for(var marker:MARKERS.values())if(marker.updated && !marker.released) {
            var position=marker.previous.lerp(marker.position,partial).subtract(camera);pose.pushPose();pose.translate(position.x,position.y,position.z);
            LegacyEntityMarker.draw(pose,buffers.getBuffer(ACShaders.ENTITY_MARKER),marker.width,marker.height,
                    marker.hit?185:74,marker.hit?25:74,marker.hit?25:74,marker.hit?180:160);pose.popPose();
        }
        buffers.endBatch(ACShaders.ENTITY_MARKER);
    }
    private FleshRippingEffects(){}
}
