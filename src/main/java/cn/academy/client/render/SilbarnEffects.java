package cn.academy.client.render;

import cn.academy.entity.ACThrownItemEntity;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** EntitySilbarn's unlit, freely rotating Sprite fragments in the original translucent entity pass. */
public final class SilbarnEffects {
    private static final Map<ACThrownItemEntity, Double> CREATED = new WeakHashMap<>();
    private static final List<Fragment> FRAGMENTS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    // One decorator was shared by the entire original factory, not randomized per fragment.
    private static final double PHI = RANDOM.nextDouble() * Math.PI * 2;
    private static final double YAW_STEP = Math.sin(PHI) * 25, PITCH_STEP = Math.cos(PHI) * 25;
    private static ClientLevel world;

    private static boolean sync(Minecraft minecraft) {
        if (world != minecraft.level) { FRAGMENTS.clear(); CREATED.clear(); world = minecraft.level; }
        return world != null;
    }

    public static void spawned(ACThrownItemEntity entity) {
        if (sync(Minecraft.getInstance())) CREATED.put(entity, RailgunHandEffects.seconds());
    }

    public static float rotationDegrees(ACThrownItemEntity entity) {
        double now = RailgunHandEffects.seconds();
        double created = CREATED.computeIfAbsent(entity, ignored -> now);
        return (float) (.03 * (long) ((now - created) * 1000));
    }

    public static void burst(ACThrownItemEntity.SilbarnFragments event) {
        if (!sync(Minecraft.getInstance()) || event.entity.level() != world) return;
        for (var velocity : event.velocities) add(event.entity.position(), velocity);
    }

    private static void add(Vec3 position, Vec3 velocity) {
        FRAGMENTS.add(new Fragment(position, velocity, world.getGameTime()));
    }

    private static final class Fragment {
        Vec3 position, previous, velocity;
        final long createdTick;
        float yaw = RANDOM.nextFloat() * 360, pitch = -90 + RANDOM.nextFloat() * 180;
        int age, alpha;
        Fragment(Vec3 position, Vec3 velocity, long createdTick) {
            this.position = this.previous = position;
            this.velocity = velocity;
            this.createdTick = createdTick;
        }
        void tick() {
            previous = position;
            position = position.add(velocity);
            // Particle's Rigidbody moved first; Particle.onUpdate subtracted gravity afterwards.
            velocity = velocity.add(0, -(double) .03f, 0);
            yaw += YAW_STEP;
            pitch += PITCH_STEP;
            alpha = (int) (Math.min(1, ++age / 5f) * 255);
        }
    }

    public static void tick(Minecraft minecraft) {
        if (!sync(minecraft) || minecraft.isPaused()) return;
        for (var fragment : FRAGMENTS) if (world.getGameTime() > fragment.createdTick) fragment.tick();
        // The original template never calls fadeAfter: keep its long default life, not a new 26-tick fade.
        FRAGMENTS.removeIf(fragment -> fragment.age > 10_000_000);
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !sync(Minecraft.getInstance())) return;
        var pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        for (var fragment : FRAGMENTS) {
            if (fragment.age == 0) continue;
            var point = fragment.previous.lerp(fragment.position, partial);
            // Legacy Particle inherited Entity's 0.6 x 1.8 box and 64-block render distance.
            if (point.distanceToSqr(camera) >= 64 * 64 || !event.getFrustum().isVisible(
                    new net.minecraft.world.phys.AABB(point.x - .3, point.y, point.z - .3,
                            point.x + .3, point.y + 1.8, point.z + .3))) continue;
            var relative = point.subtract(camera);
            pose.pushPose();
            pose.translate(relative.x, relative.y, relative.z);
            pose.mulPose(Axis.YP.rotationDegrees(fragment.yaw));
            pose.mulPose(Axis.ZP.rotationDegrees(fragment.pitch));
            var out = buffers.getBuffer(ACShaders.SILBARN_FRAGMENT);
            out.addVertex(pose.last(), -.05f, .05f, 0).setUv(0, 0).setColor(255, 255, 255, fragment.alpha);
            out.addVertex(pose.last(), -.05f, -.05f, 0).setUv(0, 1).setColor(255, 255, 255, fragment.alpha);
            out.addVertex(pose.last(), .05f, -.05f, 0).setUv(1, 1).setColor(255, 255, 255, fragment.alpha);
            out.addVertex(pose.last(), .05f, .05f, 0).setUv(1, 0).setColor(255, 255, 255, fragment.alpha);
            pose.popPose();
        }
        buffers.endBatch(ACShaders.SILBARN_FRAGMENT);
    }

    /** Keep the public particle ID usable; its renderer uses the original standalone texture. */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        public Provider(SpriteSet sprites) {}
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            if (sync(Minecraft.getInstance()) && level == world) add(new Vec3(x, y, z), new Vec3(vx, vy, vz));
            return null;
        }
    }

    private SilbarnEffects() {}
}
