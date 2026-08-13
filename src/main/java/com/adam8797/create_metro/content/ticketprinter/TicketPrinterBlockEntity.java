package com.adam8797.create_metro.content.ticketprinter;

import com.adam8797.create_metro.MetroDataComponents;
import com.adam8797.create_metro.MetroItems;
import com.adam8797.create_metro.MetroMenuTypes;
import com.adam8797.create_metro.config.MetroServerConfig;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import dev.ithundxr.createnumismatics.content.backend.Trusted;
import dev.ithundxr.createnumismatics.content.backend.trust_list.TrustListContainer;
import dev.ithundxr.createnumismatics.registry.NumismaticsTags;
import dev.ithundxr.createnumismatics.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.ithundxr.createnumismatics.content.bank.CardItem;

/**
 * A basic vending block that sells (prints) Quick Trip Tickets. Owner-configured fare + station address +
 * display name; a player interacts to buy one, which is added to their inventory (or dropped if full).
 */
public class TicketPrinterBlockEntity extends SmartBlockEntity implements Trusted, MenuProvider {

    @Nullable
    protected UUID owner;
    protected String ownerName = "";

    /** Co-owners (besides the placer) permitted to configure. Derived from {@link #ownerListContainer}. */
    protected final List<UUID> ownerList = new ArrayList<>();
    public final TrustListContainer ownerListContainer = new TrustListContainer(ownerList, this::setChanged);

    /** Optional bank card the fares are deposited into; empty = owner's personal account. */
    public final Container cardContainer = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            TicketPrinterBlockEntity.this.setChanged();
        }
    };

    protected int fare = 0;
    /** Address printed onto tickets (what turnstiles match against; wildcards allowed). */
    protected String station = "";
    /** Display name printed onto tickets; falls back to the station address when blank. */
    protected String ticketName = "";

    public TicketPrinterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) { }

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    public void setOwner(Player placer) {
        this.owner = placer.getUUID();
        this.ownerName = placer.getGameProfile().getName();
        notifyUpdate();
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public int getFare() {
        return fare;
    }

    public String getStation() {
        return station;
    }

    public String getTicketName() {
        return ticketName;
    }

    public void applyConfig(int fare, String station, String ticketName) {
        int max = Math.max(1, MetroServerConfig.MaxTurnstileFare.get());
        this.fare = Math.max(0, Math.min(max, fare));
        this.station = station == null ? "" : station.trim();
        this.ticketName = ticketName == null ? "" : ticketName.trim();
        notifyUpdate();
    }

    /** Owners (placer + co-owner ID cards) may configure and print for free. */
    public boolean isOwner(Player player) {
        if (owner == null || owner.equals(player.getUUID()) || ownerList.contains(player.getUUID()))
            return true;
        return Utils.isDevEnv() && player.getItemBySlot(EquipmentSlot.FEET).is(Items.GOLDEN_BOOTS);
    }

    @Override
    public boolean isTrustedInternal(Player player) {
        return isOwner(player);
    }

    // ------------------------------------------------------------------
    // Printing
    // ------------------------------------------------------------------

    /** Charge the fare (unless free/owner) and hand the player a freshly printed ticket. */
    public void printTicket(Player player, @Nullable BankAccount source) {
        if (!(level instanceof ServerLevel))
            return;
        if (station.isBlank()) {
            player.displayClientMessage(Component.translatable("create_metro.ticket_printer.no_station")
                    .withStyle(ChatFormatting.RED), true);
            playDenySound();
            return;
        }
        int amount = fare;
        boolean free = amount <= 0 || isOwner(player);
        if (!free) {
            if (source == null || !source.deduct(amount)) {
                player.displayClientMessage(Component.translatable("create_metro.turnstile.insufficient_funds")
                        .withStyle(ChatFormatting.DARK_RED), true);
                playDenySound();
                return;
            }
            BankAccount destination = resolveDestination();
            if (destination != null)
                destination.deposit(amount);
        }
        ItemHandlerHelper.giveItemToPlayer(player, makeTicket());
        level.playSound(null, worldPosition, SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 0.8f, 1.0f);
    }

    private ItemStack makeTicket() {
        ItemStack ticket = new ItemStack(MetroItems.QUICK_TRIP_TICKET.get());
        ticket.set(MetroDataComponents.TICKET_ADDRESS.get(), station);
        String display = ticketName.isBlank() ? station : ticketName;
        ticket.set(DataComponents.CUSTOM_NAME, Component.literal(display));
        return ticket;
    }

    /** Right-click with a bank card buys a ticket from that card's account. */
    public void buyWithCard(ItemStack cardStack, Player player) {
        if (!(level instanceof ServerLevel))
            return;
        UUID accountId = CardItem.get(cardStack);
        if (accountId == null) {
            player.displayClientMessage(Component.translatable("create_metro.turnstile.card_blank")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        BankAccount source = Numismatics.BANK.getAccount(accountId);
        if (source == null || !source.isAuthorized(player)) {
            player.displayClientMessage(Component.translatable("create_metro.turnstile.card_unauthorized")
                    .withStyle(ChatFormatting.RED), true);
            playDenySound();
            return;
        }
        printTicket(player, source);
    }

    /** Buy from the player's personal account (empty-hand right-click). */
    public void buyWithPersonalAccount(Player player) {
        if (level instanceof ServerLevel)
            printTicket(player, Numismatics.BANK.getAccount(player));
    }

    @Nullable
    private BankAccount resolveDestination() {
        ItemStack card = cardContainer.getItem(0);
        if (NumismaticsTags.AllItemTags.CARDS.matches(card)) {
            UUID accountId = CardItem.get(card);
            if (accountId != null) {
                BankAccount linked = Numismatics.BANK.getAccount(accountId);
                if (linked != null)
                    return linked;
            }
        }
        if (owner != null)
            return Numismatics.BANK.getOrCreateAccount(owner, BankAccount.Type.PLAYER);
        return null;
    }

    private void playDenySound() {
        if (level != null)
            level.playSound(null, worldPosition, AllSoundEvents.DENY.getMainEvent(), SoundSource.BLOCKS, 0.5f, 1.0f);
    }

    /** Drop the deposit card + owner ID cards when broken. */
    public void dropContents() {
        if (level == null)
            return;
        net.minecraft.world.Containers.dropContents(level, worldPosition, cardContainer);
        net.minecraft.world.Containers.dropContents(level, worldPosition, ownerListContainer);
    }

    // ------------------------------------------------------------------
    // Menu / persistence
    // ------------------------------------------------------------------

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.create_metro.ticket_printer");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new TicketPrinterMenu(MetroMenuTypes.TICKET_PRINTER.get(), id, inventory, this);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (owner != null)
            tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.putInt("Fare", fare);
        tag.putString("Station", station);
        tag.putString("TicketName", ticketName);
        if (!cardContainer.getItem(0).isEmpty())
            tag.put("Card", cardContainer.getItem(0).save(registries));
        if (!ownerListContainer.isEmpty())
            tag.put("OwnerList", ownerListContainer.save(new CompoundTag(), registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        fare = tag.getInt("Fare");
        station = tag.getString("Station");
        ticketName = tag.getString("TicketName");

        ItemStack card = tag.contains("Card", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound("Card"))
                : ItemStack.EMPTY;
        cardContainer.setItem(0, card);

        ownerList.clear();
        ownerListContainer.clearContent();
        if (tag.contains("OwnerList", Tag.TAG_COMPOUND))
            ownerListContainer.load(tag.getCompound("OwnerList"), registries);
    }

    @Nullable
    public static TicketPrinterBlockEntity get(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof TicketPrinterBlockEntity printer ? printer : null;
    }
}
