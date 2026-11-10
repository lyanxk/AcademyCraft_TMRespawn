package cn.academy.tutorial.client;

import cn.academy.crafting.MachineRecipes;
import cn.academy.tutorial.ViewGroup.RecipeView;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;

/** Original order: crafting, ImagFusor, MetalFormer, furnace. Empty groups stay empty. */
public final class RecipeHandler {
    public static List<RecipeView> recipeOfStack(Level level, ItemStack target) {
        List<RecipeView> crafting = new ArrayList<>(), fusor = new ArrayList<>(), metal = new ArrayList<>(), smelting = new ArrayList<>();
        for (var holder : level.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            // Dynamic/special recipes do not have the static grids supported by the original handler.
            if (!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe)
                    && recipe.getType() != RecipeType.SMELTING) continue;
            ItemStack output = recipe.getResultItem(level.registryAccess());
            if (output.isEmpty() || !ItemStack.isSameItem(output, target)) continue;
            if (recipe instanceof ShapedRecipe shaped) {
                crafting.add(new RecipeView(cn.academy.tutorial.ViewGroup.RecipeType.CRAFTING, output, recipe.getIngredients(),
                        shaped.getWidth(), shaped.getHeight(), null, 0));
            } else if (recipe instanceof ShapelessRecipe) {
                crafting.add(new RecipeView(cn.academy.tutorial.ViewGroup.RecipeType.CRAFTING, output, recipe.getIngredients(),
                        0, (recipe.getIngredients().size() + 2) / 3, null, 0));
            } else if (recipe.getType() == RecipeType.SMELTING) {
                smelting.add(new RecipeView(cn.academy.tutorial.ViewGroup.RecipeType.SMELTING, target, recipe.getIngredients(), 1, 1, null, 0));
            }
        }
        for (var recipe : MachineRecipes.all(level)) {
            if (!ItemStack.isSameItem(recipe.output(), target) || recipe.output().getDamageValue() != target.getDamageValue()) continue;
            boolean isFusor = recipe.kind() == MachineRecipes.Kind.IMAG_FUSOR;
            var type = isFusor ? cn.academy.tutorial.ViewGroup.RecipeType.IMAG_FUSOR : cn.academy.tutorial.ViewGroup.RecipeType.METAL_FORMER;
            (isFusor ? fusor : metal).add(new RecipeView(type, recipe.result(), List.of(recipe.ingredient()),
                    1, 1, recipe.mode(), recipe.liquid()));
        }
        crafting.addAll(fusor);
        crafting.addAll(metal);
        crafting.addAll(smelting);
        return List.copyOf(crafting);
    }
    private RecipeHandler() {}
}
