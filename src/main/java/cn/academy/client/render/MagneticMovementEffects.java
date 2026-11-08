package cn.academy.client.render;

import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilityState;
import cn.academy.ability.vanilla.electromaster.skill.MagneticMovementMotion;
import cn.academy.ability.vanilla.electromaster.skill.MagneticTarget;
import cn.academy.client.render.util.*;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.network.MagneticMovementPayload;
import cn.academy.network.MagneticMovementUpdatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Random;
import static cn.academy.network.MagneticMovementPayload.*;

/** Local MovementContext motion and the fixed-audience MovementContextC arc/loop. */
public final class MagneticMovementEffects {
    private static final ThunderClapRequests REQUESTS = new ThunderClapRequests();
    private static final HashMap<Integer, Motion> MOTIONS = new HashMap<>();
    private static final HashMap<Integer, Effect> EFFECTS = new HashMap<>();
    private static ClientLevel world;
    private static Player local;

    private static final class Motion {
        final int slot, id;
        final MagneticTarget target;
        final MagneticMovementMotion motion = new MagneticMovementMotion();
        boolean first = true;
        Motion(int slot, int id, MagneticTarget target) {
            this.slot = slot; this.id = id; this.target = target;
        }
        void tick() {
            Vec3 point = target.entity() == null ? target.position() : target.entity().getEyePosition();
            PacketDistributor.sendToServer(new MagneticMovementUpdatePayload(id, first ? START : UPDATE, point));
            first = false;
            local.setDeltaMovement(motion.step(local.position(), point, local.getDeltaMovement()));
        }
    }
    private static final class Effect {
        final Player owner;
        final int slot;
        final ArcAnimation animation = new ArcAnimation(new Random(), ArcPatterns.thinContiniousArc.length,
                Integer.MAX_VALUE, 1, .1, .6);
        Vec3 start, end;
        float yaw, pitch;
        Effect(Player owner, int slot) {
            this.owner = owner; this.slot = slot;
            start = owner.getEyePosition(); end = start.add(owner.getLookAngle().scale(20));
            yaw = owner.getYRot(); pitch = owner.getXRot();
            ACContextSounds.handle(owner.getUUID(), "em.move_loop", true, 1, SoundSource.AMBIENT);
        }
        void update(Vec3 point) {
            start = owner.position().add(0, 1.6, 0); end = point;
            Vec3 delta = end.subtract(start);
            yaw = (float) (-Math.atan2(delta.x, delta.z) * 180 / Math.PI);
            pitch = (float) (-Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * 180 / Math.PI);
        }
        void stop() { ACContextSounds.handle(owner.getUUID(), "em.move_loop", false, 1, SoundSource.AMBIENT); }
    }
    private static boolean syncWorld(Minecraft mc) {
        if (world != mc.level || local != mc.player) {
            EFFECTS.values().forEach(Effect::stop); EFFECTS.clear(); MOTIONS.clear(); REQUESTS.end(-1);
            world = mc.level; local = mc.player;
        }
        return world != null && local != null;
    }
    public static int beginLocal(int slot) {
        if (!syncWorld(Minecraft.getInstance())) return 0;
        var state = AbilityState.load(local); var skill = AbilityRegistry.skill(state.controlSkill(slot));
        return skill != null && skill.name().equals("mag_movement") ? REQUESTS.begin(slot) : 0;
    }
    public static void endLocal(int slot) {
        if (!syncWorld(Minecraft.getInstance())) return;
        REQUESTS.end(slot);
        MOTIONS.values().removeIf(motion -> slot < 0 || motion.slot == slot);
        EFFECTS.values().removeIf(effect -> {
            if (effect.owner == local && (slot < 0 || effect.slot == slot)) { effect.stop(); return true; }
            return false;
        });
        // Original termination leaves motion intact; the server resets fallDistance once.
    }
    public static void tick(Minecraft mc) {
        if (!syncWorld(mc) || mc.isPaused()) return;
        EFFECTS.values().removeIf(effect -> {
            if (effect.owner.isRemoved()) { effect.stop(); return true; }
            effect.animation.tick(); return false;
        });
        var queue = local.getPersistentData().getList(QUEUE, 10);
        for (int i = 0; i < queue.size(); i++) {
            var tag = queue.getCompound(i);
            int id = tag.getInt("Context"), slot = tag.getInt("Slot"), phase = tag.getInt("Phase");
            var owner = world.getEntity(tag.getInt("Caster"));
            if (phase == END) {
                MOTIONS.remove(id); var effect = EFFECTS.remove(id); if (effect != null) effect.stop();
            } else if (phase == LINK && owner == local && REQUESTS.accepts(slot, tag.getInt("Request"))) {
                var target = MagneticTarget.trace(local, 25, AbilityState.load(local).experience("electromaster.mag_movement"));
                if (target == null) PacketDistributor.sendToServer(new MagneticMovementUpdatePayload(id, END, Vec3.ZERO));
                else MOTIONS.putIfAbsent(id, new Motion(slot, id, target));
            } else if (phase == START && owner instanceof Player player && !EFFECTS.containsKey(id)) {
                if (player != local || MOTIONS.containsKey(id) && REQUESTS.accepts(slot, tag.getInt("Request")))
                    EFFECTS.put(id, new Effect(player, slot));
            } else if (phase == UPDATE) {
                var effect = EFFECTS.get(id);
                if (effect != null) effect.update(new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")));
            }
        }
        local.getPersistentData().remove(QUEUE);
        MOTIONS.values().forEach(Motion::tick);
    }
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !syncWorld(Minecraft.getInstance())) return;
        var mc = Minecraft.getInstance(); var pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource(); var camera = event.getCamera().getPosition();
        for (Effect effect : EFFECTS.values()) if (effect.animation.shown()) {
            pose.pushPose();
            pose.translate(effect.start.x - camera.x, effect.start.y - camera.y, effect.start.z - camera.z);
            pose.mulPose(ArcTransform.local(effect.yaw, effect.pitch, effect.owner == local && mc.options.getCameraType().isFirstPerson()));
            var out = buffers.getBuffer(ACShaders.ARC);
            for (var vertex : ArcPatterns.thinContiniousArc[effect.animation.pattern()].vertices(effect.start.distanceTo(effect.end))) {
                var point = vertex.position();
                out.addVertex(pose.last(), (float) point.x, (float) point.y, (float) point.z)
                        .setUv((float) vertex.u(), (float) vertex.v()).setColor(1, 1, 1, (float) vertex.alpha());
            }
            buffers.endBatch(ACShaders.ARC); pose.popPose();
        }
    }
    private MagneticMovementEffects() {}
}
