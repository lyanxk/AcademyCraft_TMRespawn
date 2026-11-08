package cn.academy.client.screen;

import cn.academy.client.TerminalOverlay;
import cn.academy.client.render.ACLegacyFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import org.lwjgl.glfw.GLFW;

/** TechUI TextBox: whole-field copy/delete and an independently moving caret. */
final class LegacyInfoTextBox extends EditBox {
    private final boolean password;
    private final float textSize;
    private float fractionalX, fractionalY;
    Runnable onLostFocus = () -> { };
    private int displayOffset;
    private boolean changed;
    boolean encryptedHint;
    boolean changeColor = true;

    LegacyInfoTextBox(Font font, int x, int y, Component label, boolean password) {
        this(font, x, y, 40, 8, label, password, 8);
    }
    LegacyInfoTextBox(Font font, int x, int y, int width, int height, Component label, boolean password, float textSize) {
        super(font, x, y, width, height, label);
        this.password = password;
        this.textSize = textSize;
        setBordered(false);
    }
    void position(float x, float y) {
        setX((int)Math.floor(x)); setY((int)Math.floor(y));
        fractionalX = x - getX(); fractionalY = y - getY();
    }
    @Override public void setFocused(boolean value) {
        boolean lost = isFocused() && !value;
        super.setFocused(value);
        if (lost) onLostFocus.run();
    }
    void confirmed() { changed = false; }
    private String display() { return password ? "*".repeat(getValue().length()) : getValue(); }
    private int measure(String text) {
        return text.isEmpty() ? 0 : ACLegacyFont.width(Component.literal(text), textSize, false);
    }
    private void updateView() {
        String value = display();
        int caret = Mth.clamp(getCursorPosition(), 0, value.length());
        displayOffset = Mth.clamp(displayOffset, 0, caret);
        while (displayOffset < caret && measure(value.substring(displayOffset, caret)) > getWidth())
            displayOffset = value.offsetByCodePoints(displayOffset, 1);
    }
    private void edit(String value, int caret) {
        setValue(value);
        setCursorPosition(Math.min(caret, getValue().length()));
        changed = true;
        updateView();
    }
    @Override public void onClick(double mouseX, double mouseY) {
        updateView();
        String value = display();
        int chosen = displayOffset;
        for (int end = displayOffset; end < value.length();) {
            int next = value.offsetByCodePoints(end, 1);
            float midpoint = (measure(value.substring(displayOffset, end)) + measure(value.substring(displayOffset, next))) / 2f;
            if (mouseX - getX() - fractionalX < midpoint) break;
            chosen = end = next;
        }
        setCursorPosition(chosen);
    }
    @Override public boolean charTyped(char ch, int modifiers) {
        if (!canConsumeInput() || !StringUtil.isAllowedChatCharacter(ch)) return false;
        int caret = getCursorPosition(); String value = getValue();
        edit(value.substring(0, caret) + ch + value.substring(caret), caret + 1);
        return true;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!canConsumeInput()) return false;
        String value = getValue(); int caret = getCursorPosition();
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            int next = key == GLFW.GLFW_KEY_LEFT ? (caret == 0 ? 0 : value.offsetByCodePoints(caret, -1))
                    : (caret == value.length() ? caret : value.offsetByCodePoints(caret, 1));
            setCursorPosition(next); updateView(); return true;
        }
        if (Screen.hasControlDown() && key == GLFW.GLFW_KEY_C) {
            Minecraft.getInstance().keyboardHandler.setClipboard(value); return true;
        }
        if (Screen.hasControlDown() && key == GLFW.GLFW_KEY_V) {
            // Original TextBox paste leaves the caret at its previous position.
            edit(value.substring(0, caret) + Minecraft.getInstance().keyboardHandler.getClipboard() + value.substring(caret), caret);
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (caret > 0) {
                int previous = value.offsetByCodePoints(caret, -1);
                edit(value.substring(0, previous) + value.substring(caret), previous);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) { edit("", 0); return true; }
        return false;
    }
    @Override public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        updateView();
        String value = display();
        if (value.isEmpty() && password && encryptedHint && !isFocused()) value = "****";
        int end = displayOffset;
        while (end < value.length()) {
            end = value.offsetByCodePoints(end, 1);
            if (measure(value.substring(displayOffset, end)) >= getWidth()) break;
        }
        int color = ((int)(alpha * 255) << 24) | (changed && changeColor ? 0x2180d8 : 0xffffff);
        ACLegacyFont.draw(gui, Component.literal(value.substring(displayOffset, end)), getX() + fractionalX, getY() + fractionalY, textSize,
                color, ACLegacyFont.LEFT, 0, false);
        if (isFocused() && TerminalOverlay.seconds() % 2 < 1) {
            String prefix = display().substring(displayOffset, getCursorPosition());
            ACLegacyFont.draw(gui, Component.literal("|"), getX() + fractionalX + measure(prefix), getY() + fractionalY - 1, textSize,
                    color, ACLegacyFont.LEFT, 0, false);
        }
    }
}
