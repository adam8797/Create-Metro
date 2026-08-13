package com.adam8797.create_metro;

import com.adam8797.create_metro.content.ticketprinter.TicketPrinterMenu;
import com.adam8797.create_metro.content.ticketprinter.TicketPrinterScreen;
import com.adam8797.create_metro.content.turnstile.TurnstileMenu;
import com.adam8797.create_metro.content.turnstile.TurnstileScreen;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.builders.MenuBuilder;
import com.tterrag.registrate.util.entry.MenuEntry;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class MetroMenuTypes {
    private static final CreateRegistrate REGISTRATE = CreateMetro.getRegistrate();

    public static final MenuEntry<TurnstileMenu> TURNSTILE = register(
            "turnstile",
            TurnstileMenu::new,
            () -> TurnstileScreen::new
    );

    public static final MenuEntry<TicketPrinterMenu> TICKET_PRINTER = register(
            "ticket_printer",
            TicketPrinterMenu::new,
            () -> TicketPrinterScreen::new
    );

    private static <C extends AbstractContainerMenu, S extends Screen & MenuAccess<C>> MenuEntry<C> register(
            String name, MenuBuilder.ForgeMenuFactory<C> factory, NonNullSupplier<MenuBuilder.ScreenFactory<C, S>> screenFactory) {
        return REGISTRATE
                .menu(name, factory, screenFactory)
                .register();
    }

    public static void register() { }
}
