package cn.academy.client.render;

import cn.academy.client.render.util.RailgunHandPose;
import cn.academy.network.RailgunChargePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.util.Mth;
import java.util.ArrayList;

/** Original 40-frame hand billboard, attached to the world-space player dummy rather than item equip transforms. */
public final class RailgunHandEffects {
    private record Hook(Player player, double created) {}
    private static final ArrayList<Hook> HOOKS = new ArrayList<>();
    private static ClientLevel world;
    private static long rawTime = -1, time = 1;
    private static void update(Minecraft minecraft) {
        long raw = net.minecraft.Util.getMillis();
        if (rawTime >= 0 && !minecraft.isPaused()) time += Math.max(0, raw - rawTime);
        rawTime = raw;
        if (world != minecraft.level) { HOOKS.clear(); world = minecraft.level; }
    }
    public static void add(Player player) {
        update(Minecraft.getInstance());
        HOOKS.add(new Hook(player, time / 1000.0));
    }
    public static double seconds() { update(Minecraft.getInstance()); return time / 1000.0; }
    public static void tick(Minecraft minecraft) {
        update(minecraft);
        if (world == null || minecraft.player == null) { HOOKS.clear(); return; }
        var queue = minecraft.player.getPersistentData().getList(RailgunChargePayload.QUEUE, 10);
        for (int i = 0; i < queue.size(); i++)
            if (world.getEntity(queue.getCompound(i).getInt("Caster")) instanceof Player player) add(player);
        minecraft.player.getPersistentData().remove(RailgunChargePayload.QUEUE);
    }
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var minecraft = Minecraft.getInstance(); update(minecraft);
        if (world == null || minecraft.player == null || HOOKS.isEmpty()) return;
        double now = time / 1000.0;
        HOOKS.removeIf(hook -> hook.player.isRemoved() || RailgunHandPose.frame(now - hook.created) < 0);
        var pose = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        var camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        for (Hook hook : HOOKS) {
            Player owner = hook.player;
            int frame = RailgunHandPose.frame(now - hook.created);
            boolean firstPerson = owner == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
            float yaw = firstPerson ? Mth.lerp(partial, owner.yHeadRotO, owner.yHeadRot)
                    : Mth.lerp(partial, owner.yBodyRotO, owner.yBodyRot);
            var position = owner.getPosition(partial);
            pose.pushPose();
            pose.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
            pose.mulPose(RailgunHandPose.local(yaw, owner.getXRot(), minecraft.player.getXRot(),
                    minecraft.player.getEyeHeight(), firstPerson));
            var type = ACShaders.RAILGUN_HAND[frame];
            var out = buffers.getBuffer(type);
            out.addVertex(pose.last(), -1, -1, 0).setUv(0, 0).setColor(255, 255, 255, 255);
            out.addVertex(pose.last(), 1, -1, 0).setUv(1, 0).setColor(255, 255, 255, 255);
            out.addVertex(pose.last(), 1, 1, 0).setUv(1, 1).setColor(255, 255, 255, 255);
            out.addVertex(pose.last(), -1, 1, 0).setUv(0, 1).setColor(255, 255, 255, 255);
            buffers.endBatch(type); pose.popPose();
        }
    }
    private RailgunHandEffects() {}
}
