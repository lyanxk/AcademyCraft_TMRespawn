package cn.academy.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.MemoryStack;

/** The original ordered depth masks and two-sampler skill progress shader. */
public final class ACSkillTreeRenderer {
    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/developer/" + name + ".png");
    }
    private static final ResourceLocation BACK = texture("skill_back"), OUTLINE = texture("skill_outline");
    private static final ResourceLocation MASK = texture("skill_radial_mask"), LINE = texture("line");
    private static final java.util.Map<net.minecraft.client.renderer.texture.AbstractTexture, Integer> MIPMAPS = new java.util.IdentityHashMap<>();
    public static void clear() { MIPMAPS.clear(); }

    private static void prepare(ResourceLocation location) {
        var texture = Minecraft.getInstance().getTextureManager().getTexture(location);
        boolean mipmapped = location.getPath().startsWith("textures/guis/developer/");
        texture.setFilter(mipmapped, mipmapped);
        if (!mipmapped) return;
        int id = texture.getId();
        if (java.util.Objects.equals(MIPMAPS.get(texture), id)) return;
        texture.bind();
        // SimpleTexture initially limits sampling to level zero; allow generation of the full chain.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL, 1000);
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LOD, 1000);
        // Legacy GL_CLAMP has transparent border interpolation at UV 0/1. Our quads stay in [0,1].
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER);
        org.lwjgl.opengl.GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        MIPMAPS.put(texture, id);
    }

    public static State begin(GuiGraphics gui) { gui.flush(); return new State(); }

    public static final class State implements AutoCloseable {
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC), active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int texture0 = RenderSystem.getShaderTexture(0), texture1 = RenderSystem.getShaderTexture(1);
        private final ShaderInstance shader = RenderSystem.getShader();
        private final float[] color = RenderSystem.getShaderColor().clone();
        private final boolean[] colorWrite = new boolean[4];
        private State() {
            try (var stack = MemoryStack.stackPush()) {
                var values = stack.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, values);
                for (int i = 0; i < 4; i++) colorWrite[i] = values.get(i) != 0;
            }
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        @Override public void close() {
            RenderSystem.depthMask(depthWrite);
            RenderSystem.depthFunc(depthFunc);
            RenderSystem.colorMask(colorWrite[0], colorWrite[1], colorWrite[2], colorWrite[3]);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setShaderTexture(0, texture0);
            RenderSystem.setShaderTexture(1, texture1);
            RenderSystem.activeTexture(active);
            RenderSystem.setShader(() -> shader);
        }
    }

    public static void node(GuiGraphics gui, ResourceLocation icon, float x, float y, float scale,
                            float backAlpha, float iconAlpha, boolean learned, float progress) {
        gui.pose().pushPose();
        gui.pose().translate(x, y, 10);
        gui.pose().scale(scale, scale, 1);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.colorMask(true, true, true, true);
        quad(gui, BACK, -11.5f, -11.5f, 23, 23, 0, 1, backAlpha, -1, false, -1);
        quad(gui, OUTLINE, -15.5f, -15.5f, 31, 31, 0, .2f, backAlpha * .6f, -1, false, -1);

        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        quad(gui, BACK, -11.5f, -11.5f, 23, 23, 0, 1, 1, .3f, false, -1);
        quad(gui, OUTLINE, -15.5f, -15.5f, 31, 31, 1, 1, 1, .5f, false, -1);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(false);
        RenderSystem.depthFunc(GL11.GL_EQUAL);
        quad(gui, icon, -7, -7, 14, 14, 0, 1, iconAlpha, -1, !learned, -1);
        if (learned) {
            RenderSystem.disableDepthTest();
            quad(gui, OUTLINE, -15.5f, -15.5f, 31, 31, 0, 1, 1, -1, false, progress);
        }
        gui.pose().popPose();
    }

    /** NOTEQUAL at the outline-mask depth, and extension of the endpoint rather than alpha fading. */
    public static void line(GuiGraphics gui, float x0, float y0, float x1, float y1, float alpha, float progress) {
        double dx = x1 - x0, dy = y1 - y0, length = Math.hypot(dx, dy);
        if (length == 0) return;
        float sx = x0 + (float)(dx / length * 12.2), sy = y0 + (float)(dy / length * 12.2);
        float ex = x1 - (float)(dx / length * 12.2), ey = y1 - (float)(dy / length * 12.2);
        float nx = (float)(-dy / length * 2.75), ny = (float)(dx / length * 2.75);
        ex = sx + (ex - sx) * progress;
        ey = sy + (ey - sy) * progress;
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_NOTEQUAL);
        RenderSystem.depthMask(false);
        shader(LINE, -1, false, -1);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var pose = gui.pose().last();
        buffer.addVertex(pose, sx - nx, sy - ny, 11).setUv(0, 0).setColor(1f, 1f, 1f, alpha);
        buffer.addVertex(pose, sx + nx, sy + ny, 11).setUv(0, 1).setColor(1f, 1f, 1f, alpha);
        buffer.addVertex(pose, ex + nx, ey + ny, 11).setUv(1, 1).setColor(1f, 1f, 1f, alpha);
        buffer.addVertex(pose, ex - nx, ey - ny, 11).setUv(1, 0).setColor(1f, 1f, 1f, alpha);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    public static void action(GuiGraphics gui, ResourceLocation icon, float cx, float cy, float progress, boolean glow) {
        try (var ignored = begin(gui)) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            quad(gui, BACK, cx - 25, cy - 25, 50, 50, 11, 1, 1, -1, false, -1);
            quad(gui, icon, cx - 13.5f, cy - 13.5f, 27, 27, 11, 1, 1, -1, false, -1);
            quad(gui, texture(glow ? "skill_view_outline_glow" : "skill_view_outline"),
                    cx - 25, cy - 25, 50, 50, 11, 1, 1, -1, false, progress);
        }
    }

    private static void shader(ResourceLocation texture, float cutoff, boolean mono, float progress) {
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        prepare(texture);
        prepare(MASK);
        RenderSystem.setShader(ACShaders::skillTree);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderTexture(1, MASK);
        ShaderInstance shader = ACShaders.skillTree();
        shader.safeGetUniform("AlphaCutoff").set(cutoff);
        shader.safeGetUniform("Monochrome").set(mono ? 1 : 0);
        shader.safeGetUniform("UseProgress").set(progress >= 0 ? 1 : 0);
        shader.safeGetUniform("Progress").set(progress);
    }

    private static void quad(GuiGraphics gui, ResourceLocation texture, float x, float y, float w, float h,
                             float z, float shade, float alpha, float cutoff, boolean mono, float progress) {
        shader(texture, cutoff, mono, progress);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var pose = gui.pose().last();
        buffer.addVertex(pose, x, y + h, z).setUv(0, 1).setColor(shade, shade, shade, alpha);
        buffer.addVertex(pose, x + w, y + h, z).setUv(1, 1).setColor(shade, shade, shade, alpha);
        buffer.addVertex(pose, x + w, y, z).setUv(1, 0).setColor(shade, shade, shade, alpha);
        buffer.addVertex(pose, x, y, z).setUv(0, 0).setColor(shade, shade, shade, alpha);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
    private ACSkillTreeRenderer() {}
}
