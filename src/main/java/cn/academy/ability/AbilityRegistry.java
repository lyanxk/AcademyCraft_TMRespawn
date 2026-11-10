package cn.academy.ability;

import cn.academy.data.GameplayData;
import java.util.*;

/** Immutable definitions rebuilt from the active datapack snapshot. Stable saved skill IDs are preserved. */
public final class AbilityRegistry {
    private static Map<String, AbilityCategory> definitions() {
        return GameplayData.current().cached("abilities", () -> {
            Map<String, AbilityCategory> result = new LinkedHashMap<>();
            for (var entry : GameplayData.value("abilities", "categories").getAsJsonArray()) {
                var category = entry.getAsJsonObject(); String id = category.get("id").getAsString();
                List<AbilitySkill> skills = new ArrayList<>();
                for (var nameEntry : category.getAsJsonArray("skills")) {
                    String name = nameEntry.getAsString(); var data = GameplayData.document("skills/" + id + "/" + name);
                    List<AbilitySkill.Requirement> requirements = new ArrayList<>();
                    for (var dependency : data.getAsJsonArray("requirements")) {
                        var req = dependency.getAsJsonObject();
                        requirements.add(new AbilitySkill.Requirement(req.get("skill").getAsString(), req.get("experience").getAsFloat()));
                    }
                    List<AbilitySkill.Enhancement> enhancements = new ArrayList<>();
                    data.getAsJsonArray("enhancements").forEach(level -> enhancements.add(new AbilitySkill.Enhancement(level.getAsInt())));
                    skills.add(new AbilitySkill(id, name, data.get("level").getAsInt(), data.get("cp_cost").getAsFloat(),
                            data.get("overload").getAsFloat(), data.get("cooldown_ticks").getAsInt(),
                            data.get("controllable").getAsBoolean(), requirements, enhancements));
                }
                result.put(id, new AbilityCategory(id, category.get("color").getAsInt(), skills, category.get("progression_scale").getAsFloat()));
            }
            return Collections.unmodifiableMap(result);
        });
    }
    public static AbilityCategory category(String id) { return definitions().get(id); }
    public static AbilitySkill skill(String fullId) {
        int split = fullId.indexOf('.'); if (split < 1) return null;
        var category = category(fullId.substring(0, split));
        return category == null ? null : category.skill(fullId.substring(split + 1));
    }
    public static float progressionScale(String id) {
        var category = category(id); return category == null ? 1 : category.progressionScale();
    }
    public static Collection<AbilityCategory> categories() { return definitions().values(); }
    public static List<String> categoryIds() { return List.copyOf(definitions().keySet()); }
    public static String randomCategory(net.minecraft.util.RandomSource random) {
        var categories = GameplayData.value("abilities", "categories").getAsJsonArray();
        double total = categories.asList().stream().mapToDouble(entry -> entry.getAsJsonObject().get("weight").getAsDouble()).sum();
        double choice = random.nextDouble() * total;
        String selected = "";
        for (var entry : categories) {
            var category = entry.getAsJsonObject(); double weight = category.get("weight").getAsDouble();
            if (weight <= 0) continue;
            selected = category.get("id").getAsString(); choice -= weight;
            if (choice < 0) return selected;
        }
        return selected;
    }
    private AbilityRegistry() { }
}
