package cn.academy.client.render;

import cn.academy.ability.*;
import cn.academy.ability.vanilla.teleporter.FlashingInput;
import cn.academy.ability.vanilla.teleporter.skill.FlashingContext;
import cn.academy.client.render.util.TeleportMarkModel;
import cn.academy.client.render.util.ThunderClapRequests;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.network.FlashingContextPayload;
import cn.academy.network.FlashingInputPayload;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;
import static cn.academy.network.FlashingContextPayload.*;

/** Local Flashing directional delegates and EntityTPMarking. Physical key bindings are never rewritten. */
public final class FlashingEffects {
    private static final ThunderClapRequests REQUESTS = new ThunderClapRequests();
    private static final TeleportMarkModel MODEL = new TeleportMarkModel();
    private static ClientLevel world;
    private static Player local;
    private static Flash flash;
    private static final Map<Integer, Integer> GRAVITY = new HashMap<>();
    private static final Map<Integer, Integer> CONTEXTS = new HashMap<>();
    private static final class Flash {
        final int slot, request;
        final float experience, consumption;
        final FlashingInput input = new FlashingInput();
        int context;
        Marker marker;
        Flash(int slot, int request, int context, float experience) {
            this.slot = slot; this.request = request; this.context = context;
            this.experience = experience; consumption = 13 + (6 - 13) * experience;
        }
    }
    private static final class Marker {
        final Random random = new Random();
        Vec3 previous, position;
        float yaw;
        int ticks;
        Marker(Vec3 point) { previous = position = point; yaw = local.getYRot(); }
    }

    private static boolean sync(Minecraft mc) {
        if (world != mc.level || local != mc.player) {
            if (local != mc.player) GRAVITY.clear();
            flash = null; CONTEXTS.clear(); REQUESTS.end(-1); world = mc.level; local = mc.player;
        }
        return world != null && local != null;
    }
    public static boolean controlsActive() { return sync(Minecraft.getInstance()) && flash != null; }

    public static int beginLocal(int slot) {
        if (!sync(Minecraft.getInstance()) || slot < 0 || slot >= cn.academy.ability.AbilityState.SKILL_CHANNELS) return 0;
        var state = AbilityState.load(local); var skill = AbilityRegistry.skill(state.controlSkill(slot));
        if (skill == null || !skill.name().equals("flashing")) return 0;
        if (flash != null) { stop(false); return 0; } // The normal DOWN packet toggles the server exactly once.
        if (!state.canStartContext(skill)) return 0;
        int request = REQUESTS.begin(slot);
        flash = new Flash(slot, request, 0, state.experience(skill.id()));
        return request;
    }

    public static boolean endByActivate() {
        if (!sync(Minecraft.getInstance()) || flash == null) return false;
        stop(true); return true;
    }

    private static void stop(boolean notify) {
        if (flash == null) return;
        if (notify) PacketDistributor.sendToServer(new FlashingInputPayload(flash.request, 0));
        CONTEXTS.remove(flash.context);
        REQUESTS.end(flash.slot); flash.input.abort(); flash.marker = null; flash = null;
        // GravityCancellor is a separate 40-tick listener and survives MainContext termination.
    }

    public static void movement(int bits, boolean enabled) {
        if (!sync(Minecraft.getInstance()) || flash == null) return;
        if (!enabled) { flash.input.suspend(bits); flash.marker = null; return; }
        int previous = flash.input.activeDirection();
        int released = flash.input.update(bits);
        if (released != 0) {
            flash.marker = null;
            // Request zero is the command/legacy generic-input fallback, owned by the server.
            if (flash.request > 0) PacketDistributor.sendToServer(new FlashingInputPayload(flash.request, released));
        }
        int direction = flash.input.activeDirection();
        if (direction != 0 && (direction != previous || flash.marker == null))
            flash.marker = new Marker(FlashingContext.destination(local, direction, 12 + (18 - 12) * flash.experience));
    }

    public static void receive(FlashingContextPayload packet) {
        if (!sync(Minecraft.getInstance())) return;
        if (packet.phase() == STEP) {
            if (!java.util.Objects.equals(CONTEXTS.get(packet.context()), packet.caster())) return;
            if (world.getEntity(packet.caster()) instanceof Player owner) {
                ACContextSounds.once(owner, "tp.tp_flashing", 1, SoundSource.AMBIENT);
                if (owner == local) GRAVITY.put(packet.context(), 40);
            }
            return;
        }
        if (packet.phase() == END) CONTEXTS.remove(packet.context());
        if (packet.caster() != local.getId()) {
            if (packet.phase() == START) CONTEXTS.put(packet.context(), packet.caster());
            return;
        }
        boolean matches = flash != null && (flash.context == packet.context() && packet.context() != 0
                || flash.request == packet.request());
        if (packet.phase() == END) {
            if (matches) stop(false);
        } else if (packet.phase() == START) {
            if (packet.request() == 0) {
                if (flash == null) {
                    flash = new Flash(packet.slot(), 0, packet.context(), packet.experience());
                    CONTEXTS.put(packet.context(), packet.caster());
                }
            } else if (matches && REQUESTS.accepts(packet.slot(), packet.request())) {
                flash.context = packet.context();
                CONTEXTS.put(packet.context(), packet.caster());
            } else {
                // A late START cannot reactivate controls after local cancellation.
                PacketDistributor.sendToServer(new FlashingInputPayload(packet.request(), 0));
            }
        }
    }

    public static void tick(Minecraft mc) {
        if (!sync(mc) || mc.isPaused() || flash == null) return;
        var state = AbilityState.load(local);
        if (!state.active() || !local.isAlive()) { stop(true); return; }
        if (!state.canUse() || mc.screen != null || !mc.isWindowActive()) {
            flash.input.cancelActive(); flash.marker = null; return;
        }
        if (flash.input.activeDirection() != 0 && !local.isCreative() && state.cp() < flash.consumption) {
            flash.input.cancelActive(); flash.marker = null; return;
        }
        if (flash.marker != null) {
            var marker = flash.marker;
            marker.previous = marker.position;
            marker.position = FlashingContext.destination(local, flash.input.activeDirection(), 12 + (18 - 12) * flash.experience);
            marker.yaw = local.getYRot(); marker.ticks++;
            if (marker.random.nextDouble() < .4) TeleportParticleEffects.markParticle(marker.position);
        }
    }

    public static void gravityTick() {
        if (!sync(Minecraft.getInstance())) return;
        var iterator = GRAVITY.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue() <= 0) { iterator.remove(); continue; }
            if (!local.getAbilities().flying && !local.onGround())
                local.setDeltaMovement(local.getDeltaMovement().add(0, .072, 0));
            entry.setValue(entry.getValue() - 1);
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !sync(Minecraft.getInstance()) || flash == null || flash.marker == null) return;
        var marker = flash.marker;
        var pose = event.getPoseStack(); var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 point = marker.previous.lerp(marker.position, partial).subtract(event.getCamera().getPosition());
        pose.pushPose(); pose.translate(point.x, point.y, point.z);
        pose.mulPose(Axis.YP.rotationDegrees(-marker.yaw)); pose.scale(-1, -1, 1);
        var type = ACShaders.TP_MARK[(int)((marker.ticks / 2.5) % 7)];
        MODEL.draw(pose, buffers.getBuffer(type), true); buffers.endBatch(type); pose.popPose();
    }

    private FlashingEffects() {}
}
