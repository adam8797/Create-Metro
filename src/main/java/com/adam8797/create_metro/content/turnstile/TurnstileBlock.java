package com.adam8797.create_metro.content.turnstile;

import com.adam8797.create_metro.MetroBlockEntityTypes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.ithundxr.createnumismatics.content.backend.TrustedBlock;
import dev.ithundxr.createnumismatics.registry.NumismaticsTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TurnstileBlock extends Block implements IWrenchable, IBE<TurnstileBlockEntity>, TrustedBlock {

    public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** Merged with the neighbour on the counter-clockwise (left, relative to facing) side. */
    public static final BooleanProperty MERGE_LEFT = BooleanProperty.create("merge_left");
    /** Merged with the neighbour on the clockwise (right, relative to facing) side. */
    public static final BooleanProperty MERGE_RIGHT = BooleanProperty.create("merge_right");
    /** When open, the leaf swings against the facing (free egress) rather than with it. */
    public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");

    // Closed barrier: a slab that blocks passage along the facing axis. Two blocks tall so it can't be
    // jumped over (the visual model is still one block tall — taller art comes later).
    private static final VoxelShape SHAPE_BLOCKS_Z = Shapes.box(0, 0, 0.375, 1, 2.0, 0.625);
    private static final VoxelShape SHAPE_BLOCKS_X = Shapes.box(0.375, 0, 0, 0.625, 2.0, 1);

    public TurnstileBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HORIZONTAL_FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(MERGE_LEFT, false)
                .setValue(MERGE_RIGHT, false)
                .setValue(REVERSED, false));
    }

    // ------------------------------------------------------------------
    // Shape (collision barrier is per-cell; merging is purely visual)
    // ------------------------------------------------------------------

    private static VoxelShape barrierShape(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis() == Direction.Axis.Z ? SHAPE_BLOCKS_Z : SHAPE_BLOCKS_X;
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return barrierShape(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull VoxelShape getCollisionShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        // The barrier is empty only for a player who has paid (or is otherwise allowed through); it stays
        // solid for everyone else, so a tailgater can't slip through behind them.
        if (context instanceof EntityCollisionContext ec && ec.getEntity() instanceof Player player
                && level.getBlockEntity(pos) instanceof TurnstileBlockEntity be && be.isAuthorized(player))
            return Shapes.empty();
        return barrierShape(state);
    }

    // ------------------------------------------------------------------
    // Placement / states / merging
    // ------------------------------------------------------------------

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HORIZONTAL_FACING, context.getHorizontalDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING, OPEN, MERGE_LEFT, MERGE_RIGHT, REVERSED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        // Facing rotates; the merge flags are facing-relative so they carry over unchanged.
        return state.setValue(HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state.rotate(mirror.getRotation(state.getValue(HORIZONTAL_FACING)));
        // mirroring flips the left/right sides
        return mirrored
                .setValue(MERGE_LEFT, state.getValue(MERGE_RIGHT))
                .setValue(MERGE_RIGHT, state.getValue(MERGE_LEFT));
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof TurnstileBlockEntity be) {
            if (placer instanceof Player player)
                be.setOwner(player.getUUID());
            be.initFromConfig();
        }
        // Greedily merge with same-facing neighbours, then adopt the group's shared config.
        applyGreedyMerge(level, pos, level.getBlockState(pos));
        if (level.getBlockEntity(pos) instanceof TurnstileBlockEntity be)
            be.adoptGroupConfig();
    }

    private static Direction leftOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static Direction rightOf(Direction facing) {
        return facing.getClockWise();
    }

    /** True if the block on the given side is a turnstile with the same facing (eligible to merge). */
    /** A same-facing turnstile neighbour that is itself unpaired (so grouping stays limited to two). */
    private static boolean unpairedSameFacingTurnstile(Level level, BlockPos pos, Direction facing, Direction side) {
        BlockState ns = level.getBlockState(pos.relative(side));
        return ns.getBlock() instanceof TurnstileBlock && ns.getValue(HORIZONTAL_FACING) == facing
                && !ns.getValue(MERGE_LEFT) && !ns.getValue(MERGE_RIGHT);
    }

    /** Set one merge flag on a neighbour, if that neighbour is a same-facing turnstile. */
    private static void setNeighbourFlag(Level level, BlockPos neighbourPos, Direction facing, BooleanProperty flag, boolean value) {
        BlockState ns = level.getBlockState(neighbourPos);
        if (ns.getBlock() instanceof TurnstileBlock && ns.getValue(HORIZONTAL_FACING) == facing && ns.getValue(flag) != value)
            level.setBlock(neighbourPos, ns.setValue(flag, value), Block.UPDATE_ALL);
    }

    /** Merge this gate with any same-facing perpendicular neighbours (placement / post-rotate only). */
    /**
     * Pair this gate with ONE adjacent same-facing turnstile (chest rules): a lone gate joins a lone
     * neighbour, but a third gate beside an existing pair stays separate. Left side is preferred.
     */
    private static void applyGreedyMerge(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof TurnstileBlock))
            return;
        if (state.getValue(MERGE_LEFT) || state.getValue(MERGE_RIGHT))
            return; // already part of a pair
        Direction facing = state.getValue(HORIZONTAL_FACING);

        Direction left = leftOf(facing);
        Direction right = rightOf(facing);
        if (unpairedSameFacingTurnstile(level, pos, facing, left)) {
            level.setBlock(pos, state.setValue(MERGE_LEFT, true), Block.UPDATE_ALL);
            setNeighbourFlag(level, pos.relative(left), facing, MERGE_RIGHT, true);
        } else if (unpairedSameFacingTurnstile(level, pos, facing, right)) {
            level.setBlock(pos, state.setValue(MERGE_RIGHT, true), Block.UPDATE_ALL);
            setNeighbourFlag(level, pos.relative(right), facing, MERGE_LEFT, true);
        }
    }

    // ------------------------------------------------------------------
    // Redstone: emit a signal while the gate is open (drive doors / fare gates).
    // ------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction direction) {
        return state.getValue(OPEN) ? 15 : 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(@NotNull BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (state.getValue(OPEN))
            level.setBlock(pos, state.setValue(OPEN, false), Block.UPDATE_ALL);
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Entity entity) {
        if (level.isClientSide)
            return;
        if (!(entity instanceof Player player) || player.isSpectator())
            return;
        withBlockEntityDo(level, pos, be -> be.onPlayerWalkThrough(player));
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                       Player player, InteractionHand hand, BlockHitResult hitResult) {
        // Empty hand: defer to useWithoutItem (open the GUI / show status). NOTE: useItemOn is also
        // invoked with an empty stack, so this must PASS rather than SKIP or the GUI never opens.
        if (stack.isEmpty())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        // A bank card pays the fare.
        if (NumismaticsTags.AllItemTags.CARDS.matches(stack)) {
            if (level.isClientSide)
                return ItemInteractionResult.SUCCESS;
            if (level.getBlockEntity(pos) instanceof TurnstileBlockEntity be)
                be.payWithCard(stack, player);
            return ItemInteractionResult.CONSUME;
        }

        // Any other held item (wrench, blocks, tools, ID cards) acts through its own useOn — the wrench
        // rotates; nothing here should open the GUI.
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TurnstileBlockEntity be))
            return InteractionResult.PASS;

        if (isTrusted(player, level, pos)) {
            openConfig(player, pos, be);
            return InteractionResult.CONSUME;
        }
        be.showStatus(player);
        return InteractionResult.SUCCESS;
    }

    private static void openConfig(Player player, BlockPos pos, TurnstileBlockEntity be) {
        if (player instanceof ServerPlayer serverPlayer)
            serverPlayer.openMenu(be, buf -> buf.writeBlockPos(pos));
    }

    // ------------------------------------------------------------------
    // Wrench: unmerge one neighbour per click, then rotate; greedily re-merge on returning to a
    // facing that has a same-facing neighbour. (solo = 4 clicks/turn, +1 per merged neighbour.)
    // ------------------------------------------------------------------

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!isTrusted(player, level, pos))
            return InteractionResult.FAIL;
        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        Direction facing = state.getValue(HORIZONTAL_FACING);
        if (state.getValue(MERGE_RIGHT)) {
            level.setBlock(pos, state.setValue(MERGE_RIGHT, false), Block.UPDATE_ALL);
            setNeighbourFlag(level, pos.relative(rightOf(facing)), facing, MERGE_LEFT, false);
            surrenderCards(level, pos); // the partner keeps the (shared) cards; avoids a break dupe
        } else if (state.getValue(MERGE_LEFT)) {
            level.setBlock(pos, state.setValue(MERGE_LEFT, false), Block.UPDATE_ALL);
            setNeighbourFlag(level, pos.relative(leftOf(facing)), facing, MERGE_RIGHT, false);
            surrenderCards(level, pos);
        } else {
            Direction next = facing.getClockWise();
            BlockState rotated = state.setValue(HORIZONTAL_FACING, next)
                    .setValue(MERGE_LEFT, false)
                    .setValue(MERGE_RIGHT, false);
            level.setBlock(pos, rotated, Block.UPDATE_ALL);
            applyGreedyMerge(level, pos, level.getBlockState(pos));
            if (level.getBlockEntity(pos) instanceof TurnstileBlockEntity be)
                be.adoptGroupConfig();
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.6f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** When a pair is separated by the wrench, the wrenched gate gives up its copies of the shared
     *  cards so they aren't duplicated on break — the partner (which didn't move) keeps them. */
    private static void surrenderCards(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TurnstileBlockEntity be) {
            be.cardContainer.clearContent();
            be.trustListContainer.clearContent();
            be.trustListContainer.setChanged(); // rebuild the (now empty) trust list
            be.notifyUpdate();
        }
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (!isTrusted(context.getPlayer(), context.getLevel(), context.getClickedPos()))
            return InteractionResult.FAIL;
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    // ------------------------------------------------------------------
    // Ownership protection (mirrors Numismatics depositors)
    // ------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        if (!isTrusted(player, level, pos))
            return 0.0f;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.is(newState.getBlock())) {
            return;
        }
        // detach any merged neighbours so they don't render a hidden inner post
        Direction facing = state.getValue(HORIZONTAL_FACING);
        boolean merged = state.getValue(MERGE_LEFT) || state.getValue(MERGE_RIGHT);
        if (state.getValue(MERGE_RIGHT))
            setNeighbourFlag(level, pos.relative(rightOf(facing)), facing, MERGE_LEFT, false);
        if (state.getValue(MERGE_LEFT))
            setNeighbourFlag(level, pos.relative(leftOf(facing)), facing, MERGE_RIGHT, false);

        // Cards are replicated across the group; only a solo (last) gate drops them, avoiding dupes.
        if (!merged && level.getBlockEntity(pos) instanceof TurnstileBlockEntity be) {
            Containers.dropContents(level, pos, be.cardContainer);
            Containers.dropContents(level, pos, be.trustListContainer);
        }
        IBE.onRemove(state, level, pos, newState);
    }

    // ------------------------------------------------------------------
    // IBE
    // ------------------------------------------------------------------

    @Override
    public Class<TurnstileBlockEntity> getBlockEntityClass() {
        return TurnstileBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TurnstileBlockEntity> getBlockEntityType() {
        return MetroBlockEntityTypes.TURNSTILE.get();
    }
}
