package cn.academy.terminal;

import java.util.ArrayList;
import java.util.List;

/** Original installed/preinstalled distinction, with stable identities instead of reflected fields. */
public final class AppRegistry {
    private static final List<App> APPS = new ArrayList<>();
    public static final App SKILL_TREE = register("skill_tree", false);
    public static final App TUTORIAL = register("tutorial", true);
    public static final App FREQUENCY_TRANSMITTER = register("freq_transmitter", false);
    public static final App MEDIA_PLAYER = register("media_player", false);
    public static final App SETTINGS = register("settings", true);
    public static final App ABOUT = register("about", true);

    private static App register(String name, boolean preInstalled) {
        App app = new App(name, preInstalled, APPS.size());
        APPS.add(app);
        return app;
    }

    public static App get(int id) { return APPS.get(id); }
    public static App getByName(String name) {
        return APPS.stream().filter(app -> app.getName().equals(name)).findFirst().orElse(null);
    }
    public static int size() { return APPS.size(); }
    public static List<App> enumeration() { return List.copyOf(APPS); }
    private AppRegistry() {}
}
