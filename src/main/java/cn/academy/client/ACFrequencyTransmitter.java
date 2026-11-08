package cn.academy.client;

import cn.academy.client.render.ACGuiTextures;

import cn.academy.AcademyCraft;
import cn.academy.block.MachineKind;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.block.entity.ACMultiblockPartEntity;
import cn.academy.client.screen.FrequencyTransmitterScreen;
import cn.academy.network.FrequencyTransmitterPayload;
import net.minecraft.util.StringUtil;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Modern world-HUD implementation of the 1.12.2 frequency transmitter AuxGui. */
public final class ACFrequencyTransmitter {
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MOD_ID, "textures/guis/apps/freq_transmitter/icon.png");
    private static final int BG_COLOR = 0x77272727;
    private static final int GLOW_COLOR = 0xAAFFFFFF;

    private static State state = State.INACTIVE;
    private static long stateStarted;
    private static long deadline;
    private static BlockPos primary = BlockPos.ZERO;
    private static String primaryName = "";
    private static String password = "";
    private static String input = "";
    private static String notifyKey = "";
    private static State returnState = State.INACTIVE;
    private static int nextRequest;
    private static int waitingRequest = -1;
    private static int waitingAction = -1;

    public static void start() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        primary = BlockPos.ZERO;
        primaryName = "";
        password = "";
        input = "";
        waitingRequest = -1;
        setState(State.START, 20_000);
    }

    public static void stop() {
        state = State.INACTIVE;
        waitingRequest = -1;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.getPersistentData().remove("academy:frequency_result");
    }

    public static boolean active() { return state != State.INACTIVE; }
    public static boolean enteringPassword() { return state == State.AUTH_MATRIX || state == State.AUTH_NODE; }

    public static void tick() {
        if (!active()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) { stop(); return; }
        CompoundTag result = minecraft.player.getPersistentData().getCompound("academy:frequency_result");
        if (!result.isEmpty()) {
            minecraft.player.getPersistentData().remove("academy:frequency_result");
            if (result.getInt("Request") == waitingRequest && result.getInt("Action") == waitingAction)
                handleResult(result.getInt("Action"), result.getInt("Result"), result.getString("Name"));
        }
        long now = Util.getMillis();
        if (state == State.NOTIFY_QUIT && now - stateStarted >= 1000) {
            stop();
        } else if (state == State.NOTIFY_RETURN && now - stateStarted >= 700) {
            state = returnState;
            stateStarted = now;
            // Keep the original link state's 20-second absolute deadline.
        } else if (deadline > 0 && now >= deadline && state != State.NOTIFY_QUIT) {
            notifyQuit("st");
        }
    }

    private static void handleResult(int action, int result, String displayName) {
        waitingRequest = -1;
        if (action == FrequencyTransmitterPayload.AUTH_MATRIX || action == FrequencyTransmitterPayload.AUTH_NODE) {
            if (result == 0) {
                if (!displayName.isEmpty()) primaryName = displayName;
                password = input;
                input = "";
                setState(action == FrequencyTransmitterPayload.AUTH_MATRIX ? State.LINK_NODES : State.LINK_USERS, 20_000);
            } else notifyQuit(result == 1 ? "e1" : "e0");
        } else {
            if (result == 0) notifyReturn("e6", action == FrequencyTransmitterPayload.LINK_NODE
                    ? State.LINK_NODES : State.LINK_USERS);
            else notifyQuit(action == FrequencyTransmitterPayload.LINK_NODE ? "e2" : "e3");
        }
    }

    public static void rightClickTarget() {
        if (!active()) return;
        Target target = lookedAtMachine();
        if (state == State.START) {
            if (target == null) { stop(); return; }
            if (target.machine.kind() == MachineKind.MATRIX) {
                if (!target.machine.networkInitialized()) { notifyQuit("e0"); return; }
                primary = target.pos;
                primaryName = target.machine.networkId();
                input = "";
                setState(State.AUTH_MATRIX, 20_000);
                openPasswordScreen();
            } else if (target.machine.kind().isNetworkNode()) {
                primary = target.pos;
                primaryName = target.machine.nodeName();
                input = "";
                setState(State.AUTH_NODE, 20_000);
                openPasswordScreen();
            } else notifyQuit("e4");
            return;
        }
        if (state == State.LINK_NODES) {
            if (target == null || !target.machine.kind().isNetworkNode()
                    || target.machine.kind() == MachineKind.MATRIX) { notifyQuit("e4"); return; }
            send(FrequencyTransmitterPayload.LINK_NODE, target.pos, password);
            return;
        }
        if (state == State.LINK_USERS) {
            if (target == null || target.machine.kind().isNetworkNode()
                    || !target.machine.isWirelessConfigurable()) { notifyQuit("e4"); return; }
            send(FrequencyTransmitterPayload.LINK_USER, target.pos, password);
        }
    }

    private static Target lookedAtMachine() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
        if (blockEntity instanceof ACMultiblockPartEntity part) {
            pos = part.origin();
            blockEntity = minecraft.level.getBlockEntity(pos);
        }
        return blockEntity instanceof ACMachineBlockEntity machine ? new Target(pos.immutable(), machine) : null;
    }

    public static boolean charTyped(char character) {
        if (!enteringPassword() || !StringUtil.isAllowedChatCharacter(character) || input.length() >= 64) return false;
        input += character;
        return true;
    }

    public static boolean keyPressed(int keyCode) {
        if (!enteringPassword()) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            int action = state == State.AUTH_MATRIX ? FrequencyTransmitterPayload.AUTH_MATRIX
                    : FrequencyTransmitterPayload.AUTH_NODE;
            send(action, BlockPos.ZERO, input);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof FrequencyTransmitterScreen.PasswordScreen) minecraft.setScreen(null);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !input.isEmpty()) {
            input = input.substring(0, input.offsetByCodePoints(input.length(), -1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            stop();
            return false;
        }
        return false;
    }

    public static void paste(String value) {
        if (!enteringPassword()) return;
        for (int i = 0; i < value.length() && input.length() < 64; i++) {
            char c = value.charAt(i);
            if (StringUtil.isAllowedChatCharacter(c)) input += c;
        }
    }

    private static void send(int action, BlockPos target, String pass) {
        waitingRequest = ++nextRequest;
        waitingAction = action;
        PacketDistributor.sendToServer(new FrequencyTransmitterPayload(waitingRequest, action,
                primary, target, pass));
        notifyKey = action == FrequencyTransmitterPayload.AUTH_MATRIX || action == FrequencyTransmitterPayload.AUTH_NODE
                ? "s1_1" : "e5";
        setState(State.TRANSMITTING, 3_000);
    }

    private static void openPasswordScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new FrequencyTransmitterScreen.PasswordScreen());
    }

    private static void notifyQuit(String key) {
        notifyKey = key;
        setState(State.NOTIFY_QUIT, 0);
    }

    private static void notifyReturn(String key, State target) {
        notifyKey = key;
        returnState = target;
        state = State.NOTIFY_RETURN;
        stateStarted = Util.getMillis();
        // Deliberately retain deadline from the link state.
    }

    private static void setState(State next, long timeout) {
        state = next;
        stateStarted = Util.getMillis();
        deadline = timeout <= 0 ? 0 : stateStarted + timeout;
    }

    public static void render(GuiGraphics gui, Minecraft minecraft) {
        if (!active()) return;
        String appName = Component.translatable("ac.app.freq_transmitter.name").getString();
        int boxWidth = 30 + cn.academy.client.render.ACLegacyFont.width(Component.literal(appName), 10, false);
        drawBox(gui, 15, 15, boxWidth, 18);
        ACGuiTextures.blit(gui, ICON, 17, 15, 18, 18, 0, 0, 84, 85, 84, 85);
        text(gui, appName, 39, 19, 0xFFFFFFFF);

        int centerX = gui.guiWidth() / 2 + 10;
        int centerY = gui.guiHeight() / 2;
        switch (state) {
            case START -> drawMessage(gui, minecraft, local("s0_0"), centerX, centerY + 10);
            case AUTH_MATRIX, AUTH_NODE -> drawAuthorization(gui, minecraft, centerX, centerY - 10);
            case LINK_NODES -> drawMessage(gui, minecraft, local("s2_0"), centerX, centerY + 10);
            case LINK_USERS -> drawMessage(gui, minecraft, local("s3_0"), centerX, centerY + 10);
            case TRANSMITTING, NOTIFY_QUIT, NOTIFY_RETURN -> drawMessage(gui, minecraft,
                    local(notifyKey), centerX, centerY + 10);
            default -> { }
        }
    }

    private static void drawAuthorization(GuiGraphics gui, Minecraft minecraft, int x, int y) {
        drawBox(gui, x, y, 140, 40);
        String prefix = state == State.AUTH_MATRIX ? "SSID: " : "NAME: ";
        String stars = "*".repeat(input.codePointCount(0, input.length()));
        text(gui, prefix + primaryName, x + 10, y + 5, 0xFFBFBFBF);
        text(gui, "PASS: " + stars, x + 10, y + 15, 0xFFFFFFFF);
        text(gui, local("s1_0"), x + 10, y + 25, 0xFF30FFFF);
    }

    private static void drawMessage(GuiGraphics gui, Minecraft minecraft, String message, int x, int y) {
        List<String> lines = cn.academy.client.render.ACLegacyFont.wrap(message, 120, 10, false);
        int width = lines.size() == 1
                ? cn.academy.client.render.ACLegacyFont.width(Component.literal(lines.getFirst()), 10, false) : 120;
        int height = Math.max(10, lines.size() * 10);
        drawBox(gui, x, y, width + 35, height + 10);
        int lineY = y + 5;
        for (var line : lines) {
            text(gui, line, x + 5, lineY, 0xFFFFFFFF);
            lineY += 10;
        }
    }

    private static void text(GuiGraphics gui, String text, int x, int y, int color) {
        cn.academy.client.render.ACLegacyFont.draw(gui, Component.literal(text), x, y, 10, color,
                cn.academy.client.render.ACLegacyFont.LEFT, 0, false);
    }

    private static void drawBox(GuiGraphics gui, int x, int y, int width, int height) {
        gui.fill(x, y, x + width, y + height, BG_COLOR);
        gui.fill(x - 1, y - 1, x + width + 1, y, GLOW_COLOR);
        gui.fill(x - 1, y + height, x + width + 1, y + height + 1, GLOW_COLOR);
        gui.fill(x - 1, y, x, y + height, GLOW_COLOR);
        gui.fill(x + width, y, x + width + 1, y + height, GLOW_COLOR);
    }

    private static String local(String key) {
        return Component.translatable("ac.app.freq_transmitter." + key).getString();
    }

    private enum State {
        INACTIVE, START, AUTH_MATRIX, AUTH_NODE, LINK_NODES, LINK_USERS,
        TRANSMITTING, NOTIFY_QUIT, NOTIFY_RETURN
    }

    private record Target(BlockPos pos, ACMachineBlockEntity machine) {}
    private ACFrequencyTransmitter() {}
}
