package cn.academy.ability.vanilla.teleporter.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/** The old chest placement callbacks, with explicit modern double-chest state. */
final class ShiftTeleportChest {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    static boolean handles(Block block) { return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST; }

    static boolean canPlace(Block block, Level level, BlockPos pos) {
        int neighbors = 0;
        for (Direction side : HORIZONTAL) {
            BlockPos neighbor = pos.relative(side);
            if (!level.getBlockState(neighbor).is(block)) continue;
            // Old isDoubleChest looked at actual neighboring blocks, not a state property.
            for (Direction around : HORIZONTAL)
                if (level.getBlockState(neighbor.relative(around)).is(block)) return false;
            if (++neighbors > 1) return false;
        }
        return true; // BlockChest did not call Block.canPlaceBlockAt's replaceability check.
    }

    static void added(Level level, BlockPos pos, Block block) {
        if (!level.getBlockState(pos).is(block)) return;
        surrounding(level, pos, block);
        for (Direction side : HORIZONTAL) {
            BlockPos neighbor = pos.relative(side);
            if (level.getBlockState(neighbor).is(block)) surrounding(level, neighbor, block);
        }
    }

    private static void surrounding(Level level, BlockPos pos, Block block) {
        BlockState state = level.getBlockState(pos);
        Direction facing = state.getValue(ChestBlock.FACING);
        boolean north = level.getBlockState(pos.north()).is(block), south = level.getBlockState(pos.south()).is(block);
        boolean west = level.getBlockState(pos.west()).is(block), east = level.getBlockState(pos.east()).is(block);
        if (north || south) {
            BlockPos other = north ? pos.north() : pos.south();
            facing = level.getBlockState(other).getValue(ChestBlock.FACING) == Direction.WEST ? Direction.WEST : Direction.EAST;
            boolean left = full(level, pos.west()) || full(level, other.west());
            boolean right = full(level, pos.east()) || full(level, other.east());
            if (left && !right) facing = Direction.EAST;
            if (right && !left) facing = Direction.WEST;
        } else if (west || east) {
            BlockPos other = west ? pos.west() : pos.east();
            facing = level.getBlockState(other).getValue(ChestBlock.FACING) == Direction.NORTH ? Direction.NORTH : Direction.SOUTH;
            boolean behind = full(level, pos.north()) || full(level, other.north());
            boolean front = full(level, pos.south()) || full(level, other.south());
            if (behind && !front) facing = Direction.SOUTH;
            if (front && !behind) facing = Direction.NORTH;
        }
        level.setBlock(pos, state.setValue(ChestBlock.FACING, facing), 3);
    }

    static void placedBy(Level level, BlockPos pos, BlockState state, Player player) {
        Block block = state.getBlock();
        Direction facing = player.getDirection().getOpposite();
        Direction neighbor = null;
        for (Direction side : HORIZONTAL) if (level.getBlockState(pos.relative(side)).is(block)) { neighbor = side; break; }
        if (neighbor == null) {
            level.setBlock(pos, state.setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.SINGLE), 3);
            return;
        }
        BlockPos otherPos = pos.relative(neighbor);
        if (neighbor.getAxis() != facing.getAxis()) {
            level.setBlock(otherPos, level.getBlockState(otherPos).setValue(ChestBlock.FACING, facing), 3);
            level.setBlock(pos, state.setValue(ChestBlock.FACING, facing), 3);
        }
        // In the parallel case, keep the orientation selected by checkForSurroundingChests.
        BlockState current = level.getBlockState(pos), other = level.getBlockState(otherPos);
        if (!current.is(block) || !other.is(block)) return;
        facing = current.getValue(ChestBlock.FACING);
        if (facing.getAxis() == neighbor.getAxis() || other.getValue(ChestBlock.FACING) != facing) return;
        ChestType type = neighbor == facing.getClockWise() ? ChestType.LEFT : ChestType.RIGHT;
        level.setBlock(pos, current.setValue(ChestBlock.TYPE, type), 3);
        // Preserve the neighboring block entity's inventory, components and modern water state.
        if (level.getBlockState(otherPos).is(block))
            level.setBlock(otherPos, level.getBlockState(otherPos).setValue(ChestBlock.TYPE, type.getOpposite()), 3);
    }

    private static boolean full(Level level, BlockPos pos) {
        // Old Block.fullBlock was initialized from its default state's opaque-cube predicate.
        return level.getBlockState(pos).getBlock().defaultBlockState().isSolidRender(level, pos);
    }

    private ShiftTeleportChest() {}
}
