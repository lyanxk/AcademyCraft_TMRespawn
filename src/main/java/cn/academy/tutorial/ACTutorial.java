package cn.academy.tutorial;

import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Original condition/default-installed contract and registered preview groups. */
public final class ACTutorial {
    public enum Tag { CRAFT, SMELT, VIEW }
    public static final boolean SHOW_ALL = false;
    public final String id;
    private Condition condition = player -> true;
    private boolean defaultInstalled = true;
    private final java.util.List<ViewGroup> previews = new java.util.ArrayList<>();

    public ACTutorial(String id) { this.id = id; }

    public ACTutorial addCondition(Condition value) {
        condition = defaultInstalled ? value : condition.or(value);
        defaultInstalled = false;
        return this;
    }

    public boolean isActivated(Player player) {
        return SHOW_ALL || TutorialData.get(player).legacyUnlocked(id) || condition.test(player);
    }

    public boolean isDefaultInstalled() { return defaultInstalled; }

    public ACTutorial addPreview(ViewGroup... groups) { previews.addAll(java.util.Arrays.asList(groups)); return this; }
    public java.util.List<ViewGroup> getPreview() { return java.util.List.copyOf(previews); }

    @OnlyIn(Dist.CLIENT)
    public String getContent() {
        return cn.academy.client.screen.TutorialPageResource.load(net.minecraft.client.Minecraft.getInstance(), id);
    }

    @OnlyIn(Dist.CLIENT)
    public String getTitle() { return cn.academy.client.screen.TutorialPageResource.title(getContent()); }
}
