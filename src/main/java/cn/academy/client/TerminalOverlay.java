package cn.academy.client;

import cn.academy.ability.AbilityState;
import cn.academy.client.render.ACKeyHintHud;
import cn.academy.client.screen.TerminalInstallingScreen;
import cn.academy.client.screen.TerminalScreen;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Native terminal Screen lifetime, with its panel retained behind application Screens. */
public final class TerminalOverlay {
    private static TerminalScreen terminal;
    private static TerminalInstallingScreen installing;
    private static LocalPlayer player;
    private static long lastTime = -1, elapsed;
    private TerminalOverlay() { }

    public static double seconds() {
        long now = Util.getMillis();
        if (lastTime >= 0 && !Minecraft.getInstance().isPaused()) elapsed += Math.max(0, now - lastTime);
        lastTime = now;
        return elapsed / 1000.0;
    }
    private static void syncPlayer() {
        var current = Minecraft.getInstance().player;
        if (player != current) {
            terminal = null; installing = null; player = current;
        }
    }
    private static void size(Screen screen) {
        var mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        if (screen.width != width || screen.height != height) screen.init(mc, width, height);
    }
    public static boolean active() { return terminal != null; }
    public static boolean capturesMouse() {
        var mc = Minecraft.getInstance();
        return terminal != null && mc.player != null && mc.screen == terminal && mc.isWindowActive();
    }
    public static void open() {
        syncPlayer();
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (terminal == null) terminal = new TerminalScreen();
        mc.setScreen(terminal);
    }
    public static void close() {
        var mc = Minecraft.getInstance();
        ACClientEvents.suppressTerminalInputUntilRelease();
        boolean showing = terminal != null && mc.screen == terminal;
        terminal = null;
        if (showing) mc.setScreen(null);
    }
    public static void refreshApps() {
        syncPlayer();
        if (terminal != null) terminal.refreshApps();
    }
    public static void toggle() {
        syncPlayer();
        var mc = Minecraft.getInstance();
        if (mc.player == null || ACFrequencyTransmitter.active()) return;
        if (terminal != null) close();
        else if (AbilityState.load(mc.player).terminalInstalled()) open();
        else mc.player.displayClientMessage(Component.translatable("ac.terminal.notinstalled"), true);
    }
    public static void install() {
        syncPlayer();
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        installing = new TerminalInstallingScreen();
        installing.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }
    public static void finishInstall() {
        installing = null;
        toggle();
        var mc = Minecraft.getInstance();
        if (mc.player != null && !ACKeyMappings.TERMINAL.isUnbound()) mc.player.sendSystemMessage(Component.translatable("ac.terminal.key_hint",
                ACKeyHintHud.keyLabel(ACKeyMappings.TERMINAL)));
    }
    public static void tick() {
        seconds(); syncPlayer();
        var mc = Minecraft.getInstance();
        if (terminal != null) {
            if (mc.screen == null && mc.player != null && mc.player.isAlive()) mc.setScreen(terminal);
            if (mc.screen != terminal) { size(terminal); terminal.tick(); }
        }
        if (installing != null) size(installing);
    }
    public static void render(GuiGraphics gui, float partialTick) {
        if (terminal != null && Minecraft.getInstance().screen != terminal) terminal.render(gui, 0, 0, partialTick);
        if (installing != null) installing.render(gui, 0, 0, partialTick);
    }
}
