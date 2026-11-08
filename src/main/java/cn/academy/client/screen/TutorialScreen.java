package cn.academy.client.screen;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityState;
import cn.academy.client.ACKeyMappings;
import cn.academy.client.render.ACGuiTextures;
import cn.academy.client.render.ACLegacyFont;
import cn.academy.client.render.TutorialPreviewTransform;
import cn.academy.tutorial.ViewGroup;
import cn.academy.tutorial.ViewGroup.RecipeView;
import cn.academy.tutorial.TutorialRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Native recreation of GuiTutorial, tutorial.xml and tutorial_windows.xml. */
public final class TutorialScreen extends Screen {
    private static final int FRAME_WIDTH = 427;
    private static final int FRAME_HEIGHT = 240;
    private String[] pages = {};
    private final java.util.Set<String> learnedPages = new java.util.HashSet<>();

    private static final ResourceLocation LEFT_WINDOW = gui("window_tutorial_left.png");
    private static final ResourceLocation LOGO_0 = gui("tutorial/logo0.png");
    private static final ResourceLocation LOGO_1 = gui("tutorial/logo1.png");
    private static final ResourceLocation LOGO_2 = gui("tutorial/logo2.png");
    private static final ResourceLocation LOGO_3 = gui("tutorial/logo3.png");
    private static final ResourceLocation SCROLL_TRACK = gui("button/widget_scroll_1.png");
    private static final ResourceLocation SCROLL_HANDLE = gui("button/widget_scroll_2.png");
    private static final ResourceLocation ARROW_LEFT = gui("button/button_left_2.png");
    private static final ResourceLocation ARROW_RIGHT = gui("button/button_right_2.png");
    private static final ResourceLocation TAG_VIEW = gui("icons/icon_view.png");
    private static final ResourceLocation TAG_CRAFT = gui("icons/icon_craft.png");
    private static final ResourceLocation CRAFT_GRID = gui("tutorial/crafting_grid.png");
    private static final ResourceLocation METAL_FORMER = gui("tutorial_metalformer.png");
    private static final ResourceLocation SMELTING = gui("tutorial_smelting.png");
    private static final ResourceLocation IMAG_FUSOR = gui("tutorial_fusor.png");

    private PageData[] pageData = {};
    private final List<PreviewGroup> previews = new ArrayList<>();
    private int selectedPage = -1;
    private boolean selectedLearned;
    private int previewGroup;
    private static final long TIMER_START = Util.getMillis();
    private final java.util.IdentityHashMap<RecipeView, StackCycle[]> stackCycles = new java.util.IdentityHashMap<>();
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private float previewMouseX, previewMouseY;
    private static final class StackCycle {
        int current;
        double last = seconds() + java.util.concurrent.ThreadLocalRandom.current().nextDouble(-1, 1);
    }
    private static double seconds() { return (Util.getMillis() - TIMER_START) / 1000.0; }
    private float scroll;
    private boolean draggingScroll;
    private boolean firstOpen;
    private long openedAt;
    private long selectedAt;

    private boolean initialized;
    private float scrollDragOffset;
    private static final class PageData {
        final String title, rawBrief, rawContent;
        TutorialMarkdown brief, content;
        PageData(String title, String brief, String content) { this.title = title; rawBrief = brief; rawContent = content; }
        TutorialMarkdown brief() {
            if (brief == null) brief = new TutorialMarkdown(net.minecraft.client.Minecraft.getInstance(), rawBrief, 130, 8);
            return brief;
        }
        TutorialMarkdown content() {
            if (content == null) content = new TutorialMarkdown(net.minecraft.client.Minecraft.getInstance(), rawContent, 150, 8);
            return content;
        }
    }

    private static final class PreviewGroup {
        final ViewGroup definition;
        final List<ViewGroup.SubView> views;
        int index;
        PreviewGroup(ViewGroup definition, net.minecraft.world.level.Level level) {
            this.definition = definition;
            views = List.copyOf(definition.getSubViews(level));
        }
    }

    public TutorialScreen() {
        super(Component.translatable("ac.app.tutorial.name"));
    }

    private static ResourceLocation gui(String path) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "textures/guis/" + path);
    }

    private float frameScale() {
        return width / 480f;
    }
    private float frameLeft() { return (width - FRAME_WIDTH * frameScale()) * .5f; }
    private float frameTop() { return (height - FRAME_HEIGHT * frameScale()) * .5f; }
    private float virtualX(double mouseX) { return (float) ((mouseX - frameLeft()) / frameScale()); }
    private float virtualY(double mouseY) { return (float) ((mouseY - frameTop()) / frameScale()); }

    @Override
    protected void init() {
        prepareTextureFiltering();
        if (initialized) return;
        initialized = true;
        openedAt = Util.getMillis();
        if (minecraft != null && minecraft.player != null) {
            firstOpen = !minecraft.player.getPersistentData().getBoolean("academy:tutorial_opened");
            minecraft.player.getPersistentData().putBoolean("academy:tutorial_opened", true);
        }
        if (pages.length == 0 && minecraft != null && minecraft.player != null) {
            var groups = cn.academy.tutorial.TutorialRegistry.groupByLearned(minecraft.player);
            groups.getLeft().forEach(tutorial -> learnedPages.add(tutorial.id));
            pages = java.util.stream.Stream.concat(groups.getLeft().stream(), groups.getRight().stream())
                    .map(tutorial -> tutorial.id).toArray(String[]::new);
            pageData = new PageData[pages.length];
        }
        for (int i = 0; i < pages.length; i++) pageData[i] = loadPage(pages[i]);
    }

    private void prepareTextureFiltering() {
        for (ResourceLocation texture : new ResourceLocation[]{LEFT_WINDOW, LOGO_0, LOGO_1, LOGO_2, LOGO_3,
                SCROLL_TRACK, SCROLL_HANDLE, ARROW_LEFT, ARROW_RIGHT, TAG_VIEW, TAG_CRAFT, CRAFT_GRID,
                METAL_FORMER, SMELTING, IMAG_FUSOR}) ACGuiTextures.setLinearFilter(texture);
    }

    private PageData loadPage(String page) {
        String source = TutorialPageResource.load(minecraft, page);
        int title = source.indexOf("![title]"), brief = source.indexOf("![brief]"), content = source.indexOf("![content]");
        if (title < 0 || title >= brief || brief >= content) throw new IllegalArgumentException("Malformed tutorial " + page);
        return new PageData(trimHead(source.substring(title + 8, brief)),
                trimHead(source.substring(brief + 8, content)), trimHead(source.substring(content + 10)));
    }
    private static String trimHead(String text) {
        int index = 0;
        while (index < text.length() && (text.charAt(index) == ' ' || text.charAt(index) == '\r' || text.charAt(index) == '\n')) index++;
        return text.substring(index);
    }

    private boolean unlocked(int page) {
        return learnedPages.contains(pages[page]);
    }

    private void selectPage(int page) {
        if (selectedPage == page) return;
        if (selectedPage < 0) selectedAt = Util.getMillis();
        selectedPage = page;
        selectedLearned = minecraft != null && minecraft.player != null
                && TutorialRegistry.getTutorial(pages[page]).isActivated(minecraft.player);
        scroll = 0;
        previewGroup = 0;
        buildPreviews();
    }

    private void buildPreviews() {
        previews.clear();
        stackCycles.clear();
        if (selectedPage < 0 || minecraft == null) return;
        for (var group : TutorialRegistry.getTutorial(pages[selectedPage]).getPreview()) {
            var preview = new PreviewGroup(group, minecraft.level);
            previews.add(preview);
            for (var view : preview.views) if (view instanceof RecipeView recipe) {
                var cycles = new StackCycle[recipe.ingredients().size()];
                for (int i = 0; i < cycles.length; i++) cycles[i] = new StackCycle();
                stackCycles.put(recipe, cycles);
            }
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        float scale = frameScale();
        float vx = virtualX(mouseX), vy = virtualY(mouseY);
        gui.pose().pushPose();
        gui.pose().translate(frameLeft(), frameTop(), 0);
        gui.pose().scale(scale, scale, 1);

        float leftPartX = 7, partY = (FRAME_HEIGHT - 220.5f) * .5f;
        float introTime = (Util.getMillis() - openedAt) / 1000f;
        float leftAlpha = firstOpen ? blend(introTime, 1.75f, .3f) : 1;
        ACGuiTextures.hudQuad(gui, LEFT_WINDOW, leftPartX, partY, 85, 220.5f, 1, 1, 1, leftAlpha, false);

        boolean listVisible = !firstOpen || introTime > 2.4f;
        if (listVisible) drawTutorialList(gui, vx, vy, leftPartX, partY, 1);

        float rightX = 92, rightY = partY;
        float fade = selectedPage < 0 ? 1 : 1 - blend((Util.getMillis() - selectedAt) / 1000f, 0, .3f);
        if (fade > 0) drawIntroLogos(gui, rightX, rightY, introTime, fade, false);
        if (selectedPage >= 0) drawSelected(gui, vx, vy, rightX, rightY);
        if (fade > 0) drawIntroLogos(gui, rightX, rightY, introTime, fade, true);
        gui.pose().popPose();
        for (var renderable : renderables) renderable.render(gui, mouseX, mouseY, partialTick);
    }

    private void drawTutorialList(GuiGraphics gui, float mouseX, float mouseY, float x, float y, float alpha) {
        float listX = x + 6.6f, listY = y + 7;
        for (int i = 0; i < pages.length && (i + 1) * 12 <= 207; i++) {
            float rowY = listY + i * 12;
            boolean hover = inside(mouseX, mouseY, listX, rowY, 72, 12);
            if (hover) rect(gui, listX, rowY, 72, 12, 0x4DFFFFFF);
            int color = unlocked(i) ? withAlpha(0xFFFFFF, alpha) : withAlpha(0x999999, alpha);
            String title = pageData[i] == null ? pages[i] : pageData[i].title;
            ACLegacyFont.draw(gui, Component.literal(emit(title, 69, 10)), listX + 3, rowY + 1,
                    10, color, ACLegacyFont.LEFT, 0, false);
        }
    }

    private void drawIntroLines(GuiGraphics gui, float x, float y, float time) {
        float end = 500, begin = 200;
        if (firstOpen) {
            float delta = Math.max(0, time - .4f);
            if (delta < .3f) { begin = 50; end = 500 * delta / .3f; }
            else begin = 500 - Mth.lerp(Math.min(.2f, delta - .3f) / .2f, 400, 300);
        }
        if (end <= begin) return;
        glowLine(gui, x + 166 + begin * .25f, x + 166 + end * .25f, y + 173);
        glowLine(gui, x + 166 - end * .25f, x + 166 - begin * .25f, y + 173);
    }
    private static void glowLine(GuiGraphics gui, float x0, float x1, float y) {
        float w = x1 - x0, s = 1.25f, h = .75f, top = y - .25f;
        ACGuiTextures.hudQuad(gui, gui("glow_left.png"), x0 - s, top, s, h, 1, 1, 1, 1, false);
        ACGuiTextures.hudQuad(gui, gui("glow_right.png"), x1, top, s, h, 1, 1, 1, 1, false);
        ACGuiTextures.hudQuad(gui, gui("glow_up.png"), x0, top - s, w, s, 1, 1, 1, 1, false);
        ACGuiTextures.hudQuad(gui, gui("glow_down.png"), x0, top + h, w, s, 1, 1, 1, 1, false);
        ACGuiTextures.hudQuad(gui, gui("glow_ru.png"), x1, top - s, s, s, 1, 1, 1, 1, false);
        ACGuiTextures.hudQuad(gui, gui("glow_rd.png"), x1, top + h, s, s, 1, 1, 1, 1, false);
        ACGuiTextures.hudQuad(gui, gui("glow_lu.png"), x0 - s, top - s, s, s, 1, 1, 1, 1, false);
        ACGuiTextures.hudQuad(gui, gui("glow_ld.png"), x0 - s, top + h, s, s, 1, 1, 1, 1, false);
        rect(gui, x0, y - .625f, w, 1.25f, 0xFFFFFFFF);
    }
    private static void rect(GuiGraphics gui, float x, float y, float w, float h, int color) {
        gui.pose().pushPose(); gui.pose().translate(x, y, 0); gui.pose().scale(w, h, 1);
        gui.fill(0, 0, 1, 1, color); gui.pose().popPose();
    }
    private static String emit(String text, float width, float size) {
        float accumulated = 0;
        int i = 0;
        while (i < text.length() && accumulated < width) {
            accumulated += ACLegacyFont.advance(text.substring(i, i + 1), size, false, false); i++;
        }
        return text.substring(0, i);
    }

    private void drawIntroLogos(GuiGraphics gui, float x, float y, float time, float multiplier, boolean foreground) {
        float a3 = firstOpen ? blend(time, .1f, .3f) : 1;
        float a2 = firstOpen ? blend(time, .65f, .3f) : 1;
        float a1 = firstOpen ? blend(time, 1.3f, .3f) : 1;
        float a0 = firstOpen ? blend(time, 1.75f, .3f) : 1;
        float logo3Y = firstOpen ? Mth.lerp(blend(time, .7f, .4f), 63, -36) : -36;
        if (!foreground) {
            drawTexture(gui, LOGO_1, x + 53.625f, y + 139.75f, 224.75f, 59, 899, 236, a1 * multiplier);
            drawIntroLines(gui, x, y, time);
            drawTexture(gui, LOGO_0, x + 53.625f, y + 9.25f, 224.75f, 137, 899, 548, a0 * multiplier);
        } else {
            drawTexture(gui, LOGO_3, x + 147.375f, y + 91.625f + logo3Y, 37.25f, 37.25f, 149, 149, a3 * multiplier);
            drawTexture(gui, LOGO_2, x + 53.625f, y + 139.75f, 224.75f, 59, 899, 236, a2 * multiplier);
        }
    }

    private void drawSelected(GuiGraphics gui, float mouseX, float mouseY, float x, float y) {
        PageData data = pageData[selectedPage];
        boolean learned = selectedLearned;
        if (learned) drawContent(gui, data, x, y, mouseX, mouseY);

        float showX = x + 173.5f;
        float showY = y;
        drawPreview(gui, mouseX, mouseY, showX, showY);

        float descY = y + 138.5f;
        ACGuiTextures.hudQuad(gui, LEFT_WINDOW, showX, descY, 158.5f, 82, 1, 1, 1, 1, false);
        ACLegacyFont.draw(gui, Component.literal(data.title), showX + 9.25f, descY + 9.25f, 10, -1, ACLegacyFont.LEFT, 0, false);
        data.brief().draw(gui, showX + 9.25f, descY + 21.25f);
    }

    private void drawContent(GuiGraphics gui, PageData data, float x, float y, float mouseX, float mouseY) {
        float textX = x + 2, textY = y + 5, textHeight = 210.5f;
        float max = maxContentScroll();
        scroll = Mth.clamp(scroll, 0, max);
        gui.enableScissor((int) Math.floor(frameLeft() + textX * frameScale()),
                (int) Math.floor(frameTop() + textY * frameScale()),
                (int) Math.ceil(frameLeft() + (textX + 160) * frameScale()),
                (int) Math.ceil(frameTop() + (textY + textHeight) * frameScale()));
        data.content().draw(gui, textX + 3, textY + 3 - scroll);
        gui.disableScissor();
        ACGuiTextures.hudQuad(gui, SCROLL_TRACK, x + 162.5f, y + 2, 9.5f, 216.5f, 1, 1, 1, 1, false);
        float handleY = y + 2 + (max <= 0 ? 0 : scroll / max) * 163;
        ACGuiTextures.hudQuad(gui, SCROLL_HANDLE, x + 162.5f, handleY, 9.5f, 53,
                1, 1, 1, inside(mouseX, mouseY, x + 162.5f, handleY, 9.5f, 53) ? 1 : .8f, false);
    }

    private void drawPreview(GuiGraphics gui, float mouseX, float mouseY, float x, float y) {
        if (previews.isEmpty()) return;
        previewGroup = Mth.clamp(previewGroup, 0, previews.size() - 1);
        PreviewGroup group = previews.get(previewGroup);
        previewMouseX = mouseX;
        previewMouseY = mouseY;
        hoveredStack = ItemStack.EMPTY;
        float areaX = x + 12.25f, areaY = y - 1, areaSize = 134;
        if (!group.views.isEmpty()) renderPreview(gui, group.views.get(group.index), areaX, areaY, areaSize);

        if (group.views.size() > 1) {
            drawTexture(gui, ARROW_LEFT, x + 5, y + 41.75f, 12, 52, 30, 130,
                    inside(mouseX, mouseY, x + 5, y + 41.75f, 12, 52) ? 1 : .8f);
            drawTexture(gui, ARROW_RIGHT, x + 140, y + 41.75f, 12, 52, 30, 130,
                    inside(mouseX, mouseY, x + 140, y + 41.75f, 12, 52) ? 1 : .8f);
        }
        float tagX = x + 12, tagY = y + 120.75f;
        for (int index = 0; index < previews.size(); index++) {
            float tx = tagX + index * 17;
            PreviewGroup value = previews.get(index);
            boolean hover = inside(mouseX, mouseY, tx, tagY, 18, 18);
            ResourceLocation icon = switch (value.definition.getTag()) {
                case CRAFT -> TAG_CRAFT;
                case SMELT -> gui("icons/icon_smelt.png");
                case VIEW -> TAG_VIEW;
            };
            drawTexture(gui, icon, tx, tagY, 18, 18, 64, 64,
                    hover ? 1 : .7f);
            if (hover && !value.definition.getDisplayText().isEmpty())
                ACLegacyFont.draw(gui, Component.literal(value.definition.getDisplayText()),
                        tagX, tagY - 8, 10, -1, ACLegacyFont.LEFT, 0, false);
        }
    }

    private void renderPreview(GuiGraphics gui, ViewGroup.SubView view, float x, float y, float size) {
        if (view instanceof RecipeView recipe) renderRecipe(gui, recipe, x, y, size);
        else renderModelPreview(gui, view, x, y);
    }

    private void renderModelPreview(GuiGraphics gui, ViewGroup.SubView view, float x, float y) {
        if (minecraft == null) return;
        gui.flush();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        gui.pose().pushPose();
        try {
            var window = minecraft.getWindow();
            RenderSystem.setProjectionMatrix(TutorialPreviewTransform.projection(width, height,
                    (float) window.getWidth() / window.getHeight(), frameScale(),
                    frameLeft() + x * frameScale(), frameTop() + y * frameScale()), VertexSorting.DISTANCE_TO_ORIGIN);
            gui.pose().setIdentity();
            if (view instanceof ViewGroup.ItemView item && item.block() && item.stack().getItem() instanceof BlockItem block) {
                gui.pose().mulPose(TutorialPreviewTransform.block(seconds()));
                minecraft.getBlockRenderer().renderSingleBlock(block.getBlock().defaultBlockState(), gui.pose(),
                        gui.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            } else {
                gui.pose().mulPose(TutorialPreviewTransform.model());
                if (view instanceof ViewGroup.IconView icon) {
                    gui.pose().translate(icon.dx(), icon.dy(), 0);
                    gui.pose().scale(icon.scale(), icon.scale(), 1);
                    int color = icon.color();
                    ACGuiTextures.hudQuad(gui, icon.texture(), -.5f, -.5f, 1, 1,
                            (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f,
                            (color >>> 24) / 255f, false);
                } else if (view instanceof ViewGroup.ItemView item) {
                    // Legacy GUI item transform, without GuiGraphics' modern +150 depth offset.
                    gui.pose().translate(.54, .5, 0);
                    gui.pose().scale(-1 / 16f, -1 / 16f, 1);
                    gui.pose().translate(8, 8, 0);
                    gui.pose().scale(16, -16, 16);
                    Lighting.setupForFlatItems();
                    minecraft.getItemRenderer().renderStatic(item.stack(), ItemDisplayContext.GUI,
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, gui.pose(), gui.bufferSource(), minecraft.level, 0);
                }
            }
            gui.flush();
        } finally {
            gui.pose().popPose();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            Lighting.setupFor3DItems();
        }
    }

    private void renderRecipe(GuiGraphics gui, RecipeView recipe, float x, float y, float size) {
        float scale = recipe.type() == ViewGroup.RecipeType.METAL_FORMER ? .5f : .6f;
        int sourceWidth = recipe.type() == ViewGroup.RecipeType.CRAFTING || recipe.type() == ViewGroup.RecipeType.IMAG_FUSOR ? 196 : 192;
        int sourceHeight = recipe.type() == ViewGroup.RecipeType.METAL_FORMER ? 192 : 128;
        float bx = x + (size - sourceWidth * scale) / 2, by = y + (size - sourceHeight * scale) / 2;
        gui.pose().pushPose();
        gui.pose().translate(bx, by, 0);
        gui.pose().scale(scale, scale, 1);
        ResourceLocation texture = switch (recipe.type()) {
            case CRAFTING -> CRAFT_GRID;
            case SMELTING -> SMELTING;
            case METAL_FORMER -> METAL_FORMER;
            case IMAG_FUSOR -> IMAG_FUSOR;
        };
        ACGuiTextures.blit(gui, texture, 0, 0, sourceWidth, sourceHeight, 0, 0, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
        float mx = (previewMouseX - bx) / scale, my = (previewMouseY - by) / scale;
        switch (recipe.type()) {
            case CRAFTING -> {
                ACLegacyFont.draw(gui, Component.translatable("ac.gui.crafttype." + (recipe.width() == 0 ? "shapeless" : "shaped")),
                        68, -28, 24, -1, ACLegacyFont.CENTER, 0, false);
                for (int i = 0; i < recipe.ingredients().size(); i++) {
                    int slot = recipe.gridSlot(i);
                    if (slot >= 9) continue;
                    drawRecipeStack(gui, alternating(recipe, i), 5 + slot % 3 * 43, 5 + slot / 3 * 43, mx, my);
                }
                drawRecipeStack(gui, recipe.output(), 153, 49, mx, my);
            }
            case SMELTING -> {
                drawRecipeInput(gui, recipe, 30, 42.166667f, mx, my);
                drawRecipeStack(gui, recipe.output(), 123.333333f, 42.166667f, mx, my);
            }
            case METAL_FORMER -> {
                var mode = gui("icons/icon_former_" + recipe.mode().name().toLowerCase(java.util.Locale.ROOT) + ".png");
                ACGuiTextures.blit(gui, mode, 82.666667f, 22.7f, 25, 25, 0, 0, 1, 1, 1, 1);
                drawProgress(gui, "metalformer", 77.666667f, 83.5f, 36.666667f, 23.333333f);
                drawRecipeInput(gui, recipe, 7.833333f, 84, mx, my);
                drawRecipeStack(gui, recipe.output(), 151.833333f, 84, mx, my);
            }
            case IMAG_FUSOR -> {
                ACLegacyFont.draw(gui, Component.literal(Integer.toString(recipe.liquid())), 107, 14.5f, 14, -1,
                        ACLegacyFont.CENTER, 0, false);
                drawProgress(gui, "fusor", 66, 68.5f, 66, 18);
                drawRecipeInput(gui, recipe, 19, 61, mx, my);
                drawRecipeStack(gui, recipe.output(), 147, 61, mx, my);
            }
        }
        gui.pose().popPose();
        if (!hoveredStack.isEmpty()) {
            // Original name-only tooltip stays ten screen pixels tall at every GUI scale.
            float fontSize = 10 / frameScale();
            String name = hoveredStack.getHoverName().getString();
            float tx = previewMouseX + 10 * scale, ty = previewMouseY - 17 * scale;
            float textWidth = ACLegacyFont.width(Component.literal(name), fontSize, false);
            gui.pose().pushPose();
            gui.pose().translate(0, 0, 300);
            gui.fill((int) tx - 2, (int) ty - 2, (int) (tx + textWidth) + 2, (int) (ty + fontSize) + 2, 0xCC000000);
            drawSequence(gui, name, tx, ty, fontSize, -1);
            gui.pose().popPose();
        }
    }

    private void drawProgress(GuiGraphics gui, String type, float x, float y, float width, float height) {
        float progress = (float) (.5 * (1 + Math.sin(seconds())));
        ACGuiTextures.blit(gui, gui("progress/progress_" + type + ".png"), x, y, width * progress, height,
                0, 0, progress, 1, 1, 1);
    }

    private void drawRecipeInput(GuiGraphics gui, RecipeView recipe, float x, float y, float mx, float my) {
        if (!recipe.ingredients().isEmpty()) drawRecipeStack(gui, alternating(recipe, 0), x, y, mx, my);
    }

    private ItemStack alternating(RecipeView recipe, int slot) {
        ItemStack[] stacks = recipe.ingredients().get(slot).getItems();
        if (stacks.length == 0) return ItemStack.EMPTY;
        var cycle = stackCycles.get(recipe)[slot];
        double now = seconds();
        if (now - cycle.last > 2) {
            cycle.current = (cycle.current + 1) % stacks.length;
            cycle.last = now;
        }
        return stacks[cycle.current % stacks.length];
    }

    private void drawRecipeStack(GuiGraphics gui, ItemStack stack, float x, float y, float mx, float my) {
        if (stack.isEmpty()) return;
        if (inside(mx, my, x, y, 32, 34)) {
            gui.fill(Math.round(x), Math.round(y), Math.round(x + 32), Math.round(y + 34), 0x26FFFFFF);
            hoveredStack = stack;
        }
        gui.pose().pushPose();
        gui.pose().translate(x, y, 1);
        gui.pose().scale(2, 2, 1);
        gui.renderItem(stack, 0, 0);
        gui.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        float vx = virtualX(mouseX), vy = virtualY(mouseY);
        float partY = (FRAME_HEIGHT - 220.5f) * .5f;
        float intro = (Util.getMillis() - openedAt) / 1000f;
        if ((!firstOpen || intro > 2.4f) && inside(vx, vy, 13.6f, partY + 7, 72, 207)) {
            int page = (int) ((vy - partY - 7) / 12);
            if (page >= 0 && page < pages.length && (page + 1) * 12 <= 207) { selectPage(page); return true; }
        }
        if (selectedPage >= 0) {
            float rightX = 92, showX = rightX + 173.5f, rightY = partY;
            if (!previews.isEmpty()) {
                PreviewGroup group = previews.get(Mth.clamp(previewGroup, 0, previews.size() - 1));
                if (group.views.size() > 1 && inside(vx, vy, showX + 5, rightY + 41.75f, 12, 52)) {
                    group.index = Math.floorMod(group.index - 1, group.views.size()); return true;
                }
                if (group.views.size() > 1 && inside(vx, vy, showX + 140, rightY + 41.75f, 12, 52)) {
                    group.index = (group.index + 1) % group.views.size(); return true;
                }
                for (int index = 0; index < previews.size(); index++) {
                    if (inside(vx, vy, showX + 12 + index * 17, rightY + 120.75f, 18, 18)) {
                        previewGroup = index; return true;
                    }
                }
            }
            float max = maxContentScroll();
            float handleY = rightY + 2 + (max <= 0 ? 0 : scroll / max) * 163;
            if (selectedLearned && inside(vx, vy, rightX + 162.5f, handleY, 9.5f, 53)) {
                draggingScroll = true; scrollDragOffset = vy - handleY; return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScroll && selectedPage >= 0) {
            updateScroll(virtualY(mouseY), (FRAME_HEIGHT - 220.5f) * .5f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScroll) { draggingScroll = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private float maxContentScroll() {
        return selectedPage < 0 || pageData[selectedPage] == null || !selectedLearned ? 0
                : Math.max(0, pageData[selectedPage].content().height() - 200.5f);
    }

    private void updateScroll(float virtualY, float rightY) {
        if (selectedPage < 0) return;
        float progress = Mth.clamp((virtualY - rightY - 2 - scrollDragOffset) / 163f, 0, 1);
        scroll = maxContentScroll() * progress;
    }

    private void drawSequence(GuiGraphics gui, String text, float x, float y, float size, int color) {
        ACLegacyFont.draw(gui, Component.literal(text), x, y, size, color,
                ACLegacyFont.LEFT, 0, false);
    }

    private static void drawTexture(GuiGraphics gui, ResourceLocation texture, float x, float y,
                                    float width, float height, int sourceWidth, int sourceHeight, float alpha) {
        if (texture.equals(LOGO_0) || texture.equals(LOGO_1) || texture.equals(LOGO_2) || texture.equals(LOGO_3))
            ACGuiTextures.hudQuadMipmapped(gui, texture, x, y, width, height, Mth.clamp(alpha, 0, 1));
        else ACGuiTextures.hudQuad(gui, texture, x, y, width, height, 1, 1, 1, Mth.clamp(alpha, 0, 1), false);
    }

    private static float blend(float time, float start, float duration) {
        return Mth.clamp((time - start) / duration, 0, 1);
    }
    private static int withAlpha(int rgb, float alpha) {
        return Mth.clamp(Math.round(alpha * 255), 0, 255) << 24 | rgb & 0xFFFFFF;
    }
    private static boolean inside(float x, float y, float l, float t, float w, float h) {
        return x >= l && x <= l + w && y >= t && y <= t + h;
    }

}
