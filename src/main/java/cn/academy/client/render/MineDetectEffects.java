package cn.academy.client.render;

import cn.academy.ability.vanilla.electromaster.skill.ElectromasterOreRules;
import cn.academy.client.render.util.MineDetectView;
import cn.academy.network.MineDetectEffectPayload;
import cn.academy.registry.ACSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Only the casting client creates HandlerEntity and plays the original ambient sound. */
public final class MineDetectEffects {
    private static final ArrayList<MineDetectView> VIEWS = new ArrayList<>();
    private static ClientLevel world;
    private static Player player;
    private static boolean sync(Minecraft mc) {
        if (world != mc.level || player != mc.player) { VIEWS.clear(); world = mc.level; player = mc.player; }
        return world != null && player != null;
    }
    public static void tick(Minecraft mc) {
        if (!sync(mc) || mc.isPaused()) return;
        VIEWS.removeIf(view -> {
            view.tick(player.position(), pos -> ElectromasterOreRules.isOre(world.getBlockState(pos)),
                    pos -> ElectromasterOreRules.harvestLevel(world.getBlockState(pos)));
            return view.expired();
        });
        var queue = player.getPersistentData().getList(MineDetectEffectPayload.QUEUE, 10);
        for (int i = 0; i < queue.size(); i++) {
            var tag = queue.getCompound(i);
            VIEWS.add(new MineDetectView(player.position(), tag.getFloat("Range"), tag.getBoolean("Advanced")));
            world.playLocalSound(player.getX(), player.getY(), player.getZ(), ACSounds.EM_MINEDETECT.get(), SoundSource.AMBIENT, .5f, 1, false);
        }
        player.getPersistentData().remove(MineDetectEffectPayload.QUEUE);
    }
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !sync(Minecraft.getInstance())) return;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        // AFTER_LEVEL has an empty PoseStack; use the world's camera rotation explicitly.
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        var camera = event.getCamera().getPosition();
        // Draw the original colored ore markers, farthest first.
        var styles=new java.util.HashMap<BlockPos,Integer>();
        for(var view:VIEWS)for(var ore:view.ores())styles.put(ore.position(),ore.level());
        var positions=styles.keySet().stream()
                .sorted(Comparator.comparingDouble((BlockPos pos)->Vec3.atCenterOf(pos).distanceToSqr(camera)).reversed()).toList();
        for (var pos : positions) {
            pose.pushPose();
            pose.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);
            int color=MineDetectView.color(styles.get(pos));
            var out=buffers.getBuffer(ACShaders.MINE_VIEW);
            for(var vertex:MineDetectView.box())
                out.addVertex(pose.last(),(float)vertex.x(),(float)vertex.y(),(float)vertex.z()).setUv(vertex.u(),vertex.v())
                        .setColor((color>>16)&255,(color>>8)&255,color&255,180);
            pose.popPose();
        }
        buffers.endBatch(ACShaders.MINE_VIEW);
    }
    private MineDetectEffects() {}
}
