package cn.academy.data;

import com.google.gson.JsonObject;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Registry references are checked after registration, before committing a server reload. */
final class GameplayReferences {
    private static void reference(Registry<?> registry, String id, String location, boolean tags) {
        if (tags && id.startsWith("#")) { ResourceLocation.parse(id.substring(1)); return; }
        var key = ResourceLocation.parse(id);
        if (!registry.containsKey(key)) throw new IllegalArgumentException("Unknown registry entry " + id + " at " + location);
    }
    static void validate(Map<String, JsonObject> documents) {
        for (var categoryEntry : documents.get("abilities").getAsJsonArray("categories")) {
            var category = categoryEntry.getAsJsonObject(); String id = category.get("id").getAsString();
            for (var skill : category.getAsJsonArray("skills")) {
                String path = "skills/" + id + "/" + skill.getAsString();
                if (GameplayData.bundled(path) == null) throw new IllegalArgumentException("Unknown skill implementation " + path);
            }
        }
        for (var element : documents.get("apps").getAsJsonArray("apps")) {
            String id = element.getAsJsonObject().get("id").getAsString();
            if (cn.academy.terminal.AppRegistry.getByName(id) == null) throw new IllegalArgumentException("Unknown app implementation " + id);
        }
        for (var entry : documents.entrySet()) if (entry.getKey().startsWith("machines/") && entry.getValue().has("fluid"))
            reference(BuiltInRegistries.FLUID, entry.getValue().get("fluid").getAsString(), entry.getKey(), false);
        var wireless = documents.get("wireless");
        for (String field : java.util.List.of("matrix_material", "matrix_core")) reference(BuiltInRegistries.ITEM, wireless.get(field).getAsString(), "wireless." + field, false);
        reference(BuiltInRegistries.ITEM, documents.get("developers").getAsJsonObject("reset").get("item").getAsString(), "developers.reset.item", false);
        reference(BuiltInRegistries.ITEM, documents.get("tutorial_settings").get("gift_item").getAsString(), "tutorial_settings.gift_item", false);
        for (var element : documents.get("matter").getAsJsonArray("materials")) {
            var material = element.getAsJsonObject();
            reference(BuiltInRegistries.BLOCK, material.get("block").getAsString(), "matter.materials.block", false);
            reference(BuiltInRegistries.FLUID, material.get("fluid").getAsString(), "matter.materials.fluid", false);
        }
        for (var element : documents.get("fluid_interactions").getAsJsonArray("reactions")) {
            var reaction = element.getAsJsonObject();
            for (String key : java.util.List.of("source", "neighbor")) reference(BuiltInRegistries.FLUID, reaction.get(key).getAsString(), "fluid_interactions." + key, false);
            for (String key : java.util.List.of("source_result", "flowing_result")) reference(BuiltInRegistries.BLOCK, reaction.get(key).getAsString(), "fluid_interactions." + key, false);
        }
        for (var element : documents.get("tutorials").getAsJsonArray("tutorials")) {
            var tutorial = element.getAsJsonObject();
            for (var item : tutorial.getAsJsonArray("obtained_items")) reference(BuiltInRegistries.ITEM, item.getAsString(), "tutorials.obtained_items", true);
            for (var elementPreview : tutorial.getAsJsonArray("previews")) {
                var preview = elementPreview.getAsJsonObject();
                if (preview.has("item")) reference(BuiltInRegistries.ITEM, preview.get("item").getAsString(), "tutorials.previews.item", false);
            }
        }
    }
    private GameplayReferences() { }
}
