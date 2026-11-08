package cn.academy.client.screen;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityState;
import cn.academy.client.TerminalOverlay;
import cn.academy.client.render.ACGuiTextures;
import cn.academy.client.render.ACLegacyFont;
import cn.academy.client.sound.ACContextSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import cn.academy.client.ACKeyMappings;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/** Perspective terminal panel hosted by the native non-pausing Screen input lifecycle. */
public final class TerminalScreen extends Screen {
    public static final int MAX_MX = 605, MAX_MY = 740;
    private static final ResourceLocation BACK = texture("data_terminal/back.png");
    private static final ResourceLocation LOGO = texture("data_terminal/logo.png");
    private static final ResourceLocation APP_BACK = texture("data_terminal/app_back.png");
    private static final ResourceLocation APP_HIGHLIGHT = texture("data_terminal/app_back_highlight.png");
    private static final ResourceLocation ARROW_UP = texture("data_terminal/arrow_up.png");
    private static final ResourceLocation ARROW_DOWN = texture("data_terminal/arrow_down.png");
    private static final Random RANDOM = new Random();
    private static final class AppEntry {
        final String id;
        final ResourceLocation icon;
        final Supplier<Screen> screen;
        boolean lastSelected = true;
        AppEntry(String id, ResourceLocation icon, Supplier<Screen> screen) {
            this.id = id; this.icon = icon; this.screen = screen;
        }
    }
    private final List<AppEntry> apps = new ArrayList<>();
    private float mouseX = 150, mouseY = 150, buffX = 150, buffY = 150;
    private double createdAt, firstFrame = -1;
    private int selection = -1, scroll, displayScroll, stateHash;
    private boolean initialized, leftHeld, hasProjection;
    private final Matrix4f screenToTerminal = new Matrix4f();

    public TerminalScreen() { super(Component.translatable("ac.gui.terminal.title")); }
    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/guis/" + path);
    }

    @Override protected void init() {
        if (!initialized) {
            createdAt = TerminalOverlay.seconds(); refreshApps(); initialized = true;
            cn.academy.terminal.DonatorList.Instance.tryRequest();
        }
    }
    public void refreshApps() {
        if (minecraft == null || minecraft.player == null) return;
        var state = AbilityState.load(minecraft.player);
        stateHash = java.util.Objects.hash(state.terminalInstalled(), state.apps());
        apps.clear();
        for (var app : cn.academy.terminal.TerminalData.get(minecraft.player).getInstalledApps()) {
            Supplier<Screen> screen = switch (app.getName()) {
                case "skill_tree" -> SkillTreeScreen::new;
                case "tutorial" -> TutorialScreen::new;
                case "freq_transmitter" -> FrequencyTransmitterScreen::new;
                case "media_player" -> MediaPlayerScreen::new;
                case "settings" -> SettingsScreen::new;
                case "about" -> AboutScreen::new;
                default -> null;
            };
            if (screen == null) continue;
            ResourceLocation icon = app.getIcon();
            if (app == cn.academy.terminal.AppRegistry.TUTORIAL) {
                float random = RANDOM.nextFloat();
                int tutorialIcon = random < .2f ? 0 : random < .3f ? 1 : 2;
                icon = texture("apps/tutorial/icon_" + tutorialIcon + ".png");
            }
            apps.add(new AppEntry(app.getName(), icon, screen));
        }
        scroll = Math.min(scroll, maxScroll()); displayScroll = scroll;
    }
    private int maxScroll() { return Math.max(0, (apps.size() + 2) / 3 - 3); }
    private int selectedIndex() {
        if (selection < 0) return -1;
        int index = displayScroll * 3 + selection;
        return index < 0 || index >= apps.size() ? -1 : index;
    }
    public void releaseClick() {
        int selected = selectedIndex();
        if (selected < 0 || minecraft == null) return;
        var app = apps.get(selected);
        if (app.id.equals("freq_transmitter")) TerminalOverlay.close();
        // Normal applications leave the terminal behind their Screen and return to it when closed.
        minecraft.setScreen(app.screen.get());
    }
    @Override public void tick() {
        if (minecraft != null && minecraft.player != null) {
            var state = AbilityState.load(minecraft.player);
            if (java.util.Objects.hash(state.terminalInstalled(), state.apps()) != stateHash) refreshApps();
        }
    }

    @Override public void render(GuiGraphics gui, int cursorX, int cursorY, float partialTick) {
        if (minecraft == null || minecraft.player == null) return;
        double time = TerminalOverlay.seconds();
        if (firstFrame < 0) firstFrame = time;
        buffX = balance(time - firstFrame, buffX, mouseX);
        buffY = balance(time - firstFrame, buffY, mouseY);

        gui.flush();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity(); RenderSystem.applyModelViewMatrix();
        gui.pose().pushPose();
        try {
            float aspect = (float)minecraft.getWindow().getWidth() / minecraft.getWindow().getHeight();
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float)Math.toRadians(50), aspect, 1, 100), VertexSorting.DISTANCE_TO_ORIGIN);
            gui.pose().setIdentity();
            gui.pose().mulPose(new Matrix4f().translate(.35f * aspect, 1.2f, -4).translate(1, -1.8f, 0)
                    .rotateZ((float)Math.toRadians(-1.6))
                    .rotateY((float)Math.toRadians(-18 - 4 * (buffX / MAX_MX - .5) + Math.sin(time / 1000)))
                    .rotateX((float)Math.toRadians(7 + 4 * (buffY / MAX_MY - .5)))
                    .translate(-1, 1.8f, 0).scale(1 / 310f, -1 / 310f, 1 / 310f));
            screenToTerminal.set(RenderSystem.getProjectionMatrix()).mul(gui.pose().last().pose()).invert();
            hasProjection = true;
            if (minecraft.screen == this) updatePointer(cursorX, cursorY);
            gui.setColor(1, 1, 1, 1);
            quad(gui, BACK, 0, 0, 640, 785, 0, 1);
            quad(gui, LOGO, 40, 50, 50, 50, 0, 1);
            text(gui, Component.literal("DATA"), 98, 42, 40, 170, 0, 100, 15, true);
            text(gui, Component.literal("TERMINAL"), 98, 74, 40, 170, 0, 160, 15, true);
            text(gui, minecraft.player.getName(), 600, 41.666668f, 40, 204, 2, 200, 15, true);
            int dayTime = (int)(minecraft.level.getDayTime() % 24000);
            Component count = Component.literal(Component.translatable("ac.gui.terminal.appcount", apps.size()).getString()
                    + String.format(", %02d:%02d", dayTime / 1000, dayTime % 1000 * 60 / 1000));
            text(gui, count, 600, 84, 30, 153, 2, 300, 5, true);
            if (scroll > 0) quad(gui, ARROW_UP, 280, 133, 80, 20, 0, 1);
            if (scroll < maxScroll()) quad(gui, ARROW_DOWN, 280, 725, 80, 20, 0, 1);
            int selected = selectedIndex();
            for (int visible = 0; visible < 9; visible++) {
                int index = displayScroll * 3 + visible; if (index >= apps.size()) break;
                var app = apps.get(index);
                boolean highlighted = selected == index;
                if (highlighted && !app.lastSelected)
                    ACContextSounds.once(minecraft.player, "terminal.select", .2f, SoundSource.MASTER);
                app.lastSelected = highlighted;
                float alpha = Mth.clamp((float)((time - createdAt - (index + 1) * .1f) / .40f), 0, 1);
                int x = 65 + visible % 3 * 180, y = 155 + visible / 3 * 180;
                float z = highlighted ? 40 : 10;
                quad(gui, highlighted ? APP_HIGHLIGHT : APP_BACK, x, y, 151, 151, z, alpha);
                quad(gui, app.icon, x + 9, y + 32, 110, 110, z, (highlighted ? .8f : .6f) * alpha);
                text(gui, Component.translatable("ac.app." + app.id + ".name"), x + 75.5f, y + 148,
                        32, (int)((.1f + (highlighted ? .72f : .1f) * alpha) * 255), 1, 151, z, false);
            }
            gui.flush();
        } finally {
            gui.pose().popPose(); modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting); gui.setColor(1, 1, 1, 1);
        }
    }

    private Vector3f updatePointer(double x, double y) {
        selection = -1;
        if (!hasProjection || width <= 0 || height <= 0) return null;
        float nx = (float)(2 * x / width - 1), ny = (float)(1 - 2 * y / height);
        Vector3f near = new Vector3f(nx, ny, -1).mulProject(screenToTerminal);
        Vector3f far = new Vector3f(nx, ny, 1).mulProject(screenToTerminal);
        Vector3f ray = far.sub(near);
        if (Math.abs(ray.z) < 1.0e-6f) return null;
        Vector3f point = ray.mul((10 - near.z) / ray.z).add(near);
        if (!point.isFinite()) return null;
        mouseX = Mth.clamp(point.x, 0, MAX_MX);
        mouseY = Mth.clamp(point.y - 120, 0, MAX_MY);
        for (int visible = 0; visible < 9; visible++) {
            float appX = 65 + visible % 3 * 180, appY = 155 + visible / 3 * 180;
            if (point.x >= appX && point.x <= appX + 151 && point.y >= appY && point.y <= appY + 151) {
                selection = visible;
                break;
            }
        }
        return point;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || ACKeyMappings.TERMINAL.matchesMouse(button)) {
            TerminalOverlay.close();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Vector3f point = updatePointer(x, y);
            leftHeld = point != null && selectedIndex() >= 0;
            if (point != null && point.x >= 280 && point.x <= 360) {
                if (point.y >= 133 && point.y <= 153) scroll(-1);
                else if (point.y >= 725 && point.y <= 745) scroll(1);
            }
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            boolean clicked = leftHeld;
            leftHeld = false;
            updatePointer(x, y);
            if (clicked) releaseClick();
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    private void scroll(int amount) {
        scroll = Mth.clamp(scroll + amount, 0, maxScroll());
        displayScroll = scroll;
        selection = -1;
        leftHeld = false;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0) scroll(vertical > 0 ? -1 : 1);
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || ACKeyMappings.TERMINAL.matches(key, scan)) {
            TerminalOverlay.close();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() { TerminalOverlay.close(); }
    @Override public void removed() { leftHeld = false; }

    private static float balance(double dt, float from, float to) {
        return (float)(from + Math.min(3000 * dt, Math.abs(to - from)) * Math.signum(to - from));
    }
    private static void quad(GuiGraphics gui, ResourceLocation texture, float x, float y, float w, float h, float z, float alpha) {
        gui.pose().pushPose(); gui.pose().translate(0, 0, z);
        ACGuiTextures.hudQuad(gui, texture, x, y, w, h, 1, 1, 1, alpha, false); gui.pose().popPose();
    }
    static void text(GuiGraphics gui, Component content, float x, float y, float size, int alpha,
                     int align, float width, float z, boolean emit) {
        if (emit) content = Component.literal(emitted(content.getString(), size, width));
        // ACLegacyFont adds its ordinary GUI text offset internally; use the XML zLevel here.
        gui.pose().pushPose(); gui.pose().translate(0, 0, z - 20);
        ACLegacyFont.draw(gui, content, x, y, size, (alpha << 24) | 0xffffff, align, 0, false);
        gui.pose().popPose();
    }
    private static String emitted(String value, float size, float width) {
        int end = 0;
        while (end < value.length()) {
            end += Character.charCount(value.codePointAt(end));
            if (ACLegacyFont.width(Component.literal(value.substring(0, end)), size, false) >= width) break;
        }
        return value.substring(0, end);
    }
    @Override public boolean isPauseScreen() { return false; }
}
