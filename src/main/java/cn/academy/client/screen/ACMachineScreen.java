package cn.academy.client.screen;

import cn.academy.client.render.ACGuiTextures;
import cn.academy.client.render.ACLegacyFont;

import cn.academy.AcademyCraft;
import cn.academy.block.MachineKind;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.menu.ACMachineMenu;
import cn.academy.network.InterfererConfigPayload;
import cn.academy.network.MachineActionPayload;
import cn.academy.network.MatrixConfigPayload;
import cn.academy.network.NodeConfigPayload;
import cn.academy.network.WirelessPageRequestPayload;
import cn.academy.network.WirelessPageActionPayload;
import cn.academy.network.WirelessPageSnapshotPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Native implementation of the 1.12.2 TechUI machine-page framework. */
public final class ACMachineScreen extends AbstractContainerScreen<ACMachineMenu> {
    private static final ResourceLocation PARENT = texture("parent/parent_background.png");
    private static final ResourceLocation INVENTORY = texture("ui/ui_inventory.png");
    private static final ResourceLocation HISTOGRAM = texture("histogram.png");
    private static final ResourceLocation INFO_BLEND = texture("blend_quad.png");
    private static final ResourceLocation INFO_LINE = texture("line.png");
    private static final ResourceLocation TAB_INVENTORY = texture("icons/icon_inv.png");
    private static final ResourceLocation TAB_WIRELESS = texture("icons/icon_wireless.png");
    private static final ResourceLocation ELEMENT = texture("element/element_background300x32.png");
    private static final ResourceLocation CONNECTED = texture("icons/icon_connected.png");
    private static final ResourceLocation UNCONNECTED = texture("icons/icon_unconnected.png");
    private static final ResourceLocation KEY = texture("icons/icon_key.png");
    private static final ResourceLocation NODE = texture("icons/icon_node.png");
    private static final ResourceLocation MATRIX = texture("icons/icon_matrix.png");
    private static final ResourceLocation TO_NODE = texture("icons/icon_tonode.png");
    private static final ResourceLocation TO_MATRIX = texture("icons/icon_tomatrix.png");
    private static final ResourceLocation PROGRESS_FUSOR = texture("progress/progress_fusor.png");
    private static final ResourceLocation PROGRESS_FORMER = texture("progress/progress_metalformer.png");
    private static final ResourceLocation ARROW_LEFT = texture("button/button_arrowlefta.png");
    private static final ResourceLocation ARROW_RIGHT = texture("button/button_arrowrighta.png");
    private static final ResourceLocation ARROW_UP = texture("button/button_arrowupb.png");
    private static final ResourceLocation ARROW_DOWN = texture("button/button_arrowdownb.png");
    private static final ResourceLocation SWITCH_ON = texture("button/button_switch_on.png");
    private static final ResourceLocation SWITCH_OFF = texture("button/button_switch_off.png");
    private static final ResourceLocation ADD = texture("button/button_add.png");
    private static final ResourceLocation REMOVE = texture("button/button_remove.png");
    private static final ResourceLocation NODE_EFFECT = texture("effect/effect_node.png");
    private static final ResourceLocation SOLAR_EFFECT = texture("effect/effect_solar.png");

    private Page page = Page.INVENTORY;
    private LegacyInfoTextBox configName;
    private LegacyInfoTextBox configPassword;
    private final java.util.Map<BlockPos, LegacyInfoTextBox> wirelessPasswords = new java.util.LinkedHashMap<>();
    private LegacyInfoTextBox whitelistInput;
    private final List<WirelessTarget> wirelessTargets = new ArrayList<>();
    private int wirelessScroll;
    private WirelessPageSnapshotPayload appliedWirelessSnapshot;
    private WirelessTarget wirelessConnected;
    private List<String> interfererWhitelist = new ArrayList<>();
    private boolean interfererEnabled;
    private double interfererRange = 10;
    private int selectedWhitelist = -1;
    private int whitelistScroll;
    private boolean configPasswordDirty;
    private double infoStarted = Double.NaN, infoLastFrame;
    private float infoHeight;
    private int matrixInitY = -1;
    private int nodeFrame;
    private boolean nodeLinked;
    private double nodeFrameTime = Double.NaN;

    public ACMachineScreen(ACMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 207;
        imageHeight = 187;
        titleLabelX = 0;
        inventoryLabelY = 10_000;
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/guis/" + path);
    }

    private ACMachineBlockEntity machine() { return menu.machine(); }
    private MachineKind kind() { return machine() == null ? null : machine().kind(); }
    private int panelX() { return leftPos; }
    private int panelY() { return topPos; }
    private int infoX() { return leftPos + 179; }

    @Override
    protected void init() {
        super.init();
        ACMachineBlockEntity machine = machine();
        if (machine == null) return;
        int x = infoX();
        if ((kind() == MachineKind.MATRIX || isNode()) && canConfigure()) {
            configName = new LegacyInfoTextBox(font, x + 46, topPos,
                    Component.translatable(kind() == MachineKind.MATRIX ? "ac.gui.common.prop.ssid" : "ac.node.name"), false);
            configName.setBordered(false);
            configName.setMaxLength(24);
            configName.setTextColor(0xFFFFFFFF);
            configName.setValue(kind() == MachineKind.MATRIX ? machine.networkId() : machine.nodeName());
            addRenderableWidget(configName);
            configPassword = new LegacyInfoTextBox(font, x + 46, topPos,
                    Component.translatable("ac.gui.common.prop.password"), true);
            configPassword.setBordered(false);
            configPassword.setMaxLength(64);
            configPassword.setHint(Component.literal("••••"));
            configPassword.setResponder(ignored -> configPasswordDirty = true);
            addRenderableWidget(configPassword);
        }
        if (hasWirelessPage()) {
            rebuildPasswordFields();
            refreshWirelessTargets();
        }
        if (kind() == MachineKind.ABILITY_INTERFERER) {
            interfererEnabled = machine.isInterfererActive();
            interfererRange = machine.interfererRange();
            interfererWhitelist = new ArrayList<>(machine.interfererWhitelist());
            whitelistInput = new LegacyInfoTextBox(font, leftPos + 58, topPos + 86, 40, 10,
                    Component.translatable("ac.interferer.player"), false, 10);
            whitelistInput.position(leftPos + 58, topPos + 86.5f);
            whitelistInput.changeColor = false;
            whitelistInput.onLostFocus = () -> { whitelistInput.visible = false; whitelistInput.setValue(""); };
            whitelistInput.setBordered(false);
            whitelistInput.setMaxLength(32);
            whitelistInput.visible = false;
            addRenderableWidget(whitelistInput);
        }
        updatePageState();
    }

    private boolean isNode() {
        return kind() == MachineKind.NODE_BASIC || kind() == MachineKind.NODE_STANDARD
                || kind() == MachineKind.NODE_ADVANCED;
    }

    private boolean hasWirelessPage() {
        return machine() != null && machine().isWirelessConfigurable() && kind() != MachineKind.MATRIX;
    }

    private void updatePageState() {
        menu.setInventoryPage(page == Page.INVENTORY);
        layoutPasswordFields();
        if (whitelistInput != null && page != Page.INVENTORY) {
            whitelistInput.setFocused(false); whitelistInput.visible = false;
        }
    }

    private void rebuildPasswordFields() {
        for (var field : wirelessPasswords.values()) {
            if (getFocused() == field) setFocused(null);
            removeWidget(field);
        }
        wirelessPasswords.clear();
        for (var target : wirelessTargets) if (target.encrypted) {
            var field = new LegacyInfoTextBox(font, 0, 0, 48, 9,
                    Component.translatable("ac.frequency.password"), true, 10);
            field.setMaxLength(64); field.changeColor = false;
            field.visible = false;
            wirelessPasswords.put(target.pos, field);
            addRenderableWidget(field);
        }
        layoutPasswordFields();
    }

    private void layoutPasswordFields() {
        for (int index = 0; index < wirelessTargets.size(); index++) {
            var field = wirelessPasswords.get(wirelessTargets.get(index).pos);
            if (field == null) continue;
            field.visible = page == Page.WIRELESS && index >= wirelessScroll && index < wirelessScroll + 7;
            if (!field.visible && getFocused() == field) { field.setFocused(false); setFocused(null); }
            field.position(panelX() + 80.5f, panelY() + 63.5f + (index - wirelessScroll) * 16);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (machine() == null) return;
        applyWirelessSnapshot();
        if (kind() == MachineKind.ABILITY_INTERFERER) {
            interfererEnabled = machine().isInterfererActive();
            interfererRange = machine().interfererRange();
            var currentWhitelist = machine().interfererWhitelist();
            if (!interfererWhitelist.equals(currentWhitelist)) {
                interfererWhitelist = new ArrayList<>(currentWhitelist);
                selectedWhitelist = -1; whitelistScroll = 0;
            }
        }
        if (configName != null && !configName.isFocused()
                && (kind() != MachineKind.MATRIX || machine().networkInitialized())) {
            String value = kind() == MachineKind.MATRIX ? machine().networkId() : machine().nodeName();
            if (!configName.getValue().equals(value)) configName.setValue(value);
        }
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        if (page == Page.INVENTORY) renderInventoryPage(gui, partialTick, mouseX, mouseY);
        else renderWirelessPage(gui, mouseX, mouseY);
        renderPageTabs(gui, mouseX, mouseY);
        renderInfoPanel(gui, mouseX, mouseY);
    }

    private void renderInventoryPage(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = panelX(), y = panelY();
        ACGuiTextures.blit(gui, PARENT, x, y, 176, 187, 0, 0, 352, 374, 352, 374);
        float breathe = (float)(.675 + (1 + Math.sin(cn.academy.client.TerminalOverlay.seconds() / .8)) * .5 * .175);
        if (kind() != MachineKind.ABILITY_INTERFERER)
            ACGuiTextures.blit(gui, INVENTORY, x, y, 176, 187, 0, 0, 352, 374, 352, 374, 1, 1, 1, breathe);
        ResourceLocation overlay = inventoryOverlay();
        if (overlay != null) ACGuiTextures.blit(gui, overlay, x, y, 176, 187, 0, 0, 352, 374, 352, 374, 1, 1, 1, breathe);
        renderMachineSpecific(gui, x, y, mouseX, mouseY);
    }

    private ResourceLocation inventoryOverlay() {
        if (kind() == null) return null;
        String name = switch (kind()) {
            case IMAG_FUSOR -> "ui_imagfusor";
            case METAL_FORMER -> "ui_metalformer";
            case MATRIX -> "ui_matrix";
            case NODE_BASIC, NODE_STANDARD, NODE_ADVANCED -> "ui_node";
            case PHASE_GENERATOR -> "ui_phasegen";
            case SOLAR_GENERATOR, WIND_BASE -> "ui_windbase";
            case WIND_GENERATOR -> "ui_windmain";
            case ABILITY_INTERFERER -> "ui_interfere";
            default -> null;
        };
        return name == null ? null : texture("ui/" + name + ".png");
    }

    private void renderMachineSpecific(GuiGraphics gui, int x, int y, int mouseX, int mouseY) {
        if (kind() == MachineKind.IMAG_FUSOR) {
            float progress = menu.maxProgress() <= 0 ? 0 : Mth.clamp((float)menu.progress() / menu.maxProgress(), 0, 1);
            if (progress > 0) ACGuiTextures.blit(gui, PROGRESS_FUSOR, x + 57.703125f, y + 46.5f,
                    60.96875f * progress, 15, 0, 0, progress, 1, 1, 1);
            int liquid = menu.machine().currentLiquidRequirement();
            String requirement = liquid > 0 ? Integer.toString(liquid) : "IDLE";
            ACLegacyFont.draw(gui, Component.literal(requirement), x + 90, y + 12, 12,
                    0xccffffff, ACLegacyFont.CENTER, 0, false);
        } else if (kind() == MachineKind.METAL_FORMER) {
            float progress = menu.maxProgress() <= 0 ? 0 : Mth.clamp((float)menu.progress() / menu.maxProgress(), 0, 1);
            if (progress > 0) ACGuiTextures.blit(gui, PROGRESS_FORMER, x + 59.5f, y + 46.5f,
                    57 * progress, 15, 0, 0, progress, 1, 1, 1);
            ResourceLocation mode = texture("icons/icon_former_" + modeName(menu.mode()) + ".png");
            ACGuiTextures.blit(gui, mode, x + 76, y + 4.5f, 24, 24, 0, 0, 48, 48, 48, 48);
            ACGuiTextures.hudQuad(gui, ARROW_LEFT, x + 60, y + 8.5f, 16, 16, 1, 1, 1, 1, false);
            ACGuiTextures.hudQuad(gui, ARROW_RIGHT, x + 100, y + 8.5f, 16, 16, 1, 1, 1, 1, false);
        } else if (isNode()) {
            boolean linked = machine().linkedMatrixPos() != null;
            double now = cn.academy.client.TerminalOverlay.seconds();
            if (Double.isNaN(nodeFrameTime) || linked != nodeLinked) {
                nodeLinked = linked; nodeFrame = 0; nodeFrameTime = now;
            }
            if ((now - nodeFrameTime) * 1000 >= (linked ? 800 : 3000)) {
                nodeFrame = (nodeFrame + 1) % (linked ? 8 : 2); nodeFrameTime = now;
            }
            int frame = (linked ? 0 : 8) + nodeFrame;
            float breathe = (float)(.675 + (1 + Math.sin(now / .8)) * .5 * .175);
            ACGuiTextures.blit(gui, NODE_EFFECT, x + 42, y + 35.5f, 93, 37.5f,
                    0, frame * 75, 186, 75, 186, 750, 1, 1, 1, breathe);
        } else if (kind() == MachineKind.SOLAR_GENERATOR && minecraft != null && minecraft.level != null) {
            boolean sky = machine().solarCanGenerate();
            int frame = !sky ? 1 : minecraft.level.isRaining() ? 2 : 0;
            ACGuiTextures.blit(gui, SOLAR_EFFECT, x + 56, y + 23, 62.4f, 42,
                    0, frame * 70, 104, 70, 104, 210);
        } else if (kind() == MachineKind.WIND_BASE) {
            ResourceLocation base = texture("icons/icon_wind_base.png");
            ResourceLocation middle = texture("icons/icon_wind_middle.png");
            ResourceLocation main = texture("icons/icon_wind_main.png");
            var completeness = machine().windCompleteness();
            float mainAlpha = switch (completeness) { case COMPLETE -> 1; case COMPLETE_NOT_WORKING -> .6f; default -> .2f; };
            float middleAlpha = completeness == ACMachineBlockEntity.WindCompleteness.BASE_ONLY ? .2f : 1;
            ACGuiTextures.blit(gui, base, x + 76, y + 49, 24, 24, 0, 0, 48, 48, 48, 48);
            ACGuiTextures.blit(gui, middle, x + 76, y + 31, 24, 24, 0, 0, 48, 48, 48, 48, 1, 1, 1, middleAlpha);
            ACGuiTextures.blit(gui, main, x + 76, y + 13, 24, 24, 0, 0, 48, 48, 48, 48, 1, 1, 1, mainAlpha);
        } else if (kind() == MachineKind.ABILITY_INTERFERER) {
            renderInterferer(gui, x, y, mouseX, mouseY);
        }
    }

    private void renderInterferer(GuiGraphics gui, int x, int y, int mouseX, int mouseY) {
        // These two labels are literal text in page_interfere.xml (including the original typo).
        infoText(gui, "Swtich:", x + 8, y + 29, 10, 40, 1);
        float lum = interfererEnabled ? 1 : .6f;
        ACGuiTextures.hudQuad(gui, interfererEnabled ? SWITCH_ON : SWITCH_OFF, x + 48, y + 25, 16, 16,
                lum, lum, lum, 1, false);
        infoText(gui, "Range:", x + 8, y + 45, 10, 40, 1);
        alphaIcon(gui, ARROW_LEFT, x + 48, y + 41, 16, 16, 178 / 255f, 1, mouseX, mouseY);
        alphaIcon(gui, ARROW_RIGHT, x + 108, y + 41, 16, 16, 178 / 255f, 1, mouseX, mouseY);
        ACLegacyFont.draw(gui, Component.literal(Double.toString(interfererRange)), x + 86, y + 45, 10,
                0xffffffff, ACLegacyFont.CENTER, 0, false);

        float panelY = y + 81.5f;
        alphaIcon(gui, ADD, x + 12, panelY, 12, 12, 178 / 255f, 1, mouseX, mouseY);
        alphaIcon(gui, REMOVE, x + 32, panelY, 12, 12, 178 / 255f, 1, mouseX, mouseY);
        alphaIcon(gui, ARROW_UP, x + 152, panelY + 13, 16, 16, 178 / 255f, 1, mouseX, mouseY);
        alphaIcon(gui, ARROW_DOWN, x + 152, panelY + 63, 16, 16, 178 / 255f, 1, mouseX, mouseY);
        whitelistScroll = Mth.clamp(whitelistScroll, 0, legacyMaxScroll(interfererWhitelist.size(), 64));
        for (int row = 0; row < 4 && whitelistScroll + row < interfererWhitelist.size(); row++) {
            int index = whitelistScroll + row;
            float rowY = y + 94.5f + row * 16;
            ACGuiTextures.hudQuad(gui, ELEMENT, x + 3, rowY, 150, 16,
                    1, 1, 1, selectedWhitelist == index ? 1 : .7f, false);
            ACGuiTextures.hudQuad(gui, texture("icons/icon_whitelist_single.png"), x + 13, rowY + 2, 12, 12, 1, 1, 1, 1, false);
            infoText(gui, interfererWhitelist.get(index), x + 33, rowY + 4, 10, 110, 1);
        }
        if (whitelistInput != null && whitelistInput.visible)
            infoRect(gui, x + 58, y + 86.5f, 40, 10, 0xffffff, 50 / 255f);
    }

    private void renderWirelessPage(GuiGraphics gui, int mouseX, int mouseY) {
        int x = panelX(), y = panelY();
        ACGuiTextures.hudQuad(gui, PARENT, x, y, 176, 187, 1, 1, 1, 1, false);
        float breathe = (float)(.675 + (1 + Math.sin(cn.academy.client.TerminalOverlay.seconds() / .8)) * .5 * .175);
        ACGuiTextures.hudQuad(gui, isNode() ? TO_MATRIX : TO_NODE, x + 10, y + 10, 16, 16, 1, 1, 1, breathe, false);
        infoText(gui, "Connected", x + 13, y + 28, 9, 58, .8f);
        infoText(gui, "Available", x + 13, y + 52, 9, 58, .8f);

        WirelessTarget connected = connectedTarget();
        float connectedX = x + 7.5f, connectedY = y + 34.621094f;
        ACGuiTextures.hudQuad(gui, ELEMENT, connectedX, connectedY, 150, 18, 1, 1, 1, 1, false);
        // The original XML uses the matrix emblem for both user and node list entries.
        ACGuiTextures.hudQuad(gui, MATRIX, connectedX + 8, connectedY + 3, 12, 12,
                1, 1, 1, connected == null ? .6f : 1, false);
        infoText(gui, connected == null ? Component.translatable("ac.gui.common.pg_wireless.not_connected").getString()
                : connected.name, connectedX + 20, connectedY + 5, 9, 80, 1);
        if (connected == null) ACGuiTextures.hudQuad(gui, UNCONNECTED, connectedX + 125, connectedY + 3, 12, 12,
                1, 1, 1, .6f, false);
        else alphaIcon(gui, CONNECTED, connectedX + 125, connectedY + 3, 12, 12, .8f, 1, mouseX, mouseY);

        wirelessScroll = Mth.clamp(wirelessScroll, 0, legacyMaxScroll(wirelessTargets.size(), 115));
        layoutPasswordFields();
        for (int row = 0; row < 7 && wirelessScroll + row < wirelessTargets.size(); row++) {
            var target = wirelessTargets.get(wirelessScroll + row);
            float rowY = y + 60 + row * 16;
            ACGuiTextures.hudQuad(gui, ELEMENT, x + 8, rowY, 150, 16, 1, 1, 1, 1, false);
            ACGuiTextures.hudQuad(gui, MATRIX, x + 16, rowY + 2, 12, 12, 1, 1, 1, 178 / 255f, false);
            infoText(gui, target.name, x + 28, rowY + 4, 9, 40, 1);
            var field = wirelessPasswords.get(target.pos);
            if (field != null) {
                ACGuiTextures.hudQuad(gui, KEY, x + 68, rowY + 2, 12, 12, 1, 1, 1,
                        field.isFocused() ? 1 : .6f, false);
                infoRect(gui, x + 80.5f, rowY + 3.5f, 48, 9, 0x222222, 170 / 255f);
            }
            alphaIcon(gui, UNCONNECTED, x + 133, rowY + 2, 12, 12, .6f, .8f, mouseX, mouseY);
        }
        alphaIcon(gui, ARROW_UP, x + 154.5f, y + 51.621094f, 16, 16, .8f, 1, mouseX, mouseY);
        alphaIcon(gui, ARROW_DOWN, x + 154.5f, y + 161.20703f, 16, 16, .8f, 1, mouseX, mouseY);
    }

    private static void alphaIcon(GuiGraphics gui, ResourceLocation texture, float x, float y, float w, float h,
                                  float idle, float hover, int mouseX, int mouseY) {
        ACGuiTextures.hudQuad(gui, texture, x, y, w, h, 1, 1, 1,
                inside(mouseX, mouseY, x, y, w, h) ? hover : idle, false);
    }

    /** Original ElementList counts the last fully occupied row with >=, including its extra bottom scroll step. */
    private static int legacyMaxScroll(int count, float height) {
        float sum = 0;
        for (int i = count - 1; i >= 0; i--) {
            sum += 16;
            if (sum >= height) return i == count - 1 ? i : i + 1;
        }
        return 0;
    }

    private void renderPageTabs(GuiGraphics gui, int mouseX, int mouseY) {
        pageTab(gui, TAB_INVENTORY, leftPos - 20, topPos, page == Page.INVENTORY, mouseX, mouseY);
        if (hasWirelessPage()) pageTab(gui, TAB_WIRELESS, leftPos - 20, topPos + 22, page == Page.WIRELESS, mouseX, mouseY);
    }
    private static void pageTab(GuiGraphics gui, ResourceLocation texture, int x, int y, boolean selected, int mouseX, int mouseY) {
        float rgb = selected ? 1 : .8f;
        float alpha = selected || inside(mouseX, mouseY, x, y, 16.8, 16.8) ? 1 : .8f;
        ACGuiTextures.hudQuad(gui, texture, x, y, 16.8f, 16.8f, rgb, rgb, rgb, alpha, false);
    }

    private record Hist(String label, int color, double fraction, String value) { }
    private record InfoRow(String key, String value, int color, int type) { }

    private boolean canConfigure() {
        return minecraft != null && minecraft.player != null && machine() != null
                && machine().placerName().equals(minecraft.player.getName().getString());
    }

    private void renderInfoPanel(GuiGraphics gui, int mouseX, int mouseY) {
        if (machine() == null) return;
        int x = infoX(), y = topPos + 5;
        List<Hist> hist = new ArrayList<>();
        List<InfoRow> rows = new ArrayList<>();
        boolean matrix = kind() == MachineKind.MATRIX;
        if (matrix) hist.add(capacityHist());
        else if (kind() != MachineKind.WIND_GENERATOR) {
            boolean buffer = kind() == MachineKind.SOLAR_GENERATOR || kind() == MachineKind.WIND_BASE;
            hist.add(new Hist(buffer ? "buffer" : "energy", buffer ? 0x25f7ff : 0x25c4ff,
                    menu.energy() / (double)Math.max(1, menu.maxEnergy()), menu.energy() + " IF"));
            if (hasFluidTank()) hist.add(new Hist(kind() == MachineKind.PHASE_GENERATOR ? "IF" : "liquid",
                    kind() == MachineKind.PHASE_GENERATOR ? 0xb983fb : 0x7680de,
                    menu.phaseLiquid() / 8000.0, menu.phaseLiquid() + " mB"));
            else if (isNode()) hist.add(capacityHist());
        }
        if (matrix) {
            rows.add(new InfoRow("info", "", 0, 1));
            rows.add(new InfoRow("owner", machine().placerName(), 0, 0));
            rows.add(new InfoRow("range", String.format(java.util.Locale.ROOT, "%.0f", machine().wirelessRange()), 0, 0));
            rows.add(new InfoRow("bandwidth", machine().wirelessBandwidth() + " IF/T", 0, 0));
            if (machine().networkInitialized()) {
                rows.add(new InfoRow("wireless_info", "", 0, 1));
                rows.add(new InfoRow("ssid", machine().networkId(), 0, 2));
                if (canConfigure()) rows.add(new InfoRow("change_pass", "", 0, 1));
                rows.add(new InfoRow("password", machine().networkEncrypted() ? "****" : "", 0, 3));
            } else if (canConfigure()) {
                rows.add(new InfoRow("wireless_init", "", 0, 1));
                rows.add(new InfoRow("ssid", "", 0, 2));
                rows.add(new InfoRow("password", "", 0, 3));
                rows.add(new InfoRow("INIT", "", 0, 4));
            } else rows.add(new InfoRow("wireless_noinit", "", 0, 1));
        } else if (isNode()) {
            rows.add(new InfoRow("info", "", 0, 1));
            rows.add(new InfoRow("range", String.valueOf(machine().wirelessRange()), 0, 0));
            rows.add(new InfoRow("owner", machine().placerName(), 0, 0));
            rows.add(new InfoRow("node_name", machine().nodeName(), 0, 2));
            if (canConfigure()) rows.add(new InfoRow("password", machine().nodeEncrypted() ? "****" : "", 0, 3));
        } else if (kind() == MachineKind.SOLAR_GENERATOR || kind() == MachineKind.WIND_BASE) {
            rows.add(new InfoRow("info", "", 0, 1));
            rows.add(new InfoRow(kind() == MachineKind.SOLAR_GENERATOR ? "gen_speed" : "altitude",
                    kind() == MachineKind.SOLAR_GENERATOR
                            ? String.format(java.util.Locale.ROOT, "%.2fIF/T", machine().solarGeneration())
                            : Integer.toString(machine().getBlockPos().getY()), 0, 0));
        } else if (kind() == MachineKind.WIND_GENERATOR)
            rows.add(new InfoRow("altitude", Integer.toString(machine().getBlockPos().getY()), 0, 0));

        float rowY = hist.isEmpty() ? 10 : 64 + 8 * hist.size();
        float expectedHeight = rowY + 8;
        for (var row : rows) expectedHeight += row.type == 1 ? 11 : row.type == 4 ? 9 : 8;
        expectedHeight = Math.max(50, expectedHeight);
        double now = cn.academy.client.TerminalOverlay.seconds();
        if (Double.isNaN(infoStarted)) infoStarted = infoLastFrame = now;
        float step = (float)(Math.min(.5, Math.max(0, now - infoLastFrame)) * 500);
        infoHeight += Math.signum(expectedHeight - infoHeight) * Math.min(step, Math.abs(expectedHeight - infoHeight));
        infoLastFrame = now;
        float alpha = Mth.clamp((float)((now - infoStarted - .3) / .3), 0, 1);
        infoBackground(gui, x, y, infoHeight);
        if (!hist.isEmpty()) {
            ACGuiTextures.blit(gui, HISTOGRAM, x, y - 20, 84, 84, 0, 0, 210, 210, 210, 210, 1, 1, 1, alpha);
            for (int i = 0; i < hist.size(); i++) {
                var h = hist.get(i);
                float fraction = (float)Math.clamp(h.fraction, .03, 1);
                infoRect(gui, x + 22.4f + i * 16, y + 59.2f - 48 * fraction, 6.4f, 48 * fraction, h.color, alpha);
                infoRect(gui, x + 3, y + 65.5f + i * 8, 6, 6, h.color, alpha);
                String label = h.label.equals("IF") ? "IF" : Component.translatable("ac.gui.common.hist." + h.label).getString();
                infoText(gui, label, x + 10, y + 64 + i * 8, 8, 32, alpha);
                infoText(gui, h.value, x + 46, y + 64 + i * 8, 8, 40, alpha);
            }
        }
        matrixInitY = -1;
        for (var row : rows) {
            if (row.type == 1) {
                rowY += 3;
                infoText(gui, Component.translatable("ac.gui.common.sep." + row.key).getString(), x + 3, y + rowY,
                        6, 97, alpha);
            } else if (row.type == 4) {
                rowY++;
                matrixInitY = y + (int)rowY;
                ACLegacyFont.draw(gui, Component.literal("INIT"), x + 50, y + rowY, 9,
                        ((int)(255 * alpha) << 24) | (inside(mouseX, mouseY, x + 25, y + rowY, 50, 8) ? 0xffffff : 0xcccccc),
                        ACLegacyFont.CENTER, 0, false);
            } else {
                infoText(gui, Component.translatable("ac.gui.common.prop." + row.key).getString(), x + 6, y + rowY, 8, 40, alpha);
                EditBox editor = row.type == 2 ? configName : row.type == 3 ? configPassword : null;
                if (editor != null) {
                    editor.setX(x + 46); editor.setY(y + (int)rowY); editor.setWidth(40); editor.setHeight(8);
                    editor.setAlpha(alpha);
                    if (editor instanceof LegacyInfoTextBox legacy) {
                        legacy.changeColor = !matrix || machine().networkInitialized();
                        legacy.encryptedHint = matrix ? machine().networkEncrypted() : machine().nodeEncrypted();
                    }
                    infoText(gui, "[", x + 42, y + rowY, 8, 10, alpha);
                    infoText(gui, "]", x + 88, y + rowY, 8, 10, alpha);
                } else infoText(gui, row.value, x + 46, y + rowY, 8, 40, alpha);
            }
            rowY += 8;
        }
    }

    private Hist capacityHist() {
        return new Hist("capacity", 0xff6c00, menu.wirelessLoad() / (double)Math.max(1, menu.wirelessCapacity()),
                menu.wirelessLoad() + "/" + menu.wirelessCapacity());
    }

    private static void infoBackground(GuiGraphics gui, float x, float y, float height) {
        float[] xs = {x - 4, x, x + 100, x + 104};
        float[] ys = {y - 4, y, y + height, y + height + 4};
        for (int col = 0; col < 3; col++) for (int row = 0; row < 3; row++)
            ACGuiTextures.blit(gui, INFO_BLEND, xs[col], ys[row], xs[col + 1] - xs[col], ys[row + 1] - ys[row],
                    col / 3f, row / 3f, 1 / 3f, 1 / 3f, 1, 1, 0, 0, 0, .5f);
        ACGuiTextures.hudQuad(gui, INFO_LINE, x - 3.2f, y - 8.6f, 106.4f, 12, 1, 1, 1, 1, false);
        ACGuiTextures.hudQuad(gui, INFO_LINE, x - 3.2f, y + height - 2, 106.4f, 8, 1, 1, 1, 1, false);
    }

    private static void infoRect(GuiGraphics gui, float x, float y, float width, float height, int rgb, float alpha) {
        if (alpha <= 0) return;
        gui.pose().pushPose(); gui.pose().translate(x, y, 0); gui.pose().scale(width, height, 1);
        gui.fill(0, 0, 1, 1, ((int)(alpha * 255) << 24) | rgb); gui.pose().popPose();
    }

    private static void infoText(GuiGraphics gui, String text, float x, float y, float size, float width, float alpha) {
        int end = 0;
        while (end < text.length()) {
            end += Character.charCount(text.codePointAt(end));
            if (ACLegacyFont.width(Component.literal(text.substring(0, end)), size, false) >= width) break;
        }
        ACLegacyFont.draw(gui, Component.literal(text.substring(0, end)), x, y, size,
                ((int)(alpha * 255) << 24) | 0xffffff, ACLegacyFont.LEFT, 0, false);
    }

    private boolean hasFluidTank() {
        return kind() == MachineKind.IMAG_FUSOR || kind() == MachineKind.PHASE_GENERATOR;
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        // TechUI used texture labels and its floating information widget rather than vanilla captions.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (getFocused() instanceof EditBox focused && !focused.isMouseOver(mouseX, mouseY)) {
            focused.setFocused(false);
            setFocused(null);
        }
        if (whitelistInput != null && whitelistInput.visible && !inside(mouseX, mouseY,
                panelX() + 58, panelY() + 86.5, 40, 10)) {
            whitelistInput.setFocused(false); whitelistInput.visible = false;
            if (getFocused() == whitelistInput) setFocused(null);
        }
        if (button == 0) {
            if (inside(mouseX, mouseY, leftPos - 20, topPos - 2, 21, 21)) {
                page = Page.INVENTORY; updatePageState(); return true;
            }
            if (hasWirelessPage() && inside(mouseX, mouseY, leftPos - 20, topPos + 20, 21, 21)) {
                page = Page.WIRELESS; updatePageState(); return true;
            }
            if (page == Page.INVENTORY && kind() == MachineKind.METAL_FORMER) {
                if (inside(mouseX, mouseY, leftPos + 60, topPos + 8.5, 16, 16)) {
                    cycleMode(false); return true;
                }
                if (inside(mouseX, mouseY, leftPos + 100, topPos + 8.5, 16, 16)) {
                    cycleMode(true); return true;
                }
            }
            if (configPassword != null && inside(mouseX, mouseY, configPassword.getX(), configPassword.getY(),
                    configPassword.getWidth(), configPassword.getHeight())) configPasswordDirty = true;
            if (matrixInitY >= 0 && inside(mouseX, mouseY, infoX() + 25, matrixInitY, 50, 8)) {
                applyMachineConfig(); return true;
            }
            if (page == Page.WIRELESS && handleWirelessClick(mouseX, mouseY)) return true;
            if (page == Page.INVENTORY && kind() == MachineKind.ABILITY_INTERFERER
                    && handleInterfererClick(mouseX, mouseY)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void cycleMode(boolean forward) {
        PacketDistributor.sendToServer(new MachineActionPayload(machine().getBlockPos(), forward
                ? MachineActionPayload.CYCLE_MODE : MachineActionPayload.CYCLE_MODE_BACK));
    }

    private boolean handleWirelessClick(double mouseX, double mouseY) {
        int x = panelX(), y = panelY();
        if (connectedTarget() != null && inside(mouseX, mouseY, x + 132.5, y + 37.621094, 12, 12)) {
            PacketDistributor.sendToServer(new WirelessPageActionPayload(menu.containerId, menu.nextWirelessRequest(),
                    machine().getBlockPos(), java.util.Optional.empty(), ""));
            return true;
        }
        if (inside(mouseX, mouseY, x + 154.5, y + 51.621094, 16, 16)) {
            wirelessScroll = Math.max(0, wirelessScroll - 1); layoutPasswordFields(); return true;
        }
        if (inside(mouseX, mouseY, x + 154.5, y + 161.20703, 16, 16)) {
            wirelessScroll = Math.min(legacyMaxScroll(wirelessTargets.size(), 115), wirelessScroll + 1);
            layoutPasswordFields(); return true;
        }
        for (int row = 0; row < 7 && wirelessScroll + row < wirelessTargets.size(); row++) {
            if (inside(mouseX, mouseY, x + 133, y + 62 + row * 16, 12, 12)) {
                connectWireless(wirelessTargets.get(wirelessScroll + row)); return true;
            }
        }
        return false;
    }

    private void connectWireless(WirelessTarget target) {
        var field = wirelessPasswords.get(target.pos);
        PacketDistributor.sendToServer(new WirelessPageActionPayload(menu.containerId, menu.nextWirelessRequest(), machine().getBlockPos(),
                java.util.Optional.of(target.pos), field == null ? "" : field.getValue()));
        if (field != null) field.setValue("");
    }

    private boolean handleInterfererClick(double mouseX, double mouseY) {
        int x = panelX(), y = panelY();
        if (inside(mouseX, mouseY, x + 48, y + 25, 16, 16)) {
            sendInterferer(InterfererConfigPayload.ENABLED, !interfererEnabled, interfererRange, interfererWhitelist); return true;
        }
        if (inside(mouseX, mouseY, x + 48, y + 41, 16, 16)) {
            sendInterferer(InterfererConfigPayload.RANGE, interfererEnabled, Math.max(10, interfererRange - 10), interfererWhitelist); return true;
        }
        if (inside(mouseX, mouseY, x + 108, y + 41, 16, 16)) {
            sendInterferer(InterfererConfigPayload.RANGE, interfererEnabled, Math.min(100, interfererRange + 10), interfererWhitelist); return true;
        }
        if (inside(mouseX, mouseY, x + 12, y + 81.5, 12, 12)) {
            whitelistInput.setValue(""); whitelistInput.visible = true;
            if (getFocused() != null) getFocused().setFocused(false);
            setFocused(whitelistInput); whitelistInput.setFocused(true); return true;
        }
        if (inside(mouseX, mouseY, x + 32, y + 81.5, 12, 12)) {
            if (selectedWhitelist >= 0 && selectedWhitelist < interfererWhitelist.size()) {
                var updated = new ArrayList<>(interfererWhitelist); updated.remove(selectedWhitelist);
                sendInterferer(InterfererConfigPayload.WHITELIST, interfererEnabled, interfererRange, updated);
            }
            return true;
        }
        if (inside(mouseX, mouseY, x + 152, y + 94.5, 16, 16)) {
            whitelistScroll = Math.max(0, whitelistScroll - 1); return true;
        }
        if (inside(mouseX, mouseY, x + 152, y + 144.5, 16, 16)) {
            whitelistScroll = Math.min(legacyMaxScroll(interfererWhitelist.size(), 64), whitelistScroll + 1); return true;
        }
        for (int row = 0; row < 4 && whitelistScroll + row < interfererWhitelist.size(); row++) {
            if (inside(mouseX, mouseY, x + 3, y + 94.5 + row * 16, 150, 16)) {
                selectedWhitelist = whitelistScroll + row; return true;
            }
        }
        return false;
    }

    private void addWhitelistEntry() {
        if (whitelistInput == null) return;
        String name = whitelistInput.getValue();
        if (!name.isEmpty()) {
            var updated = new java.util.TreeSet<>(interfererWhitelist); updated.add(name);
            sendInterferer(InterfererConfigPayload.WHITELIST, interfererEnabled, interfererRange, List.copyOf(updated));
        }
        whitelistInput.setValue(""); whitelistInput.setFocused(false); whitelistInput.visible = false;
        setFocused(null);
    }

    private void sendInterferer(int field, boolean enabled, double range, List<String> whitelist) {
        PacketDistributor.sendToServer(new InterfererConfigPayload(machine().getBlockPos(), enabled, range, List.copyOf(whitelist), field));
    }

    private void applyMachineConfig() {
        if (configName == null || configPassword == null) return;
        boolean init = kind() == MachineKind.MATRIX && !machine().networkInitialized();
        boolean password = init || configPassword.isFocused();
        String name = password && !init ? (kind() == MachineKind.MATRIX ? machine().networkId() : machine().nodeName()) : configName.getValue();
        if (kind() == MachineKind.MATRIX)
            PacketDistributor.sendToServer(new MatrixConfigPayload(machine().getBlockPos(),
                    name, configPassword.getValue(), password && (init || configPasswordDirty)));
        else PacketDistributor.sendToServer(new NodeConfigPayload(machine().getBlockPos(),
                name, configPassword.getValue(), password && configPasswordDirty));
        if (password) {
            configPassword.setValue(""); configPasswordDirty = false; configPassword.confirmed();
        }
        if (!password || init) configName.confirmed();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (whitelistInput != null && whitelistInput.visible && whitelistInput.isFocused()) {
                addWhitelistEntry(); return true;
            }
            for (var target : wirelessTargets) {
                var field = wirelessPasswords.get(target.pos);
                if (field != null && field.visible && field.isFocused()) { connectWireless(target); return true; }
            }
            if (configName != null && (configName.isFocused() || configPassword.isFocused())) {
                // Original uninitialised matrix fields only stage values; INIT creates the network.
                if (kind() != MachineKind.MATRIX || machine().networkInitialized()) applyMachineConfig();
                return true;
            }
        }
        // CGuiScreenContainer suppressed inventory hotkeys while a TextBox was editing.
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && getFocused() instanceof EditBox editor && editor.canConsumeInput()) {
            editor.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void refreshWirelessTargets() {
        if (machine() != null) PacketDistributor.sendToServer(new WirelessPageRequestPayload(
                menu.containerId, menu.nextWirelessRequest(), machine().getBlockPos()));
    }

    private void applyWirelessSnapshot() {
        var snapshot = menu.wirelessSnapshot();
        if (snapshot == null || snapshot == appliedWirelessSnapshot) return;
        appliedWirelessSnapshot = snapshot;
        wirelessConnected = snapshot.linked().map(target -> new WirelessTarget(target.pos(), target.name(), target.encrypted())).orElse(null);
        wirelessTargets.clear();
        for (var target : snapshot.available()) wirelessTargets.add(new WirelessTarget(target.pos(), target.name(), target.encrypted()));
        wirelessScroll = 0;
        rebuildPasswordFields();
        updatePageState();
    }

    private WirelessTarget connectedTarget() { return wirelessConnected; }

    @Override
    protected void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        super.renderTooltip(gui, mouseX, mouseY);
        if (page == Page.INVENTORY && kind() == MachineKind.METAL_FORMER
                && inside(mouseX, mouseY, leftPos + 76, topPos + 4.5, 24, 24)) {
            Component label = Component.literal(modeName(menu.mode()).toUpperCase(java.util.Locale.ROOT));
            float extent = ACLegacyFont.width(label, 10, false);
            infoRect(gui, leftPos + 82 - extent / 2, topPos - 5.5f, extent + 12, 14, 0, .5f);
            ACLegacyFont.draw(gui, label, leftPos + 87, topPos - 3.5f, 10, 0xaaffffff, ACLegacyFont.CENTER, 0, false);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        renderTooltip(gui, mouseX, mouseY);
    }

    private static String modeName(int mode) {
        return switch (Math.floorMod(mode, 4)) { case 0 -> "plate"; case 1 -> "incise"; case 2 -> "etch"; default -> "refine"; };
    }

    private static boolean inside(double x, double y, double left, double top, double width, double height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    private enum Page { INVENTORY, WIRELESS }
    private record WirelessTarget(net.minecraft.core.BlockPos pos, String name, boolean encrypted) {}
}
