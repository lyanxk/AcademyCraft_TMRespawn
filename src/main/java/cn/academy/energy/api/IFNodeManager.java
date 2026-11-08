package cn.academy.energy.api;

import cn.academy.energy.api.block.IWirelessNode;
import cn.academy.support.EnergyBlockHelper.IEnergyBlockManager;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * @author WeAthFolD
 */

public class IFNodeManager implements IEnergyBlockManager {

    public static IFNodeManager instance = new IFNodeManager();


    @Override
    public boolean isSupported(BlockEntity tile) {
        return tile instanceof IWirelessNode;
    }

    @Override
    public double getEnergy(BlockEntity tile) {
        return ((IWirelessNode) tile).getEnergy();
    }

    @Override
    public void setEnergy(BlockEntity tile, double energy) {
        ((IWirelessNode) tile).setEnergy(energy);
    }

    @Override
    public double charge(BlockEntity tile, double amt, boolean ignoreBandwidth) {
        IWirelessNode node = ((IWirelessNode) tile);
        double max = node.getMaxEnergy() - node.getEnergy();
        double chg = Math.min(amt, max);
        if (!ignoreBandwidth)
            chg = Math.min(node.getBandwidth(), chg);

        node.setEnergy(node.getEnergy() + chg);
        return amt - chg;
    }

    @Override
    public double pull(BlockEntity tile, double amt, boolean ignoreBandwidth) {
        IWirelessNode node = (IWirelessNode) tile;
        double max = node.getEnergy();
        double pull = Math.min(max, amt);
        if (!ignoreBandwidth)
            pull = Math.min(node.getBandwidth(), pull);

        node.setEnergy(node.getEnergy() - pull);
        return pull;
    }

}