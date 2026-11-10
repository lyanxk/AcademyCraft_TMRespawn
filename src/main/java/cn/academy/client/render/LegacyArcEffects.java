package cn.academy.client.render;

import cn.academy.client.render.util.ArcAnimation;
import cn.academy.client.render.util.ArcFactory;
import cn.academy.client.render.util.ArcPatterns;
import cn.academy.network.ArcEffectPayload;
import cn.academy.registry.ACSounds;
import cn.academy.client.render.util.ArcTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.Random;

/** EntityArc render/update path using modern textured quads instead of GL display lists. */
public final class LegacyArcEffects {
    private static final ArrayList<Arc> ARCS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static ClientLevel world;
    private static final class Arc {
        final int caster;
        final Vec3 start;
        final float yaw, pitch;
        final double length;
        final boolean fixed;
        final boolean handOffset;
        final double bendAngle, bendAmount;
        final ArcFactory.Arc[] patterns;
        final ArcAnimation animation;
        Arc(ArcEffectPayload payload, Entity owner, Vec3 end) {
            this(payload, owner, end, true);
        }
        Arc(ArcEffectPayload payload, Entity owner, Vec3 end, boolean handOffset) {
            this.handOffset = handOffset;
            bendAngle = handOffset ? 0 : RANDOM.nextDouble() * Math.PI * 2;
            bendAmount = handOffset ? 0 : .15 + RANDOM.nextDouble() * .1;
            caster = payload.caster();
            fixed = payload.kind() == ArcEffectPayload.STRONG;
            patterns = switch (payload.kind()) {
                case ArcEffectPayload.WEAK -> ArcPatterns.weakArc;
                case ArcEffectPayload.STRONG -> ArcPatterns.strongArc;
                default -> ArcPatterns.aoeArc;
            };
            if (payload.kind() == ArcEffectPayload.AOE) {
                start = payload.start();
                Vec3 delta = end.subtract(start);
                length = delta.length();
                yaw = (float) (-Math.atan2(delta.x, delta.z) * 180 / Math.PI);
                pitch = (float) (-Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * 180 / Math.PI);
            } else {
                start = owner.getEyePosition();
                yaw = owner.getYRot(); pitch = owner.getXRot();
                length = payload.end().distanceTo(payload.start());
            }
            int life = payload.kind() == ArcEffectPayload.AOE && payload.duration() <= 0 ? 15 + RANDOM.nextInt(10) : payload.duration();
            animation = new ArcAnimation(new Random(RANDOM.nextLong()), patterns.length, life, payload.kind() == ArcEffectPayload.WEAK);
        }
    }

    public static void discharge(Entity owner, Vec3 start, Vec3 end) {
        ARCS.add(new Arc(new ArcEffectPayload(owner.getId(), ArcEffectPayload.AOE,
                start, end, -1, 4, false), owner, end, false));
    }
    public static void tick(Minecraft minecraft) {
        if (minecraft.level != world) { ARCS.clear(); world = minecraft.level; }
        if (minecraft.player == null || world == null) { ARCS.clear(); return; }
        // Existing entities tick before new spawns are drained, retaining their initial visible frame.
        ARCS.removeIf(arc -> { arc.animation.tick(); return arc.animation.expired(); });
        var queue = minecraft.player.getPersistentData().getList(ArcEffectPayload.QUEUE, 10);
        for (int i = 0; i < queue.size(); i++) {
            var payload = ArcEffectPayload.fromTag(queue.getCompound(i));
            Entity owner = world.getEntity(payload.caster());
            if (owner == null) continue;
            Entity target = payload.target() < 0 ? null : world.getEntity(payload.target());
            Vec3 end = target == null ? payload.end() : target.getEyePosition();
            ARCS.add(new Arc(payload, owner, end));
            if (payload.sound()) world.playLocalSound(owner.getX(), owner.getY(), owner.getZ(),
                    payload.kind() == ArcEffectPayload.WEAK ? ACSounds.EM_ARC_WEAK.get() : ACSounds.EM_ARC_STRONG.get(),
                    SoundSource.AMBIENT, payload.kind() == ArcEffectPayload.WEAK ? .5f : .6f, 1, false);
        }
        minecraft.player.getPersistentData().remove(ArcEffectPayload.QUEUE);
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ARCS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        var buffers = minecraft.renderBuffers().bufferSource();
        var vertices = buffers.getBuffer(ACShaders.ARC);
        var pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        for (Arc arc : ARCS) {
            if (!arc.animation.shown()) continue;
            pose.pushPose();
            pose.translate(arc.start.x - camera.x, arc.start.y - camera.y, arc.start.z - camera.z);
            boolean firstPerson = minecraft.player != null && minecraft.player.getId() == arc.caster
                    && minecraft.options.getCameraType().isFirstPerson();
            // Applied after both rotations, exactly where EntityArc.Renderer invokes ViewOptimize.fix.
            pose.mulPose(arc.handOffset ? ArcTransform.local(arc.yaw, arc.pitch, firstPerson)
                    : new org.joml.Matrix4f().rotateY((float) Math.toRadians(-arc.yaw - 90))
                            .rotateZ((float) Math.toRadians(-arc.pitch)));
            var pattern = arc.patterns[arc.animation.pattern()];
            for (ArcFactory.Vertex vertex : arc.fixed || !arc.handOffset ? pattern.fixedVertices() : pattern.vertices(arc.length)) {
                var point = vertex.position();
                if (!arc.handOffset) {
                    double t = Math.clamp(point.x / pattern.length, 0, 1);
                    // Alternating bends preserve the head and target endpoints: 0 -> +1 -> -1 -> 0.
                    double zigzag = t < .25 ? t * 4 : t < .75 ? 2 - t * 4 : t * 4 - 4;
                    double bend = zigzag * arc.length * arc.bendAmount;
                    double scale = arc.length / pattern.length;
                    point = new Vec3(point.x * scale,
                            point.y * scale + Math.cos(arc.bendAngle) * bend,
                            point.z * scale + Math.sin(arc.bendAngle) * bend);
                }
                vertices.addVertex(pose.last(), (float) point.x, (float) point.y, (float) point.z)
                        .setUv((float) vertex.u(), (float) vertex.v()).setColor(1, 1, 1, (float) vertex.alpha());
            }
            pose.popPose();
        }
        buffers.endBatch(ACShaders.ARC);
    }
    private LegacyArcEffects() {}
}
