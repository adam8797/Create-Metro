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
import net.minecraft.nbt.ListTag;
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
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TurnstileBlockEntity extends SmartBlockEntity implements Trusted, MenuProvider, GeoBlockEntity {

    /** How long (ticks) the arm stays visually swung open after a successful payment. */
    public static final int OPEN_DURATION = 30;
    /** How long (ticks) a paid player may walk through — the barrier is passable only for them. */
    private static final int PASS_TICKS = OPEN_DURATION + 20;
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

    /** Players allowed to pass (barrier is empty only for them), keyed by expiry game-time. Synced to
     *  clients so their collision prediction matches; not persisted. */
    private final Map<UUID, Long> authorizedUntil = new HashMap<>();
    /** Server-only throttle for repeated charge attempts / deny messages. */
    private final Map<UUID, Long> nextAttemptAllowed = new HashMap<>();

    /** GeckoLib animation cache + the swing animation played while the gate is OPEN. */
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlayAndHold("open");

    public TurnstileBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !authorizedUntil.isEmpty()) {
            long now = level.getGameTime();
            authorizedUntil.values().removeIf(until -> now >= until);
        }
    }

    // ------------------------------------------------------------------
    // GeckoLib animation
    // ------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "swing", 5, this::swingState));
    }

    private PlayState swingState(AnimationState<TurnstileBlockEntity> state) {
        if (getBlockState().getValue(TurnstileBlock.OPEN))
            return state.setAndContinue(OPEN_ANIM);
        return PlayState.STOP; // transition back to the closed pose
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** True while the player is allowed to walk through (barrier is empty only for them). */
    public boolean isAuthorized(Player player) {
        Long until = authorizedUntil.get(player.getUUID());
        return until != null && level != null && level.getGameTime() < until;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        int max = Math.max(1, MetroServerConfig.MaxTurnstileFare.get());
        fare = new FareScrollValueBehaviour(Component.translatable("create_metro.turnstile.fare"), this,
                new CenteredSideValueBoxTransform())
                .between(0, max) // clamp stays at the configured maximum; the wrench board is capped compactly
                .withCallback(this::onFareChanged) // keep a merged pair's fare in sync when set via the wrench
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

    /** Fare changed in-world (wrench scroll / value board) — copy it to the merged partner. Loop-safe
     *  because ScrollValueBehaviour.setValue no-ops when the value is unchanged. */
    private void onFareChanged(int value) {
        if (level == null || level.isClientSide)
            return;
        for (TurnstileBlockEntity partner : mergedGroup())
            if (partner != this)
                partner.setFare(value);
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
     * Apply fare + charge-trusted from the GUI, then push the WHOLE configuration (owner, fare,
     * charge-trusted, deposit card, and trusted-rider cards) to every merged partner — a double gate
     * is configured as one unit. Also clears cached free-pass state so the change takes effect at once.
     */
    public void applyConfig(int fare, boolean chargeTrusted) {
        setFare(fare);
        this.chargeTrusted = chargeTrusted;
        authorizedUntil.clear();
        nextAttemptAllowed.clear();
        notifyUpdate();
        syncConfigToGroup();
    }

    /** Push this gate's full configuration onto every other gate in the merged group. */
    public void syncConfigToGroup() {
        for (TurnstileBlockEntity partner : mergedGroup())
            if (partner != this)
                partner.copyConfigFrom(this);
    }

    /** On merge, adopt the configuration of an already-placed group member (the older gate wins). */
    public void adoptGroupConfig() {
        for (TurnstileBlockEntity partner : mergedGroup())
            if (partner != this) {
                copyConfigFrom(partner);
                return;
            }
    }

    /** Copy the full configuration of {@code source} into this gate (cards are copied, not moved). */
    public void copyConfigFrom(TurnstileBlockEntity source) {
        this.owner = source.owner;
        this.chargeTrusted = source.chargeTrusted;
        setFare(source.getFare());
        cardContainer.setItem(0, source.cardContainer.getItem(0).copy());
        for (int i = 0; i < trustListContainer.getContainerSize(); i++)
            trustListContainer.setItem(i, source.trustListContainer.getItem(i).copy());
        trustListContainer.setChanged(); // rebuild the derived trustList
        authorizedUntil.clear();
        nextAttemptAllowed.clear();
        notifyUpdate();
    }

    /** All turnstile block entities in this merged group (including self), via BFS over merge links. */
    private List<TurnstileBlockEntity> mergedGroup() {
        List<TurnstileBlockEntity> group = new ArrayList<>();
        if (level == null) {
            group.add(this);
            return group;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<TurnstileBlockEntity> queue = new ArrayDeque<>();
        queue.add(this);
        seen.add(worldPosition);
        while (!queue.isEmpty()) {
            TurnstileBlockEntity be = queue.poll();
            group.add(be);
            BlockState st = be.getBlockState();
            Direction f = st.getValue(TurnstileBlock.HORIZONTAL_FACING);
            if (st.getValue(TurnstileBlock.MERGE_LEFT))
                enqueueGroup(be.worldPosition.relative(f.getCounterClockWise()), seen, queue);
            if (st.getValue(TurnstileBlock.MERGE_RIGHT))
                enqueueGroup(be.worldPosition.relative(f.getClockWise()), seen, queue);
        }
        return group;
    }

    private void enqueueGroup(BlockPos pos, Set<BlockPos> seen, ArrayDeque<TurnstileBlockEntity> queue) {
        if (seen.add(pos) && level != null && level.getBlockEntity(pos) instanceof TurnstileBlockEntity be)
            queue.add(be);
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
     * Deduct the fare from {@code source} and deposit it into the destination account. Returns true on
     * success (or a free fare); does NOT grant passage — the caller does that via {@link #grantPass}.
     */
    private boolean chargeFare(@Nullable BankAccount source) {
        int amount = getFare();
        if (amount <= 0)
            return true;
        if (source == null || !source.deduct(amount))
            return false;

        BankAccount destination = resolveDestination();
        if (destination != null)
            destination.deposit(amount);
        else
            com.adam8797.create_metro.CreateMetro.LOGGER.warn(
                    "Turnstile at {} charged {} spurs with no destination account; funds discarded.", worldPosition, amount);
        return true;
    }

    /**
     * Directional walk-through: crossing WITH the gate's facing (entry) charges the player's personal
     * account; crossing AGAINST it (exit) is free — the trip has ended and they may leave.
     */
    public void onPlayerWalkThrough(Player player) {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        if (isAuthorized(player))
            return; // already allowed through — barrier is empty for them

        if (!isEntering(player)) {
            grantPass(player, true); // free egress (trip ended); swing against the facing
            return;
        }
        if (!chargeTrusted && isTrusted(player)) {
            grantPass(player, false);
            return;
        }

        long now = serverLevel.getGameTime();
        UUID id = player.getUUID();
        Long nextAllowed = nextAttemptAllowed.get(id);
        if (nextAllowed != null && now < nextAllowed)
            return; // throttle repeated attempts while pressed against the gate
        nextAttemptAllowed.put(id, now + ATTEMPT_THROTTLE);

        if (chargeFare(Numismatics.BANK.getAccount(player)))
            grantPass(player, false);
        else
            deny(player);
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
        if (chargeFare(source))
            grantPass(player, !isEntering(player));
        else
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

    /**
     * Grant a player passage: mark them authorized (barrier becomes empty only for them) and swing the
     * arm open. Applies across the whole merged group, so a double gate lets its payer through and
     * swings as one unit — while everyone else stays blocked (no tailgating).
     */
    private void grantPass(Player player, boolean reversed) {
        if (level == null)
            return;
        long until = level.getGameTime() + PASS_TICKS;
        for (TurnstileBlockEntity gate : mergedGroup()) {
            gate.authorizedUntil.put(player.getUUID(), until);
            gate.openVisual(reversed);
            gate.notifyUpdate(); // sync authorizedUntil to clients for smooth collision prediction
        }
    }

    /** Swing this gate's arm open (visual only; collision is per-player via {@link #isAuthorized}). */
    private void openVisual(boolean reversed) {
        if (level == null)
            return;
        BlockState state = getBlockState();
        BlockState target = state.setValue(TurnstileBlock.OPEN, true).setValue(TurnstileBlock.REVERSED, reversed);
        if (state != target)
            level.setBlock(worldPosition, target, 3);
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

        // Sync (but don't persist) who may currently pass, so client collision prediction matches.
        if (clientPacket) {
            ListTag list = new ListTag();
            for (Map.Entry<UUID, Long> e : authorizedUntil.entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putUUID("U", e.getKey());
                t.putLong("T", e.getValue());
                list.add(t);
            }
            tag.put("Authorized", list);
        }
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

        if (clientPacket) {
            authorizedUntil.clear();
            for (Tag t : tag.getList("Authorized", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                authorizedUntil.put(c.getUUID("U"), c.getLong("T"));
            }
        }
    }
}
