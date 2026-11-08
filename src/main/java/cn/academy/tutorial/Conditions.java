package cn.academy.tutorial;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Original craft/pickup/smelt indexed conditions, resolved after deferred item registration. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class Conditions {
    private static final Condition ALWAYS = player -> true;
    private record ItemInfo(Supplier<? extends Item> item, int damage, int index) {}
    private static final List<ItemInfo> CRAFT = new ArrayList<>(), PICKUP = new ArrayList<>(), SMELT = new ArrayList<>();
    private static int nextIndex;

    public static Condition alwaysTrue() { return ALWAYS; }
    public static Condition itemCrafted(Item item) { return itemCrafted(item, -1); }
    public static Condition itemCrafted(Item item, int damage) { return mapped(CRAFT, () -> item, damage); }
    public static Condition itemPickup(Item item) { return itemPickup(item, -1); }
    public static Condition itemPickup(Item item, int damage) { return mapped(PICKUP, () -> item, damage); }
    public static Condition itemSmelted(Item item) { return itemSmelted(item, -1); }
    public static Condition itemSmelted(Item item, int damage) { return mapped(SMELT, () -> item, damage); }
    public static Condition itemObtained(Item item) { return itemObtained(item, -1); }
    public static Condition itemObtained(Item item, int damage) { return obtained(() -> item, damage); }
    public static Condition itemObtained(Block block) { return itemObtained(block.asItem()); }
    public static Condition itemObtained(Block block, int damage) { return itemObtained(block.asItem(), damage); }
    public static Condition itemObtained(Supplier<? extends Item> item) { return obtained(item, -1); }

    private static Condition obtained(Supplier<? extends Item> item, int damage) {
        return mapped(CRAFT, item, damage).or(mapped(PICKUP, item, damage)).or(mapped(SMELT, item, damage));
    }
    private static Condition mapped(List<ItemInfo> map, Supplier<? extends Item> item, int damage) {
        int index = nextIndex++;
        map.add(new ItemInfo(item, damage, index));
        return player -> TutorialData.get(player).isCondActivate(index);
    }
    private static void trigger(List<ItemInfo> map, ItemStack stack, Player player) {
        if (player.level().isClientSide || stack.isEmpty()) return;
        var state = AbilityState.load(player);
        boolean changed = false;
        for (var info : map) if (stack.is(info.item.get()) && (info.damage == -1 || stack.getDamageValue() == info.damage))
            changed |= state.tutorialData().setCondActivate(info.index);
        if (changed) state.save(player);
    }
    @SubscribeEvent public static void onItemCraft(PlayerEvent.ItemCraftedEvent event) {
        trigger(CRAFT, event.getCrafting(), event.getEntity());
    }
    @SubscribeEvent public static void onItemSmelt(PlayerEvent.ItemSmeltedEvent event) {
        trigger(SMELT, event.getSmelting(), event.getEntity());
    }
    @SubscribeEvent public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        // The entity's remaining stack may be empty after a successful pickup.
        trigger(PICKUP, event.getOriginalStack(), event.getPlayer());
    }
    private Conditions() {}
}
