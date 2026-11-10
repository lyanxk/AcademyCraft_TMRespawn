package cn.academy.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Translucent, linearly filtered GUI quad path for smoothly scaled legacy textures. */
public final class ACGuiTextures {
    private record TextureKey(ResourceLocation location, boolean linear, boolean monochrome, boolean mipmapped) {}
    private static final Map<TextureKey, RenderType> TYPES = new HashMap<>();
    private static final Map<net.minecraft.client.renderer.texture.AbstractTexture, Integer> MIPMAPS = new java.util.IdentityHashMap<>();
    static final RenderStateShard.TransparencyStateShard BLENDING = RenderStateShard.TRANSLUCENT_TRANSPARENCY;

    public static void blit(GuiGraphics gui, ResourceLocation texture, float x, float y,
                            float u, float v, float width, float height, float textureWidth, float textureHeight) {
        blit(gui, texture, x, y, width, height, u, v, width, height, textureWidth, textureHeight);
    }

    public static void blit(GuiGraphics gui, ResourceLocation texture, float x, float y, float width, float height,
                            float u, float v, float regionWidth, float regionHeight,
                            float textureWidth, float textureHeight) {
        blit(gui, texture, x, y, width, height, u, v, regionWidth, regionHeight,
                textureWidth, textureHeight, 1, 1, 1, 1);
    }

    public static void blit(GuiGraphics gui, ResourceLocation texture, float x, float y, float width, float height,
                            float u, float v, float regionWidth, float regionHeight,
                            float textureWidth, float textureHeight,
                            float red, float green, float blue, float alpha) {
        blit(gui, texture, x, y, width, height, u, v, regionWidth, regionHeight,
                textureWidth, textureHeight, red, green, blue, alpha, true);
    }

    static void blitFont(GuiGraphics gui, ResourceLocation texture, int width, int height,
                         float red, float green, float blue, float alpha) {
        blit(gui, texture, 0, 0, width, height, 0, 0, width, height, width, height,
                red, green, blue, alpha, false);
    }

    private static void blit(GuiGraphics gui, ResourceLocation texture, float x, float y, float width, float height,
                             float u, float v, float regionWidth, float regionHeight,
                             float textureWidth, float textureHeight,
                             float red, float green, float blue, float alpha, boolean linear) {
        blit(gui, texture, x, y, width, height, u, v, regionWidth, regionHeight,
                textureWidth, textureHeight, red, green, blue, alpha, linear, false);
    }

    /** Full legacy HUD texture with optional ShaderMono conversion and vertex alpha. */
    public static void hudQuad(GuiGraphics gui, ResourceLocation texture, float x, float y, float width, float height,
                               float red, float green, float blue, float alpha, boolean monochrome) {
        blit(gui, texture, x, y, width, height, 0, 0, 1, 1, 1, 1,
                red, green, blue, alpha, true, monochrome);
    }

    /** Resources.preloadMipmapTexture for the original quarter-size about background. */
    public static void hudQuadMipmapped(GuiGraphics gui, ResourceLocation location, float x, float y, float width, float height) {
        hudQuadMipmapped(gui, location, x, y, width, height, 1);
    }

    public static void hudQuadMipmapped(GuiGraphics gui, ResourceLocation location, float x, float y, float width, float height, float alpha) {
        gui.flush();
        var texture = Minecraft.getInstance().getTextureManager().getTexture(location);
        int id = texture.getId();
        if (!java.util.Objects.equals(MIPMAPS.get(texture), id)) {
            texture.bind();
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL, 1000);
            org.lwjgl.opengl.GL11.glTexParameterf(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LOD, 1000);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER);
            org.lwjgl.opengl.GL30.glGenerateMipmap(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            MIPMAPS.put(texture, id);
        }
        blit(gui, location, x, y, width, height, 0, 0, 1, 1, 1, 1, 1, 1, 1, alpha, true, false, true);
    }

    private static void blit(GuiGraphics gui, ResourceLocation texture, float x, float y, float width, float height,
                             float u, float v, float regionWidth, float regionHeight,
                             float textureWidth, float textureHeight,
                             float red, float green, float blue, float alpha, boolean linear, boolean monochrome) {
        blit(gui, texture, x, y, width, height, u, v, regionWidth, regionHeight,
                textureWidth, textureHeight, red, green, blue, alpha, linear, monochrome, false);
    }

    private static void blit(GuiGraphics gui, ResourceLocation texture, float x, float y, float width, float height,
                             float u, float v, float regionWidth, float regionHeight,
                             float textureWidth, float textureHeight,
                             float red, float green, float blue, float alpha, boolean linear, boolean monochrome, boolean mipmapped) {
        gui.flush();
        VertexConsumer output = gui.bufferSource().getBuffer(type(texture, linear, monochrome, mipmapped));
        float u0 = u / textureWidth, v0 = v / textureHeight;
        float u1 = (u + regionWidth) / textureWidth, v1 = (v + regionHeight) / textureHeight;
        var pose = gui.pose().last();
        output.addVertex(pose, x, y + height, 0).setUv(u0, v1).setColor(red, green, blue, alpha);
        output.addVertex(pose, x + width, y + height, 0).setUv(u1, v1).setColor(red, green, blue, alpha);
        output.addVertex(pose, x + width, y, 0).setUv(u1, v0).setColor(red, green, blue, alpha);
        output.addVertex(pose, x, y, 0).setUv(u0, v0).setColor(red, green, blue, alpha);
        gui.flush();
    }

    /** Selects bilinear sampling for a regular GuiGraphics blit without changing its blend path. */
    public static void setLinearFilter(ResourceLocation texture) {
        Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(true, false);
    }

    public static synchronized void clear() { TYPES.clear(); MIPMAPS.clear(); }

    static synchronized void release(ResourceLocation texture) {
        TYPES.keySet().removeIf(key -> key.location.equals(texture));
    }

    private static synchronized RenderType type(ResourceLocation texture, boolean linear, boolean monochrome, boolean mipmapped) {
        return TYPES.computeIfAbsent(new TextureKey(texture, linear, monochrome, mipmapped), key -> RenderType.create("academy_gui_translucent_" + key,
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(key.monochrome
                                ? ACShaders::guiMonochrome : GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(key.location, key.linear, key.mipmapped))
                        .setTransparencyState(BLENDING)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false)));
    }

    private ACGuiTextures() {}
}
