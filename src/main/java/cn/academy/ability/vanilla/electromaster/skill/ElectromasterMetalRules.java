package cn.academy.ability.vanilla.electromaster.skill;

import cn.academy.config.ACConfig;
import cn.academy.entity.ACThrownItemEntity;
import cn.academy.registry.ACItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** CatElectromaster's configurable block sets and registered entity CLASS membership. */
public final class ElectromasterMetalRules {
    private static List<? extends String> entityNames = List.of();
    private static List<Predicate<Entity>> entityRules = List.of();
    public static boolean normal(Block block) { return blockMatches(block, ACConfig.NORMAL_METAL_BLOCKS.get()); }
    public static boolean weak(Block block) { return blockMatches(block, ACConfig.WEAK_METAL_BLOCKS.get()); }
    public static boolean metal(Block block) { return normal(block) || weak(block); }
    private static boolean blockMatches(Block block, List<? extends String> names) {
        for (String name : names) {
            if (name.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(name.substring(1));
                if (id != null && (block.defaultBlockState().is(TagKey.create(Registries.BLOCK, id))
                        || block.asItem().builtInRegistryHolder().is(TagKey.create(Registries.ITEM, id)))) return true;
            } else {
                String current = name.equals("golden_rail") || name.equals("minecraft:golden_rail") ? "minecraft:powered_rail" : name;
                ResourceLocation id = ResourceLocation.tryParse(current);
                if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                    if (BuiltInRegistries.BLOCK.get(id) == block) return true;
                } else {
                    // Explicit old OreDictionary-name bridge. Registry names retain first priority.
                    ResourceLocation tag = legacyDictionaryTag(name);
                    if (tag != null && block.asItem().builtInRegistryHolder().is(TagKey.create(Registries.ITEM, tag))) return true;
                }
            }
        }
        return false;
    }
    public static ResourceLocation legacyDictionaryTag(String name) {
        if (name.startsWith("block") && name.length() > 5)
            return ResourceLocation.tryParse("c:storage_blocks/" + snake(name.substring(5)));
        if (name.startsWith("ore") && name.length() > 3)
            return ResourceLocation.tryParse("c:ores/" + snake(name.substring(3)));
        return ResourceLocation.tryParse("academy:legacy_ore_dictionary/" + name.toLowerCase(java.util.Locale.ROOT));
    }
    private static String snake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }
    public static synchronized boolean metallic(Entity entity) {
        var configured = ACConfig.METAL_ENTITIES.get();
        if (!configured.equals(entityNames)) {
            var rules = new ArrayList<Predicate<Entity>>();
            for (String name : configured) {
                if (name.equals("academy:mag_hook") || name.equals("academy:EntityMagHook")) {
                    // The port shares an EntityType with coins; original EntityMagHook did not.
                    rules.add(value -> value instanceof ACThrownItemEntity thrown && thrown.getItem().is(ACItems.MAG_HOOK.get()));
                    continue;
                }
                String current = switch (name) {
                    case "villager_golem", "minecraft:villager_golem" -> "minecraft:iron_golem";
                    case "commandblock_minecart", "minecraft:commandblock_minecart" -> "minecraft:command_block_minecart";
                    default -> name;
                };
                var id = ResourceLocation.tryParse(current);
                if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id))
                    throw new IllegalArgumentException("Unknown ability.metalEntities entry: " + name);
                // Registration exposes factories rather than old EntityList classes. Create one unspawned sample.
                var sample = BuiltInRegistries.ENTITY_TYPE.get(id).create(entity.level());
                if (sample == null) throw new IllegalArgumentException("Entity type has no constructible class: " + name);
                Class<?> type = sample.getClass(); rules.add(type::isInstance);
            }
            entityRules = List.copyOf(rules); entityNames = List.copyOf(configured);
        }
        return entityRules.stream().anyMatch(rule -> rule.test(entity));
    }
    private ElectromasterMetalRules() {}
}
