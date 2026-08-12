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
 * GeckoLib renderer for the turnstile. Plays the swing animation (driven by the block state via the
 * animation controller) and hides the merged side's half so a paired gate reads as one wide gate.
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
        // Merged on the left = this is the right block of a pair -> hide the left half, and vice-versa.
        boolean hideLeft = state.getValue(TurnstileBlock.MERGE_LEFT);
        boolean hideRight = state.getValue(TurnstileBlock.MERGE_RIGHT);
        model.getBone("left").ifPresent(bone -> bone.setHidden(hideLeft));
        model.getBone("right").ifPresent(bone -> bone.setHidden(hideRight));

        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }
}
