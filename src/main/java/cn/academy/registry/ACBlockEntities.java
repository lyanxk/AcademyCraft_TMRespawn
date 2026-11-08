package cn.academy.registry;

import cn.academy.AcademyCraft;
import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.block.entity.ACMultiblockPartEntity;
import cn.academy.block.entity.ACImagPhaseBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ACBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AcademyCraft.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ACMultiblockPartEntity>> MULTIBLOCK_PART =
            TYPES.register("multiblock_part", () -> BlockEntityType.Builder.of(ACMultiblockPartEntity::new,
                    ACBlocks.MULTIBLOCK_PART.get(), ACBlocks.MATRIX_PART.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ACImagPhaseBlockEntity>> IMAG_PHASE =
            TYPES.register("imag_phase", () -> BlockEntityType.Builder.of(ACImagPhaseBlockEntity::new,
                    ACBlocks.IMAG_PHASE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ACMachineBlockEntity>> MACHINE =
            TYPES.register("machine", () -> BlockEntityType.Builder.of(ACMachineBlockEntity::create,
                    ACBlocks.NODE_BASIC.get(), ACBlocks.NODE_STANDARD.get(), ACBlocks.NODE_ADVANCED.get(),
                    ACBlocks.MATRIX.get(), ACBlocks.CAT_ENGINE.get(), ACBlocks.SOLAR_GEN.get(),
                    ACBlocks.PHASE_GEN.get(), ACBlocks.WINDGEN_BASE.get(), ACBlocks.WINDGEN_PILLAR.get(),
                    ACBlocks.WINDGEN_MAIN.get(), ACBlocks.IMAG_FUSOR.get(), ACBlocks.METAL_FORMER.get(),
                    ACBlocks.DEV_NORMAL.get(), ACBlocks.DEV_ADVANCED.get(), ACBlocks.ABILITY_INTERFERER.get(),
                    ACBlocks.FE_INPUT.get(), ACBlocks.FE_OUTPUT.get(), ACBlocks.EU_INPUT.get(), ACBlocks.EU_OUTPUT.get()
            ).build(null));

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    private ACBlockEntities() {}
}
