package cn.academy.item;

import cn.academy.ability.vanilla.electromaster.skill.ElectricAttackTrace;
import cn.academy.event.MatterUnitHarvestEvent;
import cn.academy.registry.ACBlocks;
import cn.academy.registry.ACDataComponents;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;

/** Matter collection/placement with component-backed material and vanilla bucket inventory handling. */
public final class MatterUnitItem extends Item {
    public record MatterMaterial(String name, Supplier<? extends Block> block) {}
    public static final MatterMaterial MAT_NONE = new MatterMaterial("none", () -> Blocks.AIR);
    public static final MatterMaterial MAT_PHASE_LIQUID = new MatterMaterial("phase_liquid", ACBlocks.IMAG_PHASE);
    public static final List<MatterMaterial> MATERIALS = List.of(MAT_NONE, MAT_PHASE_LIQUID);

    public MatterUnitItem(Properties properties) {
        super(properties.component(ACDataComponents.PHASE_FILLED.get(), false));
    }

    public static boolean isFilled(ItemStack stack) {
        return stack.getOrDefault(ACDataComponents.PHASE_FILLED.get(), false);
    }

    public static void setFilled(ItemStack stack, boolean filled) {
        stack.set(ACDataComponents.PHASE_FILLED.get(), filled);
        if (filled) stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
        else stack.remove(DataComponents.CUSTOM_MODEL_DATA);
    }

    public static MatterMaterial getMaterial(ItemStack stack) {
        return stack.getItem() instanceof MatterUnitItem ? (isFilled(stack) ? MAT_PHASE_LIQUID : MAT_NONE) : null;
    }

    public ItemStack create(MatterMaterial material) {
        if (!MATERIALS.contains(material)) throw new IllegalArgumentException("Unknown matter material");
        ItemStack stack = new ItemStack(this);
        setFilled(stack, material == MAT_PHASE_LIQUID);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.ac_matter_unit_" + getMaterial(stack).name() + ".name");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean isNone = getMaterial(stack) == MAT_NONE;
        var start = player.getEyePosition();
        // Forge 1.12.2 Item.rayTrace used REACH_DISTANCE (default 5 in both game modes).
        // Preserve modern attribute modifiers while normalizing survival's 4.5 default to 5.
        double reach = player.blockInteractionRange() + (player.isCreative() ? 0 : .5);
        var end = start.add(player.getLookAngle().scale(reach));
        var hit = ElectricAttackTrace.traceBlockSegment(player, start, end, isNone
                ? state -> !(state.getBlock() instanceof LiquidBlock) || state.getFluidState().isSource() : null);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
        var pos = hit.getBlockPos();
        if (!world.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), stack))
            return InteractionResultHolder.pass(stack);
        // World and inventory changes are authoritative on the server; the client predicts only success.
        if (world.isClientSide) return InteractionResultHolder.success(stack);

        if (isNone) {
            Block block = world.getBlockState(pos).getBlock();
            for (var material : MATERIALS) {
                if (material.block().get() != block) continue;
                stack = ItemUtils.createFilledResult(stack, player, create(material));
                // Level.removeBlock preserves a block's fluid: it would leave this source in place.
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                NeoForge.EVENT_BUS.post(new MatterUnitHarvestEvent(player, material));
                break;
            }
        } else {
            var target = world.getBlockState(pos).canBeReplaced() ? pos : pos.relative(hit.getDirection());
            // Original intentionally sets the adjacent block without a replaceability check there.
            world.setBlock(target, getMaterial(stack).block().get().defaultBlockState(), 3);
            // Like BucketItem.getEmptySuccessItem: creative placement keeps the filled container.
            stack = ItemUtils.createFilledResult(stack, player,
                    player.hasInfiniteMaterials() ? stack : create(MAT_NONE));
            NeoForge.EVENT_BUS.post(new MatterUnitHarvestEvent(player, MAT_NONE));
        }
        player.getInventory().setChanged();
        return InteractionResultHolder.success(stack);
    }

}
