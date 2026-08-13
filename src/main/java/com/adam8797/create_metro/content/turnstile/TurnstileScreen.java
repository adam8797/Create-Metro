package com.adam8797.create_metro.content.turnstile;

import com.adam8797.create_metro.config.MetroServerConfig;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class TurnstileScreen extends AbstractContainerScreen<TurnstileMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int TITLE_BAR = 0xFF8B8B8B;
    private static final int BORDER = 0xFF55585F;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int TEXT = 0x404040;

    private int editFare;
    private boolean chargeTrusted;
    private boolean noExit;
    private boolean autoPay;

    public TurnstileScreen(TurnstileMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 224;
        this.titleLabelY = 6;
        this.inventoryLabelY = 130;
    }

    @Override
    protected void init() {
        super.init();
        editFare = menu.contentHolder != null ? menu.contentHolder.getFare() : 0;
        chargeTrusted = menu.contentHolder != null && menu.contentHolder.getChargeTrusted();
        noExit = menu.contentHolder != null && menu.contentHolder.getNoExit();
        autoPay = menu.contentHolder == null || menu.contentHolder.getAutoPay();

        int x = leftPos;
        int y = topPos;
        // Left column: fare adjust buttons (amount + card slot are drawn in renderBg/renderLabels).
        addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustFare(-step()))
                .bounds(x + 8, y + 42, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustFare(step()))
                .bounds(x + 38, y + 42, 20, 20).build());
        // Right column: the three toggles.
        addRenderableWidget(Checkbox.builder(Component.translatable("create_metro.turnstile.auto_pay"), font)
                .pos(x + 66, y + 20)
                .selected(autoPay)
                .onValueChange((cb, value) -> autoPay = value)
                .build());
        addRenderableWidget(Checkbox.builder(Component.translatable("create_metro.turnstile.charge_trusted"), font)
                .pos(x + 66, y + 42)
                .selected(chargeTrusted)
                .onValueChange((cb, value) -> chargeTrusted = value)
                .build());
        addRenderableWidget(Checkbox.builder(Component.translatable("create_metro.turnstile.no_exit"), font)
                .pos(x + 66, y + 64)
                .selected(noExit)
                .onValueChange((cb, value) -> noExit = value)
                .build());
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
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        g.fill(x, y, x + imageWidth, y + 18, TITLE_BAR);
        g.fill(x, y, x + imageWidth, y + 1, BORDER);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, BORDER);
        g.fill(x, y, x + 1, y + imageHeight, BORDER);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, BORDER);

        slot(g, x + 8, y + 76); // deposit card slot (left column, under the fare)
        for (int i = 0; i < TurnstileMenu.ID_SLOT_COUNT; i++)
            slot(g, x + 8 + i * 18, y + 106);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                slot(g, x + 8 + col * 18, y + 142 + row * 18);
        for (int col = 0; col < 9; col++)
            slot(g, x + 8 + col * 18, y + 200);
    }

    private void slot(GuiGraphics g, int itemX, int itemY) {
        g.fill(itemX - 1, itemY - 1, itemX + 17, itemY + 17, SLOT_BORDER);
        g.fill(itemX, itemY, itemX + 16, itemY + 16, SLOT_BG);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFFF, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);

        g.drawString(font, Component.translatable("create_metro.turnstile.fare"), 8, 20, TEXT, false);
        g.drawCenteredString(font, Component.translatable("create_metro.turnstile.fare_amount", editFare), 34, 31, 0xFFFFFF);

        g.drawString(font, Component.translatable("create_metro.turnstile.deposit_label"), 8, 66, TEXT, false);
        g.drawString(font, Component.translatable("create_metro.turnstile.riders_label"), 8, 96, TEXT, false);
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
            CatnipServices.NETWORK.sendToServer(
                    new TurnstileConfigurationPacket(menu.contentHolder.getBlockPos(), editFare, chargeTrusted, noExit, autoPay));
    }
}
