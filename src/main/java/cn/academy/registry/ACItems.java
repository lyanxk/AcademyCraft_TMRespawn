package cn.academy.registry;

import cn.academy.data.GameplayData;

import cn.academy.AcademyCraft;
import cn.academy.item.EnergyItem;
import cn.academy.item.InductionFactorItem;
import cn.academy.item.MatterUnitItem;
import cn.academy.item.MediaItem;
import cn.academy.item.MatrixCoreItem;
import cn.academy.item.AppInstallerItem;
import cn.academy.item.MagneticCoilItem;
import cn.academy.item.TerminalInstallerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ACItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AcademyCraft.MOD_ID);
    public static final Map<String, DeferredItem<? extends Item>> ALL = new LinkedHashMap<>();

    public static final DeferredItem<AppInstallerItem> APP_FREQ_TRANSMITTER = add("app_freq_transmitter", () ->
            new AppInstallerItem(new Item.Properties(), "freq_transmitter"));
    public static final DeferredItem<AppInstallerItem> APP_MEDIA_PLAYER = add("app_media_player", () ->
            new AppInstallerItem(new Item.Properties(), "media_player"));
    public static final DeferredItem<AppInstallerItem> APP_SKILL_TREE = add("app_skill_tree", () ->
            new AppInstallerItem(new Item.Properties(), "skill_tree"));
    public static final DeferredItem<Item> BRAIN_COMPONENT = simple("brain_component");
    public static final DeferredItem<Item> CALC_CHIP = simple("calc_chip");
    public static final DeferredItem<Item> COIN = simple("coin");
    public static final DeferredItem<Item> CONSTRAINT_INGOT = simple("constraint_ingot");
    public static final DeferredItem<Item> CONSTRAINT_PLATE = simple("constraint_plate");
    public static final DeferredItem<Item> CRYSTAL_LOW = simple("crystal_low");
    public static final DeferredItem<Item> CRYSTAL_NORMAL = simple("crystal_normal");
    public static final DeferredItem<Item> CRYSTAL_PURE = simple("crystal_pure");
    public static final DeferredItem<Item> DATA_CHIP = simple("data_chip");
    public static final DeferredItem<EnergyItem> DEVELOPER_PORTABLE = add("developer_portable", () ->
            new EnergyItem(new Item.Properties(), "developer_portable"));
    public static final DeferredItem<Item> ENERGY_CONVERT_COMPONENT = simple("energy_convert_component");
    public static final DeferredItem<EnergyItem> ENERGY_UNIT = add("energy_unit", () ->
            new EnergyItem(new Item.Properties(), "energy_unit"));
    public static final DeferredItem<Item> IMAG_SILICON_INGOT = simple("imag_silicon_ingot");
    public static final DeferredItem<Item> IMAG_SILICON_PIECE = simple("imag_silicon_piece");
    public static final DeferredItem<InductionFactorItem> INDUCTION_FACTOR = add("induction_factor", () ->
            new InductionFactorItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> INFO_COMPONENT = simple("info_component");
    public static final DeferredItem<Item> LOGO = simple("logo");
    public static final DeferredItem<Item> MAG_HOOK = simple("mag_hook");
    public static final DeferredItem<MagneticCoilItem> MAGNETIC_COIL = add("magnetic_coil", () ->
            new MagneticCoilItem(new Item.Properties()));
    public static final DeferredItem<MatrixCoreItem> MAT_CORE = add("mat_core", () ->
            new MatrixCoreItem(new Item.Properties()));
    public static final DeferredItem<MatterUnitItem> MATTER_UNIT = add("matter_unit", () ->
            new MatterUnitItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<MediaItem> MEDIA_ITEM = add("media_item", () ->
            new MediaItem(new Item.Properties()));
    public static final DeferredItem<Item> NEEDLE = simple("needle");
    public static final DeferredItem<Item> REINFORCED_IRON_PLATE = simple("reinforced_iron_plate");
    public static final DeferredItem<Item> RESO_CRYSTAL = simple("reso_crystal");
    public static final DeferredItem<Item> RESONANCE_COMPONENT = simple("resonance_component");
    public static final DeferredItem<Item> SILBARN = simple("silbarn");
    public static final DeferredItem<TerminalInstallerItem> TERMINAL_INSTALLER = add("terminal_installer", () ->
            new TerminalInstallerItem(new Item.Properties()));
    public static final DeferredItem<Item> TUTORIAL = simple("tutorial");
    public static final DeferredItem<Item> WAFER = simple("wafer");
    public static final DeferredItem<Item> WINDGEN_FAN = add("windgen_fan", () ->
            new Item(new Item.Properties().stacksTo(1).durability(100)) {
                @Override public int getMaxDamage(net.minecraft.world.item.ItemStack stack) { return GameplayData.integer("items/windgen_fan", "durability"); }
            });

    // Every original block keeps the same BlockItem registry id.
    public static final DeferredItem<BlockItem> ABILITY_INTERFERER = block("ability_interferer", ACBlocks.ABILITY_INTERFERER);
    public static final DeferredItem<BlockItem> CAT_ENGINE = block("cat_engine", ACBlocks.CAT_ENGINE);
    public static final DeferredItem<BlockItem> CONSTRAINT_METAL = block("constraint_metal", ACBlocks.CONSTRAINT_METAL);
    public static final DeferredItem<BlockItem> CRYSTAL_ORE = block("crystal_ore", ACBlocks.CRYSTAL_ORE);
    public static final DeferredItem<BlockItem> DEV_ADVANCED = block("dev_advanced", ACBlocks.DEV_ADVANCED);
    public static final DeferredItem<BlockItem> DEV_NORMAL = block("dev_normal", ACBlocks.DEV_NORMAL);
    public static final DeferredItem<BlockItem> IMAG_FUSOR = block("imag_fusor", ACBlocks.IMAG_FUSOR);
    public static final DeferredItem<BlockItem> IMAG_PHASE = block("imag_phase", ACBlocks.IMAG_PHASE);
    public static final DeferredItem<BlockItem> IMAGSIL_ORE = block("imagsil_ore", ACBlocks.IMAGSIL_ORE);
    public static final DeferredItem<BlockItem> MACHINE_FRAME = block("machine_frame", ACBlocks.MACHINE_FRAME);
    public static final DeferredItem<BlockItem> MATRIX = block("matrix", ACBlocks.MATRIX);
    public static final DeferredItem<BlockItem> METAL_FORMER = block("metal_former", ACBlocks.METAL_FORMER);
    public static final DeferredItem<BlockItem> NODE_ADVANCED = block("node_advanced", ACBlocks.NODE_ADVANCED);
    public static final DeferredItem<BlockItem> NODE_BASIC = block("node_basic", ACBlocks.NODE_BASIC);
    public static final DeferredItem<BlockItem> NODE_STANDARD = block("node_standard", ACBlocks.NODE_STANDARD);
    public static final DeferredItem<BlockItem> PHASE_GEN = block("phase_gen", ACBlocks.PHASE_GEN);
    public static final DeferredItem<BlockItem> RESO_ORE = block("reso_ore", ACBlocks.RESO_ORE);
    public static final DeferredItem<BlockItem> SOLAR_GEN = block("solar_gen", ACBlocks.SOLAR_GEN);
    public static final DeferredItem<BlockItem> WINDGEN_BASE = block("windgen_base", ACBlocks.WINDGEN_BASE);
    public static final DeferredItem<BlockItem> WINDGEN_MAIN = block("windgen_main", ACBlocks.WINDGEN_MAIN);
    public static final DeferredItem<BlockItem> WINDGEN_PILLAR = block("windgen_pillar", ACBlocks.WINDGEN_PILLAR);
    public static final DeferredItem<BlockItem> FE_INPUT = block("ac_rf_input", ACBlocks.FE_INPUT);
    public static final DeferredItem<BlockItem> FE_OUTPUT = block("ac_rf_output", ACBlocks.FE_OUTPUT);
    public static final DeferredItem<BlockItem> EU_INPUT = block("eu_input", ACBlocks.EU_INPUT);
    public static final DeferredItem<BlockItem> EU_OUTPUT = block("eu_output", ACBlocks.EU_OUTPUT);

    private static DeferredItem<Item> simple(String name) {
        return simple(name, 64);
    }

    private static DeferredItem<Item> simple(String name, int stackSize) {
        return add(name, () -> new Item(new Item.Properties().stacksTo(stackSize)));
    }

    private static DeferredItem<BlockItem> block(String name, DeferredBlock<? extends net.minecraft.world.level.block.Block> block) {
        return add(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static <T extends Item> DeferredItem<T> add(String name, Supplier<T> supplier) {
        DeferredItem<T> value = ITEMS.register(name, supplier);
        ALL.put(name, value);
        return value;
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private ACItems() {}
}
