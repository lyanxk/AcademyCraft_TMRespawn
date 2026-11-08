package cn.academy.ability;

import cn.academy.AcademyCraft;
import cn.academy.config.ACConfig;
import cn.academy.event.BlockDestroyEvent;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Original world permission and default BlockDestroyEvent veto, with modern dimension-key aliases. */
@EventBusSubscriber(modid=AcademyCraft.MOD_ID)
public final class AbilityPipeline {
    public static boolean canBreakBlock(Level world, AbilitySkill skill) {
        return skill != null && skill.category().equals("meltdowner") || canBreakBlock(world);
    }
    public static boolean canBreakBlock(Level world) {
        var policy=cn.academy.config.ACGeneralConfig.policy(world);
        if(policy.destroyBlocks())return true;
        var values=policy.worldsDestroyingBlocks();var dimension=world.dimension();
        if(values.contains(dimension.location().toString()))return true;
        if(dimension.equals(Level.OVERWORLD))return values.contains("0");
        if(dimension.equals(Level.NETHER))return values.contains("-1") || values.contains("DIM-1");
        if(dimension.equals(Level.END))return values.contains("1") || values.contains("DIM1");
        return values.contains("dimensions/"+dimension.location().getNamespace()+"/"+dimension.location().getPath());
    }
    public static boolean isAllWorldDisableBreakBlock() {
        return isAllWorldDisableBreakBlock(null);
    }
    public static boolean isAllWorldDisableBreakBlock(Level world) {
        var policy=cn.academy.config.ACGeneralConfig.policy(world);
        if(policy.destroyBlocks())return false;
        // Original calls Property.getIntList() on its string-list whitelist, ignoring nonnumeric entries here.
        for(String value:policy.worldsDestroyingBlocks())try{Integer.parseInt(value);return false;}catch(NumberFormatException ignored){}
        return true;
    }
    @SubscribeEvent public static void onBlockDestroy(BlockDestroyEvent event) {
        if(!canBreakBlock(event.world, event.skill))event.setCanceled(true);
    }
    private AbilityPipeline(){}
}
