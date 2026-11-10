package cn.academy.energy;

import cn.academy.data.GameplayData;

import cn.academy.item.MatterUnitItem;
import cn.academy.registry.ACFluids;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/** Exact 1000 mB container capability for a single Matter Unit. */
public final class MatterUnitFluidHandler implements IFluidHandlerItem {
    private final ItemStack container;

    public MatterUnitFluidHandler(ItemStack container) {
        this.container = container;
    }

    @Override public ItemStack getContainer() { return container; }
    @Override public int getTanks() { return 1; }
    @Override public FluidStack getFluidInTank(int tank) {
        return tank == 0 && MatterUnitItem.isFilled(container)
                ? new FluidStack(MatterUnitItem.getMaterial(container).fluid().get(), GameplayData.integer("matter", "capacity")) : FluidStack.EMPTY;
    }
    @Override public int getTankCapacity(int tank) { return tank == 0 ? GameplayData.integer("matter", "capacity") : 0; }
    @Override public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && MatterUnitItem.materials().stream().anyMatch(material -> material != MatterUnitItem.MAT_NONE && stack.is(material.fluid().get()));
    }
    @Override public int fill(FluidStack resource, FluidAction action) {
        if (container.getCount() != 1 || MatterUnitItem.isFilled(container) || resource.getAmount() < GameplayData.integer("matter", "capacity")
                || !isFluidValid(0, resource)) return 0;
        if (action.execute()) MatterUnitItem.setMaterial(container, MatterUnitItem.materials().stream().filter(material -> resource.is(material.fluid().get())).findFirst().orElseThrow());
        return GameplayData.integer("matter", "capacity");
    }
    @Override public FluidStack drain(FluidStack resource, FluidAction action) {
        return resource.is(MatterUnitItem.getMaterial(container).fluid().get()) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
    }
    @Override public FluidStack drain(int maxDrain, FluidAction action) {
        if (container.getCount() != 1 || !MatterUnitItem.isFilled(container) || maxDrain < GameplayData.integer("matter", "capacity")) return FluidStack.EMPTY;
        if (action.execute()) MatterUnitItem.setFilled(container, false);
        return new FluidStack(MatterUnitItem.getMaterial(container).fluid().get(), GameplayData.integer("matter", "capacity"));
    }
}
