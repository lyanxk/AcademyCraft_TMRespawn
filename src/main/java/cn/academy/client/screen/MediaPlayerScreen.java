package cn.academy.client.screen;

import cn.academy.ability.AbilityState;
import cn.academy.client.TerminalOverlay;
import cn.academy.client.render.ACGuiTextures;
import cn.academy.client.render.ACLegacyFont;
import cn.academy.client.sound.ACMediaLibrary;
import cn.academy.client.sound.ACMediaLibrary.Track;
import cn.academy.client.sound.ACMediaPlayer;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** MediaGui's original XML coordinates, list snapshot, editable external entries and drag controls. */
public final class MediaPlayerScreen extends Screen {
    private static final ResourceLocation BACKGROUND = texture("apps/media_player/back"), PLAY = texture("apps/media_player/play"),
            PAUSE = texture("apps/media_player/pause"), STOP = texture("apps/media_player/stop"),
            VOLUME = texture("icons/volume_overlay"), EDIT = texture("icons/edit");
    private List<Track> installed = List.of();
    private boolean initialized, draggingVolume, draggingScroll;
    private float scrollProgress, dragOffset;
    private int scroll;
    private double updated;
    private Track displayed, editing;
    private boolean displayPaused, editingDescription;
    private float displayedSeconds, displayedProgress;
    private LegacyInfoTextBox editor;

    public MediaPlayerScreen() { super(Component.translatable("ac.app.media_player.name")); }
    @Override protected void init() {
        if (!initialized && minecraft != null && minecraft.player != null) {
            var acquired = AbilityState.load(minecraft.player).apps();
            installed = ACMediaLibrary.tracks().stream().filter(track -> track.external() || acquired.contains("media:" + track.id())).toList();
            initialized = true;
            updatePlayDisplay();
        }
    }
    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/" + path + ".png");
    }
    private float scale() { return .32f; }
    private float left() { return (width - 650 * scale()) * .5f; }
    private float top() { return (height - 504 * scale()) * .5f; }
    private int maxScroll() { return Math.max(0, installed.size() - 5); }
    private void updatePlayDisplay() {
        displayed = ACMediaPlayer.current(); displayPaused = ACMediaPlayer.paused();
        displayedSeconds = ACMediaPlayer.elapsedSeconds(); displayedProgress = ACMediaPlayer.progress();
        updated = TerminalOverlay.seconds();
    }

    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        if (TerminalOverlay.seconds() - updated > .5) updatePlayDisplay();
        float mx = (mouseX - left()) / scale(), my = (mouseY - top()) / scale();
        gui.pose().pushPose(); gui.pose().translate(left(), top(), 0); gui.pose().scale(scale(), scale(), 1);
        image(gui, BACKGROUND, 0, 0, 650, 504, 1);
        text(gui, displayed == null ? Component.empty() : displayed.name(), 324.4213f, 77.81148f,
                275.5787f, 36, 0x99FFFFFF, ACLegacyFont.RIGHT);
        text(gui, Component.literal(ACMediaPlayer.formatTime(displayedSeconds)), 403.7037f, 126.76981f,
                200, 30, 0xDDFFFFFF, ACLegacyFont.RIGHT);
        rect(gui, 52, 120, 554 * Mth.clamp(displayedProgress, 0, 1), 6, 0xFFFFFFFF);
        image(gui, displayed != null && !displayPaused ? PAUSE : PLAY, 51.85185f, 71.79296f, 50, 42,
                inside(mx, my, 51.85185f, 71.79296f, 50, 42) ? 1 : 200 / 255f);
        image(gui, STOP, 114.81481f, 71.79296f, 50, 42,
                inside(mx, my, 114.81481f, 71.79296f, 50, 42) ? 1 : 200 / 255f);
        image(gui, VOLUME, 179.6875f, 77, 128, 32, 1);
        float volumeX = 186 + 112 * ACMediaPlayer.volume();
        rect(gui, volumeX, 77.875f, 9.375f, 30,
                inside(mx, my, volumeX, 77.875f, 9.375f, 30) ? 0xCCFFFFFF : 0x99FFFFFF);
        for (int row = 0; row < Math.min(5, installed.size() - scroll); row++) {
            Track track = installed.get(scroll + row);
            float y = 169 + row * 60;
            rect(gui, 51, y, 554, 60, inside(mx, my, 51, y, 554, 60) ? 0x3CFFFFFF : 0x14FFFFFF);
            image(gui, track.cover(), 55, y + 5, 50, 50, 1);
            if (editor == null || editing != track || editingDescription)
                text(gui, track.name(), 116, y + 1, 300, 35, 0xBBFFFFFF, ACLegacyFont.LEFT);
            if (editor == null || editing != track || !editingDescription)
                text(gui, track.description(), 117.125f, y + 29, 300, 27, 0xEEFFFFFF, ACLegacyFont.LEFT);
            text(gui, Component.literal(track.displayLength()), 529, y + 15, 70, 28, 0xB9FFFFFF, ACLegacyFont.RIGHT);
            if (track.external()) {
                image(gui, EDIT, 419, y + 7.125f, 20, 20, inside(mx, my, 419, y + 7.125f, 20, 20) ? 1 : .2f);
                image(gui, EDIT, 419, y + 35.25f, 20, 20, inside(mx, my, 419, y + 35.25f, 20, 20) ? 1 : .2f);
            }
        }
        rect(gui, 604, 169 + 246 * scrollProgress, 5, 55, 0x82FFFFFF);
        if (editor != null) {
            rect(gui, editor.getX(), editor.getY(), editor.getWidth(), editor.getHeight(), 0x33666666);
            editor.render(gui, (int) mx, (int) my, partialTick);
        }
        gui.pose().popPose();
    }
    private static void image(GuiGraphics gui, ResourceLocation texture, float x, float y, float w, float h, float alpha) {
        ACGuiTextures.hudQuad(gui, texture, x, y, w, h, 1, 1, 1, alpha, false);
    }
    private static void rect(GuiGraphics gui, float x, float y, float w, float h, int color) {
        if (w <= 0 || h <= 0) return;
        gui.pose().pushPose(); gui.pose().translate(x, y, 0); gui.pose().scale(w, h, 1);
        gui.fill(0, 0, 1, 1, color); gui.pose().popPose();
    }
    private static void text(GuiGraphics gui, Component text, float x, float y, float width, float size, int color, int align) {
        String value = text.getString();
        // TextBox.emit truncates after the first character crossing the field width, without shrinking the font.
        float advance = 0; int end = 0;
        while (end < value.length() && advance < width) {
            int next = value.offsetByCodePoints(end, 1);
            advance += ACLegacyFont.width(Component.literal(value.substring(end, next)), size, false);
            end = next;
        }
        ACLegacyFont.draw(gui, Component.literal(value.substring(0, end)), align == ACLegacyFont.RIGHT ? x + width : x,
                y, size, color, align, 0, false);
    }
    private void edit(Track track, boolean description, int row) {
        commitEdit(); editing = track; editingDescription = description;
        editor = new LegacyInfoTextBox(font, 0, 0, 300, description ? 23 : 30, Component.empty(), false, description ? 27 : 35);
        editor.position(description ? 117.125f : 116, 169 + row * 60 + (description ? 29 : 1));
        editor.setMaxLength(Integer.MAX_VALUE);
        editor.changeColor = false;
        editor.setAlpha(description ? 238 / 255f : 187 / 255f);
        editor.setValue((description ? track.description() : track.name()).getString());
        editor.setCursorPosition(0);
        editor.onLostFocus = this::commitEdit;
        editor.setFocused(true);
    }
    private void commitEdit() {
        if (editor == null) return;
        var old = editor; editor = null;
        if (editingDescription) editing.describe(old.getValue()); else editing.rename(old.getValue());
        old.setFocused(false); editing = null;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        float mx = (float) ((x - left()) / scale()), my = (float) ((y - top()) / scale());
        if (editor != null && editor.mouseClicked(mx, my, button)) return true;
        commitEdit();
        if (button != 0) return super.mouseClicked(x, y, button);
        if (inside(mx, my, 51.85185f, 71.79296f, 50, 42)) {
            ACMediaPlayer.togglePause(installed.isEmpty() ? null : installed.getFirst()); updatePlayDisplay(); return true;
        }
        if (inside(mx, my, 114.81481f, 71.79296f, 50, 42)) { ACMediaPlayer.stop(); updatePlayDisplay(); return true; }
        float volumeX = 186 + 112 * ACMediaPlayer.volume();
        if (inside(mx, my, volumeX, 77.875f, 9.375f, 30)) {
            draggingVolume = true; dragOffset = mx - volumeX; return true;
        }
        float scrollY = 169 + 246 * scrollProgress;
        if (inside(mx, my, 604, scrollY, 5, 55)) { draggingScroll = true; dragOffset = my - scrollY; return true; }
        if (inside(mx, my, 51, 169, 554, 300)) {
            int row = (int) ((my - 169) / 60), index = scroll + row;
            if (index < installed.size()) {
                Track track = installed.get(index);
                if (track.external() && inside(mx, my, 419, 169 + row * 60 + 7.125f, 20, 20)) edit(track, false, row);
                else if (track.external() && inside(mx, my, 419, 169 + row * 60 + 35.25f, 20, 20)) edit(track, true, row);
                else { ACMediaPlayer.play(track); updatePlayDisplay(); }
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && draggingVolume) {
            ACMediaPlayer.setVolume(Mth.clamp(((float) ((x - left()) / scale()) - dragOffset - 186) / 112, 0, 1)); return true;
        }
        if (button == 0 && draggingScroll) {
            scrollProgress = Mth.clamp(((float) ((y - top()) / scale()) - dragOffset - 169) / 246, 0, 1);
            scroll = (int) (scrollProgress * maxScroll()); return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && (draggingVolume || draggingScroll)) { draggingVolume = draggingScroll = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (editor != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { commitEdit(); return true; }
            if (editor.keyPressed(key, scanCode, modifiers)) return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        return editor != null && editor.charTyped(character, modifiers) || super.charTyped(character, modifiers);
    }
    @Override public void removed() { commitEdit(); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
    private static boolean inside(float x, float y, float left, float top, float w, float h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }
}
