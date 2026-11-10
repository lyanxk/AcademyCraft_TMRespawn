package cn.academy.client.screen;

import cn.academy.ability.AbilityState;
import cn.academy.client.SkillKeyBindings;
import cn.academy.ability.AbilityRegistry;
import cn.academy.client.render.ACGuiTextures;
import cn.academy.client.render.ACLegacyFont;
import cn.academy.config.ACHudConfig;
import cn.academy.event.ConfigModifyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** SettingsUI / PropertyElements / ElementList, in the original settings.xml coordinates. */
public final class SettingsScreen extends Screen {
    private static final float SCALE = .2f;
    private static final ResourceLocation BACK = texture("settings");
    private static final ResourceLocation CHECK_TRUE = texture("check_true");
    private static final ResourceLocation CHECK_FALSE = texture("check_false");
    private static final ResourceLocation SCROLL = texture("life_record/rollbar");
    private enum Kind { HEADER, CHECKBOX, KEY, CALLBACK, GAP }
    private record Row(Kind kind, String id, int slot, ModConfigSpec.BooleanValue property) {
        float height() { return kind == Kind.GAP ? 20 : 60; }
    }
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Boolean> checkboxValues = new HashMap<>();
    private int editingKey = -1;
    private InputConstants.Key firstKey;
    private List<String> skillIds = List.of();
    private float scrollProgress, dragOffset;
    private boolean draggingScroll, configDirty;

    public SettingsScreen() { super(Component.translatable("ac.app.settings.name")); }
    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/" + name + ".png");
    }
    private float left() { return (width - 742 * SCALE) / 2; }
    private float top() { return (height - 923 * SCALE) / 2; }

    @Override protected void init() {
        if (!rows.isEmpty()) return;
        if (minecraft != null && minecraft.player != null) {
            var state = AbilityState.load(minecraft.player);
            skillIds = state.controllableSlots().stream().map(state::controlSkill).toList();
        }
        // The old registry is a HashMap: keys, generic, misc for these three category IDs.
        Map<String, List<Row>> categories = new HashMap<>();
        List<Row> generic = new ArrayList<>();
        generic.add(new Row(Kind.CHECKBOX, "headsOrTails", -1, ACHudConfig.HEADS_OR_TAILS));
        generic.add(new Row(Kind.CHECKBOX, "useMouseWheel", -1, ACHudConfig.USE_MOUSE_WHEEL));
        categories.put("generic", generic);
        List<Row> keys = new ArrayList<>();
        for (int i = 0; i < skillIds.size(); i++) keys.add(new Row(Kind.KEY, skillIds.get(i), i, null));
        categories.put("keys", keys);
        categories.put("misc", List.of(new Row(Kind.CALLBACK, "edit_ui", -1, null)));
        categories.forEach((category, entries) -> {
            rows.add(new Row(Kind.HEADER, category, -1, null));
            rows.addAll(entries);
            rows.add(new Row(Kind.GAP, "", -1, null));
        });
        for (Row row : rows) if (row.kind == Kind.CHECKBOX) checkboxValues.put(row.id, row.property.get());
    }

    // ElementList scrolls by whole entries and only draws entries that fit completely.
    private int maxProgress() {
        float sum = 0;
        for (int i = rows.size() - 1; i >= 0; i--) {
            sum += rows.get(i).height();
            if (sum >= 720) return i == rows.size() - 1 ? i : i + 1;
        }
        return 0;
    }
    private int firstRow() { return (int) (maxProgress() * scrollProgress); }

    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        float mx = (mouseX - left()) / SCALE, my = (mouseY - top()) / SCALE;
        gui.pose().pushPose(); gui.pose().translate(left(), top(), 0); gui.pose().scale(SCALE, SCALE, 1);
        ACGuiTextures.hudQuad(gui, BACK, 0, 0, 742, 923, 1, 1, 1, 1, false);
        float handleY = 119 + 641 * scrollProgress;
        ACGuiTextures.hudQuad(gui, SCROLL, 673, handleY, 9, 96, 1, 1, 1, 1, false);
        if (inside(mx, my, 673, handleY, 9, 96)) rect(gui, 673, handleY, 9, 96, 0x50FFFFFF);
        float y = 120;
        for (int i = firstRow(); i < rows.size(); i++) {
            Row row = rows.get(i);
            if (y + row.height() > 840) break;
            renderRow(gui, row, y, mx, my);
            y += row.height();
        }
        gui.pose().popPose();
    }

    private void renderRow(GuiGraphics gui, Row row, float y, float mx, float my) {
        if (row.kind == Kind.GAP) return;
        if (row.kind == Kind.HEADER) {
            rect(gui, 60, y + 56, 611, 4, 0x96FFFFFF);
            text(gui, Component.translatable("ac.settings.cat." + row.id), 68, y + 18, 42, 0xAAFFFFFF, ACLegacyFont.LEFT);
            return;
        }
        Component name = row.kind == Kind.KEY ? AbilityRegistry.skill(row.id).displayName()
                : Component.translatable("ac.settings.prop." + row.id);
        float nameSize = row.kind == Kind.KEY ? Math.min(40, 40 * 410 / Math.max(1, ACLegacyFont.width(name, 40, false))) : 40;
        text(gui, name, 75, y + 10, nameSize,
                0xFFFFFFFF, ACLegacyFont.LEFT);
        if (row.kind == Kind.CHECKBOX) {
            boolean checked = checkboxValues.get(row.id);
            ACGuiTextures.hudQuad(gui, checked ? CHECK_TRUE : CHECK_FALSE,
                    610, y + 12.5f, 35, 35, 1, 1, 1, 1, false);
        } else if (row.kind == Kind.KEY) {
            if (inside(mx, my, 500, y + 10, 160, 40)) rect(gui, 500, y + 10, 160, 40, 0x33FFFFFF);
            String label = editingKey == row.slot ? firstKey == null ? "…" : firstKey.getDisplayName().getString()
                    : SkillKeyBindings.label(row.id);
            // EditKey uses the XML white initially, then its normal gray after a completed edit.
            int color = editingKey == row.slot ? 0xC8FB8525 : editedKeys.contains(row.slot) ? 0xC8C8C8C8 : 0xFFFFFFFF;
            Component binding = Component.literal(label);
            float keySize = Math.min(40, 40 * 155 / Math.max(1, ACLegacyFont.width(binding, 40, false)));
            text(gui, binding, 500, y + 10, keySize, color, ACLegacyFont.LEFT);
        } else {
            rect(gui, 500, y + 10, 160, 40, inside(mx, my, 500, y + 10, 160, 40) ? 0xFF555555 : 0xFF333333);
            text(gui, Component.literal("OK"), 580, y + 10, 40, 0xFFFFFFFF, ACLegacyFont.CENTER);
        }
    }
    private final java.util.Set<Integer> editedKeys = new java.util.HashSet<>();
    private static void text(GuiGraphics gui, Component text, float x, float y, float size, int color, int align) {
        ACLegacyFont.draw(gui, text, x, y, size, color, align, 0, false);
    }
    private static void rect(GuiGraphics gui, float x, float y, float w, float h, int color) {
        gui.pose().pushPose(); gui.pose().translate(x, y, 0); gui.pose().scale(w, h, 1);
        gui.fill(0, 0, 1, 1, color); gui.pose().popPose();
    }
    private static boolean inside(double x, double y, double left, double top, double w, double h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }

    private void toggle(Row row) {
        boolean value = !checkboxValues.get(row.id);
        checkboxValues.put(row.id, value);
        row.property.set(value);
        configDirty = true;
        NeoForge.EVENT_BUS.post(new ConfigModifyEvent("generic", row.id, row.property));
    }
    private void finishKey(List<InputConstants.Key> keys) {
        int slot = editingKey;
        editingKey = -1;
        editedKeys.add(slot);
        firstKey = null;
        SkillKeyBindings.set(skillIds.get(slot), keys);
        NeoForge.EVENT_BUS.post(new cn.academy.event.ability.FlushControlEvent());
    }
    private void captureKey(InputConstants.Key key) {
        if (firstKey == null) firstKey = key;
        else if (!firstKey.equals(key)) finishKey(List.of(firstKey, key));
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (editingKey >= 0) { captureKey(InputConstants.Type.MOUSE.getOrCreate(button)); return true; }
        if (button != 0) return super.mouseClicked(x, y, button);
        float mx = (float) ((x - left()) / SCALE), my = (float) ((y - top()) / SCALE);
        float handleY = 119 + 641 * scrollProgress;
        if (inside(mx, my, 673, handleY, 9, 96)) { draggingScroll = true; dragOffset = my - handleY; return true; }
        float rowY = 120;
        for (int i = firstRow(); i < rows.size(); i++) {
            Row row = rows.get(i);
            if (rowY + row.height() > 840) break;
            if (row.kind == Kind.CHECKBOX
                    && inside(mx, my, 610, rowY + 12.5, 35, 35)) { toggle(row); return true; }
            if (inside(mx, my, 500, rowY + 10, 160, 40)) {
                if (row.kind == Kind.KEY) { editingKey = row.slot; firstKey = null; return true; }
                if (row.kind == Kind.CALLBACK && minecraft != null) { minecraft.setScreen(new CustomizeHudScreen()); return true; }
            }
            rowY += row.height();
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (editingKey >= 0) {
            if (key == GLFW.GLFW_KEY_ESCAPE) finishKey(List.of());
            else if (firstKey == null && (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE)) finishKey(List.of());
            else captureKey(InputConstants.getKey(key, scanCode));
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (editingKey >= 0 && InputConstants.getKey(key, scanCode).equals(firstKey)) {
            finishKey(List.of(firstKey)); return true;
        }
        return super.keyReleased(key, scanCode, modifiers);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && draggingScroll) {
            scrollProgress = Mth.clamp(((float) ((y - top()) / SCALE) - dragOffset - 119) / 641, 0, 1);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (editingKey >= 0 && InputConstants.Type.MOUSE.getOrCreate(button).equals(firstKey)) {
            finishKey(List.of(firstKey)); return true;
        }
        if (button == 0 && draggingScroll) { draggingScroll = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public void removed() {
        editingKey = -1;
        draggingScroll = false;
        if (configDirty) { ACHudConfig.SPEC.save(); configDirty = false; }

        super.removed();
    }
}
