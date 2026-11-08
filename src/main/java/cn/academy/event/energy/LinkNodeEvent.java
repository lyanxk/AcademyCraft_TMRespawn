package cn.academy.event.energy;

import cn.academy.energy.api.block.IWirelessMatrix;
import cn.academy.energy.api.block.IWirelessNode;
import cn.academy.event.WirelessEvent;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired when a node is to be linked.
 * @author WeathFolD
 */
public class LinkNodeEvent extends WirelessEvent implements ICancellableEvent {

    public final IWirelessNode node;
    public final IWirelessMatrix matrix;
    public final String pwd;

    public LinkNodeEvent(IWirelessNode _node, IWirelessMatrix _matrix) {
        this(_node, _matrix, "");
    }

    public LinkNodeEvent(IWirelessNode _node, IWirelessMatrix _matrix, String _pwd) {
        super(_node);
        node = _node;
        matrix = _matrix;
        pwd = _pwd;
    }

}