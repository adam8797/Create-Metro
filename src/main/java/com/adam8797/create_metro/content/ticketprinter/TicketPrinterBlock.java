package com.adam8797.create_metro.content.ticketprinter;

import com.adam8797.create_metro.MetroBlockEntityTypes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.ithundxr.createnumismatics.content.backend.TrustedBlock;
import dev.ithundxr.createnumismatics.registry.NumismaticsTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TicketPrinterBlock extends Block implements IWrenchable, IBE<TicketPrinterBlockEntity>, TrustedBlock {

    public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The printer is two blocks tall (bed/door style). The block entity lives on the LOWER half only. */
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** Light emitted by the glowing indicator. It sits in the upper cell, so only that half emits. Tune here. */
    public static final int GLOW_LIGHT = 10;

    /** Registration hook: {@code p.lightLevel(TicketPrinterBlock::lightEmission)}. */
    public static int lightEmission(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? GLOW_LIGHT : 0;
    }

    public TicketPrinterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HORIZONTAL_FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING, HALF);
    }

    /** The block position that carries the block entity (always the lower half). */
    private static BlockPos primaryPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    // ------------------------------------------------------------------
    // Placement & two-tall structure
    // ------------------------------------------------------------------

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // Need a free cell above for the upper half.
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context))
            return null;
        return defaultBlockState()
                .setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Place the upper half; the lower half (this pos) keeps the block entity.
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof TicketPrinterBlockEntity be)
            be.setOwner(player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER)
            return super.canSurvive(state, level, pos);
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                           @NotNull LevelAccessor level, @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        // If the paired half is gone, remove this one too.
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
            return neighborState.is(this) && neighborState.getValue(HALF) != half
                    ? state
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public @NotNull BlockState playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos,
                                                 @NotNull BlockState state, @NotNull Player player) {
        // Creative-mode break of the upper half must clear the lower half without dropping it (loot for a
        // survival break is gated to the lower half via the block's loot table). Mirrors DoublePlantBlock.
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos belowPos = pos.below();
            BlockState belowState = level.getBlockState(belowPos);
            if (belowState.is(this) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(belowPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_ALL);
                level.levelEvent(player, 2001 /* destroy-block particles */, belowPos, Block.getId(belowState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ------------------------------------------------------------------
    // Lighting: the upper half is a hollow shell whose cell is filled by the lower half's overhanging
    // model geometry (the bar + glowstone). Left as a normal full-cube block it would dim that geometry
    // and cast an ambient-occlusion shadow onto it, so make the upper half light-neutral (like the air
    // it replaced). The lower half keeps normal shading.
    // ------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    protected boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER || super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? 1.0F : super.getShadeBrightness(state, level, pos);
    }

    // ------------------------------------------------------------------
    // Interaction: plain right-click buys a ticket; shift+empty-hand opens the owner config GUI.
    // ------------------------------------------------------------------

    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                       Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (NumismaticsTags.AllItemTags.CARDS.matches(stack)) {
            if (level.isClientSide)
                return ItemInteractionResult.SUCCESS;
            if (level.getBlockEntity(primaryPos(state, pos)) instanceof TicketPrinterBlockEntity be)
                be.buyWithCard(stack, player);
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION; // let the wrench etc. act
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        BlockPos bePos = primaryPos(state, pos);
        if (!(level.getBlockEntity(bePos) instanceof TicketPrinterBlockEntity be))
            return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            if (isTrusted(player, level, bePos) && player instanceof ServerPlayer sp) {
                sp.openMenu(be, buf -> buf.writeBlockPos(bePos));
                return InteractionResult.CONSUME;
            }
            return InteractionResult.SUCCESS;
        }
        be.buyWithPersonalAccount(player);
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------
    // Ownership protection
    // ------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        if (!isTrusted(player, level, primaryPos(state, pos)))
            return 0.0f;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TicketPrinterBlockEntity be)
            be.dropContents();
        IBE.onRemove(state, level, pos, newState);
    }

    // ------------------------------------------------------------------
    // IBE
    // ------------------------------------------------------------------

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        // Only the lower half owns a block entity; the upper half is a hollow interaction/collision shell.
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? getBlockEntityType().create(pos, state) : null;
    }

    @Override
    public Class<TicketPrinterBlockEntity> getBlockEntityClass() {
        return TicketPrinterBlockEntity.class;
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntityType<? extends TicketPrinterBlockEntity> getBlockEntityType() {
        return MetroBlockEntityTypes.TICKET_PRINTER.get();
    }
}
