package cn.academy.block;

import cn.academy.data.GameplayRules;

import cn.academy.block.entity.ACMachineBlockEntity;
import cn.academy.block.entity.ACMultiblockPartEntity;
import cn.academy.registry.ACBlocks;
import cn.academy.registry.ACBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ACMachineBlock extends Block implements EntityBlock {
    public static final IntegerProperty VISUAL_STAGE = IntegerProperty.create("visual_stage", 0, 4);
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private final MachineKind kind;

    public ACMachineBlock(MachineKind kind, BlockBehaviour.Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(VISUAL_STAGE, 0).setValue(CONNECTED, false).setValue(WORKING, false)
                .setValue(FACING, Direction.NORTH));
    }

    public MachineKind kind() {
        return kind;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        return activate(level, pos, player, hand) == InteractionResult.SUCCESS
                ? ItemInteractionResult.SUCCESS : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return activate(level, pos, player, InteractionHand.MAIN_HAND);
    }

    /** Original block activation after Minecraft's item/sneak/event routing, including multiblock parts. */
    public static InteractionResult activate(Level level, BlockPos clickedPos, Player player, InteractionHand hand) {
        BlockPos pos;
        if (level.getBlockEntity(clickedPos) instanceof ACMultiblockPartEntity part) {
            if (part.originState() == null) return InteractionResult.PASS;
            pos = part.origin();
        } else pos = clickedPos;
        if (!(level.getBlockEntity(pos) instanceof ACMachineBlockEntity machine)) return InteractionResult.PASS;
        MachineKind kind = machine.kind();
        if (kind == MachineKind.WIND_PILLAR || kind == MachineKind.EU_INPUT || kind == MachineKind.EU_OUTPUT)
            return InteractionResult.PASS;
        if (kind == MachineKind.WIND_BASE && player.getItemInHand(hand).is(cn.academy.registry.ACItems.WINDGEN_PILLAR.get()))
            return InteractionResult.PASS;
        if (kind == MachineKind.CAT_ENGINE) {
            if (player instanceof ServerPlayer serverPlayer) machine.interactCatEngine(serverPlayer);
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (kind.isBridgeInput() || kind.isBridgeOutput()) {
            if (player instanceof ServerPlayer serverPlayer)
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        new cn.academy.network.OpenClientScreenPayload("bridge:" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ()));
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (kind == MachineKind.DEVELOPER_NORMAL || kind == MachineKind.DEVELOPER_ADVANCED)
                machine.useDeveloper(serverPlayer);
            else serverPlayer.openMenu(machine, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    /** Machines whose 1.12.2 blocks were invisible, non-opaque TESR hosts. */
    public static boolean usesLegacyBlockEntityModel(MachineKind kind) {
        return switch (kind) {
            case CAT_ENGINE, SOLAR_GENERATOR, PHASE_GENERATOR, MATRIX, WIND_BASE, WIND_PILLAR,
                    WIND_GENERATOR, DEVELOPER_NORMAL, DEVELOPER_ADVANCED -> true;
            default -> false;
        };
    }

    public int[][] multiblockOffsets() {
        return GameplayRules.offsets(kind);
    }

    public BlockState partState() {
        return (kind == MachineKind.MATRIX ? ACBlocks.MATRIX_PART : ACBlocks.MULTIBLOCK_PART).get().defaultBlockState();
    }

    public boolean containsPart(BlockState state, BlockPos origin, BlockPos candidate) {
        for (int[] offset : orientedOffsets(state))
            if (origin.offset(offset[0], offset[1], offset[2]).equals(candidate)) return true;
        return false;
    }

    private int[][] orientedOffsets(Direction facing) {
        int[][] source = multiblockOffsets();
        if (facing == Direction.NORTH) return source;
        int[][] result = new int[source.length][3];
        for (int index = 0; index < source.length; index++) {
            int x = source[index][0], y = source[index][1], z = source[index][2];
            result[index] = switch (facing) {
                // Legacy templates face north and extend behind their front towards positive Z.
                case EAST -> new int[]{-z, y, x};
                case SOUTH -> new int[]{-x, y, -z};
                case WEST -> new int[]{z, y, -x};
                default -> new int[]{x, y, z};
            };
        }
        return result;
    }

    private int[][] orientedOffsets(BlockState state) {
        return orientedOffsets(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos origin = context.getClickedPos();
        for (int[] offset : orientedOffsets(facing)) {
            BlockPos part = origin.offset(offset[0], offset[1], offset[2]);
            if (!context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        if (level.getBlockEntity(pos) instanceof ACMachineBlockEntity machine) machine.setPlacer(placer);
        for (int[] offset : orientedOffsets(state)) {
            BlockPos partPos = pos.offset(offset[0], offset[1], offset[2]);
            level.setBlock(partPos, partState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(partPos) instanceof ACMultiblockPartEntity part) part.setOrigin(pos);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VISUAL_STAGE, CONNECTED, FACING, WORKING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return usesLegacyBlockEntityModel(kind) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ACMachineBlockEntity.create(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ACMachineBlockEntity machine) {
                // Wireless managers observe the missing tile on their END tick, as in 1.12.2.
                machine.dropContents();
            }
            for (int[] offset : orientedOffsets(state)) {
                BlockPos partPos = pos.offset(offset[0], offset[1], offset[2]);
                if (level.getBlockState(partPos).getBlock() instanceof ACMultiblockPartBlock
                        && level.getBlockEntity(partPos) instanceof ACMultiblockPartEntity part
                        && part.origin().equals(pos)) {
                    part.suppressOriginRemovalOnce();
                    level.removeBlock(partPos, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (type != ACBlockEntities.MACHINE.get()) return null;
        return (tickerLevel, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof ACMachineBlockEntity machine) {
                if (tickerLevel.isClientSide) ACMachineBlockEntity.clientTick(tickerLevel, pos, blockState, machine);
                else ACMachineBlockEntity.serverTick(tickerLevel, pos, blockState, machine);
            }
        };
    }
}
