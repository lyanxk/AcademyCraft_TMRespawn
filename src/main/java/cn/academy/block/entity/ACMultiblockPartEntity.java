package cn.academy.block.entity;

import cn.academy.registry.ACBlockEntities;
import cn.academy.block.ACMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Invisible collision/link cell used by the original developer and matrix multiblocks. */
public final class ACMultiblockPartEntity extends BlockEntity {
    private BlockPos origin = BlockPos.ZERO;
    private boolean suppressOriginRemoval;
    private boolean hasOrigin;
    private @Nullable BlockState removedOriginState;
    private @Nullable BlockEntity removedOriginEntity;

    public ACMultiblockPartEntity(BlockPos pos, BlockState state) {
        super(ACBlockEntities.MULTIBLOCK_PART.get(), pos, state);
    }

    public BlockPos origin() { return origin; }
    public boolean suppressOriginRemoval() { return suppressOriginRemoval; }
    public void suppressOriginRemovalOnce() { suppressOriginRemoval = true; }

    /** Only a real cell in the origin's rotated template may operate on that origin. */
    public @Nullable BlockState originState() {
        if (!hasOrigin || level == null || !level.hasChunkAt(origin)) return null;
        BlockState state = level.getBlockState(origin);
        return state.getBlock() instanceof ACMachineBlock machine && machine.containsPart(state, origin, worldPosition)
                ? state : null;
    }

    public void captureRemoval() {
        removedOriginState = originState();
        removedOriginEntity = removedOriginState == null ? null : level.getBlockEntity(origin);
    }

    public @Nullable BlockState dropState() { return removedOriginState == null ? originState() : removedOriginState; }
    public @Nullable BlockEntity dropEntity() {
        return removedOriginState == null ? originState() == null ? null : level.getBlockEntity(origin) : removedOriginEntity;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    public void setOrigin(BlockPos origin) {
        this.origin = origin.immutable();
        hasOrigin = true;
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (hasOrigin) tag.putLong("Origin", origin.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        origin = BlockPos.of(tag.getLong("Origin"));
        hasOrigin = tag.contains("Origin");
    }
}
