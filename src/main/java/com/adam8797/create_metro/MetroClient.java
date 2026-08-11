package com.adam8797.create_metro;

import net.neoforged.bus.api.IEventBus;

/** Client-only setup entry point (referenced from {@link CreateMetro} only on the client dist). */
public class MetroClient {

    public static void init(IEventBus modEventBus) {
        // Force partial-model registration early (before model baking).
        MetroPartialModels.init();
    }
}
