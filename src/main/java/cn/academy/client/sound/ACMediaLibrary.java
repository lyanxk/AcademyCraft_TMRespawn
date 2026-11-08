package cn.academy.client.sound;

import cn.academy.AcademyCraft;
import cn.academy.item.MediaItem;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jcraft.jorbis.VorbisFile;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** MediaManager's local acmedia library, using the same bundled JOrbis duration reader as 1.12.2. */
public final class ACMediaLibrary {
    public record Track(String id, float lengthSeconds, boolean external, Path source, ResourceLocation cover) {
        public Component name() {
            return external ? Component.literal(externalText(id, "name"))
                    : Component.translatable("ac.media." + id + ".name");
        }
        public Component description() {
            return external ? Component.literal(externalText(id, "desc"))
                    : Component.translatable("ac.media." + id + ".desc");
        }
        public String displayLength() { return ACMediaPlayer.formatTime(lengthSeconds); }
        public void rename(String name) { if (external) setExternalText(id, "name", name); }
        public void describe(String description) { if (external) setExternalText(id, "desc", description); }
    }

    private static final ResourceLocation MISSING_COVER = resource("textures/guis/icons/icon_nomedia.png");
    private static List<Track> tracks = List.of();
    private static final List<ResourceLocation> dynamicCovers = new ArrayList<>();
    private static boolean initialized;
    private static JsonObject settings = new JsonObject();
    private static Path settingsFile;

    public static List<Track> tracks() {
        if (!initialized) reload(Minecraft.getInstance().getResourceManager());
        return tracks;
    }

    public static void reload(ResourceManager resources) {
        var minecraft = Minecraft.getInstance();
        Path root = minecraft.gameDirectory.toPath().resolve("acmedia");
        if (!initialized) {
            settingsFile = minecraft.gameDirectory.toPath().resolve("config/academy-media.json");
            if (Files.isRegularFile(settingsFile)) try (var reader = Files.newBufferedReader(settingsFile, StandardCharsets.UTF_8)) {
                settings = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception exception) {
                AcademyCraft.LOGGER.warn("Cannot read media settings {}", settingsFile, exception);
            }
        }
        for (var cover : dynamicCovers) minecraft.getTextureManager().release(cover);
        dynamicCovers.clear();
        var loaded = new ArrayList<Track>();
        try {
            Files.createDirectories(root.resolve("source"));
            Files.createDirectories(root.resolve("cover"));
            if (!initialized) copy(resources, "media/readme_template.txt", root.resolve("README.txt"));
            for (String id : MediaItem.TRACKS) {
                Path file = root.resolve(id + ".ogg");
                try {
                    if (!initialized) copy(resources, "media/source/" + id + ".ogg", file);
                    loaded.add(new Track(id, length(file), false, file, resource("media/cover/" + id + ".png")));
                } catch (Exception exception) {
                    AcademyCraft.LOGGER.warn("Cannot load internal media {}", id, exception);
                }
            }
            // Like File.listFiles in MediaManager, retain filesystem enumeration order for external songs.
            try (var files = Files.list(root.resolve("source"))) {
                for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".ogg")).toList()) {
                    String filename = file.getFileName().toString();
                    String id = filename.substring(0, filename.indexOf('.'));
                    try {
                        float seconds = length(file);
                        ResourceLocation cover = MISSING_COVER;
                        Path image = root.resolve("cover").resolve(id + ".png");
                        if (Files.isRegularFile(image)) try (var input = Files.newInputStream(image)) {
                            // Resource identifiers cannot contain arbitrary local filenames in modern Minecraft.
                            var key = resource("media/external/" + UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)));
                            minecraft.getTextureManager().register(key, new DynamicTexture(NativeImage.read(input)));
                            dynamicCovers.add(key);
                            cover = key;
                        } catch (IOException exception) {
                            AcademyCraft.LOGGER.warn("Cannot load media cover {}", image, exception);
                        }
                        loaded.add(new Track(id, seconds, true, file, cover));
                    } catch (Exception exception) {
                        AcademyCraft.LOGGER.warn("Cannot load external Ogg media {}", file, exception);
                    }
                }
            }
        } catch (IOException exception) {
            AcademyCraft.LOGGER.error("Cannot initialize media directory {}", root, exception);
        }
        tracks = List.copyOf(loaded);
        initialized = true;
    }

    private static float length(Path path) throws Exception {
        VorbisFile file = new VorbisFile(path.toString());
        try {
            float length = file.time_total(-1);
            if (!Float.isFinite(length) || length < 0) throw new IOException("Invalid Vorbis duration");
            return length;
        } finally { file.close(); }
    }

    private static void copy(ResourceManager resources, String resource, Path file) throws IOException {
        try (var input = resources.open(resource(resource))) {
            Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static float volume() {
        if (!initialized) tracks();
        try {
            float value = settings.has("volume") ? settings.get("volume").getAsFloat() : 1;
            return Float.isFinite(value) ? net.minecraft.util.Mth.clamp(value, 0, 1) : 1;
        }
        catch (RuntimeException exception) { return 1; }
    }
    public static void volume(float volume) {
        if (!initialized) tracks();
        settings.addProperty("volume", volume);
        save();
    }
    private static String externalText(String id, String field) {
        try {
            var media = settings.getAsJsonObject("media");
            return media != null && media.has(id + "_" + field) ? media.get(id + "_" + field).getAsString() : id;
        } catch (RuntimeException exception) { return id; }
    }
    private static void setExternalText(String id, String field, String value) {
        JsonObject media;
        if (!settings.has("media") || !settings.get("media").isJsonObject()) {
            media = new JsonObject(); settings.add("media", media);
        } else media = settings.getAsJsonObject("media");
        media.addProperty(id + "_" + field, value);
        save();
    }
    private static void save() {
        if (settingsFile == null) return;
        try {
            Files.createDirectories(settingsFile.getParent());
            Path temporary = settingsFile.resolveSibling(settingsFile.getFileName() + ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(settings), StandardCharsets.UTF_8);
            Files.move(temporary, settingsFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            AcademyCraft.LOGGER.warn("Cannot save media settings {}", settingsFile, exception);
        }
    }
    private static ResourceLocation resource(String path) { return ResourceLocation.fromNamespaceAndPath("academy", path); }
    private ACMediaLibrary() {}
}
