package cn.academy.client.render;

import cn.academy.network.VisualEffectPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Additive modern replacement for the legacy fixed-pipeline GLSL/ray effect stack. */
public final class ACVisualEffects {
    private static final class Effect {
        final String type;
        final Vec3 start, end;
        final float scale;
        final int color, duration, seed;
        int remaining;

        Effect(VisualEffectPayload payload) {
            type = payload.effect();
            start = new Vec3(payload.startX(), payload.startY(), payload.startZ());
            end = new Vec3(payload.endX(), payload.endY(), payload.endZ());
            scale = payload.scale();
            color = payload.color();
            duration = remaining = Math.max(1, payload.duration());
            // Simultaneous ThunderBolt arcs must not collapse onto one identical polyline.
            seed = type.equals("arc") ? java.util.concurrent.ThreadLocalRandom.current().nextInt()
                    : java.util.Objects.hash(type, start, end);
        }
    }


    private static final List<Effect> EFFECTS = new ArrayList<>();

    public static void clientTick(Minecraft minecraft) {
        LegacyArcEffects.tick(minecraft);
        LegacyRailgunEffects.tick(minecraft);
        RailgunHandEffects.tick(minecraft);
        ChargingEffects.tick(minecraft);
        MineRayEffects.tick(minecraft);
        MdSmallRayEffects.tick(minecraft);
        RadiationEffects.tick(minecraft);
        BarrageEffects.tick(minecraft);
        MeltdownerEffects.tick(minecraft);
        VectorDefenseEffects.tick(minecraft);
        VectorStrikeEffects.tick(minecraft);
        VectorImpulseEffects.tick(minecraft);
        StormWingEffects.tick(minecraft);
        PlasmaCannonEffects.tick(minecraft);
        FleshRippingEffects.tick(minecraft);
        ThreateningTeleportEffects.tick(minecraft);
        ShiftTeleportEffects.tick(minecraft);
        TargetTeleportEffects.tick(minecraft);
        FlashingEffects.tick(minecraft);
        TeleportParticleEffects.tick(minecraft);
        SilbarnEffects.tick(minecraft);
        MagneticMovementEffects.tick(minecraft);
        MagneticManipulationEffects.tick(minecraft);
        ThunderClapEffects.tick(minecraft);
        MineDetectEffects.tick(minecraft);
        IntensifyEffects.tick(minecraft);
        LegacyWeatherLightning.drain(minecraft);
        if (minecraft.player == null) {
            EFFECTS.clear();
            return;
        }
        ListTag queue = minecraft.player.getPersistentData().getList("academy:visual_effect_queue", 10);
        for (int index = 0; index < queue.size(); index++) {
            CompoundTag tag = queue.getCompound(index);
            add(new VisualEffectPayload(tag.getString("Effect"),
                    tag.getDouble("SX"), tag.getDouble("SY"), tag.getDouble("SZ"),
                    tag.getDouble("EX"), tag.getDouble("EY"), tag.getDouble("EZ"),
                    tag.getFloat("Scale"), tag.getInt("Color"), tag.getInt("Duration")));
        }
        if (!queue.isEmpty()) minecraft.player.getPersistentData().remove("academy:visual_effect_queue");
        Iterator<Effect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) if (--iterator.next().remaining <= 0) iterator.remove();
    }

    public static void add(VisualEffectPayload payload) {
        if (EFFECTS.size() >= 128) EFFECTS.removeFirst();
        Effect effect = new Effect(payload);
        EFFECTS.add(effect);
    }

    public static void render(RenderLevelStageEvent event) {
        ACElectronBallRenderer.renderDeferred(event);
        MdSmallRayEffects.render(event);
        RadiationEffects.render(event);
        BarrageEffects.render(event);
        MeltdownerEffects.render(event);
        VectorDefenseEffects.render(event);
        VectorStrikeEffects.render(event);
        VectorImpulseEffects.render(event);
        StormWingEffects.render(event);
        PlasmaCannonEffects.render(event);
        FleshRippingEffects.render(event);
        ThreateningTeleportEffects.render(event);
        ShiftTeleportEffects.render(event);
        TargetTeleportEffects.render(event);
        FlashingEffects.render(event);
        TeleportParticleEffects.render(event);
        SilbarnEffects.render(event);
        LegacyArcEffects.render(event);
        LegacyRailgunEffects.render(event);
        RailgunHandEffects.render(event);
        ChargingEffects.render(event);
        MineRayEffects.render(event);
        MagneticMovementEffects.render(event);
        MagneticManipulationEffects.render(event);
        ThunderClapEffects.render(event);
        MineDetectEffects.render(event);
        IntensifyEffects.renderWorld(event);
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || EFFECTS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType type = ACShaders.ENERGY;
        VertexConsumer out = buffers.getBuffer(type);
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (Effect effect : EFFECTS)
            renderEffect(effect, pose, out, camera, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        buffers.endBatch(type);
        pose.popPose();
    }

    private static void renderEffect(Effect effect, PoseStack pose, VertexConsumer out, Vec3 camera, float partialTick) {
        float life = Mth.clamp((effect.remaining - partialTick) / effect.duration, 0, 1);
        float alpha = Math.min(1, life * 2.5f);
        float pulse = 1 + .12f * Mth.sin((effect.duration - effect.remaining + partialTick) * .7f);
        float width = effect.scale * pulse;
        switch (effect.type) {
            case "arc" -> jaggedBeam(pose, out, effect.start, effect.end, camera, width, effect.color, alpha, effect.seed,
                    effect.duration - effect.remaining);
            case "meltdowner" -> {
                beam(pose, out, effect.start, effect.end, camera, width * 2.2f, effect.color, alpha);
                beam(pose, out, effect.start, effect.end, camera, width * .8f, 0xFFE8FFF0, alpha);
                ring(pose, out, effect.end, width * 4, effect.color, alpha, 24, 1);
            }
            case "sphere", "shield", "reflection" -> sphere(pose, out, effect.end,
                    width * (1.2f + (1 - life) * .5f), effect.color, alpha, 28);
            case "teleport" -> {
                float radius = width * (1.2f + (1 - life) * 4);
                ring(pose, out, effect.start, radius, effect.color, alpha, 28, 0);
                ring(pose, out, effect.start.add(0, .7, 0), radius * .75f, effect.color, alpha, 28, 0);
                ring(pose, out, effect.end, radius, effect.color, alpha, 28, 0);
                ring(pose, out, effect.end.add(0, .7, 0), radius * .75f, effect.color, alpha, 28, 0);
            }
            case "wave" -> {
                beam(pose, out, effect.start, effect.end, camera, width, effect.color, alpha);
                ring(pose, out, effect.end, width * (3 + (1 - life) * 6), effect.color, alpha, 32, 0);
            }
            case "plasma" -> {
                jaggedBeam(pose, out, effect.start, effect.end, camera, width * 2.2f, effect.color, alpha,
                        effect.seed, effect.duration - effect.remaining);
                sphere(pose, out, effect.end, width * (4 + (1 - life) * 5), effect.color, alpha, 32);
            }
            case "tornado" -> spiral(pose, out, effect.start, width * 5, effect.color, alpha,
                    effect.duration - effect.remaining + partialTick);
            default -> beam(pose, out, effect.start, effect.end, camera, width, effect.color, alpha);
        }
    }

    private static void jaggedBeam(PoseStack pose, VertexConsumer out, Vec3 start, Vec3 end, Vec3 camera,
                                   float width, int color, float alpha, int seed, float time) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        if (length < .01) {
            sphere(pose, out, start, width * 3, color, alpha, 18);
            return;
        }
        Vec3 normal = direction.normalize();
        Vec3 axis = Math.abs(normal.y) < .9 ? normal.cross(new Vec3(0, 1, 0)).normalize()
                : normal.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 last = start;
        int segments = Mth.clamp((int) (length * 1.5), 7, 28);
        for (int i = 1; i <= segments; i++) {
            double progress = i / (double) segments;
            double envelope = Math.sin(Math.PI * progress);
            double noiseA = Math.sin(seed * .013 + i * 2.17 + time * .9) * width * 2.6 * envelope;
            double noiseB = Math.cos(seed * .021 + i * 1.71 - time * .7) * width * 1.9 * envelope;
            Vec3 point = start.add(direction.scale(progress)).add(axis.scale(noiseA))
                    .add(normal.cross(axis).scale(noiseB));
            beam(pose, out, last, point, camera, width, color, alpha);
            last = point;
        }
    }

    private static void beam(PoseStack pose, VertexConsumer out, Vec3 start, Vec3 end, Vec3 camera,
                             float width, int color, float alpha) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0e-5) return;
        Vec3 view = camera.subtract(start);
        Vec3 side = direction.cross(view);
        if (side.lengthSqr() < 1.0e-5) side = direction.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0e-5) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(width);
        quad(pose, out, start.subtract(side), end.subtract(side), end.add(side), start.add(side), color, alpha);
        Vec3 second = direction.normalize().cross(side.normalize()).scale(width);
        quad(pose, out, start.subtract(second), end.subtract(second), end.add(second), start.add(second), color, alpha * .72f);
    }

    private static void sphere(PoseStack pose, VertexConsumer out, Vec3 center, float radius,
                               int color, float alpha, int segments) {
        ring(pose, out, center, radius, color, alpha, segments, 0);
        ring(pose, out, center, radius, color, alpha, segments, 1);
        ring(pose, out, center, radius, color, alpha, segments, 2);
    }

    private static void ring(PoseStack pose, VertexConsumer out, Vec3 center, float radius, int color,
                             float alpha, int segments, int plane) {
        float thickness = Math.max(.012f, radius * .025f);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            Vec3 p0 = circlePoint(center, radius, a0, plane);
            Vec3 p1 = circlePoint(center, radius, a1, plane);
            Vec3 q0 = circlePoint(center, radius + thickness, a0, plane);
            Vec3 q1 = circlePoint(center, radius + thickness, a1, plane);
            quad(pose, out, p0, p1, q1, q0, color, alpha);
        }
    }

    private static Vec3 circlePoint(Vec3 center, float radius, double angle, int plane) {
        double a = Math.cos(angle) * radius, b = Math.sin(angle) * radius;
        return switch (plane) {
            case 1 -> center.add(a, b, 0);
            case 2 -> center.add(0, a, b);
            default -> center.add(a, 0, b);
        };
    }

    private static void spiral(PoseStack pose, VertexConsumer out, Vec3 center, float radius,
                               int color, float alpha, float time) {
        Vec3 previous = center;
        for (int i = 1; i <= 36; i++) {
            float y = i / 12f;
            float r = radius * (1 - i / 50f);
            double angle = i * .65 + time * .3;
            Vec3 current = center.add(Math.cos(angle) * r, y, Math.sin(angle) * r);
            beam(pose, out, previous, current, center.add(0, 1, 4), Math.max(.025f, radius * .035f), color, alpha);
            previous = current;
        }
    }

    private static void quad(PoseStack pose, VertexConsumer out, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             int color, float alpha) {
        int ca = Math.round(((color >>> 24) & 255) * alpha);
        if ((color >>> 24) == 0) ca = Math.round(255 * alpha);
        int argb = (Mth.clamp(ca, 0, 255) << 24) | (color & 0xFFFFFF);
        out.addVertex(pose.last(), (float) a.x, (float) a.y, (float) a.z).setColor(argb);
        out.addVertex(pose.last(), (float) b.x, (float) b.y, (float) b.z).setColor(argb);
        out.addVertex(pose.last(), (float) c.x, (float) c.y, (float) c.z).setColor(argb);
        out.addVertex(pose.last(), (float) d.x, (float) d.y, (float) d.z).setColor(argb);
    }

    private ACVisualEffects() {}
}
