package cn.academy.client.render;

import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilityState;
import cn.academy.client.ACKeyMappings;
import cn.academy.network.AbilityContextSyncPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/** Ports KeyHintUI's default geometry and draw order, including the TP_Flashing delegate group. */
public final class ACKeyHintHud {
    static final float SCALE = .23f;
    private static Object lastPlayer;
    private static double showTime;

    sealed interface Draw permits Texture, Text, Box {}
    record Texture(ResourceLocation texture, float x, float y, float width, float height,
                   int color, boolean monochrome) implements Draw {}
    record Text(String value, float x, float y, float size, int color, int align) implements Draw {
        Text(String value, float x, float y, float size, int color) {
            this(value, x, y, size, color, ACLegacyFont.CENTER);
        }
    }
    record Box(float x, float y, float width, float height, int color) implements Draw {}
    record Hint(KeyMapping key, ResourceLocation icon, int left, int maximum, int state) {}

    public static void render(GuiGraphics gui, Minecraft minecraft, AbilityState state) {
        double time = Util.getMillis() / 1000.0;
        float alpha = opacity(minecraft.player, state.active(), time);
        if (!state.active()) return;
        var options = minecraft.options;
        var directions = new KeyMapping[]{options.keyLeft, options.keyRight, options.keyUp, options.keyDown};
        var contexts = minecraft.player.getPersistentData().getCompound("academy:ability_contexts").copy();
        String flashingKey = Integer.toString(AbilityContextSyncPayload.FLASHING_CONTEXT);
        contexts.remove(flashingKey);
        if (FlashingEffects.controlsActive()) {
            var flashing = new CompoundTag(); flashing.putString("Skill", "teleporter.flashing");
            contexts.put(flashingKey, flashing);
        }
        gui.setColor(1, 1, 1, 1);
        var position = cn.academy.config.ACHudConfig.position(cn.academy.config.ACHudConfig.Element.KEYHINT);
        gui.pose().pushPose();
        gui.pose().translate(position.x(), position.y() - 30, 0);
        for (Draw draw : compose(gui.guiWidth(), gui.guiHeight(), state, contexts, directions, time, alpha)) {
            if (draw instanceof Texture quad) {
                int color = quad.color;
                ACGuiTextures.hudQuad(gui, quad.texture, quad.x, quad.y, quad.width, quad.height,
                        (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f,
                        (color & 255) / 255f, (color >>> 24) / 255f, quad.monochrome);
            } else if (draw instanceof Text text) {
                ACLegacyFont.draw(gui, Component.literal(text.value), text.x, text.y, text.size,
                        text.color, text.align, 0, false);
            } else if (draw instanceof Box box) {
                gui.fill(Math.round(box.x), Math.round(box.y), Math.round(box.x + box.width),
                        Math.round(box.y + box.height), box.color);
            }
        }
        gui.pose().popPose();
        gui.setColor(1, 1, 1, 1);
    }

    static float opacity(Object player, boolean active, double time) {
        if (!active || player == null) { lastPlayer = null; return 0; }
        // A long frame (e.g. firing a beam) is not a new activation. The old time-gap
        // heuristic made every slow frame fully transparent and could hide the whole HUD.
        if (lastPlayer != player) showTime = time;
        lastPlayer = player;
        return (float) Math.clamp((time - showTime) / .3, 0, 1);
    }

    public static void reset() { lastPlayer = null; }

    static List<List<Hint>> groups(AbilityState state, CompoundTag contexts, KeyMapping[] directions) {
        List<List<Hint>> groups = new ArrayList<>();
        List<Hint> defaults = new ArrayList<>();
        for (int slot : List.of(cn.academy.client.SkillSelection.selected(state))) {
            var skill = AbilityRegistry.skill(state.controlSkill(slot));
            if (skill == null) continue;
            var context = contexts.getCompound(Integer.toString(slot));
            // Flashing supplies a custom KeyDelegate whose getState stays IDLE, even while its main context runs.
            int visual = !skill.name().equals("flashing") && context.getString("Skill").equals(skill.id())
                    ? context.getInt("State") : 0;
            if (skill.name().equals("railgun")) visual = cn.academy.client.ClientRailgun.state(slot);
            if (skill.name().equals("storm_wing")) visual = StormWingEffects.state();
            defaults.add(new Hint(ACKeyMappings.SLOTS[slot], skill.icon(), state.cooldown(skill.id()),
                    state.cooldownMaximum(skill.id()), visual));
        }
        if (!defaults.isEmpty()) groups.add(defaults);
        if(StormWingEffects.controlsActive()) {
            List<Hint> movement=new ArrayList<>();var icon=AbilityRegistry.skill("vecmanip.storm_wing").icon();
            int[] order={2,3,0,1}; // Original group registration order: W, S, A, D.
            for(int i=0;i<4;i++)movement.add(new Hint(directions[order[i]],icon,0,0,StormWingEffects.direction()==i+1?AbilityContextSyncPayload.ACTIVE:0));
            groups.add(movement);
        }
        var flashing = contexts.getCompound(Integer.toString(AbilityContextSyncPayload.FLASHING_CONTEXT));
        if (flashing.getString("Skill").equals("teleporter.flashing")) {
            List<Hint> movement = new ArrayList<>();
            String[] icons = {"a", "d", "w", "s"};
            for (int i = 0; i < icons.length; i++)
                movement.add(new Hint(directions[i], resource("abilities/teleporter/flashing/" + icons[i]), 0, 0, 0));
            groups.add(movement);
        }
        return groups;
    }

    /** Pure draw data supports resource/layout regression tests without creating a game window. */
    static List<Draw> compose(int width, int height, AbilityState state, CompoundTag contexts,
                              KeyMapping[] directions, double time, float fade) {
        List<Draw> draws = new ArrayList<>();
        if (!state.active()) return draws;
        var groups = groups(state, contexts, directions);
        // CGui.updateWidget: RIGHT/CENTER alignment uses scaled widget size, unscaled pos(0, 30).
        float originX = width - 140 * SCALE;
        float originY = (height - 210 * SCALE) / 2 + 30;
        var selected = AbilityRegistry.skill(state.controlSkill(cn.academy.client.SkillSelection.selected(state)));
        if (selected != null) {
            draws.add(new Text(selected.displayName().getString(), originX + 88 * SCALE,
                    originY - 12, 8, alpha(0xFFFFFFFF, fade), ACLegacyFont.RIGHT));
            wheelHint(draws, originX - 78, originY + 45 * SCALE, fade);
        }
        float pulse = .6f + (1 + (float) Math.sin(((float) time % 100) / 50)) * .2f;
        for (int group = 0; group < groups.size(); group++) {
            float x = originX + (-200 - group * 200) * SCALE;
            for (int row = 0; row < groups.get(group).size(); row++) {
                Hint hint = groups.get(group).get(row);
                float y = originY + row * 92 * SCALE;
                boolean mono = !state.canUse() || hint.left > 0;
                texture(draws, "key_hint/back", x, y, 122, 0, 185, 83, alpha(0xFFFFFFFF, fade), false);
                String label = keyLabel(hint.key);
                String keyTexture;
                if (hint.key.getKey().getType() == InputConstants.Type.MOUSE) {
                    int button = hint.key.getKey().getValue();
                    keyTexture = button == 0 ? "mouse_left" : button == 1 ? "mouse_right" : "mouse_generic";
                    label = button <= 1 ? "" : Integer.toString(button);
                } else keyTexture = label.length() <= 2 ? "key_short" : "key_long";
                texture(draws, "key_hint/" + keyTexture, x, y, 146, 10, 70, 70, alpha(0xFFFFFFFF, fade), mono);
                if (!label.isEmpty()) draws.add(new Text(label, x + 180 * SCALE, y + 27 * SCALE, 32 * SCALE,
                        alpha(mono ? 0xFF363636 : 0xFF194246, fade)));
                texture(draws, "key_hint/icon_back", x, y, 216, 5, 72, 72, alpha(0xFFFFFFFF, fade), mono);
                float progress = hint.maximum <= 0 ? 0 : Math.clamp((float) hint.left / hint.maximum, 0, 1);
                boolean active = hint.state == AbilityContextSyncPayload.ACTIVE || hint.state == AbilityContextSyncPayload.CHARGING;
                float wave = active ? pulse : 1;
                float iconAlpha = progress == 0 ? (active ? 1 : .7f) * (.4f + wave * .6f) : .4f;
                draws.add(new Texture(hint.icon, x + 221 * SCALE, y + 10 * SCALE, 62 * SCALE, 62 * SCALE,
                        alpha(0xFFFFFFFF, fade * iconAlpha), mono));
                if (active) glow(draws, x, y, alpha(hint.state == AbilityContextSyncPayload.CHARGING
                        ? 0xFFFFAD37 : 0xFF46B3FF, fade * wave), mono);
                if (progress > 0) draws.add(new Box(x + 221 * SCALE, y + (10 + 62 * (1 - progress)) * SCALE,
                        62 * SCALE, 62 * progress * SCALE, alpha(0xFF999999, fade * .3f)));
            }
        }
        return draws;
    }

    private static void wheelHint(List<Draw> draws, float x, float centerY, float fade) {
        int white = alpha(0xFFFFFFFF, fade);
        int ink = alpha(0xFF194246, fade);
        float iconSize = 70 * SCALE;
        float top = centerY - iconSize / 2;
        float textY = centerY - 18 * SCALE;
        float textSize = 32 * SCALE;
        draws.add(new Texture(resource("guis/key_hint/key_long"), x - 4, top, 24, iconSize, white, false));
        draws.add(new Text("CTRL", x + 8, textY, textSize, ink));
        draws.add(new Text("+", x + 24, textY, textSize, white));
        draws.add(new Texture(resource("guis/key_hint/mouse_generic"), x + 30, top, iconSize, iconSize, white, false));
        draws.add(new Text("↑↓", x + 52, textY, textSize, white));
    }

    private static void glow(List<Draw> draws, float x, float y, int color, boolean mono) {
        texture(draws, "glow_left", x, y, 216, 10, 5, 62, color, mono);
        texture(draws, "glow_right", x, y, 283, 10, 5, 62, color, mono);
        texture(draws, "glow_up", x, y, 221, 5, 62, 5, color, mono);
        texture(draws, "glow_down", x, y, 221, 72, 62, 5, color, mono);
        texture(draws, "glow_ru", x, y, 283, 5, 5, 5, color, mono);
        texture(draws, "glow_rd", x, y, 283, 72, 5, 5, color, mono);
        texture(draws, "glow_lu", x, y, 216, 5, 5, 5, color, mono);
        texture(draws, "glow_ld", x, y, 216, 72, 5, 5, color, mono);
    }

    private static void texture(List<Draw> draws, String path, float x, float y, float dx, float dy,
                                float width, float height, int color, boolean mono) {
        draws.add(new Texture(resource("guis/" + path), x + dx * SCALE, y + dy * SCALE,
                width * SCALE, height * SCALE, color, mono));
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png");
    }

    private static int alpha(int color, float alpha) {
        return color & 0xFFFFFF | Math.round(Math.clamp(alpha, 0, 1) * 255) << 24;
    }

    public static String keyLabel(KeyMapping mapping) {
        int key = mapping.getKey().getValue();
        String label;
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z || key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9)
            label = Character.toString((char) key);
        else if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) label = "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        else if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) label = "NUMPAD" + (key - GLFW.GLFW_KEY_KP_0);
        else label = switch (key) {
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCONTROL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCONTROL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LMENU";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RMENU";
            case GLFW.GLFW_KEY_ENTER -> "RETURN";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACK";
            case GLFW.GLFW_KEY_ESCAPE -> "ESCAPE";
            case GLFW.GLFW_KEY_PAGE_UP -> "PRIOR";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "NEXT";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPITAL";
            default -> mapping.getKey().getName().replace("key.keyboard.", "").toUpperCase(java.util.Locale.ROOT);
        };
        var modifier = mapping.getKeyModifier();
        return modifier == net.neoforged.neoforge.client.settings.KeyModifier.NONE ? label : modifier.name() + "+" + label;
    }

    private ACKeyHintHud() {}
}
