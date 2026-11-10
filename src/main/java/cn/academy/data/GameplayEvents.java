package cn.academy.data;

import cn.academy.AcademyCraft;
import cn.academy.ability.AbilityState;
import cn.academy.network.ACNetwork;
import cn.academy.network.GameplayDataPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class GameplayEvents {
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) { event.addListener(new GameplayData.Loader()); }
    @SubscribeEvent public static void sync(OnDatapackSyncEvent event) {
        var payload = new GameplayDataPayload(GameplayData.serialized());
        for (var player : event.getRelevantPlayers().toList()) {
            PacketDistributor.sendToPlayer(player, payload);
            var state = AbilityState.load(player);
            state.tutorialData().markDirty();
            cn.academy.ability.AbilityContextManager.abortAll(player, state);
            cn.academy.ability.DevelopmentManager.abort(player, true);
            state.recalculateCapacities(false);
            state.save(player);
            ACNetwork.sync(player, state);
        }
    }
    private GameplayEvents() { }
}
