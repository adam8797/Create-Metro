package com.adam8797.create_metro.content.turnstile;

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
import dev.ithundxr.createnumismatics.content.bank.IDCardItem;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TurnstileBlockEntity extends SmartBlockEntity implements Trusted {

    /** How long (ticks) the gate stays open after a successful payment. */
    public static final int OPEN_DURATION = 30;
    /** How long (ticks) a player is exempt from re-charging after paying, so they can walk through. */
    private static final int IMMUNITY_TICKS = OPEN_DURATION + 20;
    /** Minimum ticks between charge attempts (and deny messages) for a given player. */
    private static final int ATTEMPT_THROTTLE = 20;

    @Nullable
    protected UUID owner;

    /** Account that collected fares are deposited into. When null, defaults to the owner's personal account. */
    @Nullable
    protected UUID destAccountId;

    /** Players (besides the owner) permitted to pass free and reconfigure this turnstile. */
    protected final List<UUID> trustList = new ArrayList<>();

    protected ScrollValueBehaviour fare;

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

    /** Called from the block on placement to seed the fare from config. */
    public void initFromConfig() {
        if (fare != null)
            fare.setValue(MetroServerConfig.DefaultTurnstileFare.get());
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Override
    public boolean isTrustedInternal(Player player) {
        // In dev, golden boots make you "staff" for quick testing (mirrors Numismatics' depositor).
        if (dev.ithundxr.createnumismatics.util.Utils.isDevEnv())
            return player.getItemBySlot(EquipmentSlot.FEET).is(Items.GOLDEN_BOOTS);
        return owner == null || owner.equals(player.getUUID()) || trustList.contains(player.getUUID());
    }

    // ------------------------------------------------------------------
    // Fare settlement
    // ------------------------------------------------------------------

    @Nullable
    private BankAccount resolveDestination() {
        if (destAccountId != null)
            return Numismatics.BANK.getAccount(destAccountId);
        if (owner != null)
            return Numismatics.BANK.getOrCreateAccount(owner, BankAccount.Type.PLAYER);
        return null;
    }

    /**
     * Deduct the fare from {@code source} and deposit it into the destination account.
     * Opens the gate on success. Returns false if the source lacks funds.
     */
    private boolean settleFare(Player player, @Nullable BankAccount source) {
        int amount = getFare();
        if (amount <= 0) {
            openGate();
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

        openGate();
        return true;
    }

    /** Walk-through path: always charges the player's personal account. */
    public void onPlayerWalkThrough(Player player) {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        if (getBlockState().getValue(TurnstileBlock.OPEN))
            return; // already open, let them pass

        long now = serverLevel.getGameTime();
        UUID id = player.getUUID();

        Long immune = immunityUntil.get(id);
        if (immune != null && now < immune) {
            openGate();
            return;
        }
        if (isTrusted(player)) {
            openGate();
            immunityUntil.put(id, now + IMMUNITY_TICKS);
            return;
        }
        Long nextAllowed = nextAttemptAllowed.get(id);
        if (nextAllowed != null && now < nextAllowed)
            return; // throttle repeated attempts while pressed against the gate
        nextAttemptAllowed.put(id, now + ATTEMPT_THROTTLE);

        BankAccount source = Numismatics.BANK.getAccount(player);
        if (settleFare(player, source)) {
            immunityUntil.put(id, now + IMMUNITY_TICKS);
        } else {
            deny(player);
        }
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
        if (!settleFare(player, source))
            deny(player);
    }

    // ------------------------------------------------------------------
    // Configuration (owner / trusted only)
    // ------------------------------------------------------------------

    public void setDestinationFromCard(ItemStack cardStack, Player player) {
        UUID accountId = CardItem.get(cardStack);
        if (accountId == null) {
            player.displayClientMessage(Component.translatable("create_metro.turnstile.card_blank")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        destAccountId = accountId;
        notifyUpdate();
        Component name = destinationName();
        player.displayClientMessage(Component.translatable("create_metro.turnstile.destination_set", name)
                .withStyle(ChatFormatting.GREEN), true);
    }

    public void resetDestination(Player player) {
        destAccountId = null;
        notifyUpdate();
        player.displayClientMessage(Component.translatable("create_metro.turnstile.destination_reset")
                .withStyle(ChatFormatting.GREEN), true);
    }

    public void toggleTrust(ItemStack idCardStack, Player player) {
        UUID target = IDCardItem.get(idCardStack);
        if (target == null) {
            player.displayClientMessage(Component.translatable("create_metro.turnstile.card_blank")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (trustList.remove(target)) {
            player.displayClientMessage(Component.translatable("create_metro.turnstile.trust_removed")
                    .withStyle(ChatFormatting.YELLOW), true);
        } else {
            trustList.add(target);
            player.displayClientMessage(Component.translatable("create_metro.turnstile.trust_added")
                    .withStyle(ChatFormatting.GREEN), true);
        }
        notifyUpdate();
    }

    public void showStatus(Player player) {
        player.displayClientMessage(Component.translatable("create_metro.turnstile.status_fare", getFare())
                .withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.translatable("create_metro.turnstile.status_destination", destinationName())
                .withStyle(ChatFormatting.GRAY), false);
    }

    private Component destinationName() {
        BankAccount destination = (level instanceof ServerLevel) ? resolveDestination() : null;
        if (destination != null)
            return destination.getDisplayName();
        return Component.translatable("create_metro.turnstile.destination_none");
    }

    // ------------------------------------------------------------------
    // Gate control
    // ------------------------------------------------------------------

    private void openGate() {
        if (level == null)
            return;
        BlockState state = getBlockState();
        if (!state.getValue(TurnstileBlock.OPEN))
            level.setBlock(worldPosition, state.setValue(TurnstileBlock.OPEN, true), 3);
        level.playSound(null, worldPosition, SoundEvents.ARROW_HIT_PLAYER, SoundSource.BLOCKS, 0.6f, 1.2f);
        if (!level.getBlockTicks().hasScheduledTick(worldPosition, state.getBlock()))
            level.scheduleTick(worldPosition, state.getBlock(), OPEN_DURATION);
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
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (owner != null)
            tag.putUUID("Owner", owner);
        if (destAccountId != null)
            tag.putUUID("DestAccount", destAccountId);
        if (!trustList.isEmpty()) {
            tag.put("TrustList", NBTHelper.writeCompoundList(trustList, uuid -> {
                CompoundTag t = new CompoundTag();
                t.putUUID("UUID", uuid);
                return t;
            }));
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        destAccountId = tag.hasUUID("DestAccount") ? tag.getUUID("DestAccount") : null;
        trustList.clear();
        if (tag.contains("TrustList", Tag.TAG_LIST)) {
            trustList.addAll(NBTHelper.readCompoundList(
                    tag.getList("TrustList", Tag.TAG_COMPOUND),
                    t -> t.getUUID("UUID")));
        }
    }
}
