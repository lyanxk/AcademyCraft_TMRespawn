package cn.academy.client.screen;

import cn.academy.data.GameplayRules;

import cn.academy.data.GameplayData;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityCategory;
import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.block.MachineKind;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.client.render.ACLegacyFont;
import cn.academy.client.render.ACSkillTreeRenderer;
import cn.academy.client.render.ACGuiTextures;
import cn.academy.network.DeveloperActionPayload;
import cn.academy.network.DevelopmentSyncPayload;
import cn.academy.registry.ACItems;
import net.minecraft.util.StringUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native port of page_developer.xml and DeveloperUI; layout parity is tracked in the coverage report. */
public class DeveloperScreen extends Screen {
    private static final int MAIN_W = 400, MAIN_H = 187;
    private static final int AREA_X = 128, AREA_Y = 18, AREA_W = 257, AREA_H = 139;

    private static final ResourceLocation AREA_BACK = gui("effect/effect_developer_background.png");
    private static final ResourceLocation PARENT_LEFT = gui("parent/parent_background_developerleft.png");
    private static final ResourceLocation PARENT_RIGHT = gui("parent/parent_background_developerright.png");
    private static final ResourceLocation PARENT_MACHINE = gui("parent/parent_background_developermachine.png");
    private static final ResourceLocation UI_LEFT = gui("ui/ui_developerleft.png");
    private static final ResourceLocation UI_RIGHT = gui("ui/ui_developerright.png");
    private static final ResourceLocation SMALL_BUTTON = gui("developer/button.png");
    private static final ResourceLocation LEARN_BUTTON = gui("button/button_learn.png");
    private static final ResourceLocation ELEMENT_BACK = gui("element/element_background300x32.png");
    private static final ResourceLocation NODE_ICON = gui("icons/icon_node.png");
    private static final ResourceLocation NO_CATEGORY = gui("icons/icon_nocategory.png");

    private final BlockPos sourcePos;
    private final boolean portable;
    private final boolean readOnly;
    private final Map<String, HoverTransition> hoverTransitions = new HashMap<>();
    private static final class HoverTransition {
        boolean hovered;
        double started = -2;
    }
    private AbilityState pageState;
    private List<ConditionView> overlayConditions = List.of();
    private double screenStarted;
    private AbilitySkill hovered;
    private AbilitySkill selected;
    private Overlay overlay = Overlay.NONE;
    private double overlayStarted;
    private boolean overlayAttempted;
    private boolean overlayLearned;
    private Component overlayMessage;
    private float overlayProgress;
    private int overlayNextLevel;
    private int overlayEnergy;
    private LegacyConsole console;
    private DeveloperWirelessPage wirelessPage;
    private boolean closingCover;
    private boolean rebuildAfterCover;
    private double coverClosedAt;


    private enum Overlay { NONE, SKILL, LEVEL }

    public DeveloperScreen(BlockPos sourcePos, boolean portable) {
        this(sourcePos, portable, false);
    }

    protected DeveloperScreen(BlockPos sourcePos, boolean portable, boolean readOnly) {
        super(Component.translatable(readOnly ? "ac.app.skill_tree.name" : "ac.gui.developer.title"));
        this.sourcePos = sourcePos;
        this.portable = portable;
        this.readOnly = readOnly;
    }

    private static ResourceLocation gui(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/guis/" + path);
    }

    // Original CGuiScreen uses Minecraft scaled GUI coordinates; the XML root has scale 1.
    private float uiScale() { return 1; }
    private float left() { return (width - MAIN_W * uiScale()) * .5f; }
    private float top() { return (height - MAIN_H * uiScale()) * .5f; }
    private float vx(double mouseX) { return (float) ((mouseX - left()) / uiScale()); }
    private float vy(double mouseY) { return (float) ((mouseY - top()) / uiScale()); }

    @Override
    protected void init() {
        clearWidgets();
        screenStarted = cn.academy.client.TerminalOverlay.seconds();
        hoverTransitions.clear();
        if (minecraft != null && minecraft.player != null) {
            AbilityState state = AbilityState.load(minecraft.player);
            pageState = AbilityState.fromTag(state.toTag());
            ensureConsole(state, true);
        }
    }

    private String developerProfile() {
        if (portable) return "portable";
        var machine = machine();
        return machine != null && machine.kind() == MachineKind.DEVELOPER_ADVANCED ? "advanced" : "normal";
    }
    private int maximumSkillLevel() { return GameplayData.integer("developers", developerProfile() + ".maximum_level"); }
    private boolean advanced() { return !portable && developerProfile().equals("advanced"); }

    private ACMachineBlockEntity machine() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(sourcePos) instanceof ACMachineBlockEntity machine ? machine : null;
    }

    private net.minecraft.nbt.CompoundTag development() {
        return readOnly || minecraft == null || minecraft.player == null ? new net.minecraft.nbt.CompoundTag()
                : minecraft.player.getPersistentData().getCompound("academy:development");
    }

    private void setDevelopmentIdle() {
        if (readOnly || minecraft == null || minecraft.player == null) return;
        net.minecraft.nbt.CompoundTag value = development();
        value.putInt("State", DevelopmentSyncPayload.IDLE);
        minecraft.player.getPersistentData().put("academy:development", value);
    }

    private void ensureConsole(AbilityState state, boolean force) {
        int devState = development().getInt("State");
        if (console != null && devState != DevelopmentSyncPayload.IDLE) return;
        boolean emergency = state.hasCategory() && resetMode();
        boolean needed = !state.hasCategory() || emergency;
        if (!needed) {
            console = null;
            return;
        }
        if (force || console == null || console.emergency != emergency) {
            String playerName = minecraft == null || minecraft.player == null ? "User" : minecraft.player.getName().getString();
            console = new LegacyConsole(emergency, playerName, !readOnly);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        if (minecraft == null || minecraft.player == null) return;
        if (console != null) {
            console.frame(development());
            if (console.rebuild) {
                setDevelopmentIdle();
                console = null;
                init();
            }
        }
        AbilityState state = AbilityState.load(minecraft.player);
        AbilityCategory category = AbilityRegistry.category(pageState.category());
        float mx = vx(mouseX), my = vy(mouseY), scale = uiScale();

        gui.pose().pushPose();
        gui.pose().translate(left(), top(), 0);
        gui.pose().scale(scale, scale, 1);
        drawFrame(gui, pageState, category, mx, my);

        if (console != null) drawConsole(gui);
        else drawTree(gui, state, category, mx, my);
        if (overlay != Overlay.NONE || wirelessPage != null) {
            gui.pose().pushPose();
            gui.pose().translate(0, 0, 100);
            drawCoverBackground(gui);
            if (wirelessPage != null) wirelessPage.render(gui, mx, my, partialTick);
            else drawCover(gui, state, category, mx, my, development().getInt("State"));
            gui.pose().popPose();
        }
        finishCoverIfReady();
        gui.pose().popPose();
    }

    private void drawFrame(GuiGraphics gui, AbilityState state, AbilityCategory category, float mouseX, float mouseY) {
        ACGuiTextures.hudQuad(gui, PARENT_LEFT, 4, 0, 108.5f, 187, 1, 1, 1, 1, false);
        ACGuiTextures.hudQuad(gui, readOnly ? gui("ui/ui_developerleft_skilltree.png") : UI_LEFT,
                4, 0, 108.5f, 187, 1, 1, 1, 1, false);
        ACGuiTextures.hudQuad(gui, PARENT_RIGHT, 118, 0, 278, 187, 1, 1, 1, 1, false);
        ACGuiTextures.hudQuad(gui, UI_RIGHT, 118, 0, 278, 187, 1, 1, 1, 1, false);
        if (console == null) {
            double dx = Mth.clamp((mouseX + left()) / width, 0, 1) - .5;
            double dy = Mth.clamp((mouseY + top()) / height, 0, 1) - .5;
            float inverse = 1 / 1.01f;
            float u = (float)((dx * .01 - .5) * inverse + .5);
            float v = (float)((dy * .01 - .5) * inverse + .5);
            ACGuiTextures.blit(gui, AREA_BACK, AREA_X, AREA_Y, AREA_W, AREA_H,
                    u, v, inverse, inverse, 1, 1);
        }
        ResourceLocation icon = category == null ? NO_CATEGORY : ResourceLocation.fromNamespaceAndPath(
                AcademyCraft.MOD_ID, "textures/guis/icons/icon_" + category.id() + ".png");
        ACGuiTextures.hudQuad(gui, icon, 6, 67.5f, 32, 32, 1, 1, 1, 1, false);
        xmlText(gui, category == null ? Component.literal("N/A") : category.displayName(),
                37, 67.5f, 70, 13, 0xFFFFFFFF, ACLegacyFont.LEFT);
        rect(gui, 37, 80.75f, 70, 1.5f, 0x4C666666);
        rect(gui, 37, 80.75f, 70 * (category == null ? 0 : Math.max(.02f, state.levelProgress())), 1.5f, 0xFFFFFFFF);
        xmlText(gui, Component.literal("EXP " + (int)(state.levelProgress() * 100) + "%"),
                36, 84, 42, 8, 0xFFFFFFFF, ACLegacyFont.LEFT);
        boolean canUpgrade = !readOnly && state.hasCategory() && state.level() < 5 && state.levelProgress() == 1;
        if (canUpgrade) ACGuiTextures.hudQuad(gui, LEARN_BUTTON, 66, 82, 48.363636f, 15.476364f,
                1, 1, 1, inside(mouseX, mouseY, 66, 82, 48.363636f, 15.476364f) ? 1 : 178 / 255f, false);
        else xmlText(gui, Component.translatable("ac.ability.level" + state.level()),
                65.265625f, 83.5f, 41.15625f, 9, 0xFF1177D6, ACLegacyFont.RIGHT);
        if (readOnly) return;
        ACGuiTextures.hudQuad(gui, PARENT_MACHINE, 4, 0, 108.5f, 187, 1, 1, 1, 1, false);
        if (!portable) drawWirelessPanel(gui, mouseX, mouseY);
        double[] energy = developerEnergy();
        xmlText(gui, Component.literal("Power:"), 8.25f, 130.5f, 100, 12, 0xFFFFFFFF, ACLegacyFont.LEFT);
        float power = energy[1] == 0 ? 0 : (float)Mth.clamp(energy[0] / energy[1], 0, 1);
        rect(gui, 9.75f, 145, 97 * power, 8, 0xFFFCC532);
        xmlText(gui, Component.literal("Sync Rate:"), 8.25f, 153.5f, 100, 12, 0xFFFFFFFF, ACLegacyFont.LEFT);
        rect(gui, 9.75f, 167, 97 * (portable ? .3f : advanced() ? 1 : .7f), 8, 0xFF32A4FC);
    }

    private void drawWirelessPanel(GuiGraphics gui, float mouseX, float mouseY) {
        xmlText(gui, Component.literal("Current Node:"), 8.25f, 104.5f, 100, 12, 0xFFFFFFFF, ACLegacyFont.LEFT);
        ACGuiTextures.hudQuad(gui, ELEMENT_BACK, 8.25f, 114.5f, 100, 16, 1, 1, 1,
                inside(mouseX, mouseY, 8.25f, 114.5f, 100, 16) ? 1 : 178 / 255f, false);
        ACGuiTextures.hudQuad(gui, NODE_ICON, 15.25f, 116.5f, 12, 12, 1, 1, 1, 1, false);
        xmlText(gui, Component.literal(linkedNodeName()), 34.25f, 116.5f, 70, 12, 0xFFFFFFFF, ACLegacyFont.LEFT);
    }

    private static void rect(GuiGraphics gui, float x, float y, float width, float height, int color) {
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0);
        gui.pose().scale(width, height, 1);
        gui.fill(0, 0, 1, 1, color);
        gui.pose().popPose();
    }

    /** Legacy TextBox emit includes the first glyph reaching the width, without resizing the font. */
    private static void xmlText(GuiGraphics gui, Component text, float x, float y, float width,
                                float size, int color, int align) {
        String value = text.getString();
        int end = 0;
        while (end < value.length()) {
            end = value.offsetByCodePoints(end, 1);
            if (ACLegacyFont.width(Component.literal(value.substring(0, end)), size, false) >= width) break;
        }
        ACLegacyFont.draw(gui, Component.literal(value.substring(0, end)),
                x + (align == ACLegacyFont.RIGHT ? width : align == ACLegacyFont.CENTER ? width / 2 : 0),
                y, size, color, align, 0, false);
    }

    private String linkedNodeName() {
        ACMachineBlockEntity machine = machine();
        if (machine == null || machine.linkedNodePos() == null || minecraft == null || minecraft.level == null) return "N/A";
        return minecraft.level.getBlockEntity(machine.linkedNodePos()) instanceof ACMachineBlockEntity node
                ? node.nodeName() : machine.networkId().isBlank() ? "N/A" : machine.networkId();
    }

    private double[] developerEnergy() {
        if (minecraft == null || minecraft.player == null) return new double[]{0, 0};
        net.minecraft.nbt.CompoundTag dev = development();
        if (dev.getInt("MaxEnergy") > 0 && dev.getBoolean("Portable") == portable
                && dev.getInt("State") != DevelopmentSyncPayload.IDLE)
            return new double[]{dev.getDouble("Energy"), dev.getInt("MaxEnergy")};
        ACMachineBlockEntity machine = machine();
        if (!portable && machine != null)
            return new double[]{machine.energy.exactEnergy(), machine.energy.getMaxEnergyStored()};
        ItemStack stack = minecraft.player.getMainHandItem();
        if (!stack.is(ACItems.DEVELOPER_PORTABLE.get())) return new double[]{0, 0};
        var manager = cn.academy.energy.api.IFItemManager.instance;
        return new double[]{manager.getEnergy(stack), manager.getMaxEnergy(stack)};
    }

    private boolean resetMode() {
        return minecraft != null && minecraft.player != null
                && minecraft.player.getMainHandItem().is(GameplayRules.item(GameplayData.string("developers", "reset.item")));
    }

    private List<AbilitySkill> visibleSkills(AbilityState state, AbilityCategory category) {
        if (category == null) return List.of();
        List<AbilitySkill> result = new ArrayList<>();
        for (AbilitySkill skill : category.enabledSkills()) {
            AbilitySkill parent = skill.parent();
            boolean parentLearned = parent == null || state.learned().contains(parent.id());
            if (state.level() >= skill.level() || state.learned().contains(skill.id()) || parentLearned) result.add(skill);
        }
        return result;
    }

    private float nodeX(AbilitySkill skill, float mouseX) {
        SkillTreeScreen.Point point = SkillTreeScreen.POSITIONS.get(skill.name());
        float dx = (Mth.clamp((mouseX + left()) / width, 0, 1) - .5f) * 10;
        return point == null ? AREA_X + AREA_W / 2 : AREA_X + point.x() + 8 - dx;
    }

    private float nodeY(AbilitySkill skill, float mouseY) {
        SkillTreeScreen.Point point = SkillTreeScreen.POSITIONS.get(skill.name());
        float dy = (Mth.clamp((mouseY + top()) / height, 0, 1) - .5f) * 10;
        return point == null ? AREA_Y + AREA_H / 2 : AREA_Y + point.y() + 8 - dy;
    }

    private void drawTree(GuiGraphics gui, AbilityState state, AbilityCategory category, float mouseX, float mouseY) {
        if (category == null) return;
        List<AbilitySkill> skills = visibleSkills(pageState, category);
        hovered = overlay == Overlay.NONE && wirelessPage == null ? skillAt(state, category, mouseX, mouseY) : null;

        try (var ignored = ACSkillTreeRenderer.begin(gui)) {
            for (int index = 0; index < skills.size(); index++) {
                AbilitySkill skill = skills.get(index);
                boolean learned = pageState.learned().contains(skill.id());
                float alpha = potentialAlpha(pageState, category, skill);
                ACSkillTreeRenderer.node(gui, skill.icon(), nodeX(skill, mouseX), nodeY(skill, mouseY), hoverScale(skill),
                        appearance(index, 0, 10) * alpha, appearance(index, .08, 10) * alpha, learned,
                        appearance(index, .12, 2) * state.experience(skill.id()));
                AbilitySkill parent = skill.parent();
                if (parent != null) ACSkillTreeRenderer.line(gui, nodeX(parent, mouseX), nodeY(parent, mouseY),
                        nodeX(skill, mouseX), nodeY(skill, mouseY), alpha * (learned ? 1 : .4f), appearance(index, 0, 5));
            }
        }
    }

    private float potentialAlpha(AbilityState state, AbilityCategory category, AbilitySkill skill) {
        if (state.learned().contains(skill.id())) return 1;
        AbilitySkill parent = skill.parent();
        return parent == null || state.learned().contains(parent.id()) ? .7f : .25f;
    }

    private float appearance(int index, double delay, float speed) {
        double dt = cn.academy.client.TerminalOverlay.seconds() - screenStarted - index * .08 - .1 - delay;
        return (float) Mth.clamp(dt * speed, 0, 1);
    }

    private float hoverScale(AbilitySkill skill) {
        double now = cn.academy.client.TerminalOverlay.seconds();
        HoverTransition transition = hoverTransitions.computeIfAbsent(skill.id(), ignored -> new HoverTransition());
        float progress = (float) Mth.clamp((now - transition.started) / .1, 0, 1);
        float scale = transition.hovered ? Mth.lerp(progress, 1, 1.2f) : Mth.lerp(progress, 1.2f, 1);
        if (progress == 1 && transition.hovered != (skill == hovered)) {
            transition.hovered = skill == hovered;
            transition.started = now;
        }
        return scale;
    }

    private AbilitySkill skillAt(AbilityState state, AbilityCategory category, float mouseX, float mouseY) {
        for (AbilitySkill skill : visibleSkills(pageState, category)) {
            float dx = mouseX - nodeX(skill, mouseX), dy = mouseY - nodeY(skill, mouseY);
            if (Math.abs(dx) <= 8 && Math.abs(dy) <= 8) return skill;
        }
        return null;
    }

    private void drawConsole(GuiGraphics gui) {
        if (console == null) return;
        List<String> visual = new ArrayList<>();
        visual.addAll(console.renderLines());
        int from = Math.max(0, visual.size() - 10);
        int y = AREA_Y + 5;
        for (int i = from; i < visual.size(); i++, y += 10)
            drawText(gui, Component.literal(visual.get(i)), AREA_X + 5, y, 8, 0xFFFFFFFF, false);
    }

    private void drawCoverBackground(GuiGraphics gui) {
        double now = cn.academy.client.TerminalOverlay.seconds();
        float fade = (float)Mth.clamp((now - (closingCover ? coverClosedAt : overlayStarted)) / .2, 0, 1);
        if (closingCover) fade = 1 - fade;
        rect(gui, -left(), -top(), width, height, Math.round(255 * .7f * fade) << 24);
    }

    private void closeCover(boolean rebuild) {
        if (closingCover) return;
        closingCover = true;
        rebuildAfterCover = rebuild;
        coverClosedAt = cn.academy.client.TerminalOverlay.seconds();
    }

    private void finishCoverIfReady() {
        if (!closingCover || cn.academy.client.TerminalOverlay.seconds() - coverClosedAt < .2) return;
        closingCover = false;
        overlay = Overlay.NONE;
        selected = null;
        wirelessPage = null;
        if (rebuildAfterCover) init();
    }

    private void drawCover(GuiGraphics gui, AbilityState state, AbilityCategory category,
                           float mouseX, float mouseY, int devState) {
        float cx = MAIN_W / 2f, cy = MAIN_H / 2f;
        if (devState == DevelopmentSyncPayload.DEVELOPING) overlayProgress = development().getFloat("Progress");
        else if (devState == DevelopmentSyncPayload.DONE) overlayProgress = 1;
        float progress = overlayProgress;

        if (overlay == Overlay.SKILL && selected != null) {
            boolean learned = overlayLearned;
            float iconProgress = devState == DevelopmentSyncPayload.DONE ? 1
                    : devState == DevelopmentSyncPayload.DEVELOPING || devState == DevelopmentSyncPayload.FAILED
                    ? progress : 0;
            drawActionIcon(gui, selected.icon(), 32, cx, cy, iconProgress,
                    devState == DevelopmentSyncPayload.DONE);
            Component name = learned ? selected.displayName()
                    : selected.displayName().copy().append(" (LV " + selected.level() + ")");
            ACLegacyFont.draw(gui, name, cx, cy + 23, 12, 0xFFFFFFFF, ACLegacyFont.CENTER, 0, true);
            if (learned && devState == DevelopmentSyncPayload.IDLE) {
                drawCenteredFit(gui, Component.translatable("ac.skill_tree.skill_exp").copy()
                        .append((int) (state.experience(selected.id()) * 100) + "%"),
                        cx, cy + 35, 250, 8, 0xFFA1E1FF);
                drawWrappedCentered(gui, selected.description(state), cx, cy + 44, 200, 9, 0xFFFFFFFF);
                return;
            }

            drawCenteredFit(gui, Component.translatable("ac.skill_tree.skill_not_learned"),
                    cx, cy + 35, 250, 10, 0xFFFF5555);
            if (readOnly) return;
            drawConditions(gui, state, category, selected, cx, cy + 45, mouseX, mouseY);
            Component message;
            if (devState == DevelopmentSyncPayload.DEVELOPING)
                message = Component.translatable("ac.skill_tree.progress").copy()
                        .append(" " + Math.round(progress * 100) + "%");
            else if (devState == DevelopmentSyncPayload.DONE)
                message = Component.translatable("ac.skill_tree.dev_successful");
            else if (devState == DevelopmentSyncPayload.FAILED)
                message = Component.translatable("ac.skill_tree.dev_failed");
            else message = overlayMessage != null ? overlayMessage
                    : Component.translatable("ac.skill_tree.learn_question", skillEnergy(selected));
            drawCenteredFit(gui, message, cx, cy + 60, 285, 10,
                    devState == DevelopmentSyncPayload.FAILED ? 0xFFFF7777 : 0xAAFFFFFF);
            if (devState == DevelopmentSyncPayload.IDLE && !overlayAttempted)
                drawSmallButton(gui, cx - 16, cy + 72, inside(mouseX, mouseY, cx - 16, cy + 72, 32, 16));
        } else {
            int next = overlayNextLevel;
            ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,
                    "textures/abilities/condition/any" + next + ".png");
            drawActionIcon(gui, icon, 32, cx, cy, progress, devState == DevelopmentSyncPayload.DONE);
            Component nextLevel = Component.translatable("ac.ability.level" + next);
            drawCenteredFit(gui, Component.translatable("ac.skill_tree.uplevel", nextLevel),
                    cx, cy + 23, 250, 12, 0xFFFFFFFF);
            int estimate = overlayEnergy;
            drawCenteredFit(gui, Component.translatable("ac.skill_tree.req").copy().append(" " + estimate),
                    cx, cy + 36, 250, 9, 0xFFFFFFFF);
            Component hint = switch (devState) {
                case DevelopmentSyncPayload.DEVELOPING -> Component.translatable("ac.skill_tree.dev_developing");
                case DevelopmentSyncPayload.DONE -> Component.translatable("ac.skill_tree.dev_successful");
                case DevelopmentSyncPayload.FAILED -> Component.translatable("ac.skill_tree.dev_failed");
                default -> overlayMessage != null ? overlayMessage : Component.translatable("ac.skill_tree.level_question");
            };
            drawCenteredFit(gui, hint, cx, cy + 46, 270, 9,
                    devState == DevelopmentSyncPayload.FAILED ? 0xFFFF7777 : 0xFFFFFFFF);
            if (devState == DevelopmentSyncPayload.IDLE && !overlayAttempted)
                drawSmallButton(gui, cx - 16, cy + 57, inside(mouseX, mouseY, cx - 16, cy + 57, 32, 16));
        }
    }

    private void drawConditions(GuiGraphics gui, AbilityState state, AbilityCategory category, AbilitySkill skill,
                                float cx, float y, float mouseX, float mouseY) {
        List<ConditionView> conditions = overlayConditions;
        int step = 16, len = step * conditions.size();
        ACLegacyFont.draw(gui, Component.translatable("ac.skill_tree.req"), cx - len / 2f - 2, y + 3,
                9, 0xAAFFFFFF, ACLegacyFont.RIGHT, 0, false);
        ConditionView hovering = null;
        for (int i = 0; i < conditions.size(); i++) {
            float x = cx - len / 2f + i * step;
            ConditionView condition = conditions.get(i);
            ACGuiTextures.hudQuad(gui, condition.icon, x, y, 14, 14, 1, 1, 1, 1, !condition.accepted);
            if (inside(mouseX, mouseY, x, y, 14, 14)) hovering = condition;
        }
        if (hovering != null) ACLegacyFont.draw(gui, Component.literal("(").append(hovering.hint).append(")"),
                cx + len / 2f + 3, y + 2, 9, hovering.accepted ? 0xEEFFFFFF : 0xFFEE5858,
                ACLegacyFont.LEFT, 0, false);
    }

    private List<ConditionView> conditionViews(AbilityState state, AbilityCategory category, AbilitySkill skill) {
        List<ConditionView> conditions = new ArrayList<>();
        // DevConditionLevel is deliberately hidden; developer type is a separate visible condition.
        String type = skill.level() <= 2 ? "portable" : skill.level() <= 3 ? "normal" : "advanced";
        conditions.add(new ConditionView(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,
                "textures/" + (type.equals("portable") ? "items/developer_portable_empty" : "blocks/dev_" + type) + ".png"),
                skill.level() <= maximumSkillLevel(), Component.translatable("ac.skill_tree.type_" + type)));
        if (category != null) for (AbilitySkill.Requirement requirement : skill.enabledRequirements()) {
            AbilitySkill dependency = category.skill(requirement.skillId());
            if (dependency != null && !dependency.isEnabled()) continue;
            boolean accepted = dependency != null && state.learned().contains(dependency.id())
                    && state.experienceForLearning(dependency.id()) >= requirement.experience();
            Component hint = (dependency == null ? Component.literal(requirement.skillId()) : dependency.displayName())
                    .copy().append(": " + Math.round(requirement.experience() * 100) + "%");
            conditions.add(new ConditionView(dependency == null ? NO_CATEGORY : dependency.icon(), accepted, hint));
        }
        int anyLevel = skill.requiredAnySkillLevel();
        if (anyLevel > 0) {
            boolean accepted = category != null && category.skills().stream().anyMatch(candidate ->
                    candidate.level() == anyLevel && state.learned().contains(candidate.id()));
            conditions.add(new ConditionView(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID,
                    "textures/abilities/condition/any" + anyLevel + ".png"), accepted,
                    Component.translatable("ac.skill_tree.anyskill", anyLevel)));
        }
        return List.copyOf(conditions);
    }

    private record ConditionView(ResourceLocation icon, boolean accepted, Component hint) {}

    private void drawActionIcon(GuiGraphics gui, ResourceLocation icon, int iconTextureSize,
                                float cx, float cy, float progress, boolean glow) {
        ACSkillTreeRenderer.action(gui, icon, cx, cy, progress, glow);
    }

    private int energyPerStimulation() { return GameplayData.integer("developers", developerProfile() + ".energy_per_stimulation"); }
    private int skillEnergy(AbilitySkill skill) {
        return (int) (GameplayData.number("developers", "learning.base_stimulations") + skill.level() * skill.level() * GameplayData.number("developers", "learning.level_squared_multiplier")) * energyPerStimulation();
    }
    private int levelEnergy(AbilityState state) { return GameplayData.integer("developers", "level_up.stimulations_per_level") * (state.level() + 1) * energyPerStimulation(); }
    private boolean hasDevelopmentEnergy(int amount) { return developerEnergy()[0] >= amount; }

    private void drawSmallButton(GuiGraphics gui, float x, float y, boolean hovered) {
        float alpha = hovered ? 1 : .6f;
        ACGuiTextures.blit(gui, SMALL_BUTTON, x, y, 32, 16,
                0, 0, 64, 32, 64, 32, 1, 1, 1, alpha);
    }

    private void beginDevelopment(DeveloperActionPayload payload) {
        if (minecraft != null && minecraft.player != null) {
            double[] energy = developerEnergy();
            net.minecraft.nbt.CompoundTag value = new net.minecraft.nbt.CompoundTag();
            value.putInt("State", DevelopmentSyncPayload.DEVELOPING);
            value.putInt("Action", payload.action());
            value.putString("Skill", payload.skill());
            value.putFloat("Progress", 0);
            value.putBoolean("Portable", portable);
            value.putDouble("Energy", energy[0]);
            value.putInt("MaxEnergy", (int)energy[1]);
            minecraft.player.getPersistentData().put("academy:development", value);
        }
        PacketDistributor.sendToServer(payload);
    }

    private void runConsoleCommand() {
        if (console == null || minecraft == null || minecraft.player == null) return;
        String command = console.takeCommand();
        AbilityState state = AbilityState.load(minecraft.player);
        if (!readOnly && !state.hasCategory() && command.equals("learn")) {
            console.startDevelopment(false);
            beginDevelopment(DeveloperActionPayload.level(sourcePos, portable));
        } else if (!readOnly && resetMode() && command.equals("reset")) {
            if (!advanced()) console.outputLine(Component.translatable("ac.skill_tree.console.reset_fail_dev").getString());
            else if (state.level() < GameplayData.integer("developers", "reset.minimum_level") || minecraft.player.getInventory().items.stream().noneMatch(stack -> {
                String factorCategory = cn.academy.item.InductionFactorItem.category(stack);
                return stack.is(ACItems.INDUCTION_FACTOR.get()) && AbilityRegistry.category(factorCategory) != null
                        && !factorCategory.equals(state.category());
            })) console.outputLine(Component.translatable("ac.skill_tree.console.reset_fail_other").getString());
            else {
                console.startDevelopment(true);
                beginDevelopment(DeveloperActionPayload.reset(sourcePos));
            }
        } else console.outputLine(Component.translatable("ac.skill_tree.console.invalid_command").getString());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || minecraft == null || minecraft.player == null)
            return super.mouseClicked(mouseX, mouseY, button);
        float x = vx(mouseX), y = vy(mouseY);
        if (closingCover) return true;
        if (wirelessPage != null) {
            if (!wirelessPage.mouseClicked(x, y, button)) closeCover(true);
            return true;
        }
        if (overlay == Overlay.NONE && !inside(x, y, 0, 0, MAIN_W, MAIN_H)) return false;
        int devState = development().getInt("State");
        if (overlay != Overlay.NONE && devState == DevelopmentSyncPayload.DEVELOPING) return true;
        AbilityState state = AbilityState.load(minecraft.player);
        AbilityCategory category = AbilityRegistry.category(state.category());

        if (overlay != Overlay.NONE) {
            if (devState == DevelopmentSyncPayload.DONE || devState == DevelopmentSyncPayload.FAILED) {
                if (devState == DevelopmentSyncPayload.DONE) {
                    setDevelopmentIdle();
                    overlay = Overlay.NONE;
                    selected = null;
                    init();
                } else closeCover(false);
                return true;
            }
            float cy = MAIN_H / 2f;
            if (!readOnly && overlay == Overlay.SKILL && selected != null && !overlayLearned && !overlayAttempted
                    && inside(x, y, MAIN_W / 2 - 16, cy + 72, 32, 16)) {
                overlayAttempted = true; // The original button disposes even when validation fails.
                if (!hasDevelopmentEnergy(skillEnergy(selected)))
                    overlayMessage = Component.translatable("ac.skill_tree.noenergy");
                else if (selected.level() > state.level())
                    overlayMessage = Component.translatable("ac.skill_tree.level_fail", selected.level());
                else if (!state.canLearn(selected) || selected.level() > maximumSkillLevel())
                    overlayMessage = Component.translatable("ac.skill_tree.condition_fail");
                else beginDevelopment(DeveloperActionPayload.learn(sourcePos, portable, selected.id()));
                return true;
            }
            if (overlay == Overlay.LEVEL && !overlayAttempted
                    && inside(x, y, MAIN_W / 2 - 16, cy + 57, 32, 16)) {
                overlayAttempted = true;
                if (!hasDevelopmentEnergy(levelEnergy(state)))
                    overlayMessage = Component.translatable("ac.skill_tree.noenergy");
                else beginDevelopment(DeveloperActionPayload.level(sourcePos, portable));
                return true;
            }
            closeCover(false);
            return true;
        }
        if (!readOnly && !portable && inside(x, y, 8.25f, 114.5f, 100, 16)) {
            wirelessPage = new DeveloperWirelessPage(sourcePos, font);
            overlayStarted = cn.academy.client.TerminalOverlay.seconds();
            return true;
        }
        if (!readOnly && inside(x, y, 66, 82, 48.363636f, 15.476364f) && state.level() < 5
                && state.levelProgress() >= 1) {
            overlay = Overlay.LEVEL;
            overlayStarted = cn.academy.client.TerminalOverlay.seconds();
            overlayAttempted = false;
            overlayMessage = null;
            overlayProgress = 0;
            overlayNextLevel = Mth.clamp(state.level() + 1, 1, 5);
            overlayEnergy = levelEnergy(state);
            overlayLearned = selected != null && state.learned().contains(selected.id());
            setDevelopmentIdle();
            return true;
        }
        if (console != null) return true;
        AbilitySkill skill = category == null ? null : skillAt(state, category, x, y);
        if (skill != null) {
            selected = skill;
            overlayConditions = readOnly ? List.of() : conditionViews(state, category, skill);
            overlay = Overlay.SKILL;
            overlayStarted = cn.academy.client.TerminalOverlay.seconds();
            overlayAttempted = false;
            overlayMessage = null;
            overlayProgress = 0;
            overlayNextLevel = Mth.clamp(state.level() + 1, 1, 5);
            overlayEnergy = levelEnergy(state);
            overlayLearned = selected != null && state.learned().contains(selected.id());
            setDevelopmentIdle();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (wirelessPage != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) closeCover(true);
            else wirelessPage.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (console != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                console.backspace();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                runConsoleCommand();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (wirelessPage != null) return wirelessPage.charTyped(codePoint, modifiers);
        if (console != null && StringUtil.isAllowedChatCharacter(codePoint)) {
            console.type(codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override public void removed() {
        if (!readOnly && !portable) PacketDistributor.sendToServer(new DeveloperActionPayload(
                DeveloperActionPayload.CLOSE, sourcePos, false, ""));
        super.removed();
    }

    private void drawText(GuiGraphics gui, Component text, float x, float y, float size, int color, boolean centered) {
        ACLegacyFont.draw(gui, text, x, y, size, color,
                centered ? ACLegacyFont.CENTER : ACLegacyFont.LEFT, 0, false);
    }

    private void drawCenteredFit(GuiGraphics gui, Component text, float center, float y, int maxWidth, int size, int color) {
        ACLegacyFont.draw(gui, text, center, y, size, color, ACLegacyFont.CENTER, 0, false);
    }

    private void drawWrappedCentered(GuiGraphics gui, Component text, float center, float y, int width, int size, int color) {
        List<String> lines = wrapLegacy(text.getString(), width, size, false);
        for (int i = 0; i < lines.size(); i++) ACLegacyFont.draw(gui, Component.literal(lines.get(i)),
                center, y + i * (size + 1), size, color, ACLegacyFont.CENTER, width, false);
    }

    private static List<String> wrapLegacy(String text, int maxWidth, float size, boolean bold) {
        return ACLegacyFont.wrap(text, maxWidth, size, bold);
    }

    private static boolean inside(float x, float y, float left, float top, float width, float height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    @Override public boolean isPauseScreen() { return false; }

    /** Frame-driven task queue from Common.Console, including input while startup tasks are active. */
    private static final class LegacyConsole {
        private final boolean emergency;
        private final List<String> lines = new ArrayList<>();
        private final java.util.ArrayDeque<Task> tasks = new java.util.ArrayDeque<>();
        private String input = "";
        private Task current;
        private boolean rebuild;
        private net.minecraft.nbt.CompoundTag development = new net.minecraft.nbt.CompoundTag();
        private interface Task {
            default void begin() {}
            default void update() {}
            default void finish() {}
            boolean finished();
        }
        private final Task inputTask = new Task() {
            public void begin() { output("OS >"); }
            public boolean finished() { return false; }
        };

        LegacyConsole(boolean emergency, String playerName, boolean developerAttached) {
            this.emergency = emergency;
            lines.add("");
            enqueue(slowPrint(local("init", playerName)));
            pause(.4);
            var random = java.util.concurrent.ThreadLocalRandom.current();
            List<String> sequence = new ArrayList<>();
            for (int i = 1; i <= 6; i++) sequence.add((i * 10 + random.nextInt(6) - 3) + "%");
            sequence.add((64 + random.nextInt(4)) + "%");
            sequence.add(local("boot_failed"));
            for (int i = 0; i < sequence.size(); i++) {
                String text = sequence.get(i);
                boolean last = i == sequence.size() - 1;
                enqueue(timed(.3, () -> output(text), () -> { if (!last) output("\b".repeat(text.length())); }));
            }
            enqueue(slowPrint(local(emergency ? "override" : "invalid_cat")
                    + (emergency || !developerAttached ? "" : local("learn_hint"))));
        }

        private static String local(String key, Object... arguments) {
            return Component.translatable("ac.skill_tree.console." + key, arguments).getString().replace("\\n", "\n");
        }
        private static double now() { return cn.academy.client.TerminalOverlay.seconds(); }
        private void enqueue(Task task) {
            tasks.addLast(task);
            if (current == inputTask) {
                output(input + "\n");
                current = null;
            }
        }
        private Task timed(double life, Runnable begin, Runnable finish) {
            return new Task() {
                double started;
                public void begin() { started = now(); begin.run(); }
                public void finish() { finish.run(); }
                public boolean finished() { return now() - started >= life; }
            };
        }
        private void pause(double time) { enqueue(timed(time, () -> {}, () -> {})); }
        private Task print(String value) {
            return new Task() {
                public void begin() { output(value); }
                public boolean finished() { return true; }
            };
        }
        private Task slowPrint(String value) {
            return new Task() {
                int index;
                double last;
                public void begin() { last = now(); }
                public void update() {
                    int count = (int)((now() - last) / .01);
                    if (count <= 0) return;
                    int end = Math.min(value.length(), index + count);
                    output(value.substring(index, end));
                    index = end;
                    last += count * .01;
                }
                public boolean finished() { return index == value.length(); }
            };
        }
        void frame(net.minecraft.nbt.CompoundTag development) {
            this.development = development;
            if (current != null && current.finished()) {
                current.finish();
                current = null;
            }
            if (current != null) current.update();
            if (current == null) {
                current = tasks.isEmpty() ? inputTask : tasks.removeFirst();
                current.begin();
            }
        }
        List<String> renderLines() {
            var result = new ArrayList<>(lines);
            if (current == inputTask) {
                int last = result.size() - 1;
                result.set(last, result.get(last) + input + ((int)(now() * 1000) % 1000 < 500 ? "_" : ""));
            }
            return result;
        }
        void type(char value) { input += value; }
        void backspace() { if (!input.isEmpty()) input = input.substring(0, input.length() - 1); }
        String takeCommand() {
            String value = input;
            if (current == inputTask) { output(input + "\n"); current = null; }
            input = "";
            return value; // Original commands are case-sensitive, with no whitespace stripping.
        }
        void outputLine(String value) { enqueue(print(value.replace("\\n", "\n"))); }
        void startDevelopment(boolean reset) {
            enqueue(print(local(reset ? "reset_begin" : "dev_begin")));
            enqueue(print(local("progress", "00")));
            enqueue(new Task() {
                public boolean finished() {
                    int state = development.getInt("State");
                    return state == DevelopmentSyncPayload.DONE || state == DevelopmentSyncPayload.FAILED;
                }
                public void update() {
                    int amount = (int)(development.getFloat("Progress") * 100);
                    output("\b\b\b" + (amount < 10 ? "0" + amount : Integer.toString(amount)) + "%");
                }
                public void finish() {
                    boolean success = development.getInt("State") == DevelopmentSyncPayload.DONE;
                    output("\n" + local(reset ? success ? "reset_succ" : "reset_fail" : success ? "dev_succ" : "dev_fail"));
                    pause(.5);
                    enqueue(new Task() {
                        public void begin() { rebuild = true; }
                        public boolean finished() { return true; }
                    });
                }
            });
        }
        private void output(String value) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '\b') {
                    String last = lines.getLast();
                    if (!last.isEmpty()) lines.set(lines.size() - 1, last.substring(0, last.length() - 1));
                } else if (c == '\n') lines.add("");
                else lines.set(lines.size() - 1, lines.getLast() + c);
            }
            while (lines.size() > 10) lines.removeFirst();
        }
    }
}
