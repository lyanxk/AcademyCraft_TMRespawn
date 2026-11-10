package cn.academy.client.screen;

import cn.academy.client.TerminalOverlay;
import cn.academy.client.render.ACGuiTextures;
import cn.academy.client.render.ACLegacyFont;
import cn.academy.network.WirelessPageActionPayload;
import cn.academy.network.WirelessPageRequestPayload;
import cn.academy.network.WirelessPageSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shared WirelessPage.userPage, centered in a 400x187 developer cover or bridge screen. */
final class DeveloperWirelessPage {
    private static int nextSession = -1;
    private static final float X = 112, Y = 0;
    private final int session = nextSession--;
    private final BlockPos source;
    private final Font font;
    private int request, appliedRequest = -1, scroll;
    private WirelessPageSnapshotPayload snapshot;
    private final List<LegacyInfoTextBox> passwords = new ArrayList<>();

    DeveloperWirelessPage(BlockPos source, Font font) {
        this.source = source;
        this.font = font;
        PacketDistributor.sendToServer(new WirelessPageRequestPayload(session, ++request, source));
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/" + path + ".png");
    }

    private void update() {
        var response = WirelessPageSnapshotPayload.developerSnapshot(Minecraft.getInstance().player);
        if (response != null && response.containerId() == session && response.pos().equals(source)
                && response.requestId() == request && response.requestId() > appliedRequest) {
            snapshot = response;
            appliedRequest = response.requestId();
            scroll = 0;
            passwords.clear();
            for (var target : response.available()) {
                LegacyInfoTextBox field = null;
                if (target.encrypted()) {
                    field = new LegacyInfoTextBox(font, 0, 0, 48, 9, Component.literal(target.name()), true, 10);
                    field.setMaxLength(64);
                    field.changeColor = false;
                }
                passwords.add(field);
            }
        }
        for (int i = 0; i < passwords.size(); i++) {
            var field = passwords.get(i);
            if (field == null) continue;
            field.visible = i >= scroll && i < scroll + 7;
            if (!field.visible) field.setFocused(false);
            field.position(X + 80.5f, Y + 63.5f + (i - scroll) * 16);
        }
    }

    void render(GuiGraphics gui, float mx, float my, float partialTick) {
        update();
        quad(gui, "parent/parent_background", X, Y, 176, 187, 1);
        float breathe = (float)(.675 + (1 + Math.sin(TerminalOverlay.seconds() / .8)) * .5 * .175);
        quad(gui, "icons/icon_tonode", X + 10, Y + 10, 16, 16, breathe);
        text(gui, "Connected", X + 13, Y + 28, 58, .8f);
        text(gui, "Available", X + 13, Y + 52, 58, .8f);
        var linked = snapshot == null ? null : snapshot.linked().orElse(null);
        quad(gui, "element/element_background300x32", X + 7.5f, Y + 34.621094f, 150, 18, 1);
        quad(gui, "icons/icon_matrix", X + 15.5f, Y + 37.621094f, 12, 12, linked == null ? .6f : 1);
        text(gui, linked == null ? Component.translatable("ac.gui.common.pg_wireless.not_connected").getString()
                : linked.name(), X + 27.5f, Y + 39.621094f, 80, 1);
        icon(gui, linked == null ? "icons/icon_unconnected" : "icons/icon_connected", X + 132.5f,
                Y + 37.621094f, 12, linked == null ? .6f : .8f, linked == null ? .6f : 1, mx, my);
        if (snapshot != null) for (int row = 0; row < 7 && scroll + row < snapshot.available().size(); row++) {
            var target = snapshot.available().get(scroll + row);
            float y = Y + 60 + row * 16;
            quad(gui, "element/element_background300x32", X + 8, y, 150, 16, 1);
            quad(gui, "icons/icon_matrix", X + 16, y + 2, 12, 12, 178 / 255f);
            text(gui, target.name(), X + 28, y + 4, 40, 1);
            var field = passwords.get(scroll + row);
            if (field != null) {
                quad(gui, "icons/icon_key", X + 68, y + 2, 12, 12, field.isFocused() ? 1 : .6f);
                gui.pose().pushPose();
                gui.pose().translate(X + 80.5f, y + 3.5f, 0);
                gui.fill(0, 0, 48, 9, 0xAA222222);
                gui.pose().popPose();
                field.render(gui, (int)mx, (int)my, partialTick);
            }
            icon(gui, "icons/icon_unconnected", X + 133, y + 2, 12, .6f, .8f, mx, my);
        }
        icon(gui, "button/button_arrowupb", X + 154.5f, Y + 51.621094f, 16, .8f, 1, mx, my);
        icon(gui, "button/button_arrowdownb", X + 154.5f, Y + 161.20703f, 16, .8f, 1, mx, my);
    }

    boolean mouseClicked(float mx, float my, int button) {
        update();
        if (!inside(mx, my, X, Y, 176, 187)) return false;
        if (button != 0) return true;
        for (var field : passwords) if (field != null) {
            field.setFocused(false);
            if (field.visible && field.mouseClicked(mx, my, button)) field.setFocused(true);
        }
        if (snapshot == null) return true;
        if (snapshot.linked().isPresent() && inside(mx, my, X + 132.5f, Y + 37.621094f, 12, 12)) {
            PacketDistributor.sendToServer(new WirelessPageActionPayload(session, ++request, source, Optional.empty(), ""));
        } else if (inside(mx, my, X + 154.5f, Y + 51.621094f, 16, 16)) scroll = Math.max(0, scroll - 1);
        else if (inside(mx, my, X + 154.5f, Y + 161.20703f, 16, 16))
            scroll = Math.min(Math.max(0, snapshot.available().size() - 7), scroll + 1);
        else for (int row = 0; row < 7 && scroll + row < snapshot.available().size(); row++)
            if (inside(mx, my, X + 133, Y + 62 + row * 16, 12, 12)) { connect(scroll + row); break; }
        update();
        return true;
    }

    private void connect(int index) {
        var field = passwords.get(index);
        PacketDistributor.sendToServer(new WirelessPageActionPayload(session, ++request, source,
                Optional.of(snapshot.available().get(index).pos()), field == null ? "" : field.getValue()));
        if (field != null) field.setValue("");
    }

    boolean keyPressed(int key, int scan, int modifiers) {
        for (int i = 0; i < passwords.size(); i++) {
            var field = passwords.get(i);
            if (field != null && field.visible && field.isFocused()) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) connect(i);
                else field.keyPressed(key, scan, modifiers);
                return true;
            }
        }
        return false;
    }

    boolean charTyped(char ch, int modifiers) {
        for (var field : passwords) if (field != null && field.visible && field.isFocused()) return field.charTyped(ch, modifiers);
        return false;
    }

    private static void quad(GuiGraphics gui, String path, float x, float y, float w, float h, float alpha) {
        ACGuiTextures.hudQuad(gui, texture(path), x, y, w, h, 1, 1, 1, alpha, false);
    }
    private static void icon(GuiGraphics gui, String path, float x, float y, float size,
                             float idle, float hover, float mx, float my) {
        quad(gui, path, x, y, size, size, inside(mx, my, x, y, size, size) ? hover : idle);
    }
    private static void text(GuiGraphics gui, String value, float x, float y, float width, float alpha) {
        int end = 0;
        while (end < value.length()) {
            end = value.offsetByCodePoints(end, 1);
            if (ACLegacyFont.width(Component.literal(value.substring(0, end)), 9, false) >= width) break;
        }
        ACLegacyFont.draw(gui, Component.literal(value.substring(0, end)), x, y, 9,
                (Math.round(alpha * 255) << 24) | 0xFFFFFF, ACLegacyFont.LEFT, 0, false);
    }
    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
