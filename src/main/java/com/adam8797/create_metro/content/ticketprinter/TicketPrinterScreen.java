package com.adam8797.create_metro.content.ticketprinter;

import com.adam8797.create_metro.config.MetroServerConfig;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public class TicketPrinterScreen extends AbstractContainerScreen<TicketPrinterMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int TITLE_BAR = 0xFF8B8B8B;
    private static final int BORDER = 0xFF55585F;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int TEXT = 0x404040;

    private int editFare;
    private EditBox stationBox;
    private EditBox ticketNameBox;

    public TicketPrinterScreen(TicketPrinterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 252;
        this.titleLabelY = 6;
        this.inventoryLabelY = 158;
    }

    @Override
    protected void init() {
        super.init();
        editFare = menu.contentHolder != null ? menu.contentHolder.getFare() : 0;
        String station = menu.contentHolder != null ? menu.contentHolder.getStation() : "";
        String ticketName = menu.contentHolder != null ? menu.contentHolder.getTicketName() : "";

        int x = leftPos;
        int y = topPos;

        stationBox = new EditBox(font, x + 8, y + 30, 160, 14, Component.translatable("create_metro.ticket_printer.station"));
        stationBox.setMaxLength(64);
        stationBox.setValue(station);
        addRenderableWidget(stationBox);

        ticketNameBox = new EditBox(font, x + 8, y + 58, 160, 14, Component.translatable("create_metro.ticket_printer.ticket_name"));
        ticketNameBox.setMaxLength(64);
        ticketNameBox.setValue(ticketName);
        addRenderableWidget(ticketNameBox);

        addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustFare(-step()))
                .bounds(x + 8, y + 100, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustFare(step()))
                .bounds(x + 38, y + 100, 20, 20).build());
    }

    private static int step() {
        if (Screen.hasControlDown()) return 64;
        if (Screen.hasShiftDown()) return 10;
        return 1;
    }

    private void adjustFare(int delta) {
        int max = Math.max(1, MetroServerConfig.MaxTurnstileFare.get());
        editFare = Math.max(0, Math.min(max, editFare + delta));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        for (EditBox box : new EditBox[]{stationBox, ticketNameBox})
            if (box != null && box.isFocused() && box.canConsumeInput())
                return box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput();
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        g.fill(x, y, x + imageWidth, y + 18, TITLE_BAR);
        g.fill(x, y, x + imageWidth, y + 1, BORDER);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, BORDER);
        g.fill(x, y, x + 1, y + imageHeight, BORDER);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, BORDER);

        slot(g, x + 150, y + 90); // deposit card slot
        for (int i = 0; i < TicketPrinterMenu.OWNER_SLOT_COUNT; i++)
            slot(g, x + 8 + i * 18, y + 136);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                slot(g, x + 8 + col * 18, y + 170 + row * 18);
        for (int col = 0; col < 9; col++)
            slot(g, x + 8 + col * 18, y + 228);
    }

    private void slot(GuiGraphics g, int itemX, int itemY) {
        g.fill(itemX - 1, itemY - 1, itemX + 17, itemY + 17, SLOT_BORDER);
        g.fill(itemX, itemY, itemX + 16, itemY + 16, SLOT_BG);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFFF, false);
        String ownerName = menu.contentHolder != null ? menu.contentHolder.getOwnerName() : "";
        if (ownerName != null && !ownerName.isEmpty())
            g.drawString(font, ownerName, imageWidth - 8 - font.width(ownerName), titleLabelY, 0xFFFFFF, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);

        g.drawString(font, Component.translatable("create_metro.ticket_printer.station"), 8, 20, TEXT, false);
        g.drawString(font, Component.translatable("create_metro.ticket_printer.ticket_name"), 8, 48, TEXT, false);

        g.drawString(font, Component.translatable("create_metro.turnstile.fare"), 8, 78, TEXT, false);
        Component fareText = editFare <= 0
                ? Component.translatable("create_metro.turnstile.fare_ticket")
                : Component.translatable("create_metro.turnstile.fare_amount", editFare);
        g.drawCenteredString(font, fareText, 34, 89, 0xFFFFFF);

        g.drawString(font, Component.translatable("create_metro.turnstile.deposit_label"), 110, 78, TEXT, false);
        g.drawString(font, Component.translatable("create_metro.ticket_printer.owners_label"), 8, 124, TEXT, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public void removed() {
        super.removed();
        if (menu.contentHolder != null)
            CatnipServices.NETWORK.sendToServer(new TicketPrinterConfigurationPacket(
                    menu.contentHolder.getBlockPos(), editFare,
                    stationBox != null ? stationBox.getValue() : "",
                    ticketNameBox != null ? ticketNameBox.getValue() : ""));
    }
}
