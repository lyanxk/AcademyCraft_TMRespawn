package cn.academy.client.render;

import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.client.render.util.*;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.network.ChargingEffectPayload;
import cn.academy.support.EnergyBlockHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.HashMap;
import java.util.Random;

/** ChargingContextC's persistent arc, two surround modes, and following-loop lifetime. */
public final class ChargingEffects {
    private static final HashMap<Integer, Effect> EFFECTS = new HashMap<>();
    private static ClientLevel world;
    private static final class Effect {
        final Player owner;
        final boolean item;
        final SurroundArc surround;
        final ArcAnimation arc = new ArcAnimation(new Random(), ArcPatterns.chargingArc.length, Integer.MAX_VALUE, .8, .2, .8);
        Vec3 start, end, ring;
        float yaw, pitch, ringYaw;
        boolean ringVisible = true;
        Effect(Player owner, boolean item) {
            this.owner = owner; this.item = item;
            surround = new SurroundArc(item, item ? owner.getBbWidth() * 1.3 : 1, item ? owner.getBbHeight() * 1.3 : 1);
            start = owner.getEyePosition(); end = start.add(owner.getLookAngle().scale(20)); ring = owner.position();
            yaw = owner.getYRot(); pitch = owner.getXRot();
            ACContextSounds.handle(owner.getUUID(), "em.charge_loop", true, .3f, SoundSource.AMBIENT);
        }
        void tick() {
            surround.tick();
            if (item) { ring = owner.position(); ringYaw = owner.getYHeadRot(); return; }
            arc.tick();
            HitResult hit = ElectricAttackTrace.trace(owner, 15, false);
            end = hit.getLocation();
            if (hit instanceof EntityHitResult entity) end = end.add(0, entity.getEntity().getEyeHeight(), 0);
            // ACRenderingHelper.getHeightFix is a fixed 1.6, not the player's current eye height.
            start = owner.position().add(0, 1.6, 0);
            Vec3 delta = end.subtract(start);
            yaw = (float) (-Math.atan2(delta.x, delta.z) * 180 / Math.PI);
            pitch = (float) (-Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * 180 / Math.PI);
            ringVisible = hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block
                    && EnergyBlockHelper.isSupported(owner.level().getBlockEntity(block.getBlockPos()));
            if (ringVisible) {
                var pos = ((BlockHitResult) hit).getBlockPos();
                ring = new Vec3(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            }
        }
        void stop() { ACContextSounds.handle(owner.getUUID(), "em.charge_loop", false, .3f, SoundSource.AMBIENT); }
    }
    private static void reset(ClientLevel level) {
        EFFECTS.values().forEach(Effect::stop); EFFECTS.clear(); world = level;
    }
    public static void tick(Minecraft minecraft) {
        if (minecraft.level != world) reset(minecraft.level);
        if (world == null || minecraft.player == null) { reset(world); return; }
        if (minecraft.isPaused()) return;
        EFFECTS.values().removeIf(effect -> {
            if (effect.owner.isRemoved()) { effect.stop(); return true; }
            effect.tick(); return false;
        });
        var queue = minecraft.player.getPersistentData().getList(ChargingEffectPayload.QUEUE, 10);
        for (int i = 0; i < queue.size(); i++) {
            var tag = queue.getCompound(i);
            int caster = tag.getInt("Caster");
            var previous = EFFECTS.remove(caster); if (previous != null) previous.stop();
            if (tag.getBoolean("Start") && world.getEntity(caster) instanceof Player player)
                EFFECTS.put(caster, new Effect(player, tag.getBoolean("Item")));
        }
        minecraft.player.getPersistentData().remove(ChargingEffectPayload.QUEUE);
    }
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != world) { reset(minecraft.level); return; }
        var pose = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        Vec3 camera = event.getCamera().getPosition();
        for (Effect effect : EFFECTS.values()) {
            if (!effect.item && effect.arc.shown()) {
                pose.pushPose();
                pose.translate(effect.start.x - camera.x, effect.start.y - camera.y, effect.start.z - camera.z);
                pose.mulPose(ArcTransform.local(effect.yaw, effect.pitch,
                        effect.owner == minecraft.player && minecraft.options.getCameraType().isFirstPerson()));
                var out = buffers.getBuffer(ACShaders.ARC);
                for (var vertex : ArcPatterns.chargingArc[effect.arc.pattern()].vertices(effect.start.distanceTo(effect.end))) {
                    Vec3 point = vertex.position();
                    out.addVertex(pose.last(), (float) point.x, (float) point.y, (float) point.z)
                            .setUv((float) vertex.u(), (float) vertex.v()).setColor(1, 1, 1, (float) vertex.alpha());
                }
                buffers.endBatch(ACShaders.ARC); pose.popPose();
            }
            if (!effect.ringVisible || effect.surround.expired()) continue;
            pose.pushPose();
            pose.translate(effect.ring.x - camera.x, effect.ring.y - camera.y, effect.ring.z - camera.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-effect.ringYaw));
            var out = buffers.getBuffer(ACShaders.SUB_ARCS);
            for (SubArc arc : effect.surround.arcs()) if (!arc.dead && arc.shown) {
                var template = effect.surround.templates[arc.pattern];
                pose.pushPose(); pose.mulPose(arc.transform(template.length));
                for (var vertex : template.fixedVertices()) {
                    Vec3 point = vertex.position();
                    out.addVertex(pose.last(), (float) point.x, (float) point.y, (float) point.z)
                            .setUv((float) vertex.u(), (float) vertex.v()).setColor(1, 1, 1, (float) vertex.alpha());
                }
                pose.popPose();
            }
            buffers.endBatch(ACShaders.SUB_ARCS); pose.popPose();
        }
    }
    private ChargingEffects() {}
}
