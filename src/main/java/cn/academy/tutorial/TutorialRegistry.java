package cn.academy.tutorial;
import cn.academy.data.GameplayData;
import cn.academy.data.GameplayRules;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;
import java.util.*;

/** Tutorial IDs and event IDs are stable across pack order changes and reloads. */
public final class TutorialRegistry {
    private static Map<String, ACTutorial> definitions() {
        return GameplayData.current().cached("tutorials", () -> {
            Map<String, ACTutorial> result = new LinkedHashMap<>();
            for (var entry : GameplayData.value("tutorials", "tutorials").getAsJsonArray()) {
                var data = entry.getAsJsonObject(); var tutorial = new ACTutorial(data.get("id").getAsString());
                for (var item : data.getAsJsonArray("obtained_items")) tutorial.addCondition(player -> TutorialData.get(player).hasObtained(item.getAsString()));
                for (var previewEntry : data.getAsJsonArray("previews")) {
                    var preview = previewEntry.getAsJsonObject(); String type = preview.get("type").getAsString();
                    switch (type) {
                        case "recipe" -> tutorial.addPreview(ViewGroups.recipes(() -> GameplayRules.item(preview.get("item").getAsString())));
                        case "block" -> tutorial.addPreview(ViewGroups.drawsBlock(() -> GameplayRules.item(preview.get("item").getAsString())));
                        case "item" -> tutorial.addPreview(ViewGroups.drawsItem(() -> GameplayRules.item(preview.get("item").getAsString())));
                        case "icon" -> tutorial.addPreview(ViewGroups.displayIcon(preview.get("path").getAsString(), preview.get("dx").getAsFloat(), preview.get("dy").getAsFloat(), preview.get("scale").getAsFloat(), preview.get("color").getAsInt()));
                        default -> throw new IllegalArgumentException("Unknown tutorial preview " + type);
                    }
                }
                result.put(tutorial.id, tutorial);
            }
            return Collections.unmodifiableMap(result);
        });
    }
    public static ACTutorial find(String id) { return definitions().get(id); }
    public static ACTutorial getTutorial(String id) { return Objects.requireNonNull(find(id), "Unknown tutorial " + id); }
    public static Collection<ACTutorial> enumeration() { return definitions().values(); }
    public static Collection<ACTutorial> getLearned(Player player) { return enumeration().stream().filter(t -> t.isActivated(player)).toList(); }
    public static Pair<List<ACTutorial>, List<ACTutorial>> groupByLearned(Player player) {
        List<ACTutorial> learned = new ArrayList<>(), unlearned = new ArrayList<>();
        for (var tutorial : enumeration()) (tutorial.isActivated(player) ? learned : unlearned).add(tutorial);
        return Pair.of(learned, unlearned);
    }
    private TutorialRegistry() { }
}
