package cn.academy.support.fe;

import cn.academy.data.GameplayData;

import net.neoforged.neoforge.energy.IEnergyStorage;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** FE bridge port. Native storage remains in fractional IF. */
public final class FEEnergyStorage implements IEnergyStorage {
    public static double ratio() { return GameplayData.number("wireless", "fe_per_if"); }
    private final DoubleSupplier stored;
    private final DoubleConsumer store;
    private final DoubleSupplier capacity;
    private final boolean input;

    public FEEnergyStorage(DoubleSupplier stored, DoubleConsumer store, DoubleSupplier capacity, boolean input) {
        this.stored = stored;
        this.store = store;
        this.capacity = capacity;
        this.input = input;
    }
    public static int toFE(double energy) {
        return Double.isNaN(energy) || energy <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, energy * ratio());
    }
    public static double toIF(int energy) { return energy / (double) ratio(); }
    @Override public int receiveEnergy(int request, boolean simulate) {
        if (!input || request <= 0) return 0;
        int accepted = Math.min(request, toFE(capacity.getAsDouble() - stored.getAsDouble()));
        if (!simulate && accepted > 0) store.accept(stored.getAsDouble() + toIF(accepted));
        return accepted;
    }
    @Override public int extractEnergy(int request, boolean simulate) {
        if (input || request <= 0) return 0;
        int extracted = Math.min(request, getEnergyStored());
        if (!simulate && extracted > 0) store.accept(stored.getAsDouble() - toIF(extracted));
        return extracted;
    }
    @Override public int getEnergyStored() { return toFE(stored.getAsDouble()); }
    @Override public int getMaxEnergyStored() { return toFE(capacity.getAsDouble()); }
    @Override public boolean canExtract() { return !input; }
    @Override public boolean canReceive() { return input; }
}
