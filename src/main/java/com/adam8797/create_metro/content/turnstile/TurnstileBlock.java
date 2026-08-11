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
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TurnstileBlock extends Block implements IWrenchable, IBE<TurnstileBlockEntity>, TrustedBlock {

    public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    // Closed barrier: a slab that blocks passage along the relevant horizontal axis. Height is capped at
    // one block for dev art; a taller/animated model comes later.
    private static final VoxelShape SHAPE_BLOCKS_Z = Shapes.box(0, 0, 0.375, 1, 1.0, 0.625);
    private static final VoxelShape SHAPE_BLOCKS_X = Shapes.box(0.375, 0, 0, 0.625, 1.0, 1);

    public TurnstileBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HORIZONTAL_FACING, Direction.NORTH)
                .setValue(OPEN, false));
    }

    // ------------------------------------------------------------------
    // Shape
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
        return state.getValue(OPEN) ? Shapes.empty() : barrierShape(state);
    }

    // ------------------------------------------------------------------
    // Placement / states
    // ------------------------------------------------------------------

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HORIZONTAL_FACING, context.getHorizontalDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING, OPEN);
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HORIZONTAL_FACING)));
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof TurnstileBlockEntity be) {
            if (placer instanceof Player player)
                be.setOwner(player.getUUID());
            be.initFromConfig();
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
        boolean bankCard = NumismaticsTags.AllItemTags.CARDS.matches(stack);
        boolean idCard = NumismaticsTags.AllItemTags.ID_CARDS.matches(stack);
        if (!bankCard && !idCard)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TurnstileBlockEntity be))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        boolean sneaking = player.isShiftKeyDown();
        if (bankCard) {
            // Sneak + trusted opens the config GUI (where the card can be linked as the deposit
            // account); otherwise the card pays the fare.
            if (sneaking && isTrusted(player, level, pos))
                openConfig(player, pos, be);
            else
                be.payWithCard(stack, player);
            return ItemInteractionResult.CONSUME;
        }

        // ID card: assign/revoke a free-pass rider (owner/trusted only).
        if (sneaking) {
            if (isTrusted(player, level, pos))
                be.toggleTrust(stack, player);
            else
                notifyNotOwner(player);
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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

    private static void notifyNotOwner(Player player) {
        player.displayClientMessage(net.minecraft.network.chat.Component
                .translatable("create_metro.turnstile.not_owner")
                .withStyle(net.minecraft.ChatFormatting.RED), true);
    }

    // ------------------------------------------------------------------
    // Ownership protection (mirrors Numismatics depositors)
    // ------------------------------------------------------------------

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (!isTrusted(context.getPlayer(), context.getLevel(), context.getClickedPos()))
            return InteractionResult.FAIL;
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!isTrusted(context.getPlayer(), context.getLevel(), context.getClickedPos()))
            return InteractionResult.FAIL;
        return IWrenchable.super.onWrenched(state, context);
    }

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
        if (level.getBlockEntity(pos) instanceof TurnstileBlockEntity be)
            Containers.dropContents(level, pos, be.cardContainer);
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
