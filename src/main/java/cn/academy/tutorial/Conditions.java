package cn.academy.tutorial;
import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class Conditions {
    private static void trigger(String event, ItemStack stack, Player player) {
        if (player.level().isClientSide || stack.isEmpty()) return;
        boolean tracked = cn.academy.data.GameplayData.value("tutorials", "tutorials").getAsJsonArray().asList().stream()
                .flatMap(entry -> entry.getAsJsonObject().getAsJsonArray("obtained_items").asList().stream())
                .anyMatch(item -> cn.academy.data.GameplayRules.itemMatches(stack, item.getAsString()));
        if (!tracked) return;
        var state = AbilityState.load(player);
        if (state.tutorialData().recordCondition(event + "/" + BuiltInRegistries.ITEM.getKey(stack.getItem()))) state.save(player);
    }
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) { trigger("craft", event.getCrafting(), event.getEntity()); }
    @SubscribeEvent public static void smelted(PlayerEvent.ItemSmeltedEvent event) { trigger("smelt", event.getSmelting(), event.getEntity()); }
    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Post event) { trigger("pickup", event.getOriginalStack(), event.getPlayer()); }
    private Conditions() { }
}
