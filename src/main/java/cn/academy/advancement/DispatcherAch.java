package cn.academy.advancement;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityCategory;
import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilitySkill;
import cn.academy.ability.AbilityState;
import cn.academy.event.MatterUnitHarvestEvent;
import cn.academy.event.ability.*;
import cn.academy.registry.ACItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Port of 1.12.2 DispatcherAch, using registry IDs for deferred criterion registration. */
public final class DispatcherAch {
    public static final DispatcherAch INSTANCE = new DispatcherAch();
    private final Map<Item, Set<ResourceLocation>> crafted = new HashMap<>();
    private final Map<AbilityCategory, ResourceLocation[]> levels = new HashMap<>();
    private final Map<AbilitySkill, ResourceLocation> learned = new HashMap<>();
    private final Map<Item, ResourceLocation> pickedUp = new HashMap<>();

    public void rgItemCrafted(Item item, ResourceLocation trigger) {
        crafted.computeIfAbsent(item, ignored -> new HashSet<>()).add(trigger);
    }
    public void urItemCrafted(Item item, ResourceLocation trigger) {
        Set<ResourceLocation> triggers = crafted.get(item);
        if (triggers != null) triggers.remove(trigger);
    }
    public void rgLevelChange(int level, ResourceLocation trigger) {
        for (AbilityCategory category : AbilityRegistry.categories()) rgLevelChange(category, level, trigger);
    }
    public void rgLevelChange(AbilityCategory category, int level, ResourceLocation trigger) {
        if (level < 1 || level > 5) throw new IllegalArgumentException("Level must be 1..5");
        levels.computeIfAbsent(category, ignored -> new ResourceLocation[5])[level - 1] = trigger;
    }
    public void urLevelChange(AbilityCategory category, int level) {
        ResourceLocation[] triggers = levels.get(category);
        if (triggers != null && level >= 1 && level <= 5) triggers[level - 1] = null;
    }
    public void rgSkillLearn(AbilitySkill skill, ResourceLocation trigger) { learned.put(skill, trigger); }
    public void urSkillLearn(AbilitySkill skill) { learned.remove(skill); }
    public void rgPlayerPickup(ItemStack stack, ResourceLocation trigger) { pickedUp.put(stack.getItem(), trigger); }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Set<ResourceLocation> triggers = crafted.get(event.getCrafting().getItem());
        if (triggers != null && event.getEntity() instanceof ServerPlayer player)
            for (ResourceLocation trigger : Set.copyOf(triggers)) ACAdvancements.trigger(player, trigger);
    }

    @SubscribeEvent
    public void onLevelChange(LevelChangeEvent event) {
        if (!(event.player instanceof ServerPlayer player)) return;
        AbilityState state = AbilityState.load(player);
        if (!state.hasCategory()) return;
        ResourceLocation[] triggers = levels.get(AbilityRegistry.category(state.category()));
        int index = state.level() - 1;
        if (triggers != null && index >= 0 && index < triggers.length && triggers[index] != null)
            ACAdvancements.trigger(player, triggers[index]);
    }

    @SubscribeEvent
    public void onMatterUnitHarvest(MatterUnitHarvestEvent event) {
        if (event.player instanceof ServerPlayer player) ACAdvancements.trigger(player, "getting_phase");
    }

    @SubscribeEvent
    public void onSkillLearn(SkillLearnEvent event) {
        if (!(event.player instanceof ServerPlayer player)) return;
        ResourceLocation trigger = learned.get(event.skill);
        if (trigger != null) ACAdvancements.trigger(player, trigger);
        ACAdvancements.trigger(player, "ac_learning_skill");
    }

    @SubscribeEvent
    public void onPlayerPickup(ItemEntityPickupEvent.Post event) {
        ResourceLocation trigger = pickedUp.get(event.getOriginalStack().getItem());
        if (trigger != null && event.getPlayer() instanceof ServerPlayer player) ACAdvancements.trigger(player, trigger);
    }

    @SubscribeEvent
    public void onPlayerTransformCategory(TransformCategoryEvent event) {
        if (event.player instanceof ServerPlayer player) ACAdvancements.trigger(player, "convert_category");
    }

    @SubscribeEvent
    public void onSkillExpAdded(SkillExpAddedEvent event) {
        if (event.player instanceof ServerPlayer player && AbilityState.load(player).experience(event.skill.id()) >= 1)
            ACAdvancements.trigger(player, "ac_exp_full");
    }

    @SubscribeEvent
    public void onPlayerOverload(OverloadEvent event) {
        if (event.player instanceof ServerPlayer player) ACAdvancements.trigger(player, "ac_overload");
    }

    public static void init() {
        INSTANCE.rgItemCrafted(ACItems.PHASE_GEN.get(), id("phase_generator"));
        INSTANCE.rgItemCrafted(ACItems.NODE_BASIC.get(), id("ac_node"));
        INSTANCE.rgItemCrafted(ACItems.MATRIX.get(), id("ac_matrix"));
        INSTANCE.rgPlayerPickup(new ItemStack(ACItems.INDUCTION_FACTOR.get()), id("getting_factor"));
        INSTANCE.rgItemCrafted(ACItems.DEVELOPER_PORTABLE.get(), id("ac_developer"));
        INSTANCE.rgLevelChange(1, id("dev_category"));
        INSTANCE.rgLevelChange(3, id("ac_level_3"));
        INSTANCE.rgLevelChange(5, id("ac_level_5"));
        NeoForge.EVENT_BUS.register(INSTANCE);
    }

    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, name); }
    private DispatcherAch() {}
}
