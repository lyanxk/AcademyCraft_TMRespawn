package cn.academy.client.render;

import cn.academy.client.render.util.IntensifyBurst;
import cn.academy.client.render.util.IntensifyHudAnimation;
import cn.academy.client.render.util.SurroundArc;
import cn.academy.client.sound.ACContextSounds;
import cn.academy.network.IntensifyEffectPayload;
import cn.academy.registry.ACSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;

/** IntensifyContextC's local-only HUD/loop and independently living release entity. */
public final class IntensifyEffects {
    private record Burst(Player owner, IntensifyBurst animation) {}
    private static final ArrayList<Burst> BURSTS = new ArrayList<>();
    private static final ArrayList<IntensifyHudAnimation> HUDS = new ArrayList<>();
    private static ClientLevel world;
    private static Player local;
    private static IntensifyHudAnimation activeHud;
    private static int activeSlot = -1;
    private static void stopLoop() {
        if (local != null) ACContextSounds.handle(local.getUUID(), "em.intensify_loop", false, 1, SoundSource.AMBIENT);
    }
    private static boolean syncWorld(Minecraft mc) {
        if (world != mc.level || local != mc.player) {
            stopLoop(); HUDS.clear(); BURSTS.clear(); activeHud = null; activeSlot = -1;
            world = mc.level; local = mc.player;
        }
        return world != null && local != null;
    }
    /** KEYABORT is local in the original and must stop the loop without waiting for a server round trip. */
    public static void abortLocal(int slot) {
        if (!syncWorld(Minecraft.getInstance()) || activeHud == null || slot >= 0 && slot != activeSlot) return;
        stopLoop(); activeHud.blend(RailgunHandEffects.seconds(), false, false);
        activeHud = null; activeSlot = -1;
    }
    public static void tick(Minecraft mc) {
        if (!syncWorld(mc) || mc.isPaused()) return;
        HUDS.forEach(IntensifyHudAnimation::tick);
        BURSTS.removeIf(burst -> {
            burst.animation.tick(); return burst.owner.isRemoved() || burst.animation.expired();
        });
        var queue = local.getPersistentData().getList(IntensifyEffectPayload.QUEUE, 10);
        double now = RailgunHandEffects.seconds();
        for (int i = 0; i < queue.size(); i++) {
            var tag = queue.getCompound(i);
            int caster = tag.getInt("Caster"), phase = tag.getInt("Phase");
            if (caster == local.getId()) {
                if (phase == IntensifyEffectPayload.START) {
                    abortLocal(-1);
                    activeHud = new IntensifyHudAnimation(now); activeSlot = tag.getInt("Slot"); HUDS.add(activeHud);
                    ACContextSounds.handle(local.getUUID(), "em.intensify_loop", true, 1, SoundSource.AMBIENT);
                } else {
                    stopLoop();
                    if (activeHud != null) activeHud.blend(now, phase == IntensifyEffectPayload.PERFORMED,
                            mc.options.getCameraType().isFirstPerson());
                    if (phase == IntensifyEffectPayload.TERMINATED) { activeHud = null; activeSlot = -1; }
                }
            }
            if (phase == IntensifyEffectPayload.PERFORMED && world.getEntity(caster) instanceof Player owner) {
                world.playLocalSound(owner.getX(), owner.getY(), owner.getZ(), ACSounds.EM_INTENSIFY.get(), SoundSource.AMBIENT, .5f, 1, false);
                BURSTS.add(new Burst(owner, new IntensifyBurst()));
            }
        }
        local.getPersistentData().remove(IntensifyEffectPayload.QUEUE);
    }
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !syncWorld(Minecraft.getInstance())) return;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var pose = event.getPoseStack(); var camera = event.getCamera().getPosition();
        for (Burst burst : BURSTS) {
            var position = burst.owner.position();
            pose.pushPose(); pose.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-burst.owner.getYHeadRot()));
            var out = buffers.getBuffer(ACShaders.SUB_ARCS);
            for (var arc : burst.animation.arcs()) if (!arc.dead && arc.shown) {
                var template = SurroundArc.THIN[arc.pattern];
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
    }
    public static void renderHud(GuiGraphics gui) {
        var mc = Minecraft.getInstance();
        if (!syncWorld(mc) || mc.options.hideGui || local.getVehicle() instanceof net.minecraft.world.entity.animal.horse.AbstractHorse) return;
        double now = RailgunHandEffects.seconds();
        var iterator = HUDS.iterator();
        while (iterator.hasNext()) {
            var animation = iterator.next();
            var frame = animation.frame(now, gui.guiWidth(), gui.guiHeight());
            quad(gui, ACShaders.INTENSIFY_BLACK, 0, 0, gui.guiWidth(), gui.guiHeight(), (float) (.1 * frame.maskAlpha()), false);
            quad(gui, ACShaders.INTENSIFY_MASK, 0, 0, gui.guiWidth(), gui.guiHeight(), (float) frame.maskAlpha(), true);
            for (var arc : frame.arcs()) quad(gui, ACShaders.INTENSIFY_HUD_ARCS[arc.texture()],
                    (float) arc.x(), (float) arc.y(), (float) arc.size(), (float) arc.size(), (float) arc.alpha(), true);
            if (frame.expired()) iterator.remove();
        }
    }
    private static void quad(GuiGraphics gui, RenderType type, float x, float y, float w, float h, float alpha, boolean texture) {
        gui.flush();
        var out = gui.bufferSource().getBuffer(type); var pose = gui.pose().last();
        float color = texture ? 1 : 0;
        out.addVertex(pose, x, y + h, 0).setColor(color, color, color, alpha); if (texture) out.setUv(0, 1);
        out.addVertex(pose, x + w, y + h, 0).setColor(color, color, color, alpha); if (texture) out.setUv(1, 1);
        out.addVertex(pose, x + w, y, 0).setColor(color, color, color, alpha); if (texture) out.setUv(1, 0);
        out.addVertex(pose, x, y, 0).setColor(color, color, color, alpha); if (texture) out.setUv(0, 0);
        gui.flush();
    }
    private IntensifyEffects() {}
}
