package com.adam8797.create_metro;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;

public class MetroItems {

    private static final CreateRegistrate REGISTRATE = CreateMetro.getRegistrate();

    static {
        REGISTRATE.setCreativeTab(MetroCreativeTabs.MAIN);
    }

    /** A single-use fare ticket. Name it in an anvil to match a turnstile's station (wildcards allowed). */
    public static final ItemEntry<Item> QUICK_TRIP_TICKET = REGISTRATE
            .item("quick_trip_ticket", Item::new)
            .lang("Quick Trip Ticket")
            .model((c, p) -> { }) // model is hand-authored in src/main/resources
            .register();

    public static void register() { }
}
