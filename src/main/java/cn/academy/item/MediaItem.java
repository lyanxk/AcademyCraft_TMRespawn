package cn.academy.item;

import cn.academy.data.GameplayData;

import cn.academy.registry.ACDataComponents;
import cn.academy.ability.AbilityState;
import cn.academy.network.ACNetwork;
import cn.academy.terminal.AppRegistry;
import cn.academy.terminal.TerminalData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

public final class MediaItem extends Item {
    // The server snapshot supplies track identities, visual models and drop weights.
    public static List<String> tracks() {
        return GameplayData.value("media", "tracks").getAsJsonArray().asList().stream()
                .map(entry -> entry.getAsJsonObject().get("id").getAsString()).toList();
    }
    public static int model(String id) {
        return GameplayData.value("media", "tracks").getAsJsonArray().asList().stream()
                .map(com.google.gson.JsonElement::getAsJsonObject).filter(track -> track.get("id").getAsString().equals(id))
                .mapToInt(track -> track.get("model").getAsInt()).findFirst().orElse(0);
    }
    public static String random(net.minecraft.util.RandomSource random) {
        var tracks = GameplayData.value("media", "tracks").getAsJsonArray();
        int total = tracks.asList().stream().mapToInt(track -> track.getAsJsonObject().get("weight").getAsInt()).sum();
        int selected = random.nextInt(total);
        for (var entry : tracks) { var track = entry.getAsJsonObject(); selected -= track.get("weight").getAsInt(); if (selected < 0) return track.get("id").getAsString(); }
        throw new IllegalStateException("Empty media selection");
    }

    public MediaItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack of(Item item, String media) {
        ItemStack stack = new ItemStack(item);
        setMedia(stack, media);
        return stack;
    }

    public static void setMedia(ItemStack stack, String media) {
        stack.set(ACDataComponents.MEDIA_ID.get(), media);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(Math.max(0, model(media))));
    }

    public static String media(ItemStack stack) {
        String id = stack.getOrDefault(ACDataComponents.MEDIA_ID.get(), tracks().getFirst());
        return tracks().contains(id) ? id : tracks().getFirst();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            String media = media(stack);
            var state = AbilityState.load(player);
            if (!TerminalData.get(player).isInstalled(AppRegistry.MEDIA_PLAYER))
                player.sendSystemMessage(Component.translatable("ac.media.notinstalled"));
            else if (!state.installApp("media:" + media))
                player.sendSystemMessage(Component.translatable("ac.media.haveone", getName(stack)));
            else {
                state.save(player);
                ACNetwork.sync(serverPlayer, state);
                if (!player.isCreative()) stack.shrink(1);
                player.sendSystemMessage(Component.translatable("ac.media.acquired",
                        Component.translatable("ac.media." + media + ".name")));
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("ac.media." + media(stack) + ".name");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("ac.media." + media(stack) + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
