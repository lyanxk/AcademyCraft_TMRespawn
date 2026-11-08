package cn.academy.client.render;

import cn.academy.config.ACHudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Original NotifyUI layout, icon scan, font sizes and last-notification-wins timeline. */
public final class ACNotifications {
    private static final ResourceLocation BACK = texture("guis/notification/back");
    private static final ResourceLocation LOGO = texture("guis/edit_preview/notify_logo");
    private static NotificationAnimation animation = new NotificationAnimation();
    private static ResourceLocation icon = LOGO;
    private static Component title = Component.empty(), content = Component.empty();

    public static void reset() { animation = new NotificationAnimation(); }

    public static void showAppInstalled(String appId) {
        show(LOGO, Component.translatable("ac.notification.app_installed"),
                Component.translatable("ac.app." + appId + ".name"));
    }

    public static void showTutorialUpdated(String id) {
        String source = cn.academy.client.screen.TutorialPageResource.load(Minecraft.getInstance(), id);
        show(texture("tutorial/update_notify"), Component.translatable("ac.tutorial.update"),
                Component.literal(cn.academy.client.screen.TutorialPageResource.title(source)));
    }

    public static void show(ResourceLocation image, Component heading, Component body) {
        tick();
        icon = image;
        title = heading;
        content = body;
        animation.show();
    }

    public static void tick() {
        animation.clock(net.minecraft.Util.getMillis(), Minecraft.getInstance().isPaused());
    }

    public static void render(GuiGraphics gui, Minecraft minecraft, float partialTick) {
        tick();
        var frame = animation.frame();
        if (!frame.visible()) return;
        var element = ACHudConfig.Element.NOTIFICATION;
        var bounds = element.bounds(gui.guiWidth(), gui.guiHeight(), ACHudConfig.position(element), false);
        gui.pose().pushPose();
        gui.pose().translate(bounds.x(), bounds.y(), 0);
        gui.pose().scale(bounds.scale(), bounds.scale(), 1);
        draw(gui, icon, title, content, frame);
        gui.pose().popPose();
    }

    /** The preview deliberately uses the exact dummy strings from NotifyUI.init. */
    public static void renderPreview(GuiGraphics gui) {
        draw(gui, LOGO, Component.literal("Some Notification"), Component.literal("blablabla"),
                new NotificationAnimation.Frame(true, 1, 34, 1, 1));
    }

    private static void draw(GuiGraphics gui, ResourceLocation icon, Component title,
                             Component content, NotificationAnimation.Frame frame) {
        gui.setColor(1, 1, 1, 1);
        ACGuiTextures.hudQuad(gui, BACK, 0, 0, 517, 170, 1, 1, 1, frame.backAlpha(), false);
        ACGuiTextures.hudQuad(gui, icon, frame.iconX(), 42, 83, 83, 1, 1, 1, frame.iconAlpha(), false);
        if (frame.textAlpha() >= 0) {
            int color = 0xFFFFFF | (int) (frame.textAlpha() * 255) << 24;
            ACLegacyFont.draw(gui, title, 137, 32, 38, color, ACLegacyFont.LEFT, 0, false);
            ACLegacyFont.draw(gui, content, 137, 81, 54, color, ACLegacyFont.LEFT, 0, false);
        }
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png");
    }
    private ACNotifications() {}
}
