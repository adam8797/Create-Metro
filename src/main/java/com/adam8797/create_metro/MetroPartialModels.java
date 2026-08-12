package com.adam8797.create_metro;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;

/** Client-only: partial models rendered by block-entity renderers (the animated turnstile arm). */
public class MetroPartialModels {

    public static final PartialModel TURNSTILE_ARM_LEFT = PartialModel.of(CreateMetro.asResource("block/turnstile_arm_left"));
    public static final PartialModel TURNSTILE_ARM_RIGHT = PartialModel.of(CreateMetro.asResource("block/turnstile_arm_right"));

    /** Force class-load so the partial models above register before model baking. */
    public static void init() { }
}
