package cn.academy.block;

import cn.academy.block.entity.ACImagPhaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;

/** Imaginary phase liquid keeps a block entity solely for its legacy three-layer visual effect. */
public final class ACImagPhaseBlock extends LiquidBlock implements EntityBlock {
    public ACImagPhaseBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
        super(fluid, properties);
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        // No bucket is registered. Vanilla LiquidBlock would remove the source and then return AIR.
        return ItemStack.EMPTY;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ACImagPhaseBlockEntity(pos, state);
    }
}
