package cn.academy.advancement;

import cn.academy.AcademyCraft;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ACAdvancements {
    public static final List<String> TRIGGER_NAMES = List.of(
            "ac_developer", "ac_exp_full", "ac_learning_skill", "ac_level_3", "ac_level_5",
            "ac_matrix", "ac_node", "ac_overload", "convert_category", "dev_category",
            "getting_factor", "getting_phase", "open_misaka_cloud", "phase_generator", "terminal_installed");
    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, AcademyCraft.MOD_ID);
    static { TRIGGER_NAMES.forEach(name -> TRIGGERS.register(name, ACTrigger::new)); }

    public static void register(IEventBus bus) { TRIGGERS.register(bus); }

    public static boolean trigger(ServerPlayer player, String name) {
        ResourceLocation id = ResourceLocation.tryParse(name.contains(":") ? name : AcademyCraft.MOD_ID + ":" + name);
        return id != null && trigger(player, id);
    }

    public static boolean trigger(ServerPlayer player, ResourceLocation id) {
        if (!(BuiltInRegistries.TRIGGER_TYPES.get(id) instanceof ACTrigger trigger)) return false;
        trigger.trigger(player);
        return true;
    }

    public static void grant(ServerPlayer player, String path) {
        AdvancementHolder advancement = player.server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, path));
        if (advancement == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    public static void grantLegacyLevels(ServerPlayer player, String category, int level) {
        if (category == null || category.isEmpty()) return;
        for (int current = 1; current <= Math.min(5, level); current++) {
            grant(player, "legacy/" + category + "/lv" + current);
        }
    }

    public static void grantLegacySkillLearn(ServerPlayer player, String category, String skill) {
        String achievement = switch (category + "." + skill) {
            case "meltdowner.rad_intensify" -> "rad_intensify";
            case "meltdowner.light_shield" -> "light_shield";
            case "meltdowner.meltdowner" -> "meltdowner";
            case "meltdowner.mine_ray_basic" -> "mine_ray";
            case "meltdowner.electron_missile" -> "electron_missile";
            default -> "";
        };
        if (!achievement.isEmpty()) grant(player, "legacy/" + category + "/" + achievement);
    }

    public static void grantLegacySkillUse(ServerPlayer player, String category, String skill) {
        String achievement = switch (category + "." + skill) {
            case "electromaster.mag_movement" -> "mag_movement";
            case "electromaster.body_intensify" -> "body_intensify";
            case "electromaster.mine_detect" -> "mine_detect";
            case "electromaster.thunder_bolt" -> "thunder_bolt";
            case "electromaster.railgun" -> "railgun";
            case "electromaster.thunder_clap" -> "thunder_clap";
            case "meltdowner.jet_engine" -> "jet_engine";
            case "teleporter.threatening_teleport" -> "threatening_teleport";
            case "teleporter.penetrate_teleport" -> "ignore_barrier";
            case "teleporter.flashing" -> "flashing";
            case "vecmanip.ground_shock" -> "ground_shock";
            case "vecmanip.dir_blast" -> "dir_blast";
            case "vecmanip.storm_wing" -> "storm_wing";
            case "vecmanip.blood_retro" -> "blood_retro";
            case "vecmanip.vec_reflection" -> "vec_reflection";
            default -> "";
        };
        if (!achievement.isEmpty()) grant(player, "legacy/" + category + "/" + achievement);
    }

    private ACAdvancements() {}
}
