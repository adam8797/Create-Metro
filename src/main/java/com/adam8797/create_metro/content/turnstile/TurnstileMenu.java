package com.adam8797.create_metro.content.turnstile;

import com.simibubi.create.foundation.gui.menu.MenuBase;
import dev.ithundxr.createnumismatics.content.bank.CardSlot;
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
    public static final int PLAYER_INV_START = 1;
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
        addSlot(new CardSlot.BoundCardSlot(contentHolder.cardContainer, 0, 140, 34));
        addPlayerSlots(8, 84);
    }

    @Override
    protected void saveData(TurnstileBlockEntity be) {}

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = getSlot(index);
        if (!slot.hasItem())
            return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();

        if (index == CARD_SLOT) {
            // pull the linked card back into the player's inventory
            if (!moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, false))
                return ItemStack.EMPTY;
        } else {
            // from the player inventory: only bound cards go into the card slot
            if (NumismaticsTags.AllItemTags.CARDS.matches(stack)) {
                if (!moveItemStackTo(stack, CARD_SLOT, CARD_SLOT + 1, false))
                    return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
        }
        slot.setChanged();
        return ItemStack.EMPTY;
    }
}
