package cn.academy.event.ability;

import cn.academy.ability.AbilityCategory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;

/** Raised before magnetic-coil conversion; cancellation preserves category and consumed items. */
public class TransformCategoryEvent extends AbilityEvent implements ICancellableEvent {
    public AbilityCategory category;
    public int level;

    public TransformCategoryEvent(Player player, AbilityCategory category, int level) {
        super(player);
        this.category = category;
        this.level = level;
    }
}
