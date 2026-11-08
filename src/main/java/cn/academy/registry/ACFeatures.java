package cn.academy.registry;

import cn.academy.AcademyCraft;
import cn.academy.worldgen.ACWorldGen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ACFeatures {
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(
            Registries.FEATURE, AcademyCraft.MOD_ID);

    static {
        FEATURES.register("worldgen", ACWorldGen::new);
    }

    public static void register(IEventBus bus) { FEATURES.register(bus); }
    private ACFeatures() {}
}
