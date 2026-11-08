package cn.academy.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Values mirrored from the original default.conf where they still apply. */
public final class ACConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue DAMAGE_SCALE;
    public static final ModConfigSpec.DoubleValue PROGRESSION_SCALE;
    public static final ModConfigSpec.DoubleValue CP_RECOVERY_SCALE;
    public static final ModConfigSpec.DoubleValue OVERLOAD_RECOVERY_SCALE;
    public static final ModConfigSpec.BooleanValue DESTROY_BLOCKS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> WORLDS_DESTROYING_BLOCKS;
    public static final ModConfigSpec.BooleanValue ATTACK_PLAYERS;
    public static final ModConfigSpec.BooleanValue GIVE_MISAKA_TERMINAL;
    public static final ModConfigSpec.BooleanValue GENERATE_ORES;
    public static final ModConfigSpec.BooleanValue GENERATE_PHASE_LIQUID;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> NORMAL_METAL_BLOCKS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> WEAK_METAL_BLOCKS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> METAL_ENTITIES;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> VECTOR_ENTITY_DIFFICULTIES;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> VECTOR_EXCLUDED_ENTITIES;
    public static final ModConfigSpec.DoubleValue CP_CAPACITY_GROWTH;
    public static final ModConfigSpec.DoubleValue OVERLOAD_CAPACITY_GROWTH;
    public static final ModConfigSpec.DoubleValue[] INITIAL_CP = new ModConfigSpec.DoubleValue[6];
    public static final ModConfigSpec.DoubleValue[] INITIAL_OVERLOAD = new ModConfigSpec.DoubleValue[6];
    public static final ModConfigSpec.DoubleValue[] ADDITIONAL_CP = new ModConfigSpec.DoubleValue[6];
    public static final ModConfigSpec.DoubleValue[] ADDITIONAL_OVERLOAD = new ModConfigSpec.DoubleValue[6];
    public record SkillSettings(ModConfigSpec.BooleanValue enabled, ModConfigSpec.BooleanValue destroyBlocks,
                                ModConfigSpec.DoubleValue damageScale, ModConfigSpec.DoubleValue cpConsumeSpeed,
                                ModConfigSpec.DoubleValue overloadConsumeSpeed, ModConfigSpec.DoubleValue expIncreaseSpeed) {}
    private static final java.util.Map<String, SkillSettings> SKILLS = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, ModConfigSpec.DoubleValue> CATEGORY_PROGRESSION = new java.util.LinkedHashMap<>();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("ability");
        builder.push("vectorAffectedEntities");
        VECTOR_ENTITY_DIFFICULTIES=builder.comment("Legacy affected_entities.difficulties, entity ID=difficulty. Original code uses only the first resolvable entity class.")
                .defineListAllowEmpty("difficulties",java.util.List.of("minecraft:arrow=1.0","minecraft:potion=1.4","minecraft:snowball=0.1"),
                        ()->"minecraft:arrow=1.0",value->value instanceof String s && s.contains("="));
        VECTOR_EXCLUDED_ENTITIES=builder.comment("Legacy affected_entities.excluded. living/mob are base classes; IDs match subclasses.")
                .defineListAllowEmpty("excluded",java.util.List.of("item","xp_bottle","living","mob"),
                        ()->"living",value->value instanceof String s && !s.isBlank());
        builder.pop();
        NORMAL_METAL_BLOCKS = builder.comment("Original normalMetalBlocks. Block IDs or #block/item tags; defaults match 1.12.2.")
                .defineListAllowEmpty("normalMetalBlocks", java.util.List.of("minecraft:rail", "minecraft:iron_bars", "minecraft:iron_block",
                        "minecraft:activator_rail", "minecraft:detector_rail", "minecraft:powered_rail", "minecraft:sticky_piston", "minecraft:piston"),
                        () -> "minecraft:iron_block", value -> value instanceof String s && !s.isBlank());
        WEAK_METAL_BLOCKS = builder.comment("Original weakMetalBlocks. Weak entries also belong to isMetalBlock, including below 0.6 proficiency.")
                .defineListAllowEmpty("weakMetalBlocks", java.util.List.of("minecraft:dispenser", "minecraft:hopper", "minecraft:iron_ore"),
                        () -> "minecraft:iron_ore", value -> value instanceof String s && !s.isBlank());
        METAL_ENTITIES = builder.comment("Original metalEntities: registered entity IDs use Java class/subclass matching. academy:mag_hook selects only magnetic hooks.")
                .defineListAllowEmpty("metalEntities", java.util.List.of("minecraft:minecart", "minecraft:chest_minecart", "minecraft:furnace_minecart",
                        "minecraft:tnt_minecart", "minecraft:hopper_minecart", "minecraft:spawner_minecart", "minecraft:command_block_minecart",
                        "academy:mag_hook", "minecraft:iron_golem"), () -> "minecraft:iron_golem", value -> value instanceof String s && !s.isBlank());
        DAMAGE_SCALE = builder.comment("Global skill damage multiplier.")
                .defineInRange("damageScale", 1.0, 0.0, 100.0);
        PROGRESSION_SCALE = builder.comment("Legacy prog_incr_rate: level progression only, not skill proficiency.")
                .defineInRange("progressionScale", 1.0, 0.0, 100.0);
        CP_RECOVERY_SCALE = builder.defineInRange("cpRecoveryScale", 1.0, 0.0, 100.0);
        OVERLOAD_RECOVERY_SCALE = builder.defineInRange("overloadRecoveryScale", 1.0, 0.0, 100.0);
        // Keep the previous port's world-specific entries on disk for an explicit migration.
        // Active policy is now shared across saves in academy-common.toml, like original academy.cfg.
        builder.comment("Legacy port value, no longer active. Use generic.destroyBlocks in academy-common.toml.")
                .define("destroyBlocks", true);
        builder.comment("Legacy port value, no longer active. Use generic.worldsWhitelistedDestroyingBlocks in academy-common.toml.")
                .defineListAllowEmpty("worldsWhitelistedDestroyingBlocks",java.util.List.of(),
                        ()->"minecraft:overworld",value->value instanceof String);
        builder.comment("Legacy port value, no longer active. Use generic.attackPlayer in academy-common.toml.")
                .define("attackPlayers", true);
        DESTROY_BLOCKS = ACGeneralConfig.DESTROY_BLOCKS;
        ATTACK_PLAYERS = ACGeneralConfig.ATTACK_PLAYERS;
        WORLDS_DESTROYING_BLOCKS = ACGeneralConfig.WORLDS_DESTROYING_BLOCKS;
        CP_CAPACITY_GROWTH = builder.comment("Legacy maxcp_incr_rate: capacity gained per CP consumed.")
                .defineInRange("maxCpIncreaseRate", .0025, 0, 100);
        OVERLOAD_CAPACITY_GROWTH = builder.comment("Legacy maxo_incr_rate; each action adds at most 10 overload capacity.")
                .defineInRange("maxOverloadIncreaseRate", .0058, 0, 100);
        double[] cp = {1800, 1800, 2800, 4000, 5800, 8000};
        double[] overload = {100, 100, 150, 240, 350, 500};
        double[] extraCp = {0, 900, 1000, 1500, 1700, 12000};
        double[] extraOverload = {0, 40, 70, 80, 100, 500};
        builder.push("capacity");
        for (int level = 0; level <= 5; level++) {
            builder.push("level" + level);
            INITIAL_CP[level] = builder.defineInRange("initialCp", cp[level], 1, 100000000);
            INITIAL_OVERLOAD[level] = builder.defineInRange("initialOverload", overload[level], 1, 100000000);
            ADDITIONAL_CP[level] = builder.defineInRange("additionalCp", extraCp[level], 0, 100000000);
            ADDITIONAL_OVERLOAD[level] = builder.defineInRange("additionalOverload", extraOverload[level], 0, 100000000);
            builder.pop();
        }
        builder.pop();
        builder.push("skills");
        builder.push("generic");
        for (String name : java.util.List.of("brain_course", "brain_course_advanced", "mind_course")) {
            builder.push(name);
            SKILLS.put("generic." + name, defineSkill(builder));
            builder.pop();
        }
        builder.pop();
        for (var category : cn.academy.ability.AbilityRegistry.categories()) {
            builder.push(category.id());
            CATEGORY_PROGRESSION.put(category.id(), builder.comment("Legacy category common.prog_incr_rate.")
                    .defineInRange("progressionScale", 1.0, 0.0, 100.0));
            for (var skill : category.skills()) {
                SkillSettings generic = SKILLS.get("generic." + skill.name());
                if (generic != null) {
                    SKILLS.put(skill.id(), generic);
                    continue;
                }
                builder.push(skill.name());
                SKILLS.put(skill.id(), defineSkill(builder));
                builder.pop();
            }
            builder.pop();
        }
        builder.pop();
        builder.pop();
        GIVE_MISAKA_TERMINAL = builder.comment("Give each new player the original MisakaCloud tutorial terminal.")
                .define("giveMisakaCloudTerminal", true);
        builder.push("generic");
        GENERATE_ORES = builder.comment("Whether the ores will be generated in overworld.")
                .define("genOres", true);
        GENERATE_PHASE_LIQUID = builder.comment("Whether phase liquid will be generated in overworld.")
                .define("genPhaseLiquid", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ACConfig() {}

    public static void onReload(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> {
            for (var player : server.getPlayerList().getPlayers()) {
                var state = cn.academy.ability.AbilityState.load(player);
                state.recalculateCapacities(false);
                cn.academy.network.ACNetwork.sync(player, state);
            }
        });
    }

    public static float skillDamageScale(cn.academy.ability.AbilitySkill skill) {
        return skillSettings(skill).damageScale().get().floatValue();
    }

    public static SkillSettings skillSettings(cn.academy.ability.AbilitySkill skill) {
        return java.util.Objects.requireNonNull(SKILLS.get(skill.id()), "Unregistered skill: " + skill.id());
    }

    public static float categoryProgression(String category) {
        var value = CATEGORY_PROGRESSION.get(category);
        return value == null ? 1 : value.get().floatValue();
    }

    private static SkillSettings defineSkill(ModConfigSpec.Builder builder) {
        return new SkillSettings(builder.define("enabled", true), builder.define("destroyBlocks", true),
                builder.defineInRange("damageScale", 1.0, 0.0, 100.0),
                builder.defineInRange("cpConsumeSpeed", 1.0, 0.0, 100.0),
                builder.defineInRange("overloadConsumeSpeed", 1.0, 0.0, 100.0),
                builder.defineInRange("expIncreaseSpeed", 1.0, 0.0, 100.0));
    }
}
