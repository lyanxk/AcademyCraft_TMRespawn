package cn.academy.terminal;

import net.minecraft.resources.ResourceLocation;

/** Common application identity; modern client screens are bound separately. */
public final class App {
    private final String name;
    private final boolean preInstalled;
    private final int id;

    App(String name, boolean preInstalled, int id) {
        this.name = name;
        this.preInstalled = preInstalled;
        this.id = id;
    }

    public String getName() { return name; }
    public String getDisplayKey() { return "ac.app." + name + ".name"; }
    public boolean isPreInstalled() { return preInstalled; }
    public int getID() { return id; }
    public ResourceLocation getIcon() {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/apps/" + name + "/icon.png");
    }
}
