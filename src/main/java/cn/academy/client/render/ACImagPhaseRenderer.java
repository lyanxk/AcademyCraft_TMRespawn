package cn.academy.client.render;

import cn.academy.AcademyCraft;
import cn.academy.block.entity.ACImagPhaseBlockEntity;
import cn.academy.registry.ACFluids;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Port of 1.12.2 RenderImagPhaseLiquid's three scrolling, full-bright translucent layers. */
public final class ACImagPhaseRenderer implements BlockEntityRenderer<ACImagPhaseBlockEntity> {
    private static final Map<Integer, RenderType> TYPES = new ConcurrentHashMap<>();

    public ACImagPhaseRenderer(BlockEntityRendererProvider.Context ignored) {}

    @Override
    public void render(ACImagPhaseBlockEntity entity, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var level = entity.getLevel();
        if (level == null) return;

        var player = Minecraft.getInstance().player;
        if (player == null || level.getFluidState(entity.getBlockPos()).getFluidType() != ACFluids.IMAG_PHASE_TYPE.get()) return;
        var pos = entity.getBlockPos();
        double distance = Math.sqrt(pos.distToCenterSqr(player.getX(), player.getY(), player.getZ()));
        float alpha = (float) (1 / (1 + .2 * distance));
        if (alpha < .1f) return;

        float fluidHeight = level.getFluidState(pos).getHeight(level, pos);
        double height = 1.2 * Math.sqrt(fluidHeight);
        double time = (level.getGameTime() + partialTick) / 20.0;
        drawLayer(pose, buffers, 0, -.3 * height, .3, .2, .7, time, alpha);
        drawLayer(pose, buffers, 1, .35 * height, .3, .05, .7, time, alpha);
        if (height > .5) drawLayer(pose, buffers, 2, .7 * height, .1, .25, .7, time, alpha);
    }

    private static void drawLayer(PoseStack pose, MultiBufferSource buffers, int layer, double height,
                                  double velocityU, double velocityV, double density,
                                  double time, float alpha) {
        float u = (float) ((time * velocityU) % 1);
        float v = (float) ((time * velocityV) % 1);
        float u2 = u + (float) density, v2 = v + (float) density;
        VertexConsumer output = buffers.getBuffer(type(layer));
        var matrix = pose.last();
        output.addVertex(matrix, 0, (float) height, 0).setUv(u, v).setColor(1, 1, 1, alpha);
        output.addVertex(matrix, 1, (float) height, 0).setUv(u2, v).setColor(1, 1, 1, alpha);
        output.addVertex(matrix, 1, (float) height, 1).setUv(u2, v2).setColor(1, 1, 1, alpha);
        output.addVertex(matrix, 0, (float) height, 1).setUv(u, v2).setColor(1, 1, 1, alpha);
    }

    private static RenderType type(int layer) {
        return TYPES.computeIfAbsent(layer, index -> {
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,
                    "textures/effects/imag_proj_liquid/" + index + ".png");
            return RenderType.create("academy_imag_phase_" + index,
                    DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setCullState(RenderStateShard.NO_CULL)
                            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                            .createCompositeState(false));
        });
    }

    @Override
    public AABB getRenderBoundingBox(ACImagPhaseBlockEntity entity) {
        return new AABB(entity.getBlockPos()).inflate(1);
    }
}
