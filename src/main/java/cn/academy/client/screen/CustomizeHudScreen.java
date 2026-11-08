package cn.academy.client.screen;

import cn.academy.client.render.ACGuiTextures;
import cn.academy.client.render.ACLegacyFont;
import cn.academy.client.render.ACNotifications;
import cn.academy.config.ACHudConfig;
import cn.academy.config.ACHudConfig.Element;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** CustomizeUI.java and ui_edit.xml: list, previews, outline, and X/Y confirmation. */
public final class CustomizeHudScreen extends Screen {
    private Element selected;

    public CustomizeHudScreen() { super(Component.translatable("ac.settings.prop.edit_ui")); }

    @Override protected void init() { selected = null; setFocused(null); }

    private void select(Element element) {
        if (selected == element) return;
        selected = element;
        setFocused(null);
        clearWidgets();
        var pos = ACHudConfig.position(element);
        int y = 123 + element.ordinal() * 12;
        addRenderableWidget(new HudCoordinateBox(font, 177, y, Component.literal("X"), pos.x(), value -> {
            var current = ACHudConfig.position(element);
            ACHudConfig.save(element, new ACHudConfig.Position(value, current.y()));
        }));
        addRenderableWidget(new HudCoordinateBox(font, 220, y, Component.literal("Y"), pos.y(), value -> {
            var current = ACHudConfig.position(element);
            ACHudConfig.save(element, new ACHudConfig.Position(current.x(), value));
        }));
    }

    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // CGuiScreen's world background was a gradient, not Minecraft 1.21's blurred menu backdrop.
        gui.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        gui.setColor(1, 1, 1, 1);
        for (Element element : Element.values()) {
            var b = element.bounds(width, height, ACHudConfig.position(element), true);
            gui.pose().pushPose();
            gui.pose().translate(b.x(), b.y(), 0);
            gui.pose().scale(b.scale(), b.scale(), 1);
            switch (element) {
                case CPBAR -> texture(gui, "edit_preview/cpbar", 0, 0, 964, 147);
                case KEYHINT -> texture(gui, "edit_preview/key_hint", 0, 0, 128, 193);
                case NOTIFICATION -> ACNotifications.renderPreview(gui);
                case MEDIA -> cn.academy.client.sound.ACMediaPlayer.renderPreview(gui);
            }
            gui.pose().popPose();
            if (element == selected) outline(gui, b.x(), b.y(), b.width(), b.height(), 1);
        }
        texture(gui, "window_ui_resize", 94, 104, 72, 101.5f);
        text(gui, Component.translatable("ac.gui.uiedit.elements"), 100.5f, 110.5f, 9, 0xBBFFFFFF);
        for (Element element : Element.values()) {
            int y = 122 + element.ordinal() * 12;
            boolean hover = mouseX >= 98 && mouseX < 162 && mouseY >= y && mouseY < y + 12;
            rect(gui, 98, y, 64, 12, hover ? 0x7FFFFFFF : 0x19FFFFFF);
            text(gui, Component.translatable("ac.gui.uiedit.elm." + element.id), 103, y + 1.5f, 9, 0xFFFFFFFF);
        }
        if (selected != null) {
            int y = 120 + selected.ordinal() * 12;
            rect(gui, 167, y, 90, 16, 0xBB222222);
            outline(gui, 167, y, 90, 16, 1);
            text(gui, Component.literal("X"), 169, y + 3, 10, 0xFFFFFFFF);
            text(gui, Component.literal("Y"), 213, y + 3, 10, 0xFFFFFFFF);
        }
        for (var renderable : renderables) renderable.render(gui, mouseX, mouseY, partialTick);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && x >= 98 && x < 162 && y >= 122 && y < 122 + Element.values().length * 12) {
            select(Element.values()[(int) ((y - 122) / 12)]);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    private static void texture(GuiGraphics gui, String path, float x, float y, float w, float h) {
        ACGuiTextures.hudQuad(gui, ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/" + path + ".png"),
                x, y, w, h, 1, 1, 1, 1, false);
    }
    private static void text(GuiGraphics gui, Component text, float x, float y, float size, int color) {
        ACLegacyFont.draw(gui, text, x, y, size, color, ACLegacyFont.LEFT, 0, false);
    }
    private static void rect(GuiGraphics gui, float x, float y, float w, float h, int color) {
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        gui.pose().scale(w, h, 1);
        gui.fill(0, 0, 1, 1, color);
        gui.pose().popPose();
    }
    private static void outline(GuiGraphics gui, float x, float y, float w, float h, float line) {
        rect(gui, x, y, w, line, -1);
        rect(gui, x, y + h - line, w, line, -1);
        rect(gui, x, y, line, h, -1);
        rect(gui, x + w - line, y, line, h, -1);
    }
}
