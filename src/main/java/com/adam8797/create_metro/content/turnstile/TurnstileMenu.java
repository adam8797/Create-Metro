package com.adam8797.create_metro.content.turnstile;

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

public class TurnstileMenu extends MenuBase<TurnstileBlockEntity> {

    public static final int CARD_SLOT = 0;
    public static final int OWNER_SLOT_START = 1;
    public static final int OWNER_SLOT_COUNT = 9;
    public static final int RIDER_SLOT_START = OWNER_SLOT_START + OWNER_SLOT_COUNT; // 10
    public static final int RIDER_SLOT_COUNT = 9;
    public static final int PLAYER_INV_START = RIDER_SLOT_START + RIDER_SLOT_COUNT; // 19
    public static final int PLAYER_INV_END = PLAYER_INV_START + 36;

    public TurnstileMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    public TurnstileMenu(MenuType<?> type, int id, Inventory inv, TurnstileBlockEntity be) {
        super(type, id, inv, be);
    }

    @Override
    protected TurnstileBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null)
            return null;
        BlockEntity be = level.getBlockEntity(extraData.readBlockPos());
        return be instanceof TurnstileBlockEntity turnstile ? turnstile : null;
    }

    @Override
    protected void initAndReadInventory(TurnstileBlockEntity be) {}

    @Override
    protected void addSlots() {
        addSlot(new CardSlot.BoundCardSlot(contentHolder.cardContainer, 0, 8, 102));
        for (int i = 0; i < OWNER_SLOT_COUNT; i++)
            addSlot(new IDCardSlot.BoundIDCardSlot(contentHolder.ownerListContainer, i, 8 + i * 18, 132));
        for (int i = 0; i < RIDER_SLOT_COUNT; i++)
            addSlot(new IDCardSlot.BoundIDCardSlot(contentHolder.trustListContainer, i, 8 + i * 18, 164));
        addPlayerSlots(8, 198);
    }

    @Override
    protected void saveData(TurnstileBlockEntity be) {}

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = getSlot(index);
        if (!slot.hasItem())
            return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();

        if (index < PLAYER_INV_START) {
            // from a machine slot back to the player
            if (!moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, false))
                return ItemStack.EMPTY;
        } else if (NumismaticsTags.AllItemTags.CARDS.matches(stack)) {
            if (!moveItemStackTo(stack, CARD_SLOT, CARD_SLOT + 1, false))
                return ItemStack.EMPTY;
        } else if (NumismaticsTags.AllItemTags.ID_CARDS.matches(stack)) {
            // Fill the owner slots first, then the free-pass rider slots.
            if (!moveItemStackTo(stack, OWNER_SLOT_START, PLAYER_INV_START, false))
                return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        slot.setChanged();
        return ItemStack.EMPTY;
    }
}
