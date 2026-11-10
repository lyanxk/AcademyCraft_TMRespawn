package cn.academy.advancement;
import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityState;
import cn.academy.data.GameplayData;
import cn.academy.data.GameplayRules;
import cn.academy.event.MatterUnitHarvestEvent;
import cn.academy.event.ability.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class DispatcherAch {
    private static void item(ServerPlayer player, ItemStack item, String event) {
        for (var mapping : GameplayData.value("advancements", event).getAsJsonObject().entrySet())
            if (GameplayRules.itemMatches(item, mapping.getKey())) ACAdvancements.trigger(player, mapping.getValue().getAsString());
    }
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) item(player, event.getCrafting(), "crafted");
    }
    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player) item(player, event.getOriginalStack(), "pickup");
    }
    @SubscribeEvent public static void level(LevelChangeEvent event) {
        if (event.player instanceof ServerPlayer player) {
            var state = AbilityState.load(player);
            var trigger = GameplayData.value("advancements", "levels").getAsJsonObject().get(Integer.toString(state.level()));
            if (trigger != null) ACAdvancements.trigger(player, trigger.getAsString());
            ACAdvancements.trigger(player, "ability_level");
        }
    }
    @SubscribeEvent public static void harvest(MatterUnitHarvestEvent event) { if (event.player instanceof ServerPlayer player) ACAdvancements.trigger(player, "getting_phase"); }
    @SubscribeEvent public static void learned(SkillLearnEvent event) {
        if (event.player instanceof ServerPlayer player) { ACAdvancements.trigger(player, "ac_learning_skill"); ACAdvancements.triggerSkill(player, "skill_learned", event.skill); }
    }
    @SubscribeEvent public static void transform(TransformCategoryEvent event) { if (event.player instanceof ServerPlayer player) ACAdvancements.trigger(player, "convert_category"); }
    @SubscribeEvent public static void experience(SkillExpAddedEvent event) { if (event.player instanceof ServerPlayer player && AbilityState.load(player).experience(event.skill.id()) >= 1) ACAdvancements.trigger(player, "ac_exp_full"); }
    @SubscribeEvent public static void overload(OverloadEvent event) { if (event.player instanceof ServerPlayer player) ACAdvancements.trigger(player, "ac_overload"); }
    private DispatcherAch() { }
}
