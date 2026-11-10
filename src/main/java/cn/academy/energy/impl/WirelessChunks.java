package cn.academy.energy.impl;

import cn.academy.AcademyCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletionException;

/** The original VBlock.isLoaded actually calls World.isChunkGeneratedAt, not chunkExists. */
public final class WirelessChunks {
    private WirelessChunks() {}

    /** Checks loaded chunks and persisted/pending chunk data without generating a missing chunk. */
    public static boolean isGenerated(ServerLevel level, BlockPos pos) {
        if (level.hasChunkAt(pos)) return true;
        try {
            // The server's IOWorker sees both pending saves and region files. Do not open a second
            // RegionFileStorage or cache negative results: either would miss a subsequent save.
            return level.getChunkSource().chunkMap.read(new ChunkPos(pos)).join().isPresent();
        } catch (CompletionException error) {
            AcademyCraft.LOGGER.warn("Cannot check wireless chunk {} in {}; retaining its links",
                    new ChunkPos(pos), level.dimension().location(), error);
            return false;
        }
    }

    /** Like VBlock.get after its isLoaded gate: existing disk chunks may be brought into memory. */
    public static @Nullable BlockEntity getGenerated(ServerLevel level, long position) {
        BlockPos pos = BlockPos.of(position);
        if (!isGenerated(level, pos)) return null;
        BlockEntity block = level.getBlockEntity(pos);
        return block != null && !block.isRemoved() ? block : null;
    }
}
