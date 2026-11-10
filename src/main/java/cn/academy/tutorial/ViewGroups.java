package cn.academy.tutorial;

import cn.academy.tutorial.client.RecipeHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.List;
import java.util.function.Supplier;

/** Side-neutral preview registration; suppliers defer access until item registries are ready. */
public final class ViewGroups {
    public static ViewGroup drawsBlock(Block block) { return drawsBlock(() -> block.asItem()); }
    public static ViewGroup drawsBlock(Supplier<? extends Item> item) { return itemView(item, true); }
    public static ViewGroup drawsItem(Item item) { return itemView(() -> item, false); }
    public static ViewGroup drawsItem(Supplier<? extends Item> item) { return itemView(item, false); }
    private static ViewGroup itemView(Supplier<? extends Item> item, boolean block) {
        return new ViewGroup() {
            public List<SubView> getSubViews(Level level) { return List.of(new ItemView(new ItemStack(item.get()), block)); }
            public ACTutorial.Tag getTag() { return ACTutorial.Tag.VIEW; }
        };
    }
    public static ViewGroup recipes(Item item) { return recipes(() -> item); }
    public static ViewGroup recipes(Block block) { return recipes(() -> block.asItem()); }
    public static ViewGroup recipes(Supplier<? extends Item> item) {
        return new ViewGroup() {
            public List<SubView> getSubViews(Level level) {
                return level == null ? List.of() : List.copyOf(RecipeHandler.recipeOfStack(level, new ItemStack(item.get())));
            }
            public ACTutorial.Tag getTag() { return ACTutorial.Tag.CRAFT; }
            public String getDisplayText() {
                return Component.translatable("ac.tutorial.crafting", new ItemStack(item.get()).getHoverName()).getString();
            }
        };
    }
    public static ViewGroup displayIcon(String path, float dx, float dy, float scale, int color) {
        // Flattened item/block asset directories are the 1.21 resource equivalents of the old paths.
        String modern = path.replaceFirst("^items/", "item/").replaceFirst("^blocks/", "block/");
        var icon = new ViewGroup.IconView(ResourceLocation.fromNamespaceAndPath("academy", "textures/" + modern + ".png"),
                dx, dy, scale, color);
        return new ViewGroup() {
            public List<SubView> getSubViews(Level level) { return List.of(icon); }
            public ACTutorial.Tag getTag() { return ACTutorial.Tag.VIEW; }
        };
    }
    private ViewGroups() {}
}
