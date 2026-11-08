package cn.academy.block;

import cn.academy.block.entity.ACMultiblockPartEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Internal, itemless block that restores collision and interaction over legacy multiblock models. */
public final class ACMultiblockPartBlock extends Block implements EntityBlock {
    public ACMultiblockPartBlock(Properties properties) { super(properties); }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ACMultiblockPartEntity(pos, state);
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ACMultiblockPartEntity part && part.originState() != null)
            return part.originState().getDestroyProgress(player, level, pos);
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return level.getBlockEntity(pos) instanceof ACMultiblockPartEntity part && part.originState() != null
                && part.originState().canHarvestBlock(level, pos, player);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof ACMultiblockPartEntity part && part.originState() != null)
            return part.originState().getBlock().getCloneItemStack(level, part.origin(), part.originState());
        return ItemStack.EMPTY;
    }

    @Override
    protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof ACMultiblockPartEntity part && part.originState() != null)
            level.levelEvent(player, 2001, pos, Block.getId(part.originState()));
        else super.spawnDestroyParticles(level, player, pos, state);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        // PlayerGameMode retains this tile across removal; the rest of the machine is already gone.
        if (blockEntity instanceof ACMultiblockPartEntity part && part.dropState() != null)
            part.dropState().getBlock().playerDestroy(level, player, pos, part.dropState(), part.dropEntity(), tool);
        else super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity entity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(entity instanceof ACMultiblockPartEntity part) || part.dropState() == null) return List.of();
        BlockState previous = params.getOptionalParameter(LootContextParams.BLOCK_STATE);
        try {
            return part.dropState().getDrops(params.withOptionalParameter(LootContextParams.BLOCK_ENTITY, part.dropEntity()));
        } finally {
            params.withOptionalParameter(LootContextParams.BLOCK_ENTITY, entity)
                    .withOptionalParameter(LootContextParams.BLOCK_STATE, previous);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof ACMultiblockPartEntity part)) return;
        if (!level.hasChunkAt(part.origin())) {
            level.scheduleTick(pos, this, 20);
            return;
        }
        BlockState origin = part.originState();
        if (origin == null) return;
        BlockState expected = ((ACMachineBlock) origin.getBlock()).partState();
        if (state.is(expected.getBlock())) return;
        // Migrate the old shared part ID without dismantling the machine or touching its inventory.
        BlockPos originPos = part.origin();
        part.suppressOriginRemovalOnce();
        level.setBlock(pos, expected, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof ACMultiblockPartEntity replacement) replacement.setOrigin(originPos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        return ACMachineBlock.activate(level, pos, player, hand) == InteractionResult.SUCCESS
                ? ItemInteractionResult.SUCCESS : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return ACMachineBlock.activate(level, pos, player, InteractionHand.MAIN_HAND);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ACMultiblockPartEntity part
                && !part.suppressOriginRemoval() && part.originState() != null) {
            part.captureRemoval();
            // The initiating harvest/explosion owns the one block drop. Structural cleanup never drops another.
            level.removeBlock(part.origin(), false);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
