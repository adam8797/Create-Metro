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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TicketPrinterBlock extends Block implements IWrenchable, IBE<TicketPrinterBlockEntity>, TrustedBlock {

    public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;

    public TicketPrinterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof TicketPrinterBlockEntity be)
            be.setOwner(player);
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
            if (level.getBlockEntity(pos) instanceof TicketPrinterBlockEntity be)
                be.buyWithCard(stack, player);
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION; // let the wrench etc. act
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TicketPrinterBlockEntity be))
            return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            if (isTrusted(player, level, pos) && player instanceof ServerPlayer sp) {
                sp.openMenu(be, buf -> buf.writeBlockPos(pos));
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
        if (!isTrusted(player, level, pos))
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
    public Class<TicketPrinterBlockEntity> getBlockEntityClass() {
        return TicketPrinterBlockEntity.class;
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntityType<? extends TicketPrinterBlockEntity> getBlockEntityType() {
        return MetroBlockEntityTypes.TICKET_PRINTER.get();
    }
}
