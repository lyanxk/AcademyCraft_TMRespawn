package cn.academy.terminal;

import cn.academy.data.GameplayData;

import java.util.ArrayList;
import java.util.List;

/** Original installed/preinstalled distinction, with stable identities instead of reflected fields. */
public final class AppRegistry {
    private static final List<App> APPS = new ArrayList<>();
    public static final App SKILL_TREE = register("skill_tree");
    public static final App TUTORIAL = register("tutorial");
    public static final App FREQUENCY_TRANSMITTER = register("freq_transmitter");
    public static final App MEDIA_PLAYER = register("media_player");
    public static final App SETTINGS = register("settings");
    public static final App ABOUT = register("about");

    private static App register(String name) {
        App app = new App(name, APPS.size());
        APPS.add(app);
        return app;
    }

    public static App get(int id) { return APPS.get(id); }
    public static App getByName(String name) {
        return APPS.stream().filter(app -> app.getName().equals(name)).findFirst().orElse(null);
    }
    public static int size() { return APPS.size(); }
    public static List<App> enumeration() {
        List<App> result = new ArrayList<>();
        for (var entry : GameplayData.value("apps", "apps").getAsJsonArray()) {
            var value = entry.getAsJsonObject(); var app = getByName(value.get("id").getAsString());
            if (app != null && value.get("enabled").getAsBoolean()) result.add(app);
        }
        return List.copyOf(result);
    }
    private AppRegistry() {}
}
