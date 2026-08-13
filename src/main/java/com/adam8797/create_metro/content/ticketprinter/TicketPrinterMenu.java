package com.adam8797.create_metro.content.ticketprinter;

import com.simibubi.create.foundation.gui.menu.MenuBase;
import dev.ithundxr.createnumismatics.content.bank.CardSlot;
import dev.ithundxr.createnumismatics.content.bank.IDCardSlot;
import dev.ithundxr.createnumismatics.registry.NumismaticsTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

public class TicketPrinterMenu extends MenuBase<TicketPrinterBlockEntity> {

    public static final int CARD_SLOT = 0;
    public static final int OWNER_SLOT_START = 1;
    public static final int OWNER_SLOT_COUNT = 9;
    public static final int PLAYER_INV_START = OWNER_SLOT_START + OWNER_SLOT_COUNT; // 10
    public static final int PLAYER_INV_END = PLAYER_INV_START + 36;

    public TicketPrinterMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    public TicketPrinterMenu(MenuType<?> type, int id, Inventory inv, TicketPrinterBlockEntity be) {
        super(type, id, inv, be);
    }

    @Override
    protected TicketPrinterBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null)
            return null;
        BlockEntity be = level.getBlockEntity(extraData.readBlockPos());
        return be instanceof TicketPrinterBlockEntity printer ? printer : null;
    }

    @Override
    protected void initAndReadInventory(TicketPrinterBlockEntity be) {}

    @Override
    protected void addSlots() {
        addSlot(new CardSlot.BoundCardSlot(contentHolder.cardContainer, 0, 150, 90));
        for (int i = 0; i < OWNER_SLOT_COUNT; i++)
            addSlot(new IDCardSlot.BoundIDCardSlot(contentHolder.ownerListContainer, i, 8 + i * 18, 136));
        addPlayerSlots(8, 170);
    }

    @Override
    protected void saveData(TicketPrinterBlockEntity be) {}

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = getSlot(index);
        if (!slot.hasItem())
            return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();

        if (index < PLAYER_INV_START) {
            if (!moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, false))
                return ItemStack.EMPTY;
        } else if (NumismaticsTags.AllItemTags.CARDS.matches(stack)) {
            if (!moveItemStackTo(stack, CARD_SLOT, CARD_SLOT + 1, false))
                return ItemStack.EMPTY;
        } else if (NumismaticsTags.AllItemTags.ID_CARDS.matches(stack)) {
            if (!moveItemStackTo(stack, OWNER_SLOT_START, PLAYER_INV_START, false))
                return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        slot.setChanged();
        return ItemStack.EMPTY;
    }
}
