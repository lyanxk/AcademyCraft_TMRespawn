package cn.academy.block.entity;

import cn.academy.registry.ACBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Marker block entity used by the client-side imaginary phase liquid renderer. */
public final class ACImagPhaseBlockEntity extends BlockEntity {
    public ACImagPhaseBlockEntity(BlockPos pos, BlockState state) {
        super(ACBlockEntities.IMAG_PHASE.get(), pos, state);
    }
}
