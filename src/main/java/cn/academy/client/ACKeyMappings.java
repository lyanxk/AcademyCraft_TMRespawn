package cn.academy.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Controls exposed through Minecraft's remapping screen. */
public final class ACKeyMappings {
    public static final String CATEGORY = "key.categories.academy";
    public static final KeyMapping ACTIVATE = keyboard("key.academy.activate", GLFW.GLFW_KEY_V);
    public static final KeyMapping CAST_SKILL = keyboard("key.academy.cast_skill", GLFW.GLFW_KEY_C);
    public static final KeyMapping TERMINAL = keyboard("key.academy.terminal", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping[] SLOTS = java.util.stream.IntStream
            .range(0, cn.academy.ability.AbilityState.SKILL_CHANNELS)
            .mapToObj(ignored -> CAST_SKILL).toArray(KeyMapping[]::new);

    /** Read physical input like the original KeyManager, even when a GUI clears KeyMapping.isDown. */
    public static boolean physicalDown(KeyMapping mapping) {
        if (!mapping.getKeyModifier().isActive(null)) return false;
        var key = mapping.getKey();
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE)
            return key.getValue() >= 0 && GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.KEYSYM)
            return key.getValue() >= 0 && InputConstants.isKeyDown(window, key.getValue());
        return mapping.isDown();
    }

    private static KeyMapping keyboard(String name, int keyCode) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, keyCode, CATEGORY);
    }

    private static KeyMapping mouse(String name, int button) {
        return new KeyMapping(name, InputConstants.Type.MOUSE, button, CATEGORY);
    }

    private ACKeyMappings() {}
}
