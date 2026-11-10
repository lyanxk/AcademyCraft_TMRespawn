package cn.academy.energy;

import cn.academy.registry.ACBlockEntities;
import cn.academy.registry.ACItems;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class ACCapabilities {
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ACBlockEntities.MACHINE.get(),
                (machine, side) -> machine.externalEnergy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ACBlockEntities.MACHINE.get(),
                (machine, side) -> machine.externalItems(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ACBlockEntities.MACHINE.get(),
                (machine, side) -> machine.phaseTank());
        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, context) -> new MatterUnitFluidHandler(stack), ACItems.MATTER_UNIT.get());
    }

    private ACCapabilities() {}
}
