package cn.academy.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import cn.academy.terminal.AppRegistry;
import cn.academy.terminal.TerminalData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/** Keeps ItemApp's shared item name while exposing the installed app name in its tooltip. */
public final class AppInstallerItem extends Item {
    private final String app;

    public AppInstallerItem(Properties properties, String app) {
        super(properties);
        this.app = app;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        var application = java.util.Objects.requireNonNull(AppRegistry.getByName(app), "Unknown application " + app);
        if (player instanceof ServerPlayer) {
            var terminal = TerminalData.get(player);
            if (!AppRegistry.enumeration().contains(application)) return InteractionResultHolder.fail(stack);
            if (!terminal.isTerminalInstalled())
                player.sendSystemMessage(Component.translatable("ac.terminal.notinstalled"));
            else if (terminal.isInstalled(application))
                player.sendSystemMessage(Component.translatable("ac.terminal.app_alrdy_installed",
                        Component.translatable(application.getDisplayKey())));
            else {
                if (!player.isCreative()) stack.shrink(1);
                terminal.installApp(application);
                player.sendSystemMessage(Component.translatable("ac.terminal.app_installed",
                        Component.translatable(application.getDisplayKey())));
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("ac.app." + app + ".name").withStyle(ChatFormatting.GRAY));
    }
}
