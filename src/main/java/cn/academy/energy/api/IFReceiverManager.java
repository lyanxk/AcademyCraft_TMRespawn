package cn.academy.energy.api;

import cn.academy.energy.api.block.IWirelessReceiver;
import cn.academy.support.EnergyBlockHelper.IEnergyBlockManager;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * @author WeAthFolD
 */
public class IFReceiverManager implements IEnergyBlockManager {

    public static IFReceiverManager instance = new IFReceiverManager();


    private IFReceiverManager() {
    }

    @Override
    public boolean isSupported(BlockEntity tile) {
        return tile instanceof IWirelessReceiver;
    }

    @Override
    public double getEnergy(BlockEntity tile) {
        return 0;
    }

    @Override
    public void setEnergy(BlockEntity tile, double energy) {
        // throw new UnsupportedOperationException();
    }

    @Override
    public double charge(BlockEntity tile, double amt, boolean ignoreBandwidth) {
        return ((IWirelessReceiver) tile).injectEnergy(amt);
    }

    @Override
    public double pull(BlockEntity tile, double amt, boolean ignoreBandwidth) {
        return ((IWirelessReceiver) tile).pullEnergy(amt);
    }

}