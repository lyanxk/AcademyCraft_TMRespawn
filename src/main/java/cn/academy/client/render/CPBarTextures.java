package cn.academy.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;

/** Two-texture core shaders for CPBar, replacing its legacy texture-unit-4 programs. */
final class CPBarTextures {
    private record Key(ResourceLocation texture, ResourceLocation mask, boolean overload) { }
    private static final Map<Key, RenderType> TYPES = new HashMap<>();

    record Cut(float leftTop, float leftBottom, float right, float top, float bottom) { }

    static Cut cpCut(float progress) {
        float len = 883 * (.16f + .8f * Math.clamp(progress, 0, 1));
        return new Cut(930 - len, 930 - len + (float) (103 * Math.sin(Math.toRadians(44))), 930, 30, 114);
    }

    static void cp(GuiGraphics gui, ResourceLocation texture, ResourceLocation icon, float progress,
                   float r, float g, float b, float alpha) {
        gui.flush();
        var output = gui.bufferSource().getBuffer(type(new Key(texture, icon, false)));
        Cut cut = cpCut(progress);
        vertex(gui, output, cut.leftTop, cut.top, cut.leftTop / 964, cut.top / 147, r, g, b, alpha);
        vertex(gui, output, cut.leftBottom, cut.bottom, cut.leftBottom / 964, cut.bottom / 147, r, g, b, alpha);
        vertex(gui, output, cut.right, cut.bottom, cut.right / 964, cut.bottom / 147, r, g, b, alpha);
        vertex(gui, output, cut.right, cut.top, cut.right / 964, cut.top / 147, r, g, b, alpha);
        gui.flush();
    }

    static void overload(GuiGraphics gui, ResourceLocation texture, ResourceLocation mask, float offset, float alpha) {
        gui.flush();
        var uniform = ACShaders.cpBarOverload().getUniform("TexOffset");
        if (uniform != null) uniform.set(offset);
        var output = gui.bufferSource().getBuffer(type(new Key(texture, mask, true)));
        // Original HudUtils.rect(30,0,0,0,914,147,914,147), with the 974-wide front texture bound.
        vertex(gui, output, 30, 0, 0, 0, 1, 1, 1, alpha);
        vertex(gui, output, 30, 147, 0, 1, 1, 1, 1, alpha);
        vertex(gui, output, 944, 147, 914f / 974, 1, 1, 1, 1, alpha);
        vertex(gui, output, 944, 0, 914f / 974, 0, 1, 1, 1, alpha);
        gui.flush();
    }

    private static void vertex(GuiGraphics gui, VertexConsumer out, float x, float y, float u, float v,
                               float r, float g, float b, float alpha) {
        out.addVertex(gui.pose().last(), x, y, 0).setUv(u, v).setColor(r, g, b, alpha);
    }

    private static RenderType type(Key key) {
        return TYPES.computeIfAbsent(key, k -> RenderType.create("academy_cpbar_" + k,
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(k.overload ? ACShaders::cpBarOverload : ACShaders::cpBar))
                        .setTextureState(RenderStateShard.MultiTextureStateShard.builder()
                                .add(k.texture, true, false).add(k.mask, true, false).build())
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE).createCompositeState(false)));
    }
}
