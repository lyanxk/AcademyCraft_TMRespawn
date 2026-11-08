package cn.academy.support;

import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * A generic typed energyBlock helper.
 */
public class EnergyBlockHelper {


    private static List<IEnergyBlockManager> handlers = new ArrayList<>(List.of(
            cn.academy.energy.api.IFNodeManager.instance, cn.academy.energy.api.IFReceiverManager.instance,
            new cn.academy.support.fe.FEBlockManager()));

    public static void register(IEnergyBlockManager handler) {
        handlers.add(handler);
    }

    public static boolean isSupported(BlockEntity tile) {
        for (IEnergyBlockManager handler : handlers)
            if (handler.isSupported(tile))
                return true;
        return false;
    }

    public static double getEnergy(BlockEntity tile) {
        for (IEnergyBlockManager handler : handlers)
            if (handler.isSupported(tile))
                return handler.getEnergy(tile);
        return 0;
    }

    public static void setEnergy(BlockEntity tile, double energy) {
        for (IEnergyBlockManager handler : handlers)
            if (handler.isSupported(tile)) {
                handler.setEnergy(tile, energy);
                break;
            }
    }

    public static double charge(BlockEntity tile, double amt, boolean ignoreBandwidth) {
        // Preserve the 1.12.2 implementation: every matching handler is called, and the
        // original amount is returned. CurrentCharging ignores this return value.
        for (IEnergyBlockManager handler : handlers){
            if (handler.isSupported(tile)) {
                handler.charge(tile, amt, ignoreBandwidth);
            }
        }
        return amt;
    }

    public static double pull(BlockEntity tile, double amt, boolean ignoreBandwidth) {
        for (IEnergyBlockManager handler : handlers)
            if (handler.isSupported(tile)) {
                return handler.pull(tile, amt, ignoreBandwidth);
            }
        return 0;
    }

    public static interface IEnergyBlockManager {

        boolean isSupported(BlockEntity tile);

        double getEnergy(BlockEntity tile);

        void setEnergy(BlockEntity tile, double energy);

        /**
         * Charge a specified amount of energy into the tile.
         * 
         * @return How much energy not charged into the tile(left)
         */
        double charge(BlockEntity tile, double amt, boolean ignoreBandwidth);

        /**
         * Pull a specified amount of energy from the energy tile.
         * 
         * @return How much energy pulled out
         */
        double pull(BlockEntity tile, double amt, boolean ignoreBandwidth);

    }

}