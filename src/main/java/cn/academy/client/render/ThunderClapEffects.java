package cn.academy.client.render;

import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.client.render.util.RippleMarkAnimation;
import cn.academy.client.render.util.SurroundArc;
import cn.academy.client.render.util.ThunderClapAnimation;
import cn.academy.client.render.util.ThunderClapRequests;
import cn.academy.network.ThunderClapEffectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.HashMap;

/** ThunderClapContextC: local movement/mark, shared BOLD surround, independent ten-tick tail. */
public final class ThunderClapEffects {
    private static final HashMap<Integer, Effect> EFFECTS = new HashMap<>();
    private static final ThunderClapRequests REQUESTS = new ThunderClapRequests();
    private static ClientLevel world;
    private static Player local;
    private static final class Effect {
        final Player owner;
        final int slot;
        final SurroundArc surround;
        final ThunderClapAnimation animation = new ThunderClapAnimation();
        final double created = RailgunHandEffects.seconds();
        Vec3 hit = Vec3.ZERO, position;
        float yaw;
        Effect(Player owner, int slot) {
            this.owner = owner; this.slot = slot;
            surround = SurroundArc.bold(owner.getBbWidth() * 1.3, owner.getBbHeight() * 1.3);
            position = owner.position();
        }
        void terminate() {
            if (!animation.active()) return;
            animation.terminate();
            // This is intentionally not the pre-skill speed: the original resets to exactly .1.
            owner.getAbilities().setWalkingSpeed(.1f);
        }
        void tick() {
            if (animation.active()) {
                hit = ElectricAttackTrace.traceBlocks(owner, 40).getLocation();
                animation.tick();
                if (owner == local) owner.getAbilities().setWalkingSpeed(animation.walkSpeed());
            } else animation.tick();
            surround.tick(); position = owner.position(); yaw = owner.getYHeadRot();
        }
    }
    private static boolean syncWorld(Minecraft mc) {
        if (world != mc.level || local != mc.player) {
            EFFECTS.values().forEach(Effect::terminate); EFFECTS.clear();
            REQUESTS.end(-1);
            world = mc.level; local = mc.player;
        }
        return world != null && local != null;
    }
    /** Both KEYUP and KEYABORT terminate locally without a network round trip. */
    public static void endLocal(int slot) {
        if (!syncWorld(Minecraft.getInstance())) return;
        REQUESTS.end(slot);
        EFFECTS.values().stream().filter(e -> e.owner == local && (slot < 0 || e.slot == slot)).forEach(Effect::terminate);
    }
    public static int beginLocal(int slot) {
        if (!syncWorld(Minecraft.getInstance())) return 0;
        var state = cn.academy.ability.AbilityState.load(local);
        var skill = cn.academy.ability.AbilityRegistry.skill(state.controlSkill(slot));
        return skill != null && skill.name().equals("thunder_clap") ? REQUESTS.begin(slot) : 0;
    }
    public static void tick(Minecraft mc) {
        if (!syncWorld(mc) || mc.isPaused()) return;
        EFFECTS.values().removeIf(effect -> {
            if (effect.owner.isRemoved()) { effect.terminate(); return true; }
            effect.tick(); return effect.animation.expired();
        });
        var queue = local.getPersistentData().getList(ThunderClapEffectPayload.QUEUE, 10);
        for (int i = 0; i < queue.size(); i++) {
            var tag = queue.getCompound(i); int id = tag.getInt("Context");
            if (tag.getBoolean("Start")) {
                if (!EFFECTS.containsKey(id) && world.getEntity(tag.getInt("Caster")) instanceof Player owner)
                    if (owner != local || REQUESTS.accepts(tag.getInt("Slot"), tag.getInt("Request")))
                        EFFECTS.put(id, new Effect(owner, tag.getInt("Slot")));
            } else {
                var effect = EFFECTS.get(id); if (effect != null) effect.terminate();
            }
        }
        local.getPersistentData().remove(ThunderClapEffectPayload.QUEUE);
    }
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !syncWorld(Minecraft.getInstance())) return;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var pose = event.getPoseStack(); var camera = event.getCamera().getPosition();
        double now = RailgunHandEffects.seconds();
        for (var effect : EFFECTS.values()) {
            if (!effect.surround.expired()) {
                pose.pushPose();
                pose.translate(effect.position.x - camera.x, effect.position.y - camera.y, effect.position.z - camera.z);
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-effect.yaw));
                var out = buffers.getBuffer(ACShaders.SUB_ARCS);
                for (var arc : effect.surround.arcs()) if (!arc.dead && arc.shown) {
                    var template = effect.surround.templates[arc.pattern];
                    pose.pushPose(); pose.mulPose(arc.transform(template.length));
                    for (var vertex : template.fixedVertices()) {
                        var point = vertex.position();
                        out.addVertex(pose.last(), (float) point.x, (float) point.y, (float) point.z)
                                .setUv((float) vertex.u(), (float) vertex.v()).setColor(1, 1, 1, (float) vertex.alpha());
                    }
                    pose.popPose();
                }
                buffers.endBatch(ACShaders.SUB_ARCS); pose.popPose();
            }
            if (effect.owner != local || !effect.animation.active()) continue;
            pose.pushPose(); pose.translate(effect.hit.x - camera.x, effect.hit.y - camera.y, effect.hit.z - camera.z);
            var out = buffers.getBuffer(ACShaders.RIPPLE_MARK);
            for (int i = 0; i < 3; i++) {
                var ring = RippleMarkAnimation.ring(now - effect.created, i);
                pose.pushPose(); pose.translate(0, ring.height(), 0); pose.scale(ring.size(), 1, ring.size());
                out.addVertex(pose.last(), -.5f, 0, -.5f).setUv(0, 0).setColor(204, 204, 204, ring.alpha());
                out.addVertex(pose.last(), .5f, 0, -.5f).setUv(0, 1).setColor(204, 204, 204, ring.alpha());
                out.addVertex(pose.last(), .5f, 0, .5f).setUv(1, 1).setColor(204, 204, 204, ring.alpha());
                out.addVertex(pose.last(), -.5f, 0, .5f).setUv(1, 0).setColor(204, 204, 204, ring.alpha());
                pose.popPose();
            }
            buffers.endBatch(ACShaders.RIPPLE_MARK); pose.popPose();
        }
    }
    private ThunderClapEffects() {}
}
