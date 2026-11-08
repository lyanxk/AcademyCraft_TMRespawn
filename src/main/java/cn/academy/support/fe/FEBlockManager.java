package cn.academy.support.fe;

import cn.academy.energy.api.block.IWirelessTile;
import cn.academy.support.EnergyBlockHelper.IEnergyBlockManager;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Modern FE fallback; native IF tiles use their double-precision managers exactly once. */
public final class FEBlockManager implements IEnergyBlockManager {
    private IEnergyStorage storage(BlockEntity tile) {
        return tile == null || tile instanceof IWirelessTile || tile.getLevel() == null ? null
                : tile.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, tile.getBlockPos(), Direction.UP);
    }
    @Override public boolean isSupported(BlockEntity tile) {
        var storage = storage(tile);
        return storage != null && (storage.canReceive() || storage.canExtract());
    }
    @Override public double getEnergy(BlockEntity tile) {
        var storage = storage(tile); return storage == null ? 0 : FEEnergyStorage.toIF(storage.getEnergyStored());
    }
    @Override public void setEnergy(BlockEntity tile, double energy) { /* FE has no setter. */ }
    @Override public double charge(BlockEntity tile, double amount, boolean ignoreBandwidth) {
        var storage = storage(tile);
        return storage == null || !storage.canReceive() ? amount
                : amount - FEEnergyStorage.toIF(storage.receiveEnergy(FEEnergyStorage.toFE(amount), false));
    }
    @Override public double pull(BlockEntity tile, double amount, boolean ignoreBandwidth) {
        var storage = storage(tile);
        return storage == null || !storage.canExtract() ? 0
                : FEEnergyStorage.toIF(storage.extractEnergy(FEEnergyStorage.toFE(amount), false));
    }
}
