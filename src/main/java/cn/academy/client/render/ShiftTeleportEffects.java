package cn.academy.client.render;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.teleporter.skill.ShiftTeleportContext;
import cn.academy.client.render.util.LegacyEntityMarker;
import cn.academy.client.render.util.ThunderClapRequests;
import cn.academy.network.ShiftTeleportPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;
import static cn.academy.network.ShiftTeleportPayload.*;

/** STContextC: through-wall markers, three-tick target rebuild and pre-consumption trail. */
public final class ShiftTeleportEffects {
    private static final ThunderClapRequests REQUESTS=new ThunderClapRequests();
    private static final Map<Integer,Marker> MARKERS=new HashMap<>();
    private static ClientLevel world;private static Player local;
    private static final class TargetMarker {
        final Entity target;Vec3 previous,position;boolean updated;
        TargetMarker(Entity target){this.target=target;previous=position=target.position();}
        void tick(){previous=position;position=target.position();updated=true;}
    }
    private static final class Marker {
        final int slot,request;final float experience;
        final List<TargetMarker> targets=new ArrayList<>();
        Vec3 previous,position;int ticks;boolean updated,released;
        Marker(int slot,int request,float experience){this.slot=slot;this.request=request;this.experience=experience;previous=position=local.position();}
    }
    private static boolean sync(Minecraft mc){if(world!=mc.level || local!=mc.player){MARKERS.clear();REQUESTS.end(-1);world=mc.level;local=mc.player;}return world!=null && local!=null;}
    public static int beginLocal(int slot) {
        if(!sync(Minecraft.getInstance()) || slot<0 || slot >= cn.academy.ability.AbilityState.SKILL_CHANNELS)return 0;
        var state=AbilityState.load(local);var skill=AbilityRegistry.skill(state.controlSkill(slot));
        if(skill==null || !skill.name().equals("shift_tp") || !state.canStartContext(skill))return 0;
        MARKERS.values().removeIf(m->m.slot==slot);int request=REQUESTS.begin(slot);
        MARKERS.put(request,new Marker(slot,request,state.experience(skill.id())));return request;
    }
    public static void endLocal(int slot,boolean abort) {
        if(!sync(Minecraft.getInstance()))return;
        if(abort || !ShiftTeleportContext.validHand(local))for(var marker:MARKERS.values())if(slot<0 || marker.slot==slot)marker.released=true;
    }
    public static boolean abortRelease(int slot) {
        return sync(Minecraft.getInstance()) && !ShiftTeleportContext.validHand(local)
                && MARKERS.values().stream().anyMatch(marker->marker.slot==slot);
    }
    public static void receive(ShiftTeleportPayload packet) {
        if(!sync(Minecraft.getInstance()))return;
        if(packet.caster()==local.getId()) {
            var marker=MARKERS.get(packet.request());
            if(packet.phase()==END){if(marker!=null){MARKERS.remove(packet.request());if(REQUESTS.accepts(marker.slot,marker.request))REQUESTS.end(marker.slot);}}
            else if(packet.phase()==START && marker==null && packet.request()==0)MARKERS.put(0,new Marker(packet.slot(),0,packet.experience()));
            else if(packet.phase()==EFFECT && marker!=null)marker.released=true;
        }
        if(packet.phase()==EFFECT && packet.performed() && world.getEntity(packet.caster()) instanceof Player owner)
            TeleportParticleEffects.trail(owner,Vec3.atCenterOf(ShiftTeleportContext.destination(owner,packet.experience())),true);
    }
    public static void tick(Minecraft mc) {
        if(!sync(mc) || mc.isPaused())return;
        for(var marker:MARKERS.values()) {
            if(!AbilityState.load(local).active())marker.released=true;
            if(marker.released)continue;
            for(var target:marker.targets)target.tick();
            if(++marker.ticks%3==0) {
                marker.targets.clear();
                for(var target:ShiftTeleportContext.targets(local,marker.experience))marker.targets.add(new TargetMarker(target));
            }
            var destination=ShiftTeleportContext.destination(local,marker.experience);
            marker.previous=marker.position;marker.position=Vec3.atBottomCenterOf(destination);marker.updated=true;
        }
    }
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES || !sync(Minecraft.getInstance()))return;
        var pose=event.getPoseStack();var buffers=Minecraft.getInstance().renderBuffers().bufferSource();var camera=event.getCamera().getPosition();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        for(var marker:MARKERS.values())if(marker.updated && !marker.released) {
            var point=marker.previous.lerp(marker.position,partial).subtract(camera);pose.pushPose();pose.translate(point.x,point.y,point.z);
            LegacyEntityMarker.draw(pose,buffers.getBuffer(ACShaders.ENTITY_MARKER_NO_DEPTH),1.2f,1.2f,139,139,139,180);pose.popPose();
            for(var target:marker.targets)if(target.updated) {
                point=target.previous.lerp(target.position,partial).subtract(camera);pose.pushPose();pose.translate(point.x,point.y,point.z);
                LegacyEntityMarker.draw(pose,buffers.getBuffer(ACShaders.ENTITY_MARKER_NO_DEPTH),target.target.getBbWidth(),target.target.getBbHeight(),235,81,81,180);pose.popPose();
            }
        }
        buffers.endBatch(ACShaders.ENTITY_MARKER_NO_DEPTH);
    }
    private ShiftTeleportEffects(){}
}
