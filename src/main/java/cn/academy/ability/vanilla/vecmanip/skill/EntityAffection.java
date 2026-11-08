package cn.academy.ability.vanilla.vecmanip.skill;

import cn.academy.config.ACConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import java.util.*;

/** EntityAffection.scala: class/subclass matching, with the original first-valid difficulty rule. */
public final class EntityAffection {
    private static List<? extends String> names=List.of(), exclusions=List.of();
    private static final List<Class<?>> excluded=new ArrayList<>();
    private static Class<?> difficultyClass;
    private static float difficulty=1;
    private static boolean initialized;
    public static final String MARK="ac_vm_deviated";
    /** NaN means excluded. The original .find(...).toList intentionally retains only one difficulty. */
    public static synchronized float difficulty(Entity entity) {
        var configured=ACConfig.VECTOR_ENTITY_DIFFICULTIES.get();var ignored=ACConfig.VECTOR_EXCLUDED_ENTITIES.get();
        if(!initialized || !configured.equals(names) || !ignored.equals(exclusions)) {
            initialized=true;names=List.copyOf(configured);exclusions=List.copyOf(ignored);excluded.clear();difficultyClass=null;
            for(String name:ignored) {
                Class<?> type=name.equalsIgnoreCase("living")?LivingEntity.class:name.equalsIgnoreCase("mob")?Monster.class:resolve(entity,name);
                if(type!=null)excluded.add(type);
            }
            for(String entry:configured) {
                int split=entry.lastIndexOf('=');if(split<1)continue;
                var type=resolve(entity,entry.substring(0,split));if(type==null)continue;
                try {difficulty=Float.parseFloat(entry.substring(split+1));if(!Float.isFinite(difficulty))continue;}
                catch(NumberFormatException ignoredNumber){continue;}
                difficultyClass=type;break;
            }
        }
        for(var type:excluded)if(type.isInstance(entity))return Float.NaN;
        return difficultyClass!=null && difficultyClass.isInstance(entity)?difficulty:1;
    }
    private static Class<?> resolve(Entity entity,String name) {
        String current=switch(name){case "xp_bottle","minecraft:xp_bottle"->"minecraft:experience_bottle";
            case "potion","minecraft:potion"->"minecraft:potion";default->name;};
        var id=ResourceLocation.tryParse(current);
        if(id==null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id))return null;
        var sample=BuiltInRegistries.ENTITY_TYPE.get(id).create(entity.level());
        return sample==null?null:sample.getClass();
    }
    public static boolean marked(Entity entity){
        var tag=entity.getPersistentData();
        if(tag.getBoolean("academy:vector_affected")){tag.putBoolean(MARK,true);tag.remove("academy:vector_affected");}
        return tag.getBoolean(MARK);
    }
    public static void mark(Entity entity){entity.getPersistentData().putBoolean(MARK,true);}
    private EntityAffection(){}
}
