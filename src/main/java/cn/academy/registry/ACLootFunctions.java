package cn.academy.registry;

import cn.academy.AcademyCraft;
import cn.academy.loot.RandomInternalMediaFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ACLootFunctions {
    private static final DeferredRegister<LootItemFunctionType<?>> FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, AcademyCraft.MOD_ID);
    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<RandomInternalMediaFunction>> RANDOM_INTERNAL_MEDIA =
            FUNCTIONS.register("random_internal_media", () -> new LootItemFunctionType<>(RandomInternalMediaFunction.CODEC));

    public static void register(IEventBus bus) { FUNCTIONS.register(bus); }
    private ACLootFunctions() {}
}
