package cn.academy.support;

import cn.academy.energy.api.IFItemManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Original ordered manager extension point. Native IF keeps its double precision. */
public final class EnergyItemHelper {
    private static final List<EnergyItemManager> supported = new ArrayList<>(List.of(IFItemManager.instance));

    public static void register(EnergyItemManager manager) { supported.add(manager); }
    public static boolean isSupported(ItemStack stack) { return manager(stack) != null; }
    private static EnergyItemManager manager(ItemStack stack) {
        for (EnergyItemManager manager : supported) if (manager.isSupported(stack)) return manager;
        return null;
    }
    public static double getEnergy(ItemStack stack) {
        var manager = manager(stack);
        return manager == null ? 0 : manager.getEnergy(stack);
    }
    public static void setEnergy(ItemStack stack, double energy) {
        var manager = manager(stack);
        if (manager != null) manager.setEnergy(stack, energy);
    }
    public static double charge(ItemStack stack, double amount, boolean ignoreBandwidth) {
        var manager = manager(stack);
        return manager == null ? amount : manager.charge(stack, amount, ignoreBandwidth);
    }
    public static double pull(ItemStack stack, double amount, boolean ignoreBandwidth) {
        var manager = manager(stack);
        return manager == null ? 0 : manager.pull(stack, amount, ignoreBandwidth);
    }
    public static ItemStack createEmptyItem(Item item) {
        var stack = new ItemStack(item);
        charge(stack, 0, true);
        return stack;
    }
    public static ItemStack createFullItem(Item item) {
        var stack = new ItemStack(item);
        charge(stack, Integer.MAX_VALUE, true);
        return stack;
    }
    public interface EnergyItemManager {
        boolean isSupported(ItemStack stack);
        double getEnergy(ItemStack stack);
        void setEnergy(ItemStack stack, double energy);
        double charge(ItemStack stack, double amount, boolean ignoreBandwidth);
        double pull(ItemStack stack, double amount, boolean ignoreBandwidth);
    }
    private EnergyItemHelper() {}
}
