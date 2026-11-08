package cn.academy.client.screen;

import cn.academy.client.render.ACLegacyFont;
import cn.academy.config.ACHudConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.function.DoubleConsumer;

/** Original 34x10 TextBox styling, with native editing/clipboard and Enter-only confirmation. */
final class HudCoordinateBox extends EditBox {
    private final DoubleConsumer confirm;
    private boolean invalid;
    private int selection;
    private float scroll;

    HudCoordinateBox(Font font, int x, int y, Component label, double value, DoubleConsumer confirm) {
        super(font, x, y, 34, 10, label);
        this.confirm = confirm;
        setBordered(false);
        setMaxLength(64);
        setValue(Double.toString(value));
    }

    boolean invalid() { return invalid; }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            try {
                double value = ACHudConfig.parseCoordinate(getValue());
                confirm.accept(value);
                invalid = false;
            } catch (NumberFormatException e) { invalid = true; }
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void setHighlightPos(int position) {
        super.setHighlightPos(position);
        selection = Math.clamp(position, 0, getValue().length());
    }

    private float advance(int index) {
        return ACLegacyFont.width(Component.literal(getValue().substring(0, index)), 10, false);
    }

    @Override public void onClick(double mouseX, double mouseY) {
        float x = (float) mouseX - getX() + scroll;
        int index = 0;
        while (index < getValue().length() && (advance(index) + advance(index + 1)) / 2 < x) index++;
        moveCursorTo(index, Screen.hasShiftDown());
    }

    @Override public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(getX(), getY(), getRight(), getBottom(), invalid ? 0xFFBB3333 : 0xFF333333);
        float caret = advance(getCursorPosition());
        if (caret < scroll) scroll = caret;
        if (caret > scroll + getWidth() - 1) scroll = caret - getWidth() + 1;
        scroll = Math.min(scroll, Math.max(0, advance(getValue().length()) - getWidth() + 1));
        gui.enableScissor(getX(), getY(), getRight(), getBottom());
        if (isFocused() && selection != getCursorPosition()) {
            float other = advance(Math.min(selection, getValue().length()));
            gui.fill(Math.round(getX() + Math.min(caret, other) - scroll), getY(),
                    Math.round(getX() + Math.max(caret, other) - scroll), getBottom(), 0xFF426A94);
        }
        ACLegacyFont.draw(gui, Component.literal(getValue()), getX() - scroll, getY(), 10,
                0xFFFFFFFF, ACLegacyFont.LEFT, 0, false);
        if (isFocused() && net.minecraft.Util.getMillis() / 500 % 2 == 0) {
            int x = Math.round(getX() + caret - scroll);
            gui.fill(x, getY(), x + 1, getBottom(), 0xFFFFFFFF);
        }
        gui.disableScissor();
    }
}
