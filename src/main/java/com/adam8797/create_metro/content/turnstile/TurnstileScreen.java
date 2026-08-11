package com.adam8797.create_metro.content.turnstile;

import com.adam8797.create_metro.config.MetroServerConfig;
import dev.ithundxr.createnumismatics.content.bank.CardItem;
import dev.ithundxr.createnumismatics.util.UsernameUtils;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class TurnstileScreen extends AbstractContainerScreen<TurnstileMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int TITLE_BAR = 0xFF8B8B8B;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int TEXT = 0x404040;

    private int editFare;

    public TurnstileScreen(TurnstileMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 168;
        this.titleLabelY = 6;
        this.inventoryLabelY = 74;
    }

    @Override
    protected void init() {
        super.init();
        editFare = menu.contentHolder != null ? menu.contentHolder.getFare() : 0;

        int x = leftPos;
        int y = topPos;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustFare(-step()))
                .bounds(x + 8, y + 32, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustFare(step()))
                .bounds(x + 60, y + 32, 20, 20).build());
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

        // panel + title bar
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        g.fill(x, y, x + imageWidth, y + 18, TITLE_BAR);

        // card (deposit) slot indent
        slot(g, x + 140, y + 34);

        // player inventory indents
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                slot(g, x + 8 + col * 18, y + 84 + row * 18);
        for (int col = 0; col < 9; col++)
            slot(g, x + 8 + col * 18, y + 142);
    }

    private void slot(GuiGraphics g, int itemX, int itemY) {
        g.fill(itemX - 1, itemY - 1, itemX + 17, itemY + 17, SLOT_BORDER);
        g.fill(itemX, itemY, itemX + 16, itemY + 16, SLOT_BG);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFFFFFF, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);

        // fare
        g.drawString(font, Component.translatable("create_metro.turnstile.fare"), 8, 22, TEXT, false);
        Component fareText = Component.translatable("create_metro.turnstile.fare_amount", editFare);
        g.drawCenteredString(font, fareText, 44, 38, 0xFFFFFF);

        // deposit destination
        g.drawString(font, Component.translatable("create_metro.turnstile.deposit_label"), 100, 22, TEXT, false);
        g.drawString(font, depositName(), 100, 56, TEXT, false);
    }

    private Component depositName() {
        ItemStack card = menu.getSlot(TurnstileMenu.CARD_SLOT).getItem();
        if (!card.isEmpty()) {
            String name = CardItem.getPlayerName(card);
            return name != null ? Component.literal(name)
                    : Component.translatable("create_metro.turnstile.deposit_linked");
        }
        UUID owner = menu.contentHolder != null ? menu.contentHolder.getOwner() : null;
        String ownerName = owner != null ? UsernameUtils.INSTANCE.getName(owner, null) : null;
        return ownerName != null ? Component.literal(ownerName)
                : Component.translatable("create_metro.turnstile.deposit_personal");
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
                    new TurnstileConfigurationPacket(menu.contentHolder.getBlockPos(), editFare));
    }
}
