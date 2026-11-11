package cn.academy.client.render;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.teleporter.skill.ThreateningTeleportContext;
import cn.academy.client.render.util.*;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;
import static cn.academy.network.ThreateningTeleportPayload.*;

/** Original TTContextC marker and locally recalculated trail endpoint. */
public final class ThreateningTeleportEffects {
    private static final ThunderClapRequests REQUESTS=new ThunderClapRequests();
    private static final Map<Integer,Marker> MARKERS=new HashMap<>();
    private static ClientLevel world;private static Player local;
    private static final class Marker {
        final int slot,request;final float experience;
        Vec3 previous,position;float width=.5f,height=.5f;boolean hit,updated,released;
        Marker(int slot,int request,float experience){this.slot=slot;this.request=request;this.experience=experience;previous=position=local.position();}
    }
    private static boolean sync(Minecraft mc){if(world!=mc.level || local!=mc.player){MARKERS.clear();REQUESTS.end(-1);world=mc.level;local=mc.player;}return world!=null && local!=null;}
    public static int beginLocal(int slot) {
        if(!sync(Minecraft.getInstance()) || slot<0 || slot >= cn.academy.ability.AbilityState.SKILL_CHANNELS)return 0;
        var state=AbilityState.load(local);var skill=AbilityRegistry.skill(state.controlSkill(slot));
        if(skill==null || !skill.name().equals("threatening_teleport") || !state.canStartContext(skill))return 0;
        MARKERS.values().removeIf(m->m.slot==slot);int request=REQUESTS.begin(slot);MARKERS.put(request,new Marker(slot,request,state.experience(skill.id())));return request;
    }
    public static void endLocal(int slot,boolean abort){if(!sync(Minecraft.getInstance()))return;if(abort)for(var marker:MARKERS.values())if(slot<0 || marker.slot==slot)marker.released=true;}
    public static void receive(ThreateningTeleportPayload packet) {
        if(!sync(Minecraft.getInstance()))return;
        if(packet.caster()==local.getId()) {
            var marker=MARKERS.get(packet.request());
            if(packet.phase()==END){if(marker!=null){MARKERS.remove(packet.request());if(REQUESTS.accepts(marker.slot,marker.request))REQUESTS.end(marker.slot);}}
            else if(packet.phase()==START && marker==null && packet.request()==0)MARKERS.put(0,new Marker(packet.slot(),0,packet.experience()));
            else if(packet.phase()==EFFECT && marker!=null)marker.released=true;
        }
        if(packet.phase()==EFFECT && packet.performed() && world.getEntity(packet.caster()) instanceof Player owner) {
            ACContextSounds.once(owner.getUUID(),"tp.tp",.5f,SoundSource.AMBIENT,packet.context());
            var target=ThreateningTeleportContext.targetAtRange(owner,packet.range());
            TeleportParticleEffects.trail(owner,target.point().add(.5,.5,.5),false);
        }
    }
    public static void tick(Minecraft mc) {
        if(!sync(mc) || mc.isPaused())return;
        for(var marker:MARKERS.values()) {
            if(!AbilityState.load(local).active())marker.released=true;
            if(marker.released)continue;
            var target=ThreateningTeleportContext.target(local,marker.experience);marker.previous=marker.position;
            marker.hit=target.entity()!=null;marker.position=marker.hit?target.entity().position():target.point();marker.updated=true;
            marker.width=marker.hit?target.entity().getBbWidth():.5f;marker.height=marker.hit?target.entity().getBbHeight():.5f;
        }
    }
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES || !sync(Minecraft.getInstance()))return;
        var pose=event.getPoseStack();var buffers=Minecraft.getInstance().renderBuffers().bufferSource();var camera=event.getCamera().getPosition();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        for(var marker:MARKERS.values())if(marker.updated && !marker.released) {
            var point=marker.previous.lerp(marker.position,partial).subtract(camera);pose.pushPose();pose.translate(point.x,point.y,point.z);
            LegacyEntityMarker.draw(pose,buffers.getBuffer(ACShaders.ENTITY_MARKER),marker.width,marker.height,186,marker.hit?178:186,marker.hit?35:186,marker.hit?42:186);pose.popPose();
        }
        buffers.endBatch(ACShaders.ENTITY_MARKER);
    }
    private ThreateningTeleportEffects(){}
}
