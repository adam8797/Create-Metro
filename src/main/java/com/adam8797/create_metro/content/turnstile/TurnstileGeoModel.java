package com.adam8797.create_metro.content.turnstile;

import com.adam8797.create_metro.CreateMetro;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Points GeckoLib at the turnstile's geo model, texture, and animations. */
public class TurnstileGeoModel extends GeoModel<TurnstileBlockEntity> {

    private static final ResourceLocation MODEL = CreateMetro.asResource("geo/turnstile.geo.json");
    private static final ResourceLocation TEXTURE = CreateMetro.asResource("textures/block/turnstile.png");
    private static final ResourceLocation ANIMATION = CreateMetro.asResource("animations/turnstile.animation.json");

    @Override
    public ResourceLocation getModelResource(TurnstileBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(TurnstileBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(TurnstileBlockEntity animatable) {
        return ANIMATION;
    }
}
