package cn.academy.crafting;

import cn.academy.registry.ACItems;
import cn.academy.registry.ACRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/** Original two/four-unit crafting yields without making the battery a stackable item. */
public final class EnergyUnitRecipe extends ShapedRecipe {
    private final ItemStack batch;

    public EnergyUnitRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
                            ItemStack result, boolean showNotification) {
        super(group, category, pattern, result, showNotification);
        batch = result;
    }

    @Override public RecipeSerializer<?> getSerializer() { return ACRecipes.ENERGY_UNIT.get(); }

    public static final class Serializer implements RecipeSerializer<EnergyUnitRecipe> {
        // ShapedRecipe's STRICT_CODEC rejects count > max stack size. Empty battery batches
        // use a bounded exception; a single configured unit may carry data components.
        private static final Codec<ItemStack> RESULT_CODEC = ItemStack.CODEC.validate(stack ->
                stack.is(ACItems.ENERGY_UNIT.get()) && stack.getCount() >= 1 && stack.getCount() <= 64
                        && (stack.getCount() == 1 || ItemStack.isSameItemSameComponents(stack, new ItemStack(ACItems.ENERGY_UNIT.get())))
                        ? DataResult.success(stack)
                        : DataResult.error(() -> "Energy unit recipes must yield 1..64 empty units or one unit with components"));
        private static final MapCodec<EnergyUnitRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(EnergyUnitRecipe::getGroup),
                CraftingBookCategory.CODEC.optionalFieldOf("category", CraftingBookCategory.MISC).forGetter(EnergyUnitRecipe::category),
                ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                RESULT_CODEC.fieldOf("result").forGetter(recipe -> recipe.batch),
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(EnergyUnitRecipe::showNotification)
        ).apply(instance, EnergyUnitRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, EnergyUnitRecipe> STREAM_CODEC =
                StreamCodec.of(ShapedRecipe.Serializer.STREAM_CODEC::encode, buffer -> {
                    ShapedRecipe recipe = ShapedRecipe.Serializer.STREAM_CODEC.decode(buffer);
                    return new EnergyUnitRecipe(recipe.getGroup(), recipe.category(), recipe.pattern,
                            recipe.getResultItem(buffer.registryAccess()), recipe.showNotification());
                });

        @Override public MapCodec<EnergyUnitRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, EnergyUnitRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
