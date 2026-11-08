package cn.academy.energy.impl;

import cn.academy.energy.api.block.IWirelessMatrix;

/** The original public network access contract, backed by the dimension's live SavedData record. */
public abstract class WirelessNet {
    public abstract String getSSID();
    public abstract void setSSID(String ssid);
    public abstract String getPassword();
    public abstract boolean resetPassword(String password);
    public abstract boolean isDisposed();
    public abstract int getLoad();
    public abstract int getCapacity();
    public abstract IWirelessMatrix getMatrix();
}
