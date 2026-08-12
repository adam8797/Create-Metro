package com.adam8797.create_metro.content.turnstile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * GeckoLib renderer for the turnstile. The swing is driven by the block state via the block entity's
 * animation controller; here we only toggle bone visibility so the one combined model can render as a
 * solo gate or as one half of a merged (wide) gate.
 *
 * <p>Bone layout: {@code left}/{@code right} are the two half-groups (each a post + a short door + a wide
 * door), aligned so the {@code left} group is hidden on {@code MERGE_LEFT} and {@code right} on
 * {@code MERGE_RIGHT}.
 */
public class TurnstileGeoRenderer extends GeoBlockRenderer<TurnstileBlockEntity> {

    public TurnstileGeoRenderer(BlockEntityRendererProvider.Context context) {
        super(new TurnstileGeoModel());
    }

    @Override
    public void preRender(PoseStack poseStack, TurnstileBlockEntity animatable, BakedGeoModel model,
                          @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        BlockState state = animatable.getBlockState();
        boolean mergeLeft = state.getValue(TurnstileBlock.MERGE_LEFT);   // neighbour on the facing-left side
        boolean mergeRight = state.getValue(TurnstileBlock.MERGE_RIGHT); // neighbour on the facing-right side
        boolean merged = mergeLeft || mergeRight;

        // Hide the half that overlaps the merged neighbour so a pair reads as one wide gate: the 'left'
        // bone group is on the MERGE_LEFT side, 'right' on the MERGE_RIGHT side. A solo gate (neither
        // flag) keeps both halves. Hiding a half-group also hides its children.
        setHidden(model, "left", mergeLeft);
        setHidden(model, "right", mergeRight);

        // Solo gates use the short doors (meet mid-block); paired gates use the wide doors (meet mid-pair).
        setHidden(model, "left_door_short", merged);
        setHidden(model, "right_door_short", merged);
        setHidden(model, "left_door_wide", !merged);
        setHidden(model, "right_door_wide", !merged);

        // The no-entry marker is only shown when exit is blocked.
        setHidden(model, "no_entry", !animatable.getNoExit());

        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    private static void setHidden(BakedGeoModel model, String bone, boolean hidden) {
        model.getBone(bone).ifPresent(b -> b.setHidden(hidden));
    }
}
