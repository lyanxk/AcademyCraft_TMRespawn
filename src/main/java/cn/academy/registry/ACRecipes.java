package cn.academy.registry;

import cn.academy.AcademyCraft;
import cn.academy.crafting.EnergyUnitRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ACRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, AcademyCraft.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>, EnergyUnitRecipe.Serializer> ENERGY_UNIT =
            SERIALIZERS.register("energy_unit", EnergyUnitRecipe.Serializer::new);

    public static void register(IEventBus bus) { SERIALIZERS.register(bus); }
    private ACRecipes() {}
}
