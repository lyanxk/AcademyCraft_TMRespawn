package cn.academy.data;

import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Validates ranges and relationships before a new gameplay snapshot is committed. */
final class GameplayValidation {
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    private static void positive(JsonObject object, String key, boolean zero) {
        double value = object.get(key).getAsDouble();
        require(Double.isFinite(value) && (zero ? value >= 0 : value > 0), "Invalid " + key + ": " + value);
    }
    static void validate(Map<String, JsonObject> documents) {
        var ability = documents.get("ability");
        for (var entry : documents.get("vector_entities").getAsJsonObject("difficulties").entrySet()) {
            ResourceLocation.parse(entry.getKey());
            require(entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()
                    && Double.isFinite(entry.getValue().getAsDouble()) && entry.getValue().getAsDouble() >= 0, "Invalid vector difficulty for " + entry.getKey());
        }
        for (String event : List.of("learn", "use", "crafted", "pickup", "levels"))
            for (var entry : documents.get("advancements").getAsJsonObject(event).entrySet()) {
                require(entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString(), "Invalid advancement mapping " + entry.getKey());
                ResourceLocation.parse(entry.getValue().getAsString());
            }
        for (var category : documents.get("advancements").getAsJsonObject("level_advancements").entrySet())
            for (var entry : category.getValue().getAsJsonObject().entrySet()) {
                require(entry.getKey().matches("[1-5]"), "Advancement levels must be 1..5");
                ResourceLocation.parse(entry.getValue().getAsString());
            }
        for (String field : List.of("damage_scale", "progression_scale", "cp_recovery_scale", "overload_recovery_scale", "max_cp_increase_rate", "max_overload_increase_rate", "max_overload_gain_per_action", "cp_recovery_rate", "overload_recovery_min", "overload_recovery_rate")) positive(ability, field, true);
        positive(ability, "overload_recovery_divisor", false);
        for (String field : List.of("initial_cp", "initial_overload", "additional_cp", "additional_overload")) {
            var array = ability.getAsJsonArray(field); require(array.size() == 6, field + " must contain levels 0..5");
            for (var value : array) require(value.getAsDouble() >= (field.startsWith("initial") ? 1 : 0), "Invalid " + field);
        }
        var categories = documents.get("abilities").getAsJsonArray("categories");
        require(!categories.isEmpty(), "At least one ability category is required");
        Set<String> categoryIds = new HashSet<>();
        double categoryWeight = 0;
        for (var element : categories) {
            var category = element.getAsJsonObject(); String id = category.get("id").getAsString();
            positive(category, "weight", true); categoryWeight += category.get("weight").getAsDouble();
            require(categoryIds.add(id) && id.matches("[a-z0-9_]+"), "Invalid or duplicate category " + id);
            Set<String> skills = new HashSet<>();
            for (var name : category.getAsJsonArray("skills")) require(skills.add(name.getAsString()), "Duplicate skill " + name);
            require(!skills.isEmpty(), "Empty skill category " + id);
            Map<String, List<String>> dependencies = new HashMap<>();
            for (String name : skills) {
                var skill = documents.get("skills/" + id + "/" + name);
                require(skill != null, "Missing skill " + id + "." + name);
                require(skill.get("level").getAsInt() >= 1 && skill.get("level").getAsInt() <= 5, "Skill level must be 1..5");
                for (String field : List.of("cp_cost", "overload", "cooldown_ticks", "damage_scale", "cp_consume_speed", "overload_consume_speed", "exp_increase_speed")) positive(skill, field, true);
                for (String field : List.of("cooldown_curve", "cooldown_enhanced_curve")) {
                    var curve = skill.getAsJsonArray(field); require(curve.size() == 2, field + " requires two endpoints");
                    for (var endpoint : curve) require(endpoint.getAsDouble() >= 0, "Negative cooldown");
                }
                for (var level : skill.getAsJsonArray("enhancements")) require(level.getAsInt() >= 1 && level.getAsInt() <= 5, "Enhancement level must be 1..5");
                List<String> parents = new ArrayList<>();
                for (var req : skill.getAsJsonArray("requirements")) {
                    var requirement = req.getAsJsonObject(); String parent = requirement.get("skill").getAsString();
                    require(skills.contains(parent), "Unknown dependency " + parent + " for " + id + "." + name);
                    double experience = requirement.get("experience").getAsDouble();
                    require(experience >= 0 && experience <= 1, "Dependency experience must be 0..1"); parents.add(parent);
                }
                dependencies.put(name, parents);
            }
            for (String name : skills) visit(name, dependencies, new HashSet<>(), new HashSet<>());
        }
        require(categoryWeight > 0, "At least one ability category needs positive selection weight");
        require(categoryIds.contains(documents.get("abilities").get("default_category").getAsString()), "Unknown default induction factor category");
        documents.forEach((path, object) -> {
            if (path.startsWith("machines/")) {
                positive(object, "capacity", true); positive(object, "transfer", true); positive(object, "generation", true);
                if (object.has("tank_capacity")) positive(object, "tank_capacity", false);
                for (String field : List.of("recipe_check_interval", "operation_interval_ticks", "sync_interval_ticks")) if (object.has(field)) positive(object, field, false);
            }
            if (path.startsWith("items/") && object.has("capacity")) { positive(object, "capacity", false); positive(object, "bandwidth", true); }
            if (path.startsWith("parameters/")) {
                if (object.has("_constraints")) for (var constraint : object.getAsJsonObject("_constraints").entrySet())
                    if (constraint.getValue().getAsString().equals("nonzero")) require(object.get(constraint.getKey()).getAsDouble() != 0, path + ":" + constraint.getKey() + " must be nonzero");
                for (var entry : object.entrySet()) if (!entry.getKey().startsWith("_")) {
                    if (entry.getValue().isJsonArray()) require(!entry.getValue().getAsJsonArray().isEmpty(), "Empty parameter table " + path + ":" + entry.getKey());
                }
            }
        });
        for (String name : List.of("portable", "normal", "advanced")) {
            var developer = documents.get("developers").getAsJsonObject(name);
            positive(developer, "ticks_per_stimulation", false); positive(developer, "energy_per_stimulation", true);
            require(developer.get("maximum_level").getAsInt() >= 1 && developer.get("maximum_level").getAsInt() <= 5, "Invalid developer maximum level");
        }
        var interferer = documents.get("machines/ability_interferer");
        positive(interferer, "minimum_range", true); positive(interferer, "maximum_range", false);
        require(interferer.get("minimum_range").getAsDouble() <= interferer.get("maximum_range").getAsDouble(), "Invalid interferer range");
        positive(documents.get("machines/wind_base"), "height_span", false);
        positive(documents.get("machines/phase_generator"), "energy_per_mb", false);
        positive(documents.get("wireless"), "fe_per_if", false); positive(documents.get("matter"), "capacity", false);
        positive(documents.get("items/windgen_fan"), "durability", false);
        positive(documents.get("tutorial_settings"), "condition_interval_ticks", false);
        positive(documents.get("tutorial_settings"), "gift_interval_ticks", false);
        positive(documents.get("tutorial_settings"), "gift_count", false);
        require(!documents.get("matter").getAsJsonArray("materials").isEmpty(), "At least one matter material is required");
        var launch = documents.get("parameters/ground_shock_launch");
        positive(launch, "gravity", false); positive(launch, "maximum_velocity", false);
        require(launch.get("air_drag").getAsDouble() > 0 && launch.get("air_drag").getAsDouble() < 1, "Launch air drag must be between 0 and 1");
        for (String field : List.of("ground_speed", "water_speed", "air_speed"))
            require(documents.get("parameters/directed_shock_knockback").getAsJsonArray(field).size() == 5, "Knockback tables require five proficiency tiers");
        var charging = documents.get("parameters/charging_context");
        for (String field : List.of("charge", "cp_per_second")) require(charging.getAsJsonArray(field).size() == 6, "Charging table " + field + " requires six endpoints");
        for (String field : List.of("ol_start", "ol_end")) require(charging.getAsJsonArray(field).size() == 5, "Charging overload table requires five levels");
        require(documents.get("parameters/t_p_skill_helper").getAsJsonArray("rates").size() == 3, "Teleport critical multipliers require three tiers");
        var cores = documents.get("wireless").getAsJsonArray("cores"); require(!cores.isEmpty(), "At least one matrix core is required");
        positive(documents.get("wireless"), "candidate_limit", false); positive(documents.get("wireless"), "network_candidate_limit", false);
        for (var entry : cores) for (String key : List.of("range", "bandwidth", "connections")) positive(entry.getAsJsonObject(), key, true);
        Set<String> tracks = new HashSet<>();
        for (var entry : documents.get("media").getAsJsonArray("tracks")) {
            var track = entry.getAsJsonObject(); String id = track.get("id").getAsString();
            require(id.matches("[a-z0-9_-]+") && tracks.add(id), "Invalid or duplicate media id " + id);
            positive(track, "weight", false);
        }
        require(!tracks.isEmpty(), "At least one media track is required");
        Set<String> tutorials = new HashSet<>();
        for (var entry : documents.get("tutorials").getAsJsonArray("tutorials")) require(tutorials.add(entry.getAsJsonObject().get("id").getAsString()), "Duplicate tutorial id");
        for (var entry : documents.get("multiblocks").entrySet()) {
            Set<String> offsets = new HashSet<>();
            for (var value : entry.getValue().getAsJsonArray()) {
                require(value.getAsJsonArray().size() == 3, "Multiblock offsets need three coordinates");
                require(offsets.add(value.toString()), "Duplicate multiblock offset");
                require(value.getAsJsonArray().asList().stream().anyMatch(axis -> axis.getAsInt() != 0), "Multiblock part overlaps its origin");
            }
        }
    }
    private static void visit(String name, Map<String, List<String>> dependencies, Set<String> visiting, Set<String> complete) {
        if (complete.contains(name)) return;
        require(visiting.add(name), "Cyclic skill dependency at " + name);
        for (String parent : dependencies.get(name)) visit(parent, dependencies, visiting, complete);
        visiting.remove(name); complete.add(name);
    }
    private GameplayValidation() { }
}
