package cn.academy.ability.vanilla.vecmanip.skill;

import cn.academy.data.GameplayData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import java.util.*;

/** EntityAffection.scala: class/subclass matching, with the original first-valid difficulty rule. */
public final class EntityAffection {
    public static final String MARK="ac_vm_deviated";
    public static float difficulty(Entity entity) {
        if (entity.getType().is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath("academy", "vector_excluded_entities")))
                || GameplayData.flag("vector_entities", "exclude_living") && entity instanceof LivingEntity
                || GameplayData.flag("vector_entities", "exclude_monsters") && entity instanceof Monster) return Float.NaN;
        var values = GameplayData.value("vector_entities", "difficulties").getAsJsonObject();
        var value = values.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        return value == null ? GameplayData.decimal("vector_entities", "default_difficulty") : value.getAsFloat();
    }
    public static boolean marked(Entity entity){
        var tag=entity.getPersistentData();
        if(tag.getBoolean("academy:vector_affected")){tag.putBoolean(MARK,true);tag.remove("academy:vector_affected");}
        return tag.getBoolean(MARK);
    }
    public static void mark(Entity entity){entity.getPersistentData().putBoolean(MARK,true);}
    private EntityAffection(){}
}
