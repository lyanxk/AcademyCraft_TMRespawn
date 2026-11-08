package cn.academy.block.entity;

import cn.academy.energy.api.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The original four mutually distinct wireless roles, sharing the modern machine implementation. */
final class WirelessMachineRoles {
    static final class Matrix extends ACMachineBlockEntity implements IWirelessMatrix {
        Matrix(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public int getCapacity() { return wirelessCapacity(); }
        @Override public double getBandwidth() { return wirelessBandwidth(); }
        @Override public double getRange() { return wirelessRange(); }
    }
    static final class Node extends ACMachineBlockEntity implements IWirelessNode {
        Node(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public double getMaxEnergy() { return energy.getMaxEnergyStored(); }
        @Override public double getEnergy() { return energy.exactEnergy(); }
        @Override public void setEnergy(double value) { setWirelessEnergy(value); }
        @Override public int getCapacity() { return wirelessCapacity(); }
        @Override public double getBandwidth() { return kind().transfer(); }
        @Override public double getRange() { return wirelessRange(); }
        @Override public String getNodeName() { return nodeName(); }
        @Override public String getPassword() { return wirelessNodePassword(); }
    }
    static final class Generator extends ACMachineBlockEntity implements IWirelessGenerator {
        Generator(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public double getBandwidth() { return kind().transfer(); }
        @Override public double getProvidedEnergy(double required) { return energy.pullInternal(required); }
    }
    static final class Receiver extends ACMachineBlockEntity implements IWirelessReceiver {
        Receiver(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public double getBandwidth() { return kind().transfer(); }
        @Override public double getRequiredEnergy() { return energy.getMaxEnergyStored() - energy.exactEnergy(); }
        @Override public double injectEnergy(double amount) {
            double accepted = Math.min(getRequiredEnergy(), Math.max(0, amount));
            setWirelessEnergy(energy.exactEnergy() + accepted);
            return amount - accepted;
        }
        @Override public double pullEnergy(double amount) { return energy.pullInternal(amount); }
    }
    private WirelessMachineRoles() {}
}
