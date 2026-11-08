package cn.academy.event;

import cn.academy.item.MatterUnitItem.MatterMaterial;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/** Original non-cancellable notification, emitted after both collecting and placing a material. */
public final class MatterUnitHarvestEvent extends Event {
    public final Player player;
    public final MatterMaterial mat;
    public MatterUnitHarvestEvent(Player player, MatterMaterial mat) {
        this.player = player;
        this.mat = mat;
    }
}
