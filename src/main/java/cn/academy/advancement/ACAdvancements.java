package cn.academy.advancement;

import cn.academy.data.GameplayData;

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
            "getting_factor", "getting_phase", "open_misaka_cloud", "phase_generator", "terminal_installed", "skill_learned", "skill_used", "ability_level");
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
        var mappings = GameplayData.value("advancements", "level_advancements").getAsJsonObject().get(category);
        if (mappings == null) return;
        for (var entry : mappings.getAsJsonObject().entrySet()) if (Integer.parseInt(entry.getKey()) <= level) {
            var id = ResourceLocation.parse(entry.getValue().getAsString());
            var holder = player.server.getAdvancements().get(id);
            if (holder != null) for (String criterion : player.getAdvancements().getOrStartProgress(holder).getRemainingCriteria()) player.getAdvancements().award(holder, criterion);
        }
    }

    public static void triggerSkill(ServerPlayer player, String event, cn.academy.ability.AbilitySkill skill) {
        var trigger = BuiltInRegistries.TRIGGER_TYPES.get(ResourceLocation.fromNamespaceAndPath("academy", event));
        if (trigger instanceof ACTrigger academy) academy.trigger(player, skill.id());
    }
    private static void mappedSkill(ServerPlayer player, String event, String category, String skill) {
        var value = GameplayData.value("advancements", event).getAsJsonObject().get(category + "." + skill);
        if (value != null) {
            var id = ResourceLocation.parse(value.getAsString());
            if (id.getNamespace().equals("academy")) grant(player, id.getPath());
            else {
                var holder = player.server.getAdvancements().get(id);
                if (holder != null) for (var criterion : player.getAdvancements().getOrStartProgress(holder).getRemainingCriteria()) player.getAdvancements().award(holder, criterion);
            }
        }
    }
    public static void grantLegacySkillLearn(ServerPlayer player, String category, String skill) { mappedSkill(player, "learn", category, skill); }
    public static void grantLegacySkillUse(ServerPlayer player, String category, String skill) {
        mappedSkill(player, "use", category, skill);
        var ability = cn.academy.ability.AbilityRegistry.skill(category + "." + skill);
        if (ability != null) triggerSkill(player, "skill_used", ability);
    }

    private ACAdvancements() {}
}
