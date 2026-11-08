package cn.academy.crafting;

import cn.academy.AcademyCraft;
import cn.academy.registry.ACItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import java.util.*;

/** MFIFRecipes + VanillaCategories recipes, shared by processing and tutorial previews. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MachineRecipes {
    public enum Kind { METAL_FORMER, IMAG_FUSOR }
    public enum Mode { PLATE, INCISE, ETCH, REFINE }
    public record Recipe(Kind kind, Mode mode, ItemStack input, ItemStack output, int liquid) {
        public Recipe {
            input = input.copy();
            output = output.copy();
        }
        public boolean accepts(ItemStack stack) {
            return matchesItem(stack) && stack.getCount() >= input.getCount();
        }
        public boolean matchesItem(ItemStack stack) {
            return !stack.isEmpty() && ItemStack.isSameItem(stack, input) && stack.getDamageValue() == input.getDamageValue();
        }
        public ItemStack result() { return output.copy(); }
    }
    private static final Map<Level, List<Recipe>> CACHE = new WeakHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong REVISION = new java.util.concurrent.atomic.AtomicLong();
    @SubscribeEvent
    public static void serverStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        invalidate();
    }

    public static synchronized void invalidate() { CACHE.clear(); REVISION.incrementAndGet(); }
    public static long revision() { return REVISION.get(); }
    @SubscribeEvent public static void tagsUpdated(TagsUpdatedEvent event) { invalidate(); }

    public static synchronized List<Recipe> all(Level level) {
        return CACHE.computeIfAbsent(level, MachineRecipes::create);
    }
    public static Recipe find(Level level, Kind kind, int mode, ItemStack input) {
        if (level == null || input.isEmpty()) return null;
        return all(level).stream().filter(recipe -> recipe.kind == kind
                && (kind == Kind.IMAG_FUSOR ? recipe.matchesItem(input) : recipe.mode.ordinal() == mode && recipe.accepts(input)))
                .findFirst().orElse(null);
    }
    public static boolean acceptsInput(Level level, Kind kind, ItemStack input) {
        if (level == null || input.isEmpty()) return false;
        // SlotMFItem / SlotCrystal check only the item, across every mode.
        // Damage and input count are checked when selecting the actual recipe.
        return all(level).stream().anyMatch(recipe -> recipe.kind == kind && ItemStack.isSameItem(recipe.input, input));
    }
    private static List<Recipe> create(Level level) {
        List<Recipe> recipes = new ArrayList<>();
        recipes.add(new Recipe(Kind.IMAG_FUSOR, null, ACItems.CRYSTAL_LOW.toStack(), ACItems.CRYSTAL_NORMAL.toStack(), 3000));
        recipes.add(new Recipe(Kind.IMAG_FUSOR, null, ACItems.CRYSTAL_NORMAL.toStack(), ACItems.CRYSTAL_PURE.toStack(), 8000));
        metal(recipes, ACItems.IMAG_SILICON_INGOT.get(), 1, ACItems.WAFER.get(), 2, Mode.INCISE);
        metal(recipes, ACItems.WAFER.get(), 1, ACItems.IMAG_SILICON_PIECE.get(), 4, Mode.INCISE);
        metal(recipes, ACItems.DATA_CHIP.get(), 1, ACItems.CALC_CHIP.get(), 1, Mode.ETCH);
        metal(recipes, Items.IRON_INGOT, 1, ACItems.REINFORCED_IRON_PLATE.get(), 1, Mode.PLATE);
        metal(recipes, ACItems.CONSTRAINT_INGOT.get(), 1, ACItems.CONSTRAINT_PLATE.get(), 1, Mode.PLATE);
        metal(recipes, ACItems.IMAGSIL_ORE.get(), 1, ACItems.IMAG_SILICON_INGOT.get(), 4, Mode.REFINE);
        metal(recipes, ACItems.CONSTRAINT_METAL.get(), 1, ACItems.CONSTRAINT_INGOT.get(), 2, Mode.REFINE);
        metal(recipes, ACItems.RESO_ORE.get(), 1, ACItems.RESO_CRYSTAL.get(), 3, Mode.REFINE);
        metal(recipes, ACItems.CRYSTAL_ORE.get(), 1, ACItems.CRYSTAL_LOW.get(), 4, Mode.REFINE);
        refine(level, recipes, "gold", Items.GOLD_INGOT, 2);
        refine(level, recipes, "iron", Items.IRON_INGOT, 2);
        refine(level, recipes, "emerald", Items.EMERALD, 2);
        refine(level, recipes, "quartz", Items.QUARTZ, 2);
        refine(level, recipes, "diamond", Items.DIAMOND, 2);
        refine(level, recipes, "redstone", Items.REDSTONE_BLOCK, 1);
        refine(level, recipes, "lapis", Items.LAPIS_LAZULI, 12);
        refine(level, recipes, "coal", Items.COAL, 2);
        for (String metal : List.of("copper", "tin", "silver", "lead", "aluminum", "nickel", "platinum", "iridium", "mithril")) {
            var ores = tagged(level, "ores/" + metal);
            var ingots = tagged(level, "ingots/" + metal);
            if (ores.isEmpty() || ingots.isEmpty()) continue;
            var smelting = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                    new SingleRecipeInput(new ItemStack(ores.getFirst())), level);
            if (smelting.isEmpty()) continue;
            int count = smelting.get().value().getResultItem(level.registryAccess()).getCount();
            if (count <= 0) continue;
            int output = count < 32 ? count * 2 : 64;
            for (Item ore : ores) metal(recipes, ore, 1, ingots.getFirst(), output, Mode.REFINE);
        }
        // Original VanillaCategories additions; notably the coin recipe consumes TWO plates.
        metal(recipes, ACItems.REINFORCED_IRON_PLATE.get(), 1, ACItems.NEEDLE.get(), 6, Mode.INCISE);
        metal(recipes, Items.RAIL, 1, ACItems.NEEDLE.get(), 2, Mode.INCISE);
        metal(recipes, ACItems.REINFORCED_IRON_PLATE.get(), 2, ACItems.COIN.get(), 3, Mode.PLATE);
        metal(recipes, ACItems.WAFER.get(), 1, ACItems.SILBARN.get(), 1, Mode.ETCH);
        return List.copyOf(recipes);
    }
    private static List<Item> tagged(Level level, String path) {
        var tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
        return level.registryAccess().registryOrThrow(Registries.ITEM).getTag(tag)
                .map(set -> set.stream().map(net.minecraft.core.Holder::value).toList()).orElse(List.of());
    }
    private static void refine(Level level, List<Recipe> recipes, String ore, Item output, int count) {
        for (Item input : tagged(level, "ores/" + ore)) metal(recipes, input, 1, output, count, Mode.REFINE);
    }
    private static void metal(List<Recipe> recipes, Item input, int amount, Item output, int count, Mode mode) {
        recipes.add(new Recipe(Kind.METAL_FORMER, mode, new ItemStack(input, amount), new ItemStack(output, count), 0));
    }
    private MachineRecipes() {}
}
