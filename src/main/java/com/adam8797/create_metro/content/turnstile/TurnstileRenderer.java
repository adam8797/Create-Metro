package com.adam8797.create_metro.content.turnstile;

import com.adam8797.create_metro.MetroPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders the turnstile's fare-arm as an animated leaf that swings from the outer pillar. The static
 * model (posts + arrow) is a normal block model; only the arm lives here so it can rotate smoothly.
 */
public class TurnstileRenderer extends SafeBlockEntityRenderer<TurnstileBlockEntity> {

    public TurnstileRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(TurnstileBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(TurnstileBlock.HORIZONTAL_FACING);
        boolean hingeRight = state.getValue(TurnstileBlock.MERGE_LEFT) && !state.getValue(TurnstileBlock.MERGE_RIGHT);
        boolean forward = !state.getValue(TurnstileBlock.REVERSED);

        float angle = be.swing.getValue(partialTicks) * 90f; // 0 closed .. 90 open

        // Pivot on the outer pillar, in the model's local (north-facing) space.
        float pivotX = hingeRight ? 13f / 16f : 3f / 16f;
        float pivotZ = 8f / 16f;
        float sign = (hingeRight ? -1f : 1f) * (forward ? 1f : -1f);

        SuperByteBuffer arm = CachedBuffers.partial(MetroPartialModels.TURNSTILE_ARM, state);
        arm.light(light)
                .overlay(overlay)
                .rotateCentered(Mth.DEG_TO_RAD * (180 - facing.toYRot()), Axis.YP)
                .translate(pivotX, 0, pivotZ)
                .rotateYDegrees(sign * angle)
                .translateBack(pivotX, 0, pivotZ)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
    }
}
