package cn.academy.crafting;

import cn.academy.AcademyCraft;
import cn.academy.registry.ACRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.*;

/** Standard RecipeManager recipes: overrides, tag ingredients and automatic network synchronization. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MachineRecipes {
    public enum Kind { METAL_FORMER, IMAG_FUSOR }
    public enum Mode { PLATE, INCISE, ETCH, REFINE }
    public record Recipe(Kind kind, Mode mode, Ingredient ingredient, int inputCount, ItemStack output,
                         FluidStack fluid, int duration, double energy, int priority, String resultTag, int resultCount)
            implements net.minecraft.world.item.crafting.Recipe<SingleRecipeInput> {
        public Recipe {
            output = output.copy(); fluid = fluid.copy();
            if (output.isEmpty() && resultTag.isBlank()) throw new IllegalArgumentException("A machine recipe needs result or result_tag");
            if (!resultTag.isEmpty()) net.minecraft.resources.ResourceLocation.parse(resultTag);
        }
        public boolean accepts(ItemStack stack) { return matchesItem(stack) && stack.getCount() >= inputCount; }
        public boolean matchesItem(ItemStack stack) { return ingredient.test(stack); }
        public ItemStack input() { return inputs().stream().findFirst().orElse(ItemStack.EMPTY); }
        public List<ItemStack> inputs() {
            return Arrays.stream(ingredient.getItems()).map(stack -> stack.copyWithCount(inputCount)).toList();
        }
        public int liquid() { return fluid.getAmount(); }
        @Override public ItemStack output() { return result(); }
        @Override public FluidStack fluid() { return fluid.copy(); }
        public ItemStack result() {
            if (resultTag.isEmpty()) return output.copy();
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.parse(resultTag)))
                    .flatMap(values -> values.stream().findFirst()).map(item -> new ItemStack(item, resultCount)).orElse(ItemStack.EMPTY);
        }
        @Override public boolean matches(SingleRecipeInput input, Level level) { return accepts(input.item()); }
        @Override public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) { return result(); }
        @Override public boolean canCraftInDimensions(int width, int height) { return true; }
        @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result(); }
        @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, ingredient); }
        @Override public boolean isSpecial() { return true; }
        @Override public RecipeSerializer<?> getSerializer() { return ACRecipes.MACHINE_SERIALIZER.get(); }
        @Override public RecipeType<?> getType() { return ACRecipes.MACHINE_TYPE.get(); }
    }
    public static final class Serializer implements RecipeSerializer<Recipe> {
        public static final MapCodec<Recipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.xmap(value -> Kind.valueOf(value.toUpperCase(Locale.ROOT)), value -> value.name().toLowerCase(Locale.ROOT)).fieldOf("machine").forGetter(Recipe::kind),
                Codec.STRING.xmap(value -> Mode.valueOf(value.toUpperCase(Locale.ROOT)), value -> value.name().toLowerCase(Locale.ROOT)).optionalFieldOf("mode", Mode.PLATE).forGetter(Recipe::mode),
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(Recipe::ingredient),
                Codec.intRange(1, 64).optionalFieldOf("input_count", 1).forGetter(Recipe::inputCount),
                ItemStack.CODEC.optionalFieldOf("result", ItemStack.EMPTY).forGetter(recipe -> recipe.output),
                FluidStack.CODEC.optionalFieldOf("fluid", FluidStack.EMPTY).forGetter(Recipe::fluid),
                Codec.intRange(1, 1_000_000).fieldOf("duration").forGetter(Recipe::duration),
                Codec.doubleRange(0, 1_000_000_000).fieldOf("energy_per_tick").forGetter(Recipe::energy),
                Codec.INT.optionalFieldOf("priority", 0).forGetter(Recipe::priority),
                Codec.STRING.optionalFieldOf("result_tag", "").forGetter(Recipe::resultTag),
                Codec.intRange(1, 64).optionalFieldOf("result_count", 1).forGetter(Recipe::resultCount)
        ).apply(instance, Recipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, Recipe> STREAM = new StreamCodec<>() {
            @Override public Recipe decode(RegistryFriendlyByteBuf buffer) {
                return new Recipe(buffer.readEnum(Kind.class), buffer.readEnum(Mode.class), Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
                        buffer.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                        buffer.readVarInt(), buffer.readDouble(), buffer.readInt(), buffer.readUtf(), buffer.readVarInt());
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Recipe recipe) {
                buffer.writeEnum(recipe.kind()); buffer.writeEnum(recipe.mode()); Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.ingredient());
                buffer.writeVarInt(recipe.inputCount()); ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, recipe.output);
                FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, recipe.fluid()); buffer.writeVarInt(recipe.duration());
                buffer.writeDouble(recipe.energy()); buffer.writeInt(recipe.priority()); buffer.writeUtf(recipe.resultTag()); buffer.writeVarInt(recipe.resultCount());
            }
        };
        @Override public MapCodec<Recipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, Recipe> streamCodec() { return STREAM; }
    }
    private static final java.util.concurrent.atomic.AtomicLong REVISION = new java.util.concurrent.atomic.AtomicLong();
    public static void invalidate() { REVISION.incrementAndGet(); }
    public static long revision() { return REVISION.get(); }
    @SubscribeEvent public static void tagsUpdated(TagsUpdatedEvent event) { invalidate(); }
    public static List<Recipe> all(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ACRecipes.MACHINE_TYPE.get()).stream()
                .sorted(Comparator.<RecipeHolder<Recipe>>comparingInt(holder -> holder.value().priority()).reversed().thenComparing(holder -> holder.id().toString()))
                .map(RecipeHolder::value).filter(recipe -> recipe.ingredient().getItems().length > 0 && !recipe.result().isEmpty()
                        && cn.academy.data.GameplayRules.recipeFluid(recipe, recipe.kind() == Kind.IMAG_FUSOR
                        ? cn.academy.block.MachineKind.IMAG_FUSOR : cn.academy.block.MachineKind.METAL_FORMER)).toList();
    }
    public static Recipe find(Level level, Kind kind, int mode, ItemStack input) {
        if (level == null || input.isEmpty()) return null;
        return all(level).stream().filter(recipe -> recipe.kind() == kind && recipe.accepts(input)
                && (kind == Kind.IMAG_FUSOR || recipe.mode().ordinal() == mode)).findFirst().orElse(null);
    }
    public static boolean acceptsInput(Level level, Kind kind, ItemStack input) {
        return level != null && !input.isEmpty() && all(level).stream().anyMatch(recipe -> recipe.kind() == kind && recipe.matchesItem(input));
    }
    private MachineRecipes() { }
}
