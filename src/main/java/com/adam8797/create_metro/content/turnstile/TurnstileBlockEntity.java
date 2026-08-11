package com.adam8797.create_metro.content.turnstile;

import com.adam8797.create_metro.MetroMenuTypes;
import com.adam8797.create_metro.config.MetroServerConfig;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import dev.ithundxr.createnumismatics.content.backend.Trusted;
import dev.ithundxr.createnumismatics.content.bank.CardItem;
import dev.ithundxr.createnumismatics.content.backend.trust_list.TrustListContainer;
import dev.ithundxr.createnumismatics.registry.NumismaticsTags;
import dev.ithundxr.createnumismatics.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TurnstileBlockEntity extends SmartBlockEntity implements Trusted, MenuProvider {

    /** How long (ticks) the gate stays open after a successful payment. */
    public static final int OPEN_DURATION = 30;
    /** How long (ticks) a player is exempt from re-charging after paying, so they can walk through. */
    private static final int IMMUNITY_TICKS = OPEN_DURATION + 20;
    /** Minimum ticks between charge attempts (and deny messages) for a given player. */
    private static final int ATTEMPT_THROTTLE = 20;

    @Nullable
    protected UUID owner;

    /** Players (besides the owner) permitted to pass free. Derived from {@link #trustListContainer}. */
    protected final List<UUID> trustList = new ArrayList<>();

    /** Holds the ID cards of trusted riders (edited via the GUI); rebuilds {@link #trustList} on change. */
    public final TrustListContainer trustListContainer = new TrustListContainer(trustList, this::setChanged);

    protected ScrollValueBehaviour fare;

    /** When true, even the owner and trusted riders are charged (useful for testing or paid staff). */
    protected boolean chargeTrusted = false;

    /**
     * Holds the (optional) bank card whose account collected fares are deposited into.
     * When empty, fares default to the owner's personal account.
     */
    public final Container cardContainer = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            TurnstileBlockEntity.this.setChanged();
        }
    };

    // Transient throttling state, not persisted.
    private final Map<UUID, Long> immunityUntil = new HashMap<>();
    private final Map<UUID, Long> nextAttemptAllowed = new HashMap<>();

    public TurnstileBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        int max = Math.max(1, MetroServerConfig.MaxTurnstileFare.get());
        fare = new ScrollValueBehaviour(Component.translatable("create_metro.turnstile.fare"), this,
                new CenteredSideValueBoxTransform())
                .between(0, max)
                .withFormatter(i -> i == 0 ? "Free" : String.valueOf(i))
                .requiresWrench();
        behaviours.add(fare);
    }

    public int getFare() {
        return fare != null ? fare.getValue() : MetroServerConfig.DefaultTurnstileFare.get();
    }

    /** Clamp and apply a new fare. Used by the GUI configuration packet. */
    public void setFare(int amount) {
        if (fare != null)
            fare.setValue(amount); // ScrollValueBehaviour clamps to its configured range
    }

    /** Called from the block on placement to seed the fare from config. */
    public void initFromConfig() {
        if (fare != null)
            fare.setValue(MetroServerConfig.DefaultTurnstileFare.get());
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public boolean getChargeTrusted() {
        return chargeTrusted;
    }

    public void setChargeTrusted(boolean value) {
        this.chargeTrusted = value;
        notifyUpdate();
    }

    /**
     * Apply fare + charge-trusted to this gate AND its merged partners, so a double gate stays
     * configured as one unit. Also clears cached free-pass state so the change takes effect at once.
     */
    public void applyConfig(int fare, boolean chargeTrusted) {
        applyConfigLocal(fare, chargeTrusted);
        BlockState state = getBlockState();
        Direction facing = state.getValue(TurnstileBlock.HORIZONTAL_FACING);
        if (state.getValue(TurnstileBlock.MERGE_LEFT))
            applyConfigToPartner(worldPosition.relative(facing.getCounterClockWise()), fare, chargeTrusted);
        if (state.getValue(TurnstileBlock.MERGE_RIGHT))
            applyConfigToPartner(worldPosition.relative(facing.getClockWise()), fare, chargeTrusted);
    }

    private void applyConfigToPartner(BlockPos partnerPos, int fare, boolean chargeTrusted) {
        if (level != null && level.getBlockEntity(partnerPos) instanceof TurnstileBlockEntity partner)
            partner.applyConfigLocal(fare, chargeTrusted);
    }

    private void applyConfigLocal(int fare, boolean chargeTrusted) {
        setFare(fare);
        this.chargeTrusted = chargeTrusted;
        immunityUntil.clear();
        nextAttemptAllowed.clear();
        notifyUpdate();
    }

    @Override
    public boolean isTrustedInternal(Player player) {
        if (owner == null || owner.equals(player.getUUID()) || trustList.contains(player.getUUID()))
            return true;
        // Dev convenience (mirrors Numismatics): golden boots make you staff for quick self-testing.
        return Utils.isDevEnv() && player.getItemBySlot(EquipmentSlot.FEET).is(Items.GOLDEN_BOOTS);
    }

    // ------------------------------------------------------------------
    // Fare settlement
    // ------------------------------------------------------------------

    /** The bank card currently linking this turnstile to a deposit account, or empty. */
    public ItemStack getLinkedCard() {
        ItemStack card = cardContainer.getItem(0);
        return NumismaticsTags.AllItemTags.CARDS.matches(card) ? card : ItemStack.EMPTY;
    }

    @Nullable
    private BankAccount resolveDestination() {
        ItemStack card = getLinkedCard();
        if (!card.isEmpty()) {
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

    /**
     * Deduct the fare from {@code source} and deposit it into the destination account.
     * Opens the gate on success. Returns false if the source lacks funds.
     */
    private boolean settleFare(Player player, @Nullable BankAccount source, boolean reversed) {
        int amount = getFare();
        if (amount <= 0) {
            openGate(reversed);
            return true;
        }
        if (source == null)
            return false;
        if (!source.deduct(amount))
            return false;

        BankAccount destination = resolveDestination();
        if (destination != null)
            destination.deposit(amount);
        else
            com.adam8797.create_metro.CreateMetro.LOGGER.warn(
                    "Turnstile at {} charged {} spurs with no destination account; funds discarded.", worldPosition, amount);

        openGate(reversed);
        return true;
    }

    /**
     * Directional walk-through: crossing WITH the gate's facing (entry) charges the player's personal
     * account; crossing AGAINST it (exit) is free — the trip has ended and they may leave.
     */
    public void onPlayerWalkThrough(Player player) {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        if (getBlockState().getValue(TurnstileBlock.OPEN))
            return; // already open, let them pass

        long now = serverLevel.getGameTime();
        UUID id = player.getUUID();

        if (!isEntering(player)) {
            // free egress — swing open in the direction of travel (backward, against facing)
            openGate(true);
            immunityUntil.put(id, now + IMMUNITY_TICKS);
            return;
        }

        // From here on the player is entering (crossing with the facing) → gate swings forward.
        Long immune = immunityUntil.get(id);
        if (immune != null && now < immune) {
            openGate(false);
            return;
        }
        if (!chargeTrusted && isTrusted(player)) {
            openGate(false);
            immunityUntil.put(id, now + IMMUNITY_TICKS);
            return;
        }
        Long nextAllowed = nextAttemptAllowed.get(id);
        if (nextAllowed != null && now < nextAllowed)
            return; // throttle repeated attempts while pressed against the gate
        nextAttemptAllowed.put(id, now + ATTEMPT_THROTTLE);

        BankAccount source = Numismatics.BANK.getAccount(player);
        if (settleFare(player, source, false)) {
            immunityUntil.put(id, now + IMMUNITY_TICKS);
        } else {
            deny(player);
        }
    }

    /** True if the player is crossing in the gate's facing direction (an entry that should be charged). */
    private boolean isEntering(Player player) {
        Direction facing = getBlockState().getValue(TurnstileBlock.HORIZONTAL_FACING);
        Vec3i normal = facing.getNormal();
        double dx = player.getX() - (worldPosition.getX() + 0.5);
        double dz = player.getZ() - (worldPosition.getZ() + 0.5);
        // Player standing on the side opposite the facing normal is approaching to cross with the facing.
        return dx * normal.getX() + dz * normal.getZ() < 0;
    }

    /** Right-click with a bank card: charge that card's account. */
    public void payWithCard(ItemStack cardStack, Player player) {
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
        if (!settleFare(player, source, !isEntering(player)))
            deny(player);
    }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    public void showStatus(Player player) {
        player.displayClientMessage(Component.translatable("create_metro.turnstile.status_fare", getFare())
                .withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.translatable("create_metro.turnstile.status_destination", destinationName())
                .withStyle(ChatFormatting.GRAY), false);
    }

    /** Server-side display name of the deposit destination. */
    private Component destinationName() {
        BankAccount destination = (level instanceof ServerLevel) ? resolveDestination() : null;
        if (destination != null)
            return destination.getDisplayName();
        return Component.translatable("create_metro.turnstile.destination_none");
    }

    // ------------------------------------------------------------------
    // Gate control
    // ------------------------------------------------------------------

    /** Open the gate; {@code reversed} swings the leaf against the facing (used for free egress). */
    private void openGate(boolean reversed) {
        openGate(reversed, true);
    }

    /** {@code propagate} also opens merged neighbours, so a double gate swings as one unit. */
    private void openGate(boolean reversed, boolean propagate) {
        if (level == null)
            return;
        BlockState state = getBlockState();
        BlockState target = state.setValue(TurnstileBlock.OPEN, true).setValue(TurnstileBlock.REVERSED, reversed);
        if (state != target)
            level.setBlock(worldPosition, target, 3);
        level.playSound(null, worldPosition, SoundEvents.ARROW_HIT_PLAYER, SoundSource.BLOCKS, 0.6f, 1.2f);
        if (!level.getBlockTicks().hasScheduledTick(worldPosition, state.getBlock()))
            level.scheduleTick(worldPosition, state.getBlock(), OPEN_DURATION);

        if (propagate) {
            Direction facing = state.getValue(TurnstileBlock.HORIZONTAL_FACING);
            if (state.getValue(TurnstileBlock.MERGE_LEFT))
                openPartner(worldPosition.relative(facing.getCounterClockWise()), reversed);
            if (state.getValue(TurnstileBlock.MERGE_RIGHT))
                openPartner(worldPosition.relative(facing.getClockWise()), reversed);
        }
    }

    private void openPartner(BlockPos partnerPos, boolean reversed) {
        if (level != null && level.getBlockEntity(partnerPos) instanceof TurnstileBlockEntity partner)
            partner.openGate(reversed, false); // no re-propagation, avoids ping-pong
    }

    private void deny(Player player) {
        player.displayClientMessage(Component.translatable("create_metro.turnstile.insufficient_funds")
                .withStyle(ChatFormatting.DARK_RED), true);
        playDenySound();
    }

    private void playDenySound() {
        if (level != null)
            level.playSound(null, worldPosition, AllSoundEvents.DENY.getMainEvent(), SoundSource.BLOCKS, 0.5f, 1.0f);
    }

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.create_metro.turnstile");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new TurnstileMenu(MetroMenuTypes.TURNSTILE.get(), id, inventory, this);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (owner != null)
            tag.putUUID("Owner", owner);
        tag.putBoolean("ChargeTrusted", chargeTrusted);
        if (!cardContainer.getItem(0).isEmpty())
            tag.put("Card", cardContainer.getItem(0).save(registries));
        if (!trustListContainer.isEmpty())
            tag.put("TrustList", trustListContainer.save(new CompoundTag(), registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        chargeTrusted = tag.getBoolean("ChargeTrusted");

        ItemStack card = tag.contains("Card", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound("Card"))
                : ItemStack.EMPTY;
        cardContainer.setItem(0, card);

        trustList.clear();
        trustListContainer.clearContent();
        if (tag.contains("TrustList", Tag.TAG_COMPOUND))
            trustListContainer.load(tag.getCompound("TrustList"), registries);
    }
}
