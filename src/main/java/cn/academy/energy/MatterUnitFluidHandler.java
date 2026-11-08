package cn.academy.energy;

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
                ? new FluidStack(ACFluids.IMAG_PHASE.get(), 1_000) : FluidStack.EMPTY;
    }
    @Override public int getTankCapacity(int tank) { return tank == 0 ? 1_000 : 0; }
    @Override public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && stack.is(ACFluids.IMAG_PHASE.get());
    }
    @Override public int fill(FluidStack resource, FluidAction action) {
        if (container.getCount() != 1 || MatterUnitItem.isFilled(container) || resource.getAmount() < 1_000
                || !isFluidValid(0, resource)) return 0;
        if (action.execute()) MatterUnitItem.setFilled(container, true);
        return 1_000;
    }
    @Override public FluidStack drain(FluidStack resource, FluidAction action) {
        return resource.is(ACFluids.IMAG_PHASE.get()) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
    }
    @Override public FluidStack drain(int maxDrain, FluidAction action) {
        if (container.getCount() != 1 || !MatterUnitItem.isFilled(container) || maxDrain < 1_000) return FluidStack.EMPTY;
        if (action.execute()) MatterUnitItem.setFilled(container, false);
        return new FluidStack(ACFluids.IMAG_PHASE.get(), 1_000);
    }
}
