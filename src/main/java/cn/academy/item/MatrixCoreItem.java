package cn.academy.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;


/** Modern data-component representation of the original metadata 0..2 matrix cores. */
public final class MatrixCoreItem extends Item {
    public MatrixCoreItem(Properties properties) {
        super(properties.component(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(0)));
    }

    public static int level(ItemStack stack) {
        return Math.max(1, Math.min(3, stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value() + 1));
    }

    public static ItemStack of(Item item, int level) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(Math.max(0, Math.min(2, level - 1))));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.ac_mat_core_" + (level(stack) - 1) + ".name");
    }

}
