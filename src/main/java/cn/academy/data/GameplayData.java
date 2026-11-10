package cn.academy.data;

import cn.academy.AcademyCraft;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.util.thread.EffectiveSide;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-owned gameplay documents. Each datapack file replaces one document; clients receive the same snapshot. */
public final class GameplayData {
    private static final Gson GSON = new Gson();
    private static final Map<String, JsonObject> DEFAULTS = readDefaults();
    private static volatile Snapshot server = new Snapshot(DEFAULTS);
    private static volatile Snapshot client = server;

    public static final class Snapshot {
        private final Map<String, JsonObject> documents;
        private final Map<String, Object> derived = new HashMap<>();
        private Snapshot(Map<String, JsonObject> documents) { this.documents = Map.copyOf(documents); }
        @SuppressWarnings("unchecked")
        public synchronized <T> T cached(String key, java.util.function.Supplier<T> factory) {
            return (T) derived.computeIfAbsent(key, ignored -> factory.get());
        }
        public JsonObject document(String path) {
            JsonObject value = documents.get(path);
            if (value == null) throw new IllegalArgumentException("Missing gameplay document: " + path);
            return value;
        }
        public Set<String> paths() { return documents.keySet(); }
    }

    private static Map<String, JsonObject> readDefaults() {
        try (var index = GameplayData.class.getResourceAsStream("/assets/academy/gameplay_index.json")) {
            if (index == null) throw new IllegalStateException("Missing gameplay index");
            var paths = JsonParser.parseReader(new InputStreamReader(index, StandardCharsets.UTF_8)).getAsJsonArray();
            Map<String, JsonObject> result = new LinkedHashMap<>();
            for (JsonElement entry : paths) {
                String path = entry.getAsString();
                try (var stream = GameplayData.class.getResourceAsStream("/data/academy/gameplay/" + path + ".json")) {
                    if (stream == null) throw new IllegalStateException("Missing bundled gameplay document " + path);
                    result.put(path, JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
                }
            }
            return Map.copyOf(result);
        } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot load gameplay defaults", exception); }
    }

    public static Snapshot current() { return EffectiveSide.get().isClient() ? client : server; }
    public static JsonObject document(String path) { return current().document(path); }
    public static JsonObject bundled(String path) { return DEFAULTS.get(path); }
    public static JsonElement value(String path, String key) {
        JsonElement value = document(path);
        if (value.getAsJsonObject().has(key)) return value.getAsJsonObject().get(key);
        for (String part : key.split("\\.")) {
            if (!value.isJsonObject() || !value.getAsJsonObject().has(part))
                throw new IllegalArgumentException("Missing gameplay field " + path + ":" + key);
            value = value.getAsJsonObject().get(part);
        }
        return value;
    }
    public static int integer(String path, String key) { return value(path, key).getAsInt(); }
    public static double number(String path, String key) { return value(path, key).getAsDouble(); }
    public static float decimal(String path, String key) { return value(path, key).getAsFloat(); }
    public static boolean flag(String path, String key) { return value(path, key).getAsBoolean(); }
    public static String string(String path, String key) { return value(path, key).getAsString(); }
    public static List<String> strings(String path, String key) {
        List<String> result = new ArrayList<>();
        value(path, key).getAsJsonArray().forEach(entry -> result.add(entry.getAsString()));
        return List.copyOf(result);
    }
    public static double at(String path, String key, int index) {
        var values = value(path, key).getAsJsonArray();
        return values.get(Math.clamp(index, 0, values.size() - 1)).getAsDouble();
    }

    /** Validate the bundled schema before installing anything, so a bad pack never partly updates gameplay. */
    static void validate(JsonElement schema, JsonElement value, String location) {
        if (schema.isJsonObject()) {
            if (!value.isJsonObject()) throw new IllegalArgumentException(location + " must be an object");
            if (location.matches("advancements\\.(learn|use|crafted|pickup|levels|level_advancements)|vector_entities\\.difficulties")) return;
            if (schema.getAsJsonObject().has("_constraints")) {
                for (var constraint : schema.getAsJsonObject().getAsJsonObject("_constraints").entrySet()) {
                    var actual = value.getAsJsonObject().get(constraint.getKey());
                    if (actual != null && actual.getAsDouble() == 0) throw new IllegalArgumentException(location + ":" + constraint.getKey() + " must be nonzero");
                }
            }
            for (var field : schema.getAsJsonObject().entrySet()) {
                if (field.getKey().startsWith("_")) continue;
                JsonElement actual = value.getAsJsonObject().get(field.getKey());
                if (actual == null) throw new IllegalArgumentException("Missing " + location + "." + field.getKey());
                validate(field.getValue(), actual, location + "." + field.getKey());
            }
        } else if (schema.isJsonArray()) {
            if (!value.isJsonArray()) throw new IllegalArgumentException(location + " must be an array");
            if (!schema.getAsJsonArray().isEmpty()) {
                for (var entry : value.getAsJsonArray()) validate(schema.getAsJsonArray().get(0), entry, location + "[]");
            }
        } else if (schema.isJsonPrimitive()) {
            if (!value.isJsonPrimitive()) throw new IllegalArgumentException(location + " must be a primitive");
            var expected = schema.getAsJsonPrimitive(); var actual = value.getAsJsonPrimitive();
            if (expected.isNumber() && (!actual.isNumber() || !Double.isFinite(actual.getAsDouble()))
                    || expected.isBoolean() && !actual.isBoolean() || expected.isString() && !actual.isString())
                throw new IllegalArgumentException("Invalid " + location);
            if (expected.isNumber() && expected.getAsString().matches("-?\\d+")
                    && (actual.getAsDouble() != Math.rint(actual.getAsDouble()) || Math.abs(actual.getAsDouble()) > Integer.MAX_VALUE))
                throw new IllegalArgumentException("Expected an integer at " + location);
        }
    }

    public static String serialized() {
        JsonObject snapshot = new JsonObject();
        server.documents.forEach((path, document) -> {
            JsonObject values = new JsonObject();
            document.entrySet().stream().filter(entry -> !entry.getKey().startsWith("_")).forEach(entry -> values.add(entry.getKey(), entry.getValue()));
            snapshot.add(path, values);
        });
        return GSON.toJson(snapshot);
    }
    public static void receive(String json) {
        Map<String, JsonObject> documents = new LinkedHashMap<>();
        JsonParser.parseString(json).getAsJsonObject().entrySet().forEach(entry -> documents.put(entry.getKey(), entry.getValue().getAsJsonObject()));
        client = new Snapshot(documents);
        cn.academy.crafting.MachineRecipes.invalidate();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new cn.academy.event.GameplayDataReloadedEvent());
    }
    public static void disconnect() { client = new Snapshot(DEFAULTS); }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(GSON, "gameplay"); }
        @Override protected Map<ResourceLocation, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonElement> result = new LinkedHashMap<>();
            for (var entry : manager.listResources("gameplay", id -> id.getPath().endsWith(".json")).entrySet()) {
                var location = entry.getKey();
                if (!location.getNamespace().equals(AcademyCraft.MOD_ID)) continue;
                String path = location.getPath().substring("gameplay/".length(), location.getPath().length() - ".json".length());
                try (var reader = entry.getValue().openAsReader()) {
                    result.put(ResourceLocation.fromNamespaceAndPath(location.getNamespace(), path), JsonParser.parseReader(reader));
                } catch (Exception exception) { throw new IllegalArgumentException("Invalid gameplay JSON " + location, exception); }
            }
            return result;
        }
        @Override protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, JsonObject> documents = new LinkedHashMap<>(DEFAULTS);
            resources.forEach((id, value) -> {
                if (id.getNamespace().equals(AcademyCraft.MOD_ID)) documents.put(id.getPath(), value.getAsJsonObject());
            });
            DEFAULTS.forEach((path, schema) -> validate(schema, documents.get(path), path));
            GameplayValidation.validate(documents);
            GameplayReferences.validate(documents);
            server = new Snapshot(documents);
            cn.academy.crafting.MachineRecipes.invalidate();
            AcademyCraft.LOGGER.info("Loaded {} AcademyCraft gameplay documents", documents.size());
        }
    }
    private GameplayData() { }
}
