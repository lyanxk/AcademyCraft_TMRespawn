package cn.academy.event.energy;

import cn.academy.energy.api.block.IWirelessMatrix;
import cn.academy.event.WirelessEvent;
import net.neoforged.bus.api.ICancellableEvent;

public class ChangePassEvent extends WirelessEvent implements ICancellableEvent {

    public final IWirelessMatrix mat;
    public final String pwd;

    /**
     * Encrypted creation
     */
    public ChangePassEvent(IWirelessMatrix _mat, String _pwd) {
        super(_mat);
        mat = _mat;
        pwd = _pwd;
    }

}