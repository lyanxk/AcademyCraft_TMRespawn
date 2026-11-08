package cn.academy.client;

import cn.academy.ability.AbilityRegistry;
import cn.academy.ability.AbilityState;
import cn.academy.ability.vanilla.electromaster.skill.Railgun;
import cn.academy.client.render.RailgunHandEffects;
import cn.academy.entity.ACThrownItemEntity;
import cn.academy.event.CoinThrowEvent;
import cn.academy.network.RailgunPerformPayload;
import cn.academy.registry.ACItems;
import cn.academy.registry.ACSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Local item/coin delegates. The synchronized server coin is never used to judge the displayed QTE. */
public final class ClientRailgun {
    private static final RailgunDelegate[] DELEGATES = new RailgunDelegate[AbilityState.SKILL_CHANNELS];
    private static ACThrownItemEntity localCoin;
    private static int nextLocalId = -1000000;
    public static void rebuild() { java.util.Arrays.fill(DELEGATES, null); }
    public static void reset() { rebuild(); localCoin = null; }
    public static void abort() { for (var delegate : DELEGATES) if (delegate != null) delegate.abort(); }
    private static RailgunDelegate delegate(int slot) {
        if (DELEGATES[slot] == null) DELEGATES[slot] = new RailgunDelegate();
        return DELEGATES[slot];
    }
    private static boolean railgun(AbilityState state, int slot) {
        var skill = AbilityRegistry.skill(state.controlSkill(slot));
        return skill != null && skill.name().equals("railgun");
    }
    static boolean input(int slot, AbilityKeyInput.Action action) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !railgun(AbilityState.load(minecraft.player), slot)) return false;
        var delegate = delegate(slot);
        switch (action) {
            case DOWN -> {
                switch (delegate.down(Railgun.isAccepted(minecraft.player.getMainHandItem()))) {
                    case CHARGE -> RailgunHandEffects.add(minecraft.player);
                    case FIRE_COIN -> PacketDistributor.sendToServer(new RailgunPerformPayload(slot, true));
                    default -> { }
                }
            }
            case UP, ABORT -> delegate.abort();
            default -> { }
        }
        return true;
    }
    static void tick(AbilityKeyInput[] keys) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.isPaused()) return;
        var state = AbilityState.load(minecraft.player);
        if (!state.active()) { rebuild(); return; }
        for (int slot = 0; slot < DELEGATES.length; slot++) {
            if (!railgun(state, slot)) { DELEGATES[slot] = null; continue; }
            if (DELEGATES[slot] == null) continue;
            if (!state.canUse() || !keys[slot].held()) DELEGATES[slot].abort();
            else if (DELEGATES[slot].tick()) PacketDistributor.sendToServer(new RailgunPerformPayload(slot, false));
        }
    }
    public static int state(int slot) { return DELEGATES[slot] == null ? 0 : DELEGATES[slot].state(); }
    public static void throwCoin(PlayerInteractEvent.RightClickItem event) {
        var minecraft = Minecraft.getInstance();
        if (event.getEntity() != minecraft.player || minecraft.level == null || !event.getItemStack().is(ACItems.COIN.get())) return;
        event.setCanceled(true);
        if (localCoin != null && localCoin.level() == minecraft.level && !localCoin.isRemoved()) {
            event.setCancellationResult(InteractionResult.PASS); return;
        }
        var stack = event.getItemStack();
        var coin = new ACThrownItemEntity(minecraft.player, minecraft.level, stack);
        coin.setId(nextLocalId--);
        coin.setLocalCoin();
        minecraft.level.addEntity(coin);
        localCoin = coin;
        minecraft.player.playSound(ACSounds.COIN_FLIP.get(), .5f, 1);
        NeoForge.EVENT_BUS.post(new CoinThrowEvent(minecraft.player, coin));
        if (!minecraft.player.isCreative()) stack.shrink(1);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
    public static void onThrow(CoinThrowEvent event) {
        if (event.getEntity() != Minecraft.getInstance().player) return;
        var state = AbilityState.load(event.getEntity());
        if (!state.canUse()) return;
        for (int slot = 0; slot < cn.academy.ability.AbilityState.SKILL_CHANNELS; slot++) if (railgun(state, slot)) {
            delegate(slot).informThrow(new RailgunDelegate.Coin() {
                public boolean alive() { return !event.coin.isRemoved(); }
                public double progress() { return event.coin.coinProgress(); }
            });
            return; // Original informs the first matching delegate only.
        }
    }
    private ClientRailgun() {}
}
