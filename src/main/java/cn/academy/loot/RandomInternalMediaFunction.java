package cn.academy.loot;

import cn.academy.item.MediaItem;
import cn.academy.registry.ACLootFunctions;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;

/** ACItemAdditionalRegistry.funcMedia: choose a track only after the media entry wins. */
public final class RandomInternalMediaFunction extends LootItemConditionalFunction {
    public static final MapCodec<RandomInternalMediaFunction> CODEC = RecordCodecBuilder.mapCodec(
            instance -> commonFields(instance).apply(instance, RandomInternalMediaFunction::new));

    private RandomInternalMediaFunction(List<LootItemCondition> conditions) { super(conditions); }

    @Override
    public LootItemFunctionType<RandomInternalMediaFunction> getType() { return ACLootFunctions.RANDOM_INTERNAL_MEDIA.get(); }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        if (!stack.isEmpty())
            MediaItem.setMedia(stack, MediaItem.TRACKS.get(context.getRandom().nextInt(MediaItem.TRACKS.size())));
        return stack;
    }
}
