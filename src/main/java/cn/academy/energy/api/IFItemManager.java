package cn.academy.energy.api;

import cn.academy.energy.api.item.ImagEnergyItem;
import cn.academy.item.EnergyItem;
import cn.academy.registry.ACDataComponents;
import cn.academy.registry.ACItems;
import cn.academy.support.EnergyItemHelper.EnergyItemManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/** Port of the 1.12.2 manager: charge returns the remainder; pull returns the amount removed. */
public final class IFItemManager implements EnergyItemManager {
    public static final IFItemManager instance = new IFItemManager();

    private IFItemManager() {}

    @Override public boolean isSupported(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ImagEnergyItem;
    }

    public double getMaxEnergy(ItemStack stack) {
        return ((ImagEnergyItem) stack.getItem()).getMaxEnergy();
    }

    @Override public double getEnergy(ItemStack stack) {
        double value = stack.getOrDefault(ACDataComponents.ENERGY.get(), 0)
                + stack.getOrDefault(ACDataComponents.ENERGY_FRACTION.get(), 0.0);
        return Double.isFinite(value) ? Math.clamp(value, 0, getMaxEnergy(stack)) : 0;
    }

    @Override public void setEnergy(ItemStack stack, double amount) {
        // Preserve the integer component used by previous port saves; add only the fractional part.
        double value = Double.isNaN(amount) ? 0 : Math.max(0, Math.min(getMaxEnergy(stack), amount));
        int whole = (int) value;
        stack.set(ACDataComponents.ENERGY.get(), whole);
        if (value == whole) stack.remove(ACDataComponents.ENERGY_FRACTION.get());
        else stack.set(ACDataComponents.ENERGY_FRACTION.get(), value - whole);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        if (stack.is(ACItems.ENERGY_UNIT.get())) {
            int stage = EnergyItem.legacyModelStage(value, getMaxEnergy(stack));
            if (stage == 0) stack.remove(DataComponents.CUSTOM_MODEL_DATA);
            else stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(stage));
        }
    }

    public double charge(ItemStack stack, double amount) { return charge(stack, amount, false); }

    @Override public double charge(ItemStack stack, double amount, boolean ignoreBandwidth) {
        if (stack.getCount() != 1 || !Double.isFinite(amount)) return amount;
        double current = getEnergy(stack);
        double limit = ignoreBandwidth ? Double.MAX_VALUE : ((ImagEnergyItem) stack.getItem()).getBandwidth();
        double transfer = Math.max(-current, Math.min(getMaxEnergy(stack) - current, amount));
        transfer = Math.copySign(Math.min(Math.abs(transfer), limit), transfer);
        setEnergy(stack, current + transfer);
        return amount - transfer;
    }

    @Override public double pull(ItemStack stack, double amount, boolean ignoreBandwidth) {
        if (stack.getCount() != 1 || !Double.isFinite(amount) || amount < 0) return 0;
        double current = getEnergy(stack);
        double give = Math.min(amount, current);
        if (!ignoreBandwidth) give = Math.min(give, ((ImagEnergyItem) stack.getItem()).getBandwidth());
        setEnergy(stack, current - give);
        return give;
    }

    public String getDescription(ItemStack stack) {
        return String.format("%.0f/%.0f IF", getEnergy(stack), getMaxEnergy(stack));
    }
}
