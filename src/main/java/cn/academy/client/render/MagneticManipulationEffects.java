package cn.academy.client.render;

import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilityState;
import cn.academy.client.render.util.SurroundArc;
import cn.academy.client.render.util.ThunderClapRequests;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.entity.MagManipEntityBlock;
import cn.academy.registry.ACSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.HashMap;
import static cn.academy.network.MagneticManipulationPayload.*;

public final class MagneticManipulationEffects {
    private static final ThunderClapRequests REQUESTS=new ThunderClapRequests();
    private static final HashMap<Integer,Context> CONTEXTS=new HashMap<>();
    private static final HashMap<MagManipEntityBlock,SurroundArc> ARCS=new HashMap<>();
    private static ClientLevel world;
    private static Player local;
    private record Context(Player owner,int slot,int entityId) {
        void stop(){ACContextSounds.handle(owner.getUUID(),"em.lf_loop",false,1,SoundSource.AMBIENT);}
    }
    private static boolean syncWorld(Minecraft mc) {
        if(world!=mc.level || local!=mc.player) {
            CONTEXTS.values().forEach(Context::stop);CONTEXTS.clear();ARCS.clear();REQUESTS.end(-1);
            world=mc.level;local=mc.player;
        }
        return world!=null && local!=null;
    }
    public static int beginLocal(int slot) {
        if(!syncWorld(Minecraft.getInstance()))return 0;
        var skill=AbilityRegistry.skill(AbilityState.load(local).controlSkill(slot));
        return skill!=null && skill.name().equals("mag_manip")?REQUESTS.begin(slot):0;
    }
    public static void abortLocal(int slot) {
        if(!syncWorld(Minecraft.getInstance()))return;
        REQUESTS.end(slot);
        CONTEXTS.values().removeIf(context->{
            if(context.owner==local && (slot<0 || context.slot==slot)){context.stop();return true;}return false;
        });
    }
    public static void tick(Minecraft mc) {
        if(!syncWorld(mc) || mc.isPaused())return;
        var queue=local.getPersistentData().getList(QUEUE,10);
        for(int i=0;i<queue.size();i++) {
            var tag=queue.getCompound(i);int phase=tag.getInt("Phase"),id=tag.getInt("Context"),slot=tag.getInt("Slot"),entityId=tag.getInt("Entity");
            var owner=world.getEntity(tag.getInt("Caster"));var entity=world.getEntity(entityId);
            Vec3 value=new Vec3(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));
            if(phase==ENTITY_SYNC) {
                if(entity instanceof MagManipEntityBlock block && owner instanceof Player player)block.applySync(player,tag.getInt("Action"),value);
            } else if(phase==PLACE) {
                // The old spfs handler updates EntityBlock's placement flag, not the subclass's action.
                // Placement already defaults to true; action arrives in RELEASE or the next six-tick entity sync.
            } else if(phase==START && owner instanceof Player player && !CONTEXTS.containsKey(id)) {
                if(player!=local || REQUESTS.accepts(slot,tag.getInt("Request"))) {
                    CONTEXTS.put(id,new Context(player,slot,entityId));
                    ACContextSounds.handle(player.getUUID(),"em.lf_loop",true,1,SoundSource.AMBIENT);
                }
            } else if(phase==RELEASE) {
                if(entity instanceof MagManipEntityBlock block) {block.release();block.setDeltaMovement(value);}
                if(owner instanceof Player player)world.playLocalSound(player.getX(),player.getY(),player.getZ(),ACSounds.get("em.mag_manip"),SoundSource.AMBIENT,1,1,false);
                var context=CONTEXTS.remove(id);if(context!=null)context.stop();
            } else if(phase==END) {var context=CONTEXTS.remove(id);if(context!=null)context.stop();}
        }
        local.getPersistentData().remove(QUEUE);
        CONTEXTS.values().removeIf(context->{
            if(context.owner.isRemoved()){context.stop();return true;}
            if(world.getEntity(context.entityId) instanceof MagManipEntityBlock block)block.follow(context.owner);
            return false;
        });
        for(var entity:world.entitiesForRendering())if(entity instanceof MagManipEntityBlock block && !block.isRemoved())
            ARCS.computeIfAbsent(block,ignored->SurroundArc.magneticBlock());
        ARCS.entrySet().removeIf(entry->entry.getKey().isRemoved());ARCS.values().forEach(SurroundArc::tick);
    }
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES || !syncWorld(Minecraft.getInstance()))return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
        for(var entry:ARCS.entrySet()) {
            var block=entry.getKey();var surround=entry.getValue();if(surround.expired())continue;
            pose.pushPose();pose.translate(block.getX()-camera.x,block.getY()-camera.y,block.getZ()-camera.z);
            // EntityBlock's spin angles are separate from rotationYaw; the surrounding arcs do not spin with the model.
            var out=buffers.getBuffer(ACShaders.SUB_ARCS);
            for(var arc:surround.arcs())if(!arc.dead && arc.shown) {
                var template=surround.templates[arc.pattern];pose.pushPose();pose.mulPose(arc.transform(template.length));
                for(var vertex:template.fixedVertices()) {
                    var point=vertex.position();out.addVertex(pose.last(),(float)point.x,(float)point.y,(float)point.z)
                            .setUv((float)vertex.u(),(float)vertex.v()).setColor(1,1,1,(float)vertex.alpha());
                }
                pose.popPose();
            }
            buffers.endBatch(ACShaders.SUB_ARCS);pose.popPose();
        }
    }
    private MagneticManipulationEffects(){}
}
