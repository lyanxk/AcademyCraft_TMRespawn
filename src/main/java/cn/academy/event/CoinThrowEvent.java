package cn.academy.event;

import cn.academy.entity.ACThrownItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class CoinThrowEvent extends PlayerEvent {
    public final ACThrownItemEntity coin;
    public CoinThrowEvent(Player player, ACThrownItemEntity coin) { super(player); this.coin = coin; }
}
