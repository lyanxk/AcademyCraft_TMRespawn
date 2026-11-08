package cn.academy.tutorial;

import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;
import java.util.*;

/** Stable registration order, with the original learned/unlearned grouping. */
public final class TutorialRegistry {
    private static final Map<String, ACTutorial> TUTORIALS = new LinkedHashMap<>();

    public static void addTutorials(ACTutorial... tutorials) {
        for (ACTutorial tutorial : tutorials) {
            if (TUTORIALS.putIfAbsent(tutorial.id, tutorial) != null)
                throw new IllegalArgumentException("Already have a tutorial with this id: " + tutorial.id);
        }
    }
    public static ACTutorial addTutorial(String id) {
        var tutorial = new ACTutorial(id);
        addTutorials(tutorial);
        return tutorial;
    }
    public static void addTutorials(String... ids) { for (String id : ids) addTutorial(id); }
    public static ACTutorial find(String id) { return TUTORIALS.get(id); }
    public static ACTutorial getTutorial(String id) {
        var tutorial = find(id);
        if (tutorial == null) throw new IllegalArgumentException("No such tutorial: " + id);
        return tutorial;
    }
    public static Collection<ACTutorial> enumeration() { return List.copyOf(TUTORIALS.values()); }
    public static Collection<ACTutorial> getLearned(Player player) {
        return TUTORIALS.values().stream().filter(t -> t.isActivated(player)).toList();
    }
    public static Pair<List<ACTutorial>, List<ACTutorial>> groupByLearned(Player player) {
        List<ACTutorial> learned = new ArrayList<>(), unlearned = new ArrayList<>();
        for (var tutorial : TUTORIALS.values()) (tutorial.isActivated(player) ? learned : unlearned).add(tutorial);
        return Pair.of(learned, unlearned);
    }
    private TutorialRegistry() {}
}
