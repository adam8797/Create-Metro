package com.adam8797.create_metro.config;


import com.adam8797.create_metro.CreateMetro;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.NotNull;

@EventBusSubscriber(modid = CreateMetro.MOD_ID)
public class MetroCommonConfig extends SyncConfigBase {

    @Override
    public @NotNull String getName() {
        return "common";
    }

    public final ConfigBool migrateCopycatsOnInitialize = b(true, "migrateCopycatsOnInitialize", Comments.migrateCopycatsOnInitialize);

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        MetroConfigs.common().registerAsSyncRoot(event, "2.0.0");
    }

    @SubscribeEvent
    public static void register(final RegisterConfigurationTasksEvent event) {
        event.register(new CommonSyncConfigTask(event.getListener()));
    }

    private static class Comments {
        static String migrateCopycatsOnInitialize = "Migrate copycats to Create: Copycats+ when their block entities are initialized";
    }

    public static class CommonSyncConfigTask extends SyncConfigTask {
        public CommonSyncConfigTask(ServerConfigurationPacketListener listener) {
            super(listener);
        }

        @Override
        protected SyncConfigBase getSyncConfig() {
            return MetroConfigs.common();
        }
    }
}