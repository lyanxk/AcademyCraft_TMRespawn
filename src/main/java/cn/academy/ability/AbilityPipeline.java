package cn.academy.ability;

import cn.academy.data.GameplayData;

import cn.academy.AcademyCraft;
import cn.academy.event.BlockDestroyEvent;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Datapack world policy and the per-block destruction veto. */
@EventBusSubscriber(modid=AcademyCraft.MOD_ID)
public final class AbilityPipeline {
    public static boolean canBreakBlock(Level world, AbilitySkill skill) {
        return GameplayData.strings("policy", "block_breaking_exempt_categories").contains(skill == null ? "" : skill.category()) || canBreakBlock(world);
    }
    public static boolean canBreakBlock(Level world) {
        return GameplayData.flag("policy", "destroy_blocks")
                || GameplayData.strings("policy", "worlds_destroying_blocks").contains(world.dimension().location().toString());
    }
    public static boolean isAllWorldDisableBreakBlock() {
        return !GameplayData.flag("policy", "destroy_blocks")
                && GameplayData.strings("policy", "worlds_destroying_blocks").isEmpty();
    }
    @SubscribeEvent public static void onBlockDestroy(BlockDestroyEvent event) {
        if(!canBreakBlock(event.world, event.skill))event.setCanceled(true);
    }
    private AbilityPipeline(){}
}
