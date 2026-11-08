package cn.academy.registry;

import cn.academy.AcademyCraft;
import cn.academy.block.ACMachineBlock;
import cn.academy.block.ACMultiblockPartBlock;
import cn.academy.block.ACImagPhaseBlock;
import cn.academy.block.MachineKind;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ACBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AcademyCraft.MOD_ID);
    public static final Map<String, DeferredBlock<? extends Block>> ALL = new LinkedHashMap<>();

    public static final DeferredBlock<Block> CONSTRAINT_METAL = ore("constraint_metal", 4.0f);
    public static final DeferredBlock<Block> CRYSTAL_ORE = ore("crystal_ore", 3.0f);
    public static final DeferredBlock<Block> IMAGSIL_ORE = ore("imagsil_ore", 3.75f);
    public static final DeferredBlock<Block> RESO_ORE = ore("reso_ore", 3.0f);
    public static final DeferredBlock<ACImagPhaseBlock> IMAG_PHASE = add("imag_phase", () -> new ACImagPhaseBlock(
            ACFluids.IMAG_PHASE.get(), BlockBehaviour.Properties.of().mapColor(MapColor.WATER)
                    .replaceable().noCollission().strength(0.0f, 0.0f).pushReaction(PushReaction.DESTROY)
                    .liquid().noLootTable().randomTicks().lightLevel(state ->
                            Math.max(0, (int) ((3 - state.getValue(net.minecraft.world.level.block.LiquidBlock.LEVEL)) / 3f * 8)))
                    .sound(SoundType.STONE)));

    public static final DeferredBlock<ACMultiblockPartBlock> MULTIBLOCK_PART = BLOCKS.register("multiblock_part", () ->
            new ACMultiblockPartBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(4, 4)
                    .requiresCorrectToolForDrops().sound(SoundType.STONE)
                    .noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<ACMultiblockPartBlock> MATRIX_PART = BLOCKS.register("matrix_part", () ->
            new ACMultiblockPartBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3, 3)
                    .requiresCorrectToolForDrops().sound(SoundType.STONE).lightLevel(state -> 15)
                    .noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));

    public static final DeferredBlock<Block> MACHINE_FRAME = add("machine_frame", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops()
                    .strength(0.0f, 0.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<ACMachineBlock> NODE_BASIC = machine("node_basic", MachineKind.NODE_BASIC, 2.5f);
    public static final DeferredBlock<ACMachineBlock> NODE_STANDARD = machine("node_standard", MachineKind.NODE_STANDARD, 2.5f);
    public static final DeferredBlock<ACMachineBlock> NODE_ADVANCED = machine("node_advanced", MachineKind.NODE_ADVANCED, 2.5f);
    public static final DeferredBlock<ACMachineBlock> MATRIX = machine("matrix", MachineKind.MATRIX, 3.0f);
    public static final DeferredBlock<ACMachineBlock> CAT_ENGINE = machine("cat_engine", MachineKind.CAT_ENGINE, 0.0f);
    public static final DeferredBlock<ACMachineBlock> SOLAR_GEN = machine("solar_gen", MachineKind.SOLAR_GENERATOR, 1.5f);
    public static final DeferredBlock<ACMachineBlock> PHASE_GEN = machine("phase_gen", MachineKind.PHASE_GENERATOR, 2.5f);
    public static final DeferredBlock<ACMachineBlock> WINDGEN_BASE = machine("windgen_base", MachineKind.WIND_BASE, 4.0f);
    public static final DeferredBlock<ACMachineBlock> WINDGEN_PILLAR = machine("windgen_pillar", MachineKind.WIND_PILLAR, 4.0f);
    public static final DeferredBlock<ACMachineBlock> WINDGEN_MAIN = machine("windgen_main", MachineKind.WIND_GENERATOR, 4.0f);
    public static final DeferredBlock<ACMachineBlock> IMAG_FUSOR = machine("imag_fusor", MachineKind.IMAG_FUSOR, 3.0f);
    public static final DeferredBlock<ACMachineBlock> METAL_FORMER = machine("metal_former", MachineKind.METAL_FORMER, 3.0f);
    public static final DeferredBlock<ACMachineBlock> DEV_NORMAL = machine("dev_normal", MachineKind.DEVELOPER_NORMAL, 4.0f);
    public static final DeferredBlock<ACMachineBlock> DEV_ADVANCED = machine("dev_advanced", MachineKind.DEVELOPER_ADVANCED, 4.0f);
    public static final DeferredBlock<ACMachineBlock> ABILITY_INTERFERER = machine("ability_interferer", MachineKind.ABILITY_INTERFERER, 0.0f);
    public static final DeferredBlock<ACMachineBlock> FE_INPUT = machine("ac_rf_input", MachineKind.FE_INPUT, 2.5f);
    public static final DeferredBlock<ACMachineBlock> FE_OUTPUT = machine("ac_rf_output", MachineKind.FE_OUTPUT, 2.5f);
    public static final DeferredBlock<ACMachineBlock> EU_INPUT = machine("eu_input", MachineKind.EU_INPUT, 2.5f);
    public static final DeferredBlock<ACMachineBlock> EU_OUTPUT = machine("eu_output", MachineKind.EU_OUTPUT, 2.5f);

    private static DeferredBlock<Block> ore(String name, float hardness) {
        // Original BlockOre only awarded XP to its five vanilla ore singletons, not AC subclasses.
        return add(name, () -> new Block(
                BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops()
                        .strength(hardness, hardness).sound(SoundType.STONE)));
    }

    private static DeferredBlock<ACMachineBlock> machine(String name, MachineKind kind, float hardness) {
        return add(name, () -> {
            // Legacy setHardness(h) raises stored resistance to 5h, then its explosion getter divides by 5.
            BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops().strength(hardness, hardness).sound(SoundType.STONE)
                    .lightLevel(state -> kind == MachineKind.MATRIX ? 15
                            : kind == MachineKind.IMAG_FUSOR && state.getValue(ACMachineBlock.WORKING) ? 6 : 0)
                    .pushReaction(PushReaction.BLOCK);
            // BlockMulti and the four standalone TESR blocks all returned isOpaqueCube=false in 1.12.2.
            // Keeping the default full-cube occlusion shape makes the block entity sample darkness at its
            // own position and turns the original OBJ textures almost black.
            if (ACMachineBlock.usesLegacyBlockEntityModel(kind)) properties.noOcclusion();
            return new ACMachineBlock(kind, properties);
        });
    }

    private static <T extends Block> DeferredBlock<T> add(String name, Supplier<T> supplier) {
        DeferredBlock<T> value = BLOCKS.register(name, supplier);
        ALL.put(name, value);
        return value;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }

    private ACBlocks() {}
}
