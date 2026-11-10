package cn.academy.item;

import cn.academy.ability.AbilityRegistry;
import cn.academy.registry.ACDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class InductionFactorItem extends Item {
    public InductionFactorItem(Properties properties) {
        super(properties);
    }

    public static String category(ItemStack stack) {
        return stack.getItem() instanceof InductionFactorItem ? stack.getOrDefault(ACDataComponents.ABILITY_CATEGORY.get(),
                cn.academy.data.GameplayData.string("abilities", "default_category")) : "";
    }

    public static ItemStack forCategory(Item item, String category) {
        ItemStack stack = new ItemStack(item);
        stack.set(ACDataComponents.ABILITY_CATEGORY.get(), category);
        int model = Math.max(0, AbilityRegistry.categoryIds().indexOf(category));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(model));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String category = cn.academy.item.InductionFactorItem.category(stack);
        if (AbilityRegistry.category(category) != null) {
            tooltip.add(AbilityRegistry.category(category).displayName().copy().withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("ac.factor.unstable").withStyle(ChatFormatting.GRAY));
        }
    }
}
