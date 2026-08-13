package com.adam8797.create_metro;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Custom item data components for Create: Metro. */
public class MetroDataComponents {

    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateMetro.MOD_ID);

    /**
     * The station address a Quick Trip Ticket is valid for (may contain wildcards). Kept separate from
     * the item's display name so a printed ticket can show a friendly name while still matching stations.
     */
    public static final Supplier<DataComponentType<String>> TICKET_ADDRESS = COMPONENTS.register(
            "ticket_address", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
