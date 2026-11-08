package cn.academy.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Tooltip-preserving port of ItemMagneticCoil. */
public final class MagneticCoilItem extends Item {
    public MagneticCoilItem(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        for (String line : Component.translatable("item.ac_magnetic_coil.desc").getString().split("<br>"))
            tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
    }
}
