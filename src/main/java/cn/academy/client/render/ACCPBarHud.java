package cn.academy.client.render;

import cn.academy.ability.AbilityState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** CPBar.java port: original virtual coordinates, shaders, colors, timing and hints. */
public final class ACCPBarHud {
    private static CPBarAnimation animation = new CPBarAnimation();
    private static final CPBarInterference INTERFERENCE = new CPBarInterference(new java.util.Random());
    private static final ResourceLocation BACK = texture("cpbar/back_normal"), OVERLOAD_BACK = texture("cpbar/back_overload");
    private static final ResourceLocation CP = texture("cpbar/cp"), MASK = texture("cpbar/mask");
    private static final ResourceLocation FRONT = texture("cpbar/front_overload"), HIGHLIGHT = texture("cpbar/highlight_overload");

    public static void reset() { animation = new CPBarAnimation(); }

    private static long time() {
        return animation.updateClock(net.minecraft.Util.getMillis(), Minecraft.getInstance().isPaused());
    }

    public static void numbersHeld(boolean held) { time(); animation.numbers(held); }
    public static void presetSwitched() { time(); animation.switchPreset(); }
    public static void tickClock() { time(); }

    public static void render(GuiGraphics gui, AbilityState state, String activateHint, float consumption) {
        long time = time();
        float cp = Math.clamp(state.cp() / state.maxCp(), 0, 1);
        float overload = Math.clamp(state.overload() / state.maxOverload(), 0, 1);
        var frame = animation.frame(state.active(), state.overloaded(), cp, overload);
        if (frame.alpha() <= 0) return;
        float alpha = frame.alpha();
        gui.setColor(1, 1, 1, 1);
        gui.pose().pushPose();
        var element = cn.academy.config.ACHudConfig.Element.CPBAR;
        var bounds = element.bounds(gui.guiWidth(), gui.guiHeight(), cn.academy.config.ACHudConfig.position(element), false);
        gui.pose().translate(bounds.x(), bounds.y(), 0);
        gui.pose().scale(.2f, .2f, 1);
        if (state.interfered()) {
            var offset = INTERFERENCE.at(net.minecraft.Util.getMillis());
            gui.pose().translate(offset.x(), offset.y(), 0);
            alpha *= offset.alpha();
        }
        if (state.overloaded()) {
            quad(gui, OVERLOAD_BACK, 0, 0, 964, 147, alpha * .8f);
            CPBarTextures.overload(gui, FRONT, MASK, ((float) (time / 1000.0) % 10) / 10000f, alpha);
            quad(gui, HIGHLIGHT, 0, 0, 964, 147,
                    alpha * (float) (.3 + .35 * (Math.sin(time / 1000.0 / 200) + 1)));
        } else {
            quad(gui, BACK, 0, 0, 964, 147, alpha * .8f);
            float ol = frame.overload(), len = ol * 943;
            float[] color = overloadColor(ol);
            ACGuiTextures.blit(gui, MASK, 943 - len, 21, len, 104,
                    943 - len, 21, len, 104, 964, 147, color[0], color[1], color[2], color[3] * alpha);
        }
        var category = cn.academy.ability.AbilityRegistry.category(state.category());
        boolean low = state.interfered() || state.overloadLocked();
        if (category != null) {
            if (consumption > 0) {
                drawCp(gui, category.overlayIcon(), cp, low,
                        alpha * (float) (.2 + .1 * (1 + Math.sin(time / 80f))));
                drawCp(gui, category.overlayIcon(), Math.max(0, state.cp() - consumption) / state.maxCp(), low, alpha);
            } else drawCp(gui, category.overlayIcon(), frame.cp(), low, alpha);
        }
        if (frame.numbersAlpha() > 0) drawNumbers(gui, state, .6f * alpha * frame.numbersAlpha());
        if (activateHint != null) drawActivateHint(gui, activateHint);
        gui.pose().popPose();
        gui.setColor(1, 1, 1, 1);
    }

    static float[] cpColor(float progress) {
        return progress <= .35f ? lerp(0xFFF06767, 0xFFFFAE44, progress / .35f)
                : lerp(0xFFFFAE44, 0xFFFFFFFF, (progress - .35f) / .65f);
    }

    static float[] overloadColor(float progress) {
        return progress <= .55f ? lerp(0x0ADFDFDF, 0x23F0D49D, progress / .55f)
                : lerp(0x23F0D49D, 0x50F56464, (progress - .55f) / .45f);
    }

    private static float[] lerp(int from, int to, float factor) {
        var result = new float[4];
        int[] shifts = {16, 8, 0, 24};
        for (int i = 0; i < 4; i++) result[i] = (((from >> shifts[i]) & 255) * (1 - factor)
                + ((to >> shifts[i]) & 255) * factor) / 255f;
        return result;
    }

    private static void drawCp(GuiGraphics gui, ResourceLocation overlay, float progress, boolean low, float alpha) {
        float[] color = cpColor(progress);
        // Original autoLerp's first color is bound directly at zero progress.
        float opacity = progress <= 0 ? 1 : alpha * (low ? .3f : 1);
        CPBarTextures.cp(gui, CP, overlay, progress, color[0], color[1], color[2], opacity);
    }

    private static void drawNumbers(GuiGraphics gui, AbilityState state, float alpha) {
        var cp = Component.literal(String.format(java.util.Locale.ROOT, "%.0f", state.cp()));
        var ol = Component.literal(String.format(java.util.Locale.ROOT, "%.0f", state.overload()));
        var cpLabel = Component.literal("CP ");
        var olLabel = Component.literal("OL ");
        float len0 = Math.max(ACLegacyFont.width(cpLabel, 40, false), ACLegacyFont.width(olLabel, 40, false));
        float len1 = len0 + Math.max(ACLegacyFont.width(cp, 40, false), ACLegacyFont.width(ol, 40, false));
        int color = argb(alpha, 0xFFFFFF);
        text(gui, cpLabel, 110, 55, 40, color, ACLegacyFont.LEFT, false);
        text(gui, olLabel, 110, 85, 40, color, ACLegacyFont.LEFT, false);
        text(gui, cp, 110 + len1, 55, 40, color, ACLegacyFont.RIGHT, false);
        text(gui, ol, 110 + len1, 85, 40, color, ACLegacyFont.RIGHT, false);
        text(gui, Component.literal(String.format(java.util.Locale.ROOT, "/%.0f", state.maxCp())),
                110 + len1, 55, 40, color, ACLegacyFont.LEFT, false);
        text(gui, Component.literal(String.format(java.util.Locale.ROOT, "/%.0f", state.maxOverload())),
                110 + len1, 85, 40, color, ACLegacyFont.LEFT, false);
    }

    private static void drawPresets(GuiGraphics gui, int current, float alpha) {
        for (int i = 0; i < 4; i++) {
            float x = 580 + i * 62;
            rect(gui, x, 136, 52, 52, argb(alpha, 0x303030));
            text(gui, Component.literal(Integer.toString(i + 1)), x + 26, 141, 46,
                    argb(Math.max(.05f, alpha * .8f), 0xFFFFFF), ACLegacyFont.CENTER, true);
            if (i == current) glow(gui, x, 136, 52, 52, 5, 200f / 255);
        }
    }

    private static void drawActivateHint(GuiGraphics gui, String hint) {
        var text = Component.literal(hint);
        float width = ACLegacyFont.width(text, 44, false);
        rect(gui, 492 - width, 132, width + 16, 60, 0x46414141);
        glow(gui, 492 - width, 132, width + 16, 60, 5, 40f / 255);
        text(gui, text, 500, 140, 44, 0xA0FFFFFF, ACLegacyFont.RIGHT, false);
    }

    private static int argb(float alpha, int rgb) { return rgb | (int) (Math.clamp(alpha, 0, 1) * 255) << 24; }
    private static void text(GuiGraphics gui, Component text, float x, float y, float size, int color, int alignment, boolean bold) {
        ACLegacyFont.draw(gui, text, x, y, size, color, alignment, 0, bold);
    }
    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/" + path + ".png");
    }
    private static void quad(GuiGraphics gui, ResourceLocation texture, float x, float y, float w, float h, float alpha) {
        ACGuiTextures.hudQuad(gui, texture, x, y, w, h, 1, 1, 1, alpha, false);
    }
    private static void rect(GuiGraphics gui, float x, float y, float w, float h, int color) {
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        gui.pose().scale(w, h, 1);
        gui.fill(0, 0, 1, 1, color);
        gui.pose().popPose();
    }
    private static void glow(GuiGraphics gui, float x, float y, float w, float h, float size, float alpha) {
        quad(gui, texture("glow_left"), x - size, y, size, h, alpha);
        quad(gui, texture("glow_right"), x + w, y, size, h, alpha);
        quad(gui, texture("glow_up"), x, y - size, w, size, alpha);
        quad(gui, texture("glow_down"), x, y + h, w, size, alpha);
        quad(gui, texture("glow_lu"), x - size, y - size, size, size, alpha);
        quad(gui, texture("glow_ru"), x + w, y - size, size, size, alpha);
        quad(gui, texture("glow_ld"), x - size, y + h, size, size, alpha);
        quad(gui, texture("glow_rd"), x + w, y + h, size, size, alpha);
    }

    private ACCPBarHud() { }
}
