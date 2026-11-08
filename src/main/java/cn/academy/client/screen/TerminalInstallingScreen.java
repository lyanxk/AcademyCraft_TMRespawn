package cn.academy.client.screen;

import cn.academy.client.TerminalOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Drawing delegate for TerminalInstallEffect; installation does not block gameplay. */
public final class TerminalInstallingScreen extends Screen {
    private double started = Double.NaN;
    public TerminalInstallingScreen() { super(Component.translatable("ac.gui.terminal.installing")); }
    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        double now = TerminalOverlay.seconds();
        if (Double.isNaN(started)) started = now;
        double elapsed = now - started;
        float alpha = (float)(elapsed < .2 ? elapsed / .2 : elapsed > 4 ? Math.max(0, 1 - (elapsed - 4) / .2) : 1);
        float progress = Mth.clamp((float)(elapsed / 4), 0, 1);
        rect(gui, 164, 203, 150, 9, 0x3c3c3c, (int)(120 * alpha));
        rect(gui, 165.5f, 204.5f, 147, 6, 0xffffff, (int)(150 * alpha));
        rect(gui, 166, 205, 146, 5, 0x1e1e1e, (int)(200 * alpha));
        rect(gui, 164, 195, 40, 8, 0x3c3c3c, (int)(120 * alpha));
        TerminalScreen.text(gui, title, 164, 195, 10, (int)(25 + .9 * 255 * alpha), 0, 40, 0, true);
        rect(gui, 166.5f, 205.5f, 145 * progress, 4, 0xffffff, (int)(200 * alpha));
        if (elapsed >= 4.7) TerminalOverlay.finishInstall();
    }
    private static void rect(GuiGraphics gui, float x, float y, float width, float height, int rgb, int alpha) {
        if (width <= 0 || alpha <= 0) return;
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0); gui.pose().scale(width, height, 1);
        gui.fill(0, 0, 1, 1, alpha << 24 | rgb);
        gui.pose().popPose();
    }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
}
