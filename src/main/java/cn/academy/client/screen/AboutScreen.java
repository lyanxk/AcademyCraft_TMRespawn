package cn.academy.client.screen;

import cn.academy.AcademyCraft;
import cn.academy.client.render.ACGuiTextures;
import cn.academy.client.render.ACLegacyFont;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** AppAbout's resource-driven text, language fallback, randomized donors and original XML geometry. */
public final class AboutScreen extends Screen {
    private static final ResourceLocation BACK = texture("bg"), GLOW = texture("button_glow");
    private final List<TextItem> credits = new ArrayList<>(), donation = new ArrayList<>();
    private boolean donateTab, draggingScroll;
    private float scrollProgress, creditsMaxY, dragOffset;
    private String hoveringUrl;
    private record TextItem(float x, float y, String text, int align, boolean bold, float size, String url) {}

    public AboutScreen() { super(Component.translatable("ac.app.about.name")); buildContent(); }
    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/about/" + name + ".png");
    }
    private float left() { return (width - 742 * .25f) * .5f; }
    private float top() { return (height - 923 * .25f) * .5f; }
    private void addCredit(float x, float y, String text, int align, boolean bold, float size) {
        credits.add(new TextItem(x, y, text, align, bold, size, null));
    }
    private void buildContent() {
        credits.clear(); donation.clear();
        var minecraft = Minecraft.getInstance();
        try (var reader = new InputStreamReader(minecraft.getResourceManager().open(
                ResourceLocation.fromNamespaceAndPath("academy", "config/about.conf")), StandardCharsets.UTF_8)) {
            Config config = ConfigFactory.parseReader(reader);
            Config root = config.getConfig("credits");
            float y = 60;
            for (String header : root.getStringList("header")) {
                addCredit(0, y, header, ACLegacyFont.CENTER, true, 30); y += 30;
            }
            y += 60;
            for (var groupValue : root.getList("staff")) {
                ConfigList group = (ConfigList) groupValue;
                addCredit(-30, y, (String) group.getFirst().unwrapped(), ACLegacyFont.RIGHT, true, 30);
                for (int i = 1; i < group.size(); i++) {
                    addCredit(30, y, (String) group.get(i).unwrapped(), ACLegacyFont.LEFT, false, 30); y += 30;
                }
                y += 15;
            }
            y += 30;
            addCredit(0, y, "Donators", ACLegacyFont.CENTER, true, 30); y += 33;
            for (String hint : Component.translatable("ac.about.donators_info").getString().split("\\\\n")) {
                addCredit(0, y, hint, ACLegacyFont.CENTER, false, 21); y += 21;
            }
            y += 45;
            var names = new ArrayList<>(root.getStringList("donators"));
            Collections.shuffle(names);
            for (int i = 0; i < names.size(); i++) {
                float x = 30 + (i % 3) * (620 - 60 - 150) / 2f - 310;
                addCredit(x, y, names.get(i), ACLegacyFont.LEFT, false, 24);
                if (i % 3 == 2) y += 24;
            }
            y += 60;
            addCredit(0, y, "Thank you for playing!", ACLegacyFont.CENTER, true, 30); y += 30;
            creditsMaxY = y + 30;

            root = config.getConfig("donation");
            String language = minecraft.getLanguageManager().getSelected();
            if (!root.hasPath(language)) language = "en_us";
            y = 100;
            for (String line : root.getStringList(language)) {
                if (line.startsWith("!!")) {
                    int split = line.indexOf('|');
                    y += 10;
                    donation.add(new TextItem(-280, y, line.substring(2, split), ACLegacyFont.LEFT,
                            false, 40, line.substring(split + 1)));
                    y += 50;
                } else {
                    boolean right = line.startsWith("]");
                    donation.add(new TextItem(right ? 280 : -280, y, right ? line.substring(1) : line,
                            right ? ACLegacyFont.RIGHT : ACLegacyFont.LEFT, false, 30, null));
                    y += 30;
                }
            }
        } catch (Exception exception) { AcademyCraft.LOGGER.error("Cannot read AcademyCraft about.conf", exception); }
    }
    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        float mx = (mouseX - left()) / .25f, my = (mouseY - top()) / .25f;
        gui.pose().pushPose(); gui.pose().translate(left(), top(), 0); gui.pose().scale(.25f, .25f, 1);
        ACGuiTextures.hudQuadMipmapped(gui, BACK, 0, 0, 742, 923);
        tab(gui, 53, "Credits", !donateTab, inside(mx, my, 53, 266, 315, 58), false);
        tab(gui, 368, "Donate", donateTab, inside(mx, my, 368, 266, 315, 58), true);
        rect(gui, 53, 324, 620, 540, 0x15000000);
        float handleY = 324 + 472 * scrollProgress;
        rect(gui, 673, handleY, 10, 70, inside(mx, my, 673, handleY, 10, 70) ? 0xA7FFFFFF : 0x85FFFFFF);
        // Modern scissor clips the same rectangular scroll mask without leaking its depth state to other screens.
        gui.enableScissor((int) Math.floor(left() + 53 * .25f), (int) Math.floor(top() + 324 * .25f),
                (int) Math.ceil(left() + 673 * .25f), (int) Math.ceil(top() + 864 * .25f));
        hoveringUrl = null;
        float offset = donateTab ? 0 : scrollProgress * (creditsMaxY - 540 + 50);
        float previousSize = 30;
        for (TextItem item : donateTab ? donation : credits) {
            float y = item.y - offset;
            if (y <= -50 || y >= 590) continue;
            boolean hover = false;
            if (item.url != null) {
                // AppAbout measures a link using the previous FontOption size before assigning this item's size.
                float measured = ACLegacyFont.width(Component.literal(item.text), previousSize, item.bold);
                hover = inside(mx, my, 363 + item.x, 324 + y, measured, item.size);
                if (hover) hoveringUrl = item.url;
            }
            previousSize = item.size;
            int color = item.url == null ? 0xFFFFFFFF : hover ? 0xFF8ECBFF : 0xFF5BB4FF;
            ACLegacyFont.draw(gui, Component.literal(item.text), 363 + item.x, 324 + y,
                    item.size, color, item.align, 0, item.bold);
        }
        gui.disableScissor(); gui.pose().popPose();
    }
    private static void tab(GuiGraphics gui, float x, String title, boolean selected, boolean hover, boolean donation) {
        rect(gui, x, 266, 315, 58, selected ? 0x7FFFFFFF : 0x33FFFFFF);
        if (hover) rect(gui, x, 266, 315, 58, 0x33FFFFFF);
        if (selected) ACGuiTextures.hudQuad(gui, GLOW, x - 8.5f, 255, 332, 80,
                donation ? 231 / 255f : 41 / 255f, donation ? 156 / 255f : 132 / 255f,
                donation ? 1 : 241 / 255f, 1, false);
        ACLegacyFont.draw(gui, Component.literal(title), x + 157.5f, 274, 42,
                selected ? 0xFF3D3F4B : 0xFFFFFFFF, ACLegacyFont.CENTER, 0, false);
    }
    private static void rect(GuiGraphics gui, float x, float y, float w, float h, int color) {
        gui.pose().pushPose(); gui.pose().translate(x, y, 0); gui.pose().scale(w, h, 1);
        gui.fill(0, 0, 1, 1, color); gui.pose().popPose();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return super.mouseClicked(x, y, button);
        float mx = (float) ((x - left()) / .25f), my = (float) ((y - top()) / .25f);
        if (inside(mx, my, 53, 266, 630, 58)) { donateTab = mx >= 368; scrollProgress = 0; return true; }
        float handleY = 324 + 472 * scrollProgress;
        if (inside(mx, my, 673, handleY, 10, 70)) { draggingScroll = true; dragOffset = my - handleY; return true; }
        if (hoveringUrl != null && inside(mx, my, 53, 324, 620, 540)) {
            try { Util.getPlatform().openUri(URI.create(hoveringUrl)); }
            catch (IllegalArgumentException exception) { AcademyCraft.LOGGER.warn("Invalid about link {}", hoveringUrl, exception); }
            hoveringUrl = null; return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && draggingScroll) {
            scrollProgress = Mth.clamp(((float) ((y - top()) / .25f) - dragOffset - 324) / 472, 0, 1); return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggingScroll) { draggingScroll = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        // LWJGL's usual 120 units per notch * 0.001 * 0.2, converted from GLFW notch units.
        scrollProgress = Mth.clamp(scrollProgress - (float) vertical * .024f, 0, 1); return true;
    }
    private static boolean inside(float x, float y, float left, float top, float w, float h) {
        return x >= left && x <= left + w && y >= top && y <= top + h;
    }
    @Override public boolean isPauseScreen() { return false; }
}
