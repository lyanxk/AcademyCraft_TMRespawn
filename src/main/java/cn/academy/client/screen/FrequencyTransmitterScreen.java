package cn.academy.client.screen;

import cn.academy.client.ACFrequencyTransmitter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Bootstrap screen for the original world-HUD frequency transmitter.  The app
 * immediately returns mouse control to the world; only password entry uses the
 * transparent nested screen so modern Unicode text input remains available.
 */
public final class FrequencyTransmitterScreen extends Screen {
    public FrequencyTransmitterScreen() {
        super(Component.translatable("ac.app.freq_transmitter.name"));
    }

    @Override
    protected void init() {
        ACFrequencyTransmitter.start();
        if (minecraft != null) minecraft.tell(() -> {
            if (minecraft.screen == this) minecraft.setScreen(null);
        });
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // Bootstrap frame intentionally has no background or widgets.
    }

    @Override public boolean isPauseScreen() { return false; }

    public static final class PasswordScreen extends Screen {
        public PasswordScreen() {
            super(Component.translatable("ac.app.freq_transmitter.name"));
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            return ACFrequencyTransmitter.charTyped(codePoint) || super.charTyped(codePoint, modifiers);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V && minecraft != null) {
                ACFrequencyTransmitter.paste(minecraft.keyboardHandler.getClipboard());
                return true;
            }
            return ACFrequencyTransmitter.keyPressed(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return true;
        }

        @Override
        public void onClose() {
            ACFrequencyTransmitter.stop();
            super.onClose();
        }

        @Override
        public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
            // The HUD event renders the transmitter over the live world; no darkening layer.
        }

        @Override public boolean isPauseScreen() { return false; }
    }
}
