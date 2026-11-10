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

    private static final DeferredRegister<net.minecraft.world.item.crafting.RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, AcademyCraft.MOD_ID);
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeType<?>, net.minecraft.world.item.crafting.RecipeType<cn.academy.crafting.MachineRecipes.Recipe>> MACHINE_TYPE =
            TYPES.register("machine", () -> net.minecraft.world.item.crafting.RecipeType.simple(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy", "machine")));
    public static final DeferredHolder<RecipeSerializer<?>, cn.academy.crafting.MachineRecipes.Serializer> MACHINE_SERIALIZER =
            SERIALIZERS.register("machine", cn.academy.crafting.MachineRecipes.Serializer::new);

    public static void register(IEventBus bus) { SERIALIZERS.register(bus); TYPES.register(bus); }
    private ACRecipes() {}
}
