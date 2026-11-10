package cn.academy.item;

import cn.academy.advancement.ACAdvancements;
import cn.academy.network.OpenClientScreenPayload;
import cn.academy.terminal.TerminalData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TerminalInstallerItem extends Item {
    public TerminalInstallerItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            var terminal = TerminalData.get(player);
            if (terminal.isTerminalInstalled())
                PacketDistributor.sendToPlayer(serverPlayer, new OpenClientScreenPayload("terminal"));
            else {
                terminal.install();
                ACAdvancements.trigger(serverPlayer, "terminal_installed");
                PacketDistributor.sendToPlayer(serverPlayer, new OpenClientScreenPayload("terminal_install"));
            }
        }
        return InteractionResultHolder.success(stack);
    }
}
