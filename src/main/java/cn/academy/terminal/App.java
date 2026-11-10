package cn.academy.terminal;

import cn.academy.data.GameplayData;

import net.minecraft.resources.ResourceLocation;

/** Common application identity; modern client screens are bound separately. */
public final class App {
    private final String name;
    private final int id;

    App(String name, int id) {
        this.name = name;
        this.id = id;
    }

    public String getName() { return name; }
    public String getDisplayKey() { return "ac.app." + name + ".name"; }
    public boolean isPreInstalled() {
        for (var entry : GameplayData.value("apps", "apps").getAsJsonArray()) {
            var value = entry.getAsJsonObject();
            if (value.get("id").getAsString().equals(name)) return value.get("preinstalled").getAsBoolean();
        }
        return false;
    }
    public int getID() { return id; }
    public ResourceLocation getIcon() {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/apps/" + name + "/icon.png");
    }
}
