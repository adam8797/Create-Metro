package com.adam8797.create_metro;

import com.adam8797.create_metro.content.ticketprinter.TicketPrinterBlockEntity;
import com.adam8797.create_metro.content.turnstile.TurnstileBlockEntity;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

public class MetroBlockEntityTypes {
    private static final CreateRegistrate REGISTRATE = CreateMetro.getRegistrate();

    public static final BlockEntityEntry<TurnstileBlockEntity> TURNSTILE = REGISTRATE
            .blockEntity("turnstile", TurnstileBlockEntity::new)
            .validBlocks(MetroBlocks.TURNSTILE)
            .renderer(() -> com.adam8797.create_metro.content.turnstile.TurnstileGeoRenderer::new)
            .register();

    public static final BlockEntityEntry<TicketPrinterBlockEntity> TICKET_PRINTER = REGISTRATE
            .blockEntity("ticket_printer", TicketPrinterBlockEntity::new)
            .validBlocks(MetroBlocks.TICKET_PRINTER)
            .register();

    public static void register() { }
}
