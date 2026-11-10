package cn.academy.ability.vanilla.electromaster.skill;
import cn.academy.entity.ACThrownItemEntity;
import cn.academy.registry.ACItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
public final class ElectromasterMetalRules {
    private static TagKey<Block> blocks(String name) { return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("academy", name)); }
    public static boolean normal(Block block) { return block.defaultBlockState().is(blocks("normal_metal_blocks")); }
    public static boolean weak(Block block) { return block.defaultBlockState().is(blocks("weak_metal_blocks")); }
    public static boolean metal(Block block) { return normal(block) || weak(block); }
    public static boolean metallic(Entity entity) {
        return entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("academy", "metal_entities")))
                || entity instanceof ACThrownItemEntity thrown && thrown.getItem().is(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("academy", "magnetic_projectiles")));
    }
    private ElectromasterMetalRules() { }
}
