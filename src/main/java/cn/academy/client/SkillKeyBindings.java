package cn.academy.client;

import cn.academy.config.ACHudConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Arrays;

/** Terminal-managed, optional one- or two-key chords for individual skills. */
public final class SkillKeyBindings {
    public static List<InputConstants.Key> keys(String skill) {
        var value = ACHudConfig.SKILL_KEYS.get(skill);
        if (value == null || !ACHudConfig.SPEC.isLoaded() || value.get().isBlank()) return List.of();
        try {
            return Arrays.stream(value.get().split(",")).limit(2).map(InputConstants::getKey).distinct().toList();
        } catch (IllegalArgumentException ignored) { return List.of(); }
    }

    public static void set(String skill, List<InputConstants.Key> keys) {
        var value = ACHudConfig.SKILL_KEYS.get(skill);
        if (value == null) return;
        value.set(keys.stream().distinct().limit(2).map(InputConstants.Key::getName)
                .collect(java.util.stream.Collectors.joining(",")));
        ACHudConfig.SPEC.save();
    }

    public static boolean physicalDown(InputConstants.Key key) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getValue() < 0) return false;
        return key.getType() == InputConstants.Type.MOUSE
                ? GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS
                : key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window, key.getValue());
    }

    public static boolean down(String skill) {
        var keys = keys(skill);
        return !keys.isEmpty() && keys.stream().allMatch(SkillKeyBindings::physicalDown);
    }

    public static boolean edgeDown(String skill, InputConstants.Key changed, boolean down) {
        var keys = keys(skill);
        return !keys.isEmpty() && keys.stream().allMatch(key -> key.equals(changed) ? down : physicalDown(key));
    }

    public static String label(String skill) {
        var keys = keys(skill);
        return keys.isEmpty() ? "None"
                : keys.stream().map(key -> key.getDisplayName().getString()).collect(java.util.stream.Collectors.joining(" + "));
    }
    private SkillKeyBindings() {}
}
