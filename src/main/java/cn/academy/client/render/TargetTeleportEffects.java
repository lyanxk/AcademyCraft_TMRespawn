package cn.academy.client.render;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.teleporter.skill.TargetTeleportContext;
import cn.academy.client.render.util.*;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.network.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.network.TargetTeleportPayload.*;
import static cn.academy.ability.vanilla.teleporter.skill.TargetTeleportContext.*;

/** Local PT/MT prediction and original EntityTPMarking/MarkRender model. */
public final class TargetTeleportEffects {
    private static final ThunderClapRequests REQUESTS=new ThunderClapRequests();
    private static final Map<Integer,Marker> MARKERS=new HashMap<>();
    private static final TeleportMarkModel MODEL=new TeleportMarkModel();
    private static ClientLevel world;private static Player local;
    private static final class Marker {
        final int slot,request,kind;final float maxDistance;final Random random=new Random();
        Vec3 previous,position;float selected,yaw;int ticks;boolean available=true,hidden,sent;
        Marker(int slot,int request,int kind,float experience){this.slot=slot;this.request=request;this.kind=kind;maxDistance=selected=cn.academy.data.Balance.f("target_teleport_context", "initial_range_start")+(cn.academy.data.Balance.f("target_teleport_context", "initial_range_end")-cn.academy.data.Balance.f("target_teleport_context", "initial_range_start"))*experience;previous=position=local.position();}
    }
    private static boolean sync(Minecraft mc){if(world!=mc.level || local!=mc.player){MARKERS.clear();REQUESTS.end(-1);world=mc.level;local=mc.player;}return world!=null && local!=null;}
    public static int beginLocal(int slot) {
        if(!sync(Minecraft.getInstance()) || slot<0 || slot >= cn.academy.ability.AbilityState.SKILL_CHANNELS)return 0;
        var state=AbilityState.load(local);var skill=AbilityRegistry.skill(state.controlSkill(slot));
        if(skill==null || !handles(skill) || !state.canStartContext(skill))return 0;
        MARKERS.values().removeIf(m->m.slot==slot);int request=REQUESTS.begin(slot);
        MARKERS.put(request,new Marker(slot,request,skill.name().equals("penetrate_teleport")?PENETRATE:MARK,state.experience(skill.id())));return request;
    }
    public static void endLocal(int slot,boolean abort) {
        if(!sync(Minecraft.getInstance()))return;
        for(var marker:MARKERS.values())if(slot<0 || marker.slot==slot) {
            if(abort)marker.hidden=true;
            if(marker.sent)continue;marker.sent=true;
            if(!abort && marker.kind==PENETRATE)ACContextSounds.once(local.getUUID(),"tp.tp",.5f,SoundSource.AMBIENT,-marker.request);
            if(marker.request>0)PacketDistributor.sendToServer(new TargetTeleportInputPayload(marker.request,marker.selected,abort));
        }
    }
    public static void wheel(int slot,float delta) {
        if(!sync(Minecraft.getInstance()) || !Float.isFinite(delta))return;
        for(var marker:MARKERS.values())if(marker.slot==slot && marker.kind==PENETRATE && !marker.hidden) {
            float proposed=marker.selected+delta;if(proposed>=cn.academy.data.Balance.f("target_teleport_context", "minimum_selected_range") && proposed<=marker.maxDistance)marker.selected=proposed;
        }
    }
    public static void receive(TargetTeleportPayload packet) {
        if(!sync(Minecraft.getInstance()))return;
        if(packet.caster()==local.getId()) {
            var marker=MARKERS.get(packet.request());
            if(packet.phase()==END){if(marker!=null){MARKERS.remove(packet.request());if(REQUESTS.accepts(marker.slot,marker.request))REQUESTS.end(marker.slot);}}
            else if(packet.phase()==START && marker==null && packet.request()==0)MARKERS.put(0,new Marker(packet.slot(),0,packet.kind(),packet.experience()));
        }
        if(packet.phase()==SOUND && world.getEntity(packet.caster()) instanceof Player owner)
            ACContextSounds.once(owner.getUUID(),"tp.tp",.5f,SoundSource.AMBIENT,packet.context());
    }
    public static void tick(Minecraft mc) {
        if(!sync(mc) || mc.isPaused())return;
        var state=AbilityState.load(local);
        for(var marker:MARKERS.values()) {
            if(!state.active())marker.hidden=true;
            if(marker.hidden)continue;
            marker.ticks++;marker.previous=marker.position;marker.yaw=local.getYRot();
            String skill=marker.kind==PENETRATE?"teleporter.penetrate_teleport":"teleporter.mark_teleport";
            if(marker.kind==PENETRATE) {
                var destination=penetrate(local,marker.selected,state.experience(skill),state.cp());
                // PTContext reads Forge's public eyeHeight field (default 1.62f), not the pose-adjusted getter.
                marker.available=destination.available();marker.position=destination.position().add(0,(double)1.62f,0);
            } else marker.position=mark(local,marker.ticks,state.experience(skill),state.cp());
            if(marker.available && marker.random.nextDouble()<.4)TeleportParticleEffects.markParticle(marker.position);
        }
    }
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !sync(Minecraft.getInstance()))return;
        var pose=event.getPoseStack();var buffers=Minecraft.getInstance().renderBuffers().bufferSource();var camera=event.getCamera().getPosition();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(true);
        for(var marker:MARKERS.values())if(marker.ticks>0 && !marker.hidden) {
            var point=marker.previous.lerp(marker.position,partial).subtract(camera);pose.pushPose();pose.translate(point.x,point.y,point.z);
            pose.mulPose(Axis.YP.rotationDegrees(-marker.yaw));pose.scale(-1,-1,1);
            var type=ACShaders.TP_MARK[(int)((marker.ticks/2.5)%7)];MODEL.draw(pose,buffers.getBuffer(type),marker.available);buffers.endBatch(type);pose.popPose();
        }
    }
    private TargetTeleportEffects(){}
}
