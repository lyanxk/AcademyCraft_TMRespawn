package cn.academy.item;

import cn.academy.data.GameplayData;

import cn.academy.registry.ACDataComponents;
import cn.academy.energy.api.IFItemManager;
import cn.academy.energy.api.item.ImagEnergyItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import cn.academy.registry.ACItems;

public final class EnergyItem extends Item implements ImagEnergyItem {
    private final String profile;
    public EnergyItem(Properties properties, String profile) {
        super(properties.stacksTo(1).component(ACDataComponents.ENERGY.get(), 0)); this.profile = profile;
    }
    public int capacity() { return GameplayData.integer("items/" + profile, "capacity"); }
    @Override public double getMaxEnergy() { return capacity(); }
    @Override public double getBandwidth() { return GameplayData.number("items/" + profile, "bandwidth"); }

    @Override public int getMaxStackSize(ItemStack stack) { return 1; }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!stack.is(ACItems.ENERGY_UNIT.get())) return;
        double energy = IFItemManager.instance.getEnergy(stack);
        // Recover abnormal charged batches from earlier port saves without duplicating their energy.
        // Normal empty recipe batches are split by vanilla container/inventory insertion limits.
        if (!level.isClientSide && energy > 0 && stack.getCount() > 1 && entity instanceof Player player) {
            int remainder = stack.getCount() - 1;
            stack.setCount(1);
            ItemStack empty = new ItemStack(ACItems.ENERGY_UNIT.get(), remainder);
            if (!player.getInventory().add(empty)) player.drop(empty, false);
        }
    }

    /**
     * Reproduces ItemEnergyBase's 1.12.2 damage-to-icon thresholds. The legacy item first mapped
     * energy onto damage 0..13, then used damage &lt; 3 for full, damage &gt; 10 for empty and the
     * remaining values for half. Keeping that quantisation matters near the two boundaries.
     */
    public static int legacyModelStage(double energy, double capacity) {
        int damage = (int) Math.round((1 - Math.max(0, Math.min(energy, capacity)) / Math.max(1, capacity)) * 13);
        return damage < 3 ? 2 : damage > 10 ? 0 : 1;
    }

    public static float legacyModelValue(ItemStack stack) {
        if (!(stack.getItem() instanceof EnergyItem item)) return 0;
        return legacyModelStage(IFItemManager.instance.getEnergy(stack), item.capacity()) * .5f;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal(IFItemManager.instance.getDescription(stack)));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return legacyDamage(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return 13 - legacyDamage(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return net.minecraft.util.Mth.hsvToRgb((13 - legacyDamage(stack)) / 39f, 1, 1);
    }

    private int legacyDamage(ItemStack stack) {
        return (int) Math.round((1 - IFItemManager.instance.getEnergy(stack) / Math.max(1, capacity())) * 13);
    }
}
