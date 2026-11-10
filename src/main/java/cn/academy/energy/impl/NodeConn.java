package cn.academy.energy.impl;

import cn.academy.energy.api.block.IWirelessNode;

/** Original public node-connection operations; an old record never aliases its replacement. */
public abstract class NodeConn {
    public abstract void dispose();
    public abstract boolean isDisposed();
    public abstract void tick();
    public abstract IWirelessNode getNode();
    public abstract int getLoad();
    public abstract int getCapacity();
}
