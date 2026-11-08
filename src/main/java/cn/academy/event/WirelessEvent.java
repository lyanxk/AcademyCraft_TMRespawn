package cn.academy.event;

import cn.academy.energy.api.block.IWirelessTile;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

/**
 * Base class of any wireless event.
 * All WirelessEvent should only be executed in SERVER side.
 * @author WeathFolD
 */
public class WirelessEvent extends Event {

    public final IWirelessTile tile;
    private int result;

    /** Modern GUI result code: 0 success, 2 authentication failure, 3 invalid/full/canceled. */
    public int result() {
        return result == 0 && this instanceof net.neoforged.bus.api.ICancellableEvent canceled && canceled.isCanceled() ? 3 : result;
    }
    public void fail(int code) {
        result = code;
        if (this instanceof net.neoforged.bus.api.ICancellableEvent canceled) canceled.setCanceled(true);
    }

    public WirelessEvent(IWirelessTile _tile) {
        tile = _tile;
    }

    public BlockEntity getTileEntity() {
        return (BlockEntity) tile;
    }

    public Level getWorld() {
        return getTileEntity().getLevel();
    }

}