package cn.academy.registry;

import cn.academy.AcademyCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ACFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, AcademyCraft.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, AcademyCraft.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> IMAG_PHASE_TYPE = FLUID_TYPES.register("imag_phase", () ->
            new FluidType(FluidType.Properties.create()
                    .descriptionId("block.academy.imag_phase")
                    .density(1).viscosity(6000).temperature(0).lightLevel(8)
                    .fallDistanceModifier(0)
                    .canSwim(true).canDrown(true).canPushEntity(true).supportsBoating(true)
                    .canExtinguish(true).canHydrate(true).canConvertToSource(false)) {
                @Override public boolean canDrownIn(net.minecraft.world.entity.LivingEntity entity) {
                    return !entity.canBreatheUnderwater();
                }
                @Override public boolean canRideVehicleUnder(net.minecraft.world.entity.Entity vehicle,
                                                             net.minecraft.world.entity.Entity rider) {
                    return !vehicle.dismountsUnderwater();
                }
            });

    public static final DeferredHolder<Fluid, FlowingFluid> IMAG_PHASE = FLUIDS.register("imag_phase", () ->
            new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_IMAG_PHASE = FLUIDS.register("flowing_imag_phase", () ->
            new BaseFlowingFluid.Flowing(properties()));

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(IMAG_PHASE_TYPE, IMAG_PHASE, FLOWING_IMAG_PHASE)
                .block(ACBlocks.IMAG_PHASE)
                .slopeFindDistance(1).levelDecreasePerBlock(3).tickRate(30).explosionResistance(0);
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
    }

    public static void registerInteractions() {
        // Register lava solidification through the native fluid-type interaction API.
        FluidInteractionRegistry.addInteraction(NeoForgeMod.LAVA_TYPE.value(),
                new FluidInteractionRegistry.InteractionInformation(IMAG_PHASE_TYPE.get(),
                        fluid -> fluid.isSource() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState()));
    }

    private ACFluids() {}
}
