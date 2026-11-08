package cn.academy.client.render;

import cn.academy.client.render.util.*;
import cn.academy.network.RailgunEffectPayload;
import cn.academy.registry.ACSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** EntityRailgunFX and its glow/cylinder/sub-arc renderers, using modern vertex buffers. */
public final class LegacyRailgunEffects {
    private static final Random RANDOM = new Random(), ARC_RANDOM = new Random();
    private static final ArcFactory.Arc[] TEMPLATES = templates();
    private static final List<Ray> RAYS = new ArrayList<>();
    private static ClientLevel world;
    private static long rawTime = -1, time = 1;
    private static ArcFactory.Arc[] templates() {
        var factory = new ArcFactory();
        factory.widthShrink = .9; factory.maxOffset = .8; factory.passes = 3; factory.width = .3; factory.branchFactor = .7;
        return factory.generateList(15, 2, 3);
    }
    private static void clock(Minecraft minecraft) {
        long raw = net.minecraft.Util.getMillis();
        if (rawTime >= 0 && !minecraft.isPaused()) time += Math.max(0, raw - rawTime);
        rawTime = raw;
    }
    private static final class Ray {
        final int caster;
        final Vec3 position;
        final float yaw, pitch;
        final double length;
        final RayAnimation animation;
        final List<SubArc> arcs = new ArrayList<>();
        int ticks;
        Ray(RailgunEffectPayload payload, Entity owner, Entity reflector) {
            caster = payload.caster(); length = payload.length(); animation = new RayAnimation(time);
            if (reflector == null) {
                position = owner.getEyePosition(); yaw = owner.getYRot(); pitch = owner.getXRot();
            } else {
                position = owner.getEyePosition().add(RailgunGeometry.direction(owner.getYRot(), owner.getXRot())
                        .scale(owner.distanceTo(reflector)));
                yaw = reflector.getYHeadRot(); pitch = reflector.getXRot();
            }
            for (double cur = 1; cur <= length; cur += 1 + RANDOM.nextDouble()) {
                float theta = RANDOM.nextFloat() * ((float) Math.PI * 2);
                double radius = .1 + RANDOM.nextDouble() * .15;
                // EntityRailgunFX ignores both immutable vector rotation results. Do not rotate these positions.
                Vec3 position = new Vec3(cur, radius * Mth.sin(theta), radius * Mth.cos(theta));
                arcs.add(new SubArc(position, TEMPLATES.length, ARC_RANDOM, RANDOM));
            }
        }
        boolean tick() {
            ticks++;
            if (ticks == 30) arcs.clear();
            arcs.removeIf(arc -> arc.dead);
            arcs.forEach(arc -> arc.tick(ARC_RANDOM));
            // onFirstUpdate schedules life=50 at the first entity tick (1), so removal occurs at 51.
            return ticks >= 51;
        }
    }
    public static void tick(Minecraft minecraft) {
        clock(minecraft);
        if (minecraft.level != world) { RAYS.clear(); world = minecraft.level; }
        if (minecraft.player == null || world == null) { RAYS.clear(); return; }
        if (minecraft.isPaused()) return;
        RAYS.removeIf(Ray::tick);
        var queue = minecraft.player.getPersistentData().getList(RailgunEffectPayload.QUEUE, 10);
        for (int i = 0; i < queue.size(); i++) {
            var payload = RailgunEffectPayload.fromTag(queue.getCompound(i));
            Entity owner = world.getEntity(payload.caster());
            Entity reflector = payload.reflector() < 0 ? null : world.getEntity(payload.reflector());
            if (owner == null || payload.reflector() >= 0 && reflector == null) continue;
            RAYS.add(new Ray(payload, owner, reflector));
            if (reflector == null) world.playLocalSound(owner.getX(), owner.getY(), owner.getZ(),
                    ACSounds.EM_RAILGUN.get(), SoundSource.AMBIENT, .5f, 1, false);
        }
        minecraft.player.getPersistentData().remove(RailgunEffectPayload.QUEUE);
    }
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        clock(minecraft);
        if (world != minecraft.level) { RAYS.clear(); return; }
        var buffers = minecraft.renderBuffers().bufferSource();
        var pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        for (Ray ray : RAYS) {
            boolean firstPerson = minecraft.player != null && ray.caster == minecraft.player.getId()
                    && minecraft.options.getCameraType().isFirstPerson();
            pose.pushPose();
            pose.translate(ray.position.x - camera.x, ray.position.y - camera.y, ray.position.z - camera.z);
            drawArcs(ray, firstPerson, pose, buffers);
            var frame = ray.animation.frame(time, ray.length, RANDOM);
            var glow = RailgunGeometry.glow(ray.position, camera, ray.yaw, ray.pitch, firstPerson, frame);
            // RendererRayGlow multiplies getAlpha() by getGlowAlpha(), which itself includes getAlpha().
            int alpha = (int) (255 * frame.alpha() * frame.glowAlpha());
            glow(glow.head(), alpha, ACShaders.RAILGUN_HEAD, pose, buffers);
            glow(glow.body(), alpha, ACShaders.RAILGUN_BODY, pose, buffers);
            glow(glow.tail(), alpha, ACShaders.RAILGUN_TAIL, pose, buffers);
            pose.pushPose();
            pose.mulPose(RailgunGeometry.cylinderTransform(ray.yaw, ray.pitch, firstPerson, frame.length()));
            cylinder(pose, buffers, frame, .09, .98, 241, 240, 222, 200);
            cylinder(pose, buffers, frame, .13, 1, 236, 170, 93, 60);
            pose.popPose();
            pose.popPose();
        }
    }
    private static void drawArcs(Ray ray, boolean firstPerson, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        if (ray.arcs.isEmpty()) return;
        pose.pushPose();
        Vec3 hand = RailgunGeometry.hand(firstPerson);
        pose.translate(hand.x, hand.y, hand.z); // Original sub-arcs use unrotated ViewOptimize.fix.
        VertexConsumer out = buffers.getBuffer(ACShaders.SUB_ARCS);
        for (SubArc arc : ray.arcs) if (!arc.dead && arc.shown) {
            var template = TEMPLATES[arc.pattern];
            pose.pushPose(); pose.mulPose(arc.transform(template.length));
            for (var vertex : template.fixedVertices()) {
                Vec3 point = vertex.position();
                out.addVertex(pose.last(), (float) point.x, (float) point.y, (float) point.z)
                        .setUv((float) vertex.u(), (float) vertex.v()).setColor(1, 1, 1, (float) vertex.alpha());
            }
            pose.popPose();
        }
        buffers.endBatch(ACShaders.SUB_ARCS);
        pose.popPose();
    }
    static void glow(List<RailgunGeometry.Vertex> vertices, int alpha, RenderType type, PoseStack pose,
                             MultiBufferSource.BufferSource buffers) {
        var out = buffers.getBuffer(type);
        for (var vertex : vertices) {
            Vec3 point = vertex.position();
            out.addVertex(pose.last(), (float) point.x, (float) point.y, (float) point.z)
                    .setUv((float) vertex.u(), (float) vertex.v()).setColor(255, 255, 255, alpha);
        }
        buffers.endBatch(type);
    }
    static void cylinder(PoseStack pose, MultiBufferSource.BufferSource buffers, RayAnimation.Frame frame,
                                 double baseWidth, double headFix, int r, int g, int b, int baseAlpha) {
        cylinder(pose,buffers,frame,baseWidth,headFix,r,g,b,baseAlpha,ACShaders.RAILGUN_CYLINDER);
    }
    static void cylinder(PoseStack pose,MultiBufferSource.BufferSource buffers,RayAnimation.Frame frame,
                         double baseWidth,double headFix,int r,int g,int b,int baseAlpha,RenderType type) {
        double width = baseWidth * frame.width(), offset = width * (1 - headFix);
        int alpha = (int) (baseAlpha * frame.alpha());
        var out = buffers.getBuffer(type);
        mesh(pose, out, RailgunGeometry.HEAD, offset, width * headFix, width, width, r, g, b, alpha);
        mesh(pose, out, RailgunGeometry.CYLINDER, width, frame.length() - width, width, width, r, g, b, alpha);
        mesh(pose, out, RailgunGeometry.HEAD, frame.length() + width - offset, -width * headFix, width, -width, r, g, b, alpha);
        // Preserve original inner-before-outer draw order (and depth writes).
        buffers.endBatch(type);
    }
    private static void mesh(PoseStack pose, VertexConsumer out, List<Vec3> vertices, double x,
                             double sx, double sy, double sz, int r, int g, int b, int alpha) {
        for (Vec3 vertex : vertices)
            out.addVertex(pose.last(), (float) (x + vertex.x * sx), (float) (vertex.y * sy), (float) (vertex.z * sz))
                    .setColor(r, g, b, alpha);
    }
    private LegacyRailgunEffects() {}
}
