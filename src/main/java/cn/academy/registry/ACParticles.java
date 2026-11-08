package cn.academy.registry;

import cn.academy.AcademyCraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ACParticles {
    public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(
            Registries.PARTICLE_TYPE, AcademyCraft.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ARC = register("arc");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MELTDOWNER = register("meltdowner");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TELEPORT = register("teleport");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VECTOR = register("vector");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SILBARN_FRAGMENT = register("silbarn_fragment");

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name) {
        return TYPES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus bus) { TYPES.register(bus); }
    private ACParticles() {}
}
