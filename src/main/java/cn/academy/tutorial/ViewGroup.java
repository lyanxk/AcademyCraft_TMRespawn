package cn.academy.tutorial;

import cn.academy.crafting.MachineRecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import java.util.List;

/** Modern view descriptions replace CGui Widgets while preserving groups, tags and subview order. */
public interface ViewGroup {
    interface SubView {}
    record ItemView(ItemStack stack, boolean block) implements SubView {}
    record IconView(ResourceLocation texture, float dx, float dy, float scale, int color) implements SubView {}
    enum RecipeType { CRAFTING, IMAG_FUSOR, METAL_FORMER, SMELTING }
    record RecipeView(RecipeType type, ItemStack output, List<Ingredient> ingredients, int width, int height,
                      MachineRecipes.Mode mode, int liquid) implements SubView {
        public RecipeView { output = output.copy(); ingredients = List.copyOf(ingredients); }
        public int gridSlot(int index) { return width == 0 ? index : index % width + index / width * 3; }
    }
    List<SubView> getSubViews(Level level);
    ACTutorial.Tag getTag();
    default String getDisplayText() { return ""; }
}
