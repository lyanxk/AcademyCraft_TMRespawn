package cn.academy.client.screen;

import cn.academy.block.entity.ACMachineBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** BlockConverterBase opens only WirelessPage.userPage; there is no inventory container. */
public final class EnergyBridgeScreen extends Screen {
    private final BlockPos source;
    private DeveloperWirelessPage page;

    public EnergyBridgeScreen(BlockPos source) {
        super(Component.empty());
        this.source = source.immutable();
    }
    @Override protected void init() {
        if (page == null) page = new DeveloperWirelessPage(source, font);
    }
    private float left() { return (width - 400) * .5f; }
    private float top() { return (height - 187) * .5f; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics gui, int mx, int my, float partialTick) {
        // CGuiScreen.drawDefaultBackground used the vanilla 1.12 translucent gradient.
        gui.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
    }
    @Override public void render(GuiGraphics gui, int mx, int my, float partialTick) {
        renderBackground(gui, mx, my, partialTick);
        gui.pose().pushPose();
        gui.pose().translate(left(), top(), 0);
        page.render(gui, mx - left(), my - top(), partialTick);
        gui.pose().popPose();
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        return page.mouseClicked((float) mx - left(), (float) my - top(), button)
                || super.mouseClicked(mx, my, button);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scan, modifiers);
        return page.keyPressed(key, scan, modifiers) || super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char ch, int modifiers) {
        return page.charTyped(ch, modifiers) || super.charTyped(ch, modifiers);
    }
    @Override public void tick() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null
                || !(minecraft.level.getBlockEntity(source) instanceof ACMachineBlockEntity machine)
                || !(machine.kind().isBridgeInput() || machine.kind().isBridgeOutput())
                || minecraft.player.distanceToSqr(source.getCenter()) > 64) onClose();
    }
}
