package cn.academy.ability.vanilla.electromaster.skill;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/** Modern ore tags replace BlockOre/OreDictionary; metadata tags such as ore_bearing_ground are not dictionary entries. */
public final class ElectromasterOreRules {
    public static boolean isOre(BlockState state) {
        return state.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy", "detectable_ores")));
    }
    public static boolean isOreItemTag(net.minecraft.resources.ResourceLocation tag) {
        return tag.getNamespace().equals("c") && (tag.getPath().equals("ores") || tag.getPath().startsWith("ores/"));
    }

    public static int harvestLevel(BlockState state) {
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return 3;
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) return 2;
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) return 1;
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? 0 : -1;
    }
    private ElectromasterOreRules() {}
}
