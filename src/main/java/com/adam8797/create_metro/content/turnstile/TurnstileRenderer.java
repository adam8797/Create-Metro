package com.adam8797.create_metro.content.turnstile;

import com.adam8797.create_metro.MetroPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders the turnstile's two fare-arms as animated leaves, each swinging from its own outer pillar.
 * The static model (posts + coins) is a normal block model; the arms live here so they can rotate.
 * A merged gate hides the arm on its merged (inner) side.
 */
public class TurnstileRenderer extends SafeBlockEntityRenderer<TurnstileBlockEntity> {

    // Hinge pivots in block-local space (matching the model's rotation origins [2,3,8] and [14,3,8]).
    private static final float LEFT_PIVOT_X = 2f / 16f;
    private static final float RIGHT_PIVOT_X = 14f / 16f;
    private static final float PIVOT_Z = 8f / 16f;

    public TurnstileRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(TurnstileBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(TurnstileBlock.HORIZONTAL_FACING);
        float angle = be.swing.getValue(partialTicks) * 90f; // 0 closed .. 90 open
        boolean forward = !state.getValue(TurnstileBlock.REVERSED);

        // Left leaf hinges at the left pillar; right leaf at the right pillar. Sign mirrors per side.
        if (!state.getValue(TurnstileBlock.MERGE_LEFT))
            renderArm(ms, buffer, light, overlay, state, MetroPartialModels.TURNSTILE_ARM_LEFT,
                    facing, LEFT_PIVOT_X, (forward ? 1f : -1f) * angle);
        if (!state.getValue(TurnstileBlock.MERGE_RIGHT))
            renderArm(ms, buffer, light, overlay, state, MetroPartialModels.TURNSTILE_ARM_RIGHT,
                    facing, RIGHT_PIVOT_X, (forward ? -1f : 1f) * angle);
    }

    private void renderArm(PoseStack ms, MultiBufferSource buffer, int light, int overlay, BlockState state,
                           PartialModel arm, Direction facing, float pivotX, float degrees) {
        CachedBuffers.partial(arm, state)
                .light(light)
                .overlay(overlay)
                .rotateCentered(Mth.DEG_TO_RAD * (180 - facing.toYRot()), Axis.YP)
                .translate(pivotX, 0, PIVOT_Z)
                .rotateYDegrees(degrees)
                .translateBack(pivotX, 0, PIVOT_Z)
                .renderInto(ms, buffer.getBuffer(RenderType.translucent()));
    }
}
