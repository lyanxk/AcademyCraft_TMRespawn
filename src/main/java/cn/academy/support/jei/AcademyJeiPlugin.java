package cn.academy.support.jei;

import cn.academy.crafting.MachineRecipes;
import cn.academy.crafting.MachineRecipes.Recipe;
import cn.academy.registry.ACItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/** Optional modern port of original support.jei.SubModule and its two recipe categories. */
@JeiPlugin
public final class AcademyJeiPlugin implements IModPlugin {
    private static final RecipeType<Recipe> FUSOR = RecipeType.create("academy", "imag_fusor", Recipe.class);
    private static final RecipeType<Recipe> FORMER = RecipeType.create("academy", "metal_former", Recipe.class);
    private final List<Recipe> known = new ArrayList<>();
    private List<Recipe> visible = List.of();
    private IJeiRuntime runtime;
    private Level level;
    private long revision = -1;
    private boolean listening;

    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("academy", "machines"); }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new Category(gui, FUSOR, true), new Category(gui, FORMER, false));
    }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ACItems.IMAG_FUSOR.toStack(), FUSOR);
        registration.addRecipeCatalyst(ACItems.METAL_FORMER.toStack(), FORMER);
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        level = Minecraft.getInstance().level;
        known.clear();
        visible = level == null ? List.of() : MachineRecipes.all(level);
        known.addAll(visible);
        revision = MachineRecipes.revision();
        registration.addRecipes(FUSOR, recipes(visible, true));
        registration.addRecipes(FORMER, recipes(visible, false));
    }

    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        this.runtime = runtime;
        // Recipe/tag reloads may occur after JEI starts. Observe the shared recipe cache on the client thread.
        if (!listening) { NeoForge.EVENT_BUS.register(this); listening = true; }
    }

    @Override public void onRuntimeUnavailable() {
        runtime = null;
        level = null;
        visible = List.of();
        known.clear();
        if (listening) { NeoForge.EVENT_BUS.unregister(this); listening = false; }
    }

    @SubscribeEvent public void tick(ClientTickEvent.Post event) {
        Level current = Minecraft.getInstance().level;
        long currentRevision = MachineRecipes.revision();
        if (runtime == null || current == null || current == level && currentRevision == revision) return;
        List<Recipe> updated = MachineRecipes.all(current);
        level = current;
        revision = currentRevision;
        if (sameList(visible, updated)) return;
        var manager = runtime.getRecipeManager();
        manager.hideRecipes(FUSOR, recipes(visible, true));
        manager.hideRecipes(FORMER, recipes(visible, false));
        List<Recipe> added = new ArrayList<>(), next = new ArrayList<>();
        for (Recipe recipe : updated) {
            Recipe existing = known.stream().filter(value -> same(value, recipe)).findFirst().orElse(null);
            if (existing == null) { existing = recipe; known.add(existing); added.add(existing); }
            next.add(existing);
        }
        manager.addRecipes(FUSOR, recipes(added, true));
        manager.addRecipes(FORMER, recipes(added, false));
        visible = List.copyOf(next);
        manager.unhideRecipes(FUSOR, recipes(visible, true));
        manager.unhideRecipes(FORMER, recipes(visible, false));
    }

    private static List<Recipe> recipes(List<Recipe> source, boolean fusor) {
        return source.stream().filter(recipe -> (recipe.kind() == MachineRecipes.Kind.IMAG_FUSOR) == fusor).toList();
    }
    private static boolean same(Recipe left, Recipe right) {
        return left.kind() == right.kind() && left.mode() == right.mode() && left.liquid() == right.liquid()
                && ItemStack.matches(left.input(), right.input()) && ItemStack.matches(left.output(), right.output());
    }
    private static boolean sameList(List<Recipe> left, List<Recipe> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) if (!same(left.get(i), right.get(i))) return false;
        return true;
    }

    private static final class Category implements IRecipeCategory<Recipe> {
        private final RecipeType<Recipe> type;
        private final boolean fusor;
        private final IDrawable background, icon;
        Category(IGuiHelper gui, RecipeType<Recipe> type, boolean fusor) {
            this.type = type; this.fusor = fusor;
            int width = fusor ? 115 : 94, height = fusor ? 66 : 57;
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("academy",
                    "textures/guis/" + (fusor ? "nei_fusor" : "nei_metalformer") + ".png");
            // The old createDrawable overload specified the actual texture dimensions (not a 256px atlas).
            background = gui.drawableBuilder(texture, 0, 0, width, height).setTextureSize(width, height).build();
            icon = gui.createDrawableItemStack(fusor ? ACItems.IMAG_FUSOR.toStack() : ACItems.METAL_FORMER.toStack());
        }
        @Override public RecipeType<Recipe> getRecipeType() { return type; }
        @Override public Component getTitle() { return Component.translatable(fusor ? "tile.ac_imag_fusor.name" : "tile.ac_metal_former.name"); }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
            // JEI 4's init(x,y) used an 18px slot with 1px ingredient padding.
            // Modern slots position the 16px ingredient itself; retain the original hover bounds too.
            builder.addSlot(RecipeIngredientRole.INPUT, 6, fusor ? 37 : 24)
                    .setHoverPadding(1, 1, 1, 1).addItemStack(recipe.input());
            builder.addSlot(RecipeIngredientRole.OUTPUT, fusor ? 94 : 72, fusor ? 37 : 24)
                    .setHoverPadding(1, 1, 1, 1).addItemStack(recipe.output());
        }
    }
}
