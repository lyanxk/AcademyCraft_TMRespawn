package cn.academy.ability.vanilla.teleporter.skill;

import cn.academy.AcademyCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import java.util.function.Supplier;

/** Adapters for old ItemBlock eligibility and blocks whose placement APIs changed shape. */
final class ShiftTeleportPlacement {
    private static final TagKey<Item> NON_BLOCK_ITEMS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "shift_teleport_excluded_items"));
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    static boolean validItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item && item.getBlock() != Blocks.AIR
                && !stack.is(NON_BLOCK_ITEMS);
    }

    static boolean canPlace(Block block, Level level, BlockPos pos) {
        if (ShiftTeleportChest.handles(block)) return ShiftTeleportChest.canPlace(block, level, pos);
        if (block == Blocks.SNOW) {
            BlockPos below = pos.below();
            BlockState support = level.getBlockState(below);
            // BlockSnow did not call the base replaceability check or the modern snow tags.
            return !support.is(Blocks.ICE) && !support.is(Blocks.PACKED_ICE) && !support.is(Blocks.BARRIER)
                    && (support.isFaceSturdy(level, below, Direction.UP) || support.getBlock() instanceof LeavesBlock
                    || support.is(Blocks.SNOW) && support.getValue(SnowLayerBlock.LAYERS) == 8);
        }
        if (rail(block)) return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        if (vanilla(block) && block instanceof BannerBlock) {
            if (!level.getBlockState(pos).canBeReplaced()) return false;
            for (Direction side : HORIZONTAL) if (level.getBlockState(pos.relative(side)).is(Blocks.CACTUS)) return false;
            return true;
        }
        if (doublePlant(block)) return level.getBlockState(pos).canBeReplaced() && level.isEmptyBlock(pos.above())
                && block.defaultBlockState().canSurvive(level, pos);
        if (torch(block) || button(block) || block == Blocks.LEVER) {
            // These old canPlaceBlockAt overrides did not call Block's replaceability check.
            for (Direction side : Direction.values())
                if (torch(block) ? torchSupport(level, pos, side) : attachment(level, pos, side)) return true;
            return false;
        }
        // BlockLadder only overrode canPlaceBlockOnSide, not the method STContext actually calls.
        if (block == Blocks.LADDER) return level.getBlockState(pos).canBeReplaced();
        return level.getBlockState(pos).canBeReplaced() && block.defaultBlockState().canSurvive(level, pos);
    }

    static BlockState state(Block block, Level level, BlockPos queryPos, BlockPos destination, Direction face,
                            float absoluteHitY, Player player, ItemStack stack, Supplier<BlockState> modern) {
        boolean bottom = face != Direction.DOWN && (face == Direction.UP || absoluteHitY <= .5f);
        if (ShiftTeleportChest.handles(block)) return block.defaultBlockState().setValue(ChestBlock.FACING, player.getDirection());
        if (dispenser(block)) return block.defaultBlockState().setValue(DispenserBlock.FACING, entityFacing(queryPos, player));
        if (piston(block)) return block.defaultBlockState().setValue(PistonBaseBlock.FACING, entityFacing(queryPos, player));
        if (block == Blocks.OBSERVER) return block.defaultBlockState().setValue(ObserverBlock.FACING, entityFacing(queryPos, player).getOpposite());
        if (vanilla(block) && block instanceof SlabBlock) {
            // STContext never calls ItemSlab.onItemUse: it cannot merge the hit slab into a double slab.
            return block.defaultBlockState().setValue(SlabBlock.TYPE, bottom ? SlabType.BOTTOM : SlabType.TOP);
        }
        if (vanilla(block) && block instanceof StairBlock) {
            BlockState state = block.defaultBlockState().setValue(StairBlock.FACING, player.getDirection())
                    .setValue(StairBlock.HALF, bottom ? Half.BOTTOM : Half.TOP).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
            // Old getActualState derived the shape at the placed cell, not at the ray's hit cell.
            return state.updateShape(Direction.NORTH, level.getBlockState(destination.north()), level, destination, destination.north());
        }
        if (doublePlant(block) || rail(block) || block == Blocks.SNOW) return block.defaultBlockState();
        if (block == Blocks.OAK_TRAPDOOR || block == Blocks.IRON_TRAPDOOR) {
            boolean powered = level.hasNeighborSignal(queryPos);
            return block.defaultBlockState()
                    .setValue(TrapDoorBlock.FACING, face.getAxis().isHorizontal() ? face : player.getDirection().getOpposite())
                    .setValue(TrapDoorBlock.HALF, bottom ? Half.BOTTOM : Half.TOP)
                    .setValue(TrapDoorBlock.OPEN, powered).setValue(TrapDoorBlock.POWERED, powered);
        }
        if (vanilla(block) && block instanceof BannerBlock banner) {
            int metadata = 15 - banner.getColor().getId(); // old EnumDyeColor.getDyeDamage
            DyeColor base = stack.getOrDefault(DataComponents.BASE_COLOR, DyeColor.BLACK);
            var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (data != null && data.copyTag().contains("Base")) {
                int oldBase = data.copyTag().getInt("Base");
                base = DyeColor.byId(15 - (oldBase >= 0 && oldBase < 16 ? oldBase : 0));
            }
            // ItemBanner.onItemUse normally sets the color, but this skill bypasses that method.
            return BannerBlock.byColor(base).defaultBlockState().setValue(BannerBlock.ROTATION, metadata);
        }
        if (torch(block)) {
            Direction selected = Direction.UP;
            if (torchSupport(level, queryPos, face)) selected = face;
            else for (Direction side : HORIZONTAL) if (torchSupport(level, queryPos, side)) { selected = side; break; }
            if (selected == Direction.UP) return block.defaultBlockState();
            Block wall = block == Blocks.TORCH ? Blocks.WALL_TORCH : Blocks.REDSTONE_WALL_TORCH;
            return wall.defaultBlockState().setValue(WallTorchBlock.FACING, selected);
        }
        if (block == Blocks.LADDER) {
            Direction selected = Direction.NORTH;
            if (face.getAxis().isHorizontal() && ladderSupport(level, queryPos, face)) selected = face;
            else for (Direction side : HORIZONTAL) if (ladderSupport(level, queryPos, side)) { selected = side; break; }
            // The old ladder had no waterlogged property; placing it replaces the destination fluid.
            return block.defaultBlockState().setValue(LadderBlock.FACING, selected);
        }
        if (button(block)) {
            Direction selected = attachment(level, queryPos, face) ? face : Direction.DOWN;
            return attachedState(block, selected, Direction.NORTH);
        }
        if (block == Blocks.LEVER) {
            // Old default NORTH; fallback order is N/E/S/W, then the top of the block below.
            Direction selected = Direction.NORTH;
            if (attachment(level, queryPos, face)) selected = face;
            else {
                boolean found = false;
                for (Direction side : HORIZONTAL) if (side != face && attachment(level, queryPos, side)) {
                    selected = side; found = true; break;
                }
                if (!found && topSolid(level, queryPos.below())) selected = Direction.UP;
            }
            // Map old UP_X/DOWN_X and UP_Z/DOWN_Z to identical block-model rotations.
            Direction axis = player.getDirection().getAxis() == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
            return attachedState(block, selected, axis);
        }
        return modern.get();
    }

    static void applyComponents(BlockEntity tile, ItemStack stack) {
        if (tile instanceof BannerBlockEntity banner && vanilla(tile.getBlockState().getBlock())) {
            ItemStack transferred = stack.copy();
            // The original bypass also skipped ItemBanner.setItemValues' item display-name copy.
            // Preserve a name explicitly read from BlockEntityTag, rather than the item's name.
            transferred.remove(DataComponents.CUSTOM_NAME);
            if (banner.getCustomName() != null) transferred.set(DataComponents.CUSTOM_NAME, banner.getCustomName());
            tile.applyComponentsFromItemStack(transferred);
        } else tile.applyComponentsFromItemStack(stack);
    }

    static void placedBy(Level level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        if (ShiftTeleportChest.handles(state.getBlock())) {
            ShiftTeleportChest.placedBy(level, pos, state, player);
        } else if (dispenser(state.getBlock()) || piston(state.getBlock())) {
            // The original recomputed facing at the destination, then passed the pre-write state
            // to the piston's power check. Its modern setPlacedBy retains that power check.
            level.setBlock(pos, state.setValue(DirectionalBlock.FACING, entityFacing(pos, player)), 2);
            state.getBlock().setPlacedBy(level, pos, state, player, stack);
        } else if (doublePlant(state.getBlock())) {
            // BlockDoublePlant.onBlockPlacedBy wrote the upper half with flag 2.
            level.setBlock(pos.above(), state.getBlock().defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 2);
        } else state.getBlock().setPlacedBy(level, pos, state, player, stack);
    }

    static void afterPlacement(Block original, Level level, BlockPos pos, BlockState previous) {
        if (previous.is(original)) return; // Old onBlockAdded only ran when the block type changed.
        if (dispenser(original) && level.getBlockState(pos).is(original)) {
            BlockState state = level.getBlockState(pos);
            Direction facing = state.getValue(DispenserBlock.FACING);
            if (facing.getAxis().isHorizontal() && fullBlock(level, pos.relative(facing))
                    && !fullBlock(level, pos.relative(facing.getOpposite()))) facing = facing.getOpposite();
            level.setBlock(pos, state.setValue(DispenserBlock.FACING, facing).setValue(DispenserBlock.TRIGGERED, false), 2);
            return;
        }
        if (original == Blocks.OBSERVER && level.getBlockState(pos).is(original)) {
            // Old placement always started one two-tick observation pulse.
            if (!level.getBlockState(pos).getValue(ObserverBlock.POWERED)
                    && !level.getBlockTicks().hasScheduledTick(pos, original)) level.scheduleTick(pos, original, 2);
            return;
        }
        if (ShiftTeleportChest.handles(original)) {
            ShiftTeleportChest.added(level, pos, original);
            return;
        }
        if (!torch(original)) return;
        BlockState state = level.getBlockState(pos);
        Block wall = original == Blocks.TORCH ? Blocks.WALL_TORCH : Blocks.REDSTONE_WALL_TORCH;
        if (previous.is(wall)) return; // Standing/wall variants were one block in 1.12.2.
        if (!state.is(original) && !state.is(wall)) return;
        // Old BlockTorch.onBlockAdded called checkForDrop (any supported side, not its selected side).
        if (!canPlace(original, level, pos)) {
            Block.dropResources(state, level, pos, level.getBlockEntity(pos));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static BlockState attachedState(Block block, Direction face, Direction verticalAxis) {
        AttachFace attachment = face == Direction.UP ? AttachFace.FLOOR : face == Direction.DOWN ? AttachFace.CEILING : AttachFace.WALL;
        return block.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, attachment)
                .setValue(HorizontalDirectionalBlock.FACING, face.getAxis().isHorizontal() ? face : verticalAxis);
    }

    private static boolean torch(Block block) { return block == Blocks.TORCH || block == Blocks.REDSTONE_TORCH; }
    private static boolean dispenser(Block block) { return block == Blocks.DISPENSER || block == Blocks.DROPPER; }
    private static boolean piston(Block block) { return block == Blocks.PISTON || block == Blocks.STICKY_PISTON; }
    private static boolean fullBlock(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock().defaultBlockState().isSolidRender(level, pos);
    }
    private static Direction entityFacing(BlockPos pos, Player player) {
        // EnumFacing.getDirectionFromEntityLiving used float-rounded block centers and strict bounds.
        if (Math.abs(player.getX() - (double)((float)pos.getX() + .5f)) < 2
                && Math.abs(player.getZ() - (double)((float)pos.getZ() + .5f)) < 2) {
            double eye = player.getY() + (double)player.getEyeHeight();
            if (eye - pos.getY() > 2) return Direction.UP;
            if (pos.getY() - eye > 0) return Direction.DOWN;
        }
        return player.getDirection().getOpposite();
    }
    private static boolean button(Block block) { return block == Blocks.STONE_BUTTON || block == Blocks.OAK_BUTTON; }
    private static boolean vanilla(Block block) { return BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft"); }
    private static boolean rail(Block block) {
        return block == Blocks.RAIL || block == Blocks.POWERED_RAIL || block == Blocks.DETECTOR_RAIL || block == Blocks.ACTIVATOR_RAIL;
    }
    private static boolean doublePlant(Block block) {
        return block == Blocks.SUNFLOWER || block == Blocks.LILAC || block == Blocks.TALL_GRASS
                || block == Blocks.LARGE_FERN || block == Blocks.ROSE_BUSH || block == Blocks.PEONY;
    }

    private static boolean torchSupport(Level level, BlockPos pos, Direction face) {
        if (face == Direction.DOWN) return false;
        BlockPos support = pos.relative(face.getOpposite());
        BlockState state = level.getBlockState(support);
        if (face == Direction.UP) {
            if (state.is(Blocks.END_GATEWAY) || state.is(Blocks.JACK_O_LANTERN)) return false;
            return topSolid(level, support) || state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock
                    || state.is(Blocks.GLASS) || state.getBlock() instanceof StainedGlassBlock
                    || !attachmentException(state, false)
                    && state.isFaceSturdy(level, support, face, SupportType.CENTER);
        }
        return !attachmentException(state, true) && state.isFaceSturdy(level, support, face);
    }

    private static boolean ladderSupport(Level level, BlockPos pos, Direction face) {
        BlockPos support = pos.relative(face.getOpposite());
        BlockState state = level.getBlockState(support);
        return !attachmentException(state, true) && state.isFaceSturdy(level, support, face) && !state.isSignalSource();
    }

    private static boolean attachment(Level level, BlockPos pos, Direction face) {
        BlockPos support = pos.relative(face.getOpposite());
        BlockState state = level.getBlockState(support);
        return face == Direction.UP && state.is(Blocks.HOPPER)
                || !attachmentException(state, face != Direction.UP) && state.isFaceSturdy(level, support, face);
    }

    private static boolean topSolid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.canOcclude() && state.isFaceSturdy(level, pos, Direction.UP);
    }

    private static boolean attachmentException(BlockState state, boolean pistons) {
        Block block = state.getBlock();
        return block instanceof ShulkerBoxBlock || block instanceof LeavesBlock || block instanceof TrapDoorBlock
                || block instanceof StainedGlassBlock || state.is(Blocks.BEACON) || state.is(Blocks.CAULDRON)
                || state.is(Blocks.GLASS) || state.is(Blocks.GLOWSTONE) || state.is(Blocks.ICE) || state.is(Blocks.SEA_LANTERN)
                || pistons && (state.is(Blocks.PISTON) || state.is(Blocks.STICKY_PISTON) || state.is(Blocks.PISTON_HEAD));
    }

    private ShiftTeleportPlacement() {}
}
