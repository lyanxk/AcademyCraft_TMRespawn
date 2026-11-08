package cn.academy.ability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Code registry for abilities. These are gameplay definitions rather than game registry entries,
 * allowing the original stable string ids to remain directly serializable.
 */
public final class AbilityRegistry {
    private static final Map<String, AbilityCategory> CATEGORIES = new LinkedHashMap<>();
    private static boolean bootstrapped;

    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;

        category("electromaster", 0x1471D0, b -> {
            b.skill("arc_gen", 1, 120, 8, 8);
            b.skill("charging", 1, 80, 4, 10, req("arc_gen", .3f));
            b.skill("mag_movement", 2, 180, 12, 16, req("arc_gen", 0), req("charging", .7f));
            b.skill("mag_manip", 2, 220, 14, 20, req("mag_movement", .5f));
            b.skill("mine_detect", 3, 300, 20, 80, req("mag_manip", 1));
            b.skill("body_intensify", 3, 360, 25, 120, req("arc_gen", 1), req("charging", 1));
            b.skill("thunder_bolt", 4, 520, 42, 50, req("arc_gen", 0), req("charging", .7f));
            b.skill("railgun", 4, 650, 55, 70, req("thunder_bolt", .3f), req("mag_manip", 1));
            b.skill("thunder_clap", 5, 1100, 100, 160, req("thunder_bolt", 1));
        });

        category("meltdowner", 0x7EFF84, b -> {
            b.skill("electron_bomb", 1, 140, 10, 12);
            b.passive("rad_intensify", 1, req("electron_bomb", .5f));
            b.skill("scatter_bomb", 2, 240, 18, 28, req("electron_bomb", .8f));
            b.skill("light_shield", 2, 260, 18, 100, req("electron_bomb", 1));
            b.skill("meltdowner", 3, 420, 32, 35, req("scatter_bomb", .8f), req("light_shield", .8f));
            b.skill("mine_ray_basic", 3, 380, 28, 45, req("meltdowner", .3f));
            b.skill("ray_barrage", 4, 620, 55, 80, req("meltdowner", .5f));
            b.skill("jet_engine", 4, 280, 22, 30, req("meltdowner", 1));
            b.skill("mine_ray_expert", 4, 550, 45, 65, req("mine_ray_basic", .8f));
            b.skill("mine_ray_luck", 5, 900, 80, 100, req("mine_ray_expert", 1));
            b.skill("electron_missile", 5, 1000, 90, 140, req("jet_engine", .3f));
        });

        category("teleporter", 0xA4A4A4, b -> {
            b.skill("threatening_teleport", 1, 100, 8, 12);
            b.passive("dim_folding_theorem", 1, req("threatening_teleport", .2f));
            b.skill("penetrate_teleport", 2, 210, 14, 20, req("threatening_teleport", .5f));
            b.skill("mark_teleport", 2, 180, 12, 15, req("threatening_teleport", .4f));
            b.skill("flesh_ripping", 3, 360, 28, 35, req("mark_teleport", .5f), req("penetrate_teleport", .5f));
            b.skill("location_teleport", 3, 480, 35, 80, req("penetrate_teleport", .8f), req("mark_teleport", .8f));
            b.skill("shift_tp", 4, 600, 50, 70, req("location_teleport", .5f));
            b.passive("space_fluct", 4, req("shift_tp", 0));
            b.skill("flashing", 5, 500, 45, 12, req("shift_tp", .8f));
        });

        category("vecmanip", 0x202020, b -> {
            b.skill("dir_shock", 1, 110, 8, 10);
            b.skill("ground_shock", 1, 160, 12, 22, req("dir_shock", 0));
            b.skill("vec_accel", 2, 180, 13, 50, req("dir_shock", 0));
            b.skill("vec_deviation", 2, 220, 16, 35, req("vec_accel", 0));
            b.skill("dir_blast", 3, 360, 28, 35, req("ground_shock", 0));
            b.skill("storm_wing", 3, 300, 22, 45, req("vec_accel", 0));
            b.skill("blood_retro", 4, 600, 50, 80, req("dir_blast", 0));
            b.skill("vec_reflection", 4, 520, 44, 100, req("vec_deviation", 0));
            b.skill("plasma_cannon", 5, 1100, 100, 160, req("storm_wing", 0));
        });
    }

    private static void category(String id, int color, java.util.function.Consumer<Builder> factory) {
        Builder builder = new Builder(id);
        factory.accept(builder);
        builder.genericPassives();
        CATEGORIES.put(id, new AbilityCategory(id, color, builder.skills));
    }

    private static AbilitySkill.Requirement req(String skill, float experience) {
        return new AbilitySkill.Requirement(skill, experience);
    }

    public static AbilityCategory category(String id) {
        bootstrap();
        return CATEGORIES.get(id);
    }

    public static AbilitySkill skill(String fullId) {
        bootstrap();
        int split = fullId.indexOf('.');
        if (split < 1) return null;
        AbilityCategory category = CATEGORIES.get(fullId.substring(0, split));
        return category == null ? null : category.skill(fullId.substring(split + 1));
    }

    public static Collection<AbilityCategory> categories() {
        bootstrap();
        return Collections.unmodifiableCollection(CATEGORIES.values());
    }

    public static List<String> categoryIds() {
        bootstrap();
        return List.copyOf(CATEGORIES.keySet());
    }

    private static final class Builder {
        private final String category;
        private final List<AbilitySkill> skills = new ArrayList<>();

        private Builder(String category) {
            this.category = category;
        }

        private void skill(String name, int level, float cp, float overload, int cooldown,
                           AbilitySkill.Requirement... requirements) {
            skills.add(new AbilitySkill(category, name, level, cp, overload, cooldown, true, List.of(requirements)));
        }

        private void passive(String name, int level, AbilitySkill.Requirement... requirements) {
            skills.add(new AbilitySkill(category, name, level, 0, 0, 0, false, List.of(requirements)));
        }

        private void genericPassives() {
            passive("brain_course", 3);
            passive("brain_course_advanced", 4, req("brain_course", 0));
            passive("mind_course", 5, req("brain_course_advanced", 0));
        }
    }

    private AbilityRegistry() {}
}
