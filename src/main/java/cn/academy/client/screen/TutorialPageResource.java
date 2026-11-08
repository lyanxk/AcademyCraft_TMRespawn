package cn.academy.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import java.io.IOException;

/** ACTutorial.getContent/getTitle: selected resource-pack locale, then English. */
public final class TutorialPageResource {
    private static final String UNKNOWN = "![title]\nUNKNOWN\n![brief]\n![content]\n";

    public static String load(Minecraft minecraft, String page) {
        return load(minecraft.getResourceManager(), minecraft.getLanguageManager().getSelected(), page);
    }

    static String load(ResourceProvider resources, String locale, String page) {
        for (String language : java.util.List.of(locale, "en_us")) {
            var path = ResourceLocation.tryBuild("academy", "tutorials/" + language + "/" + page + ".md");
            if (path == null) continue;
            var resource = resources.getResource(path);
            if (resource.isEmpty()) continue;
            try (var reader = resource.get().openAsReader()) {
                var text = new java.io.StringWriter();
                reader.transferTo(text);
                return text.toString();
            } catch (IOException | java.io.UncheckedIOException ignored) { return UNKNOWN; }
        }
        return UNKNOWN;
    }

    public static String title(String source) {
        int start = source.indexOf("![title]"), end = source.indexOf("![brief]");
        return start >= 0 && end >= start + 8 ? source.substring(start + 8, end).trim() : "UNKNOWN";
    }

    private TutorialPageResource() {}
}
