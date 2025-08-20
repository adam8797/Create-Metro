package com.adam8797.create_metro.config;

import com.adam8797.create_metro.CreateMetro;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.config.ConfigBase;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

// Pulled from the phenomenal Create: Connected mod
public abstract class SyncConfigBase extends ConfigBase {

    public final CompoundTag getSyncConfig() {
        CompoundTag nbt = new CompoundTag();
        writeSyncConfig(nbt);
        if (children != null)
            for (ConfigBase child : children) {
                if (child instanceof SyncConfigBase syncChild) {
                    if (nbt.contains(child.getName()))
                        throw new RuntimeException("A sync config key starts with " + child.getName() + " but does not belong to the child");
                    nbt.put(child.getName(), syncChild.getSyncConfig());
                }
            }
        return nbt;
    }

    protected void writeSyncConfig(CompoundTag nbt) {
    }

    public final void setSyncConfig(CompoundTag config) {
        if (children != null)
            for (ConfigBase child : children) {
                if (child instanceof SyncConfigBase syncChild) {
                    CompoundTag nbt = config.getCompound(child.getName());
                    syncChild.readSyncConfig(nbt);
                }
            }
        readSyncConfig(config);
    }

    protected void readSyncConfig(CompoundTag nbt) {
    }

    @Override
    public void onLoad() {
        super.onLoad();
        syncToAllPlayers();
    }

    @Override
    public void onReload() {
        super.onReload();
        syncToAllPlayers();
    }

    public void syncToAllPlayers() {
        CatnipServices.PLATFORM.executeOnServerOnly(() -> () -> {
            if (ServerLifecycleHooks.getCurrentServer() == null) return;
            CreateMetro.LOGGER.debug("Sync Config: Sending server config to all players on reload");
            PacketDistributor.sendToAllPlayers(new SyncConfig(getSyncConfig()));
        });
    }

    public void syncToPlayer(ServerPlayer player) {
        if (player == null) return;
        CatnipServices.PLATFORM.executeOnServerOnly(() -> () -> {
            CreateMetro.LOGGER.debug("Sync Config: Sending server config to {}", player.getScoreboardName());
            PacketDistributor.sendToPlayer(player, new SyncConfig(getSyncConfig()));
        });
    }

    protected void registerAsSyncRoot(final RegisterPayloadHandlersEvent event, final String version) {
        final PayloadRegistrar registrar = event.registrar(version);
        registrar.configurationToClient(
                SyncConfig.TYPE,
                SyncConfig.STREAM_CODEC,
                this::handleData
        );
        registrar.playToClient(
                SyncConfig.TYPE,
                SyncConfig.STREAM_CODEC,
                this::handleData
        );
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer serverPlayer) {
                syncToPlayer(serverPlayer);
            }
        });
    }

    public void handleData(final SyncConfig data, final IPayloadContext context) {
        this.setSyncConfig(data.nbt());
        CreateMetro.LOGGER.debug("Sync Config: Received and applied server config {}", data.nbt().toString());
    }

    public record SyncConfig(CompoundTag nbt) implements CustomPacketPayload {
        public static final Type<SyncConfig> TYPE = new Type<>(CreateMetro.asResource("sync_config"));

        public static final StreamCodec<ByteBuf, SyncConfig> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG,
                SyncConfig::nbt,
                SyncConfig::new
        );

        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static abstract class SyncConfigTask implements ICustomConfigurationTask {
        public static final ConfigurationTask.Type TYPE = new Type(CreateMetro.asResource("sync_config_task"));
        private final ServerConfigurationPacketListener listener;

        public SyncConfigTask(ServerConfigurationPacketListener listener) {
            this.listener = listener;
        }

        protected abstract SyncConfigBase getSyncConfig();

        @Override
        public void run(final Consumer<CustomPacketPayload> sender) {
            final SyncConfig payload = new SyncConfig(getSyncConfig().getSyncConfig());
            sender.accept(payload);
            listener.finishCurrentTask(this.type());
        }

        @Override
        public @NotNull Type type() {
            return TYPE;
        }
    }
}
