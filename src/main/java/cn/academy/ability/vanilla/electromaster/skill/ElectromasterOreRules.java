package cn.academy.ability.vanilla.electromaster.skill;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/** Modern ore tags replace BlockOre/OreDictionary; metadata tags such as ore_bearing_ground are not dictionary entries. */
public final class ElectromasterOreRules {
    public static boolean isOre(BlockState state) {
        if (state.is(Tags.Blocks.ORES)) return true;
        if (state.getTags().anyMatch(tag -> isOreItemTag(tag.location()))) return true;
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (id.endsWith("_ore") || id.startsWith("ore_") || id.contains("_ore_")) return true;
        var item = state.getBlock().asItem();
        return item != Items.AIR && item.builtInRegistryHolder().tags()
                .anyMatch(tag -> isOreItemTag(tag.location()));
    }
    public static boolean isOreItemTag(net.minecraft.resources.ResourceLocation tag) {
        String path = tag.getPath();
        if (path.equals("ores") || path.startsWith("ores/") || path.endsWith("_ores")) return true;
        // Optional bridge for legacy dictionary names which were not conventional oreFoo entries.
        String prefix = "legacy_ore_dictionary/";
        return tag.getNamespace().equals("academy") && path.startsWith(prefix) && path.substring(prefix.length()).contains("ore");
    }
    public static int harvestLevel(BlockState state) {
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return 3;
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) return 2;
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) return 1;
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? 0 : -1;
    }
    private ElectromasterOreRules() {}
}
